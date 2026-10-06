# Transcript Telemetry Panel Plan

Rewrite of the transcript display in the systray UI: a three-section panel that makes the
ghost pass visible as a process, rather than having it silently replaced by the final
transcript. Only the **current occurrence** (last/current utterance) is shown.

Brainstorm output — decisions logged with the user on 2026-10-04/05:

- **Composed ghost source:** composed in babelfish (`ServerDisplay` runs the shared
  ADR 0010 `GhostComposer`) — single source of truth, no Kotlin port.
- **Committed/live rendering:** two-tone — committed words in normal fg, live tail gray
  italic ("revisable frontier").
- **Timing metrics:** server-side timings carried in the protocol (onset→first ghost,
  ghost inference ms, final-pass ms); client only stamps local arrival times for the
  row offsets.
- **Three-section split:** (1) raw ghost increments, (2) on-progress composed ghost,
  (3) final transcript + timing strip.
- **Collapse state:** session only (`remember { mutableStateOf(true) }`) — open by
  default on every startup, no config persistence.

---

## Phase 1: Babelfish protocol extension (sibling repo, do first)

VogonPoet's domain model consumes these fields, so the payload change lands first.

### 1.1 `src/babelfish_stt/display.py` — `ServerDisplay`

- Add a `GhostComposer` instance — third consumer of the shared ADR 0010 algorithm
  (after `TerminalDisplay` and `InputSimulator`), reset per utterance alongside
  `_last_ghost`/`_last_final`.
- `update(text, refined, ghost, window=None, **kwargs)`:
  - Feed the raw `ghost` + `window` into the composer (do this before the dedup
    check so composition state advances exactly like `TerminalDisplay`).
  - Broadcast payload gains:
    - `composed` — composer output (full composed line)
    - `committed` — composer's committed words, joined (stable part)
    - `live` — composer's live words, joined (revisable tail)
    - `window` — audio span `(start, end)` buffer samples (currently dropped)
    - `inference_ms` — pipeline `duration_ms` kwarg (currently dropped)
    - `onset_ms` — onset→first-ghost delay (first ghost only)
  - Keep `ghost` (raw) unchanged — Section 1 of the panel still shows raw windows.
  - `refined` still forwarded as today.
- `finalize(text, **kwargs)`:
  - Broadcast `final_pass_ms` (timing of the blocking final `engine.transcribe` call).
- `reset()`: `self._composer.reset()`.

### 1.2 `src/babelfish_stt/pipeline.py`

- `_consume_ghost_result`: stamp `onset_ms = now_ms - self._utterance_start_ms` on the
  first ghost (`_first_ghost_shown` already guards this; the value is already logged at
  line ~444). Pass through to `display.update(..., onset_ms=onset_ms)`.
- Final pass (line ~374-380): wrap `self.engine.transcribe(full_audio, ...)` with a
  `time.perf_counter()` delta; pass `final_pass_ms=` to `display.finalize(...)`.
- `MultiDisplay` already forwards `**kwargs`, so no changes there; verify pytest pins
  for other displays are unaffected by the new kwargs.

### 1.3 Tests (babelfish pytest)

- `ServerDisplay.update` broadcasts `composed`/`committed`/`live` consistent with the
  composer output for a mocked server.
- Dedup/rate-limit behavior unchanged from the client's point of view.

---

## Phase 2: VogonPoet domain + infrastructure

### 2.1 `domain/model/UtteranceTelemetry.kt` (new)

```kotlin
data class GhostIncrement(
    val raw: String,
    val inferenceMs: Float?,
    val arrivalOffsetMs: Long,   // local stamp: arrival − voice-detect arrival
)
data class UtteranceTelemetry(
    val utteranceId: Int = 0,               // bumped on reset / new listening edge
    val increments: List<GhostIncrement> = emptyList(),  // Section 1: full history
    val committed: String = "",             // Section 2, stable part
    val live: String = "",                  // Section 2, gray-italic tail
    val finalText: String? = null,          // Section 3
    val firstGhostDelayMs: Float? = null,   // server onset→first ghost
    val finalComputeMs: Float? = null,      // server final-pass duration
    val utteranceDurationMs: Float? = null, // span end / 16000
    val voiceDetectedArrivalMs: Long? = null, // local stamp of `listening` edge
)
```

### 2.2 `infrastructure/BabelfishClient.kt`

- New `MutableStateFlow<UtteranceTelemetry>` — separate from `TranscriptionState`;
  the existing `TranscriptionWindow` behavior is untouched.
- `handleIncomingLine`:
  - `"transcription"` (non-final): parse `ghost`, `composed`, `committed`, `live`,
    `window`, `inference_ms`, `onset_ms`; append a `GhostIncrement` (raw ghost kept
    **from the payload**, not re-composed); stamp local arrival offsets using
    `getCurrentTimeMillis()`; update section-2 fields.
  - `"transcription"` (final): set `finalText` + `finalComputeMs`; increments are
    **kept**, never replaced.
  - `"transcription_reset"` or a new `listening` VAD edge: bump `utteranceId`, clear
    sections/increments → the last completed utterance stays readable until the next
    one starts (current-occurrence semantics).
  - Use `window` end / 16000 for `utteranceDurationMs`.
- Expose as `utteranceTelemetry: StateFlow<UtteranceTelemetry>` on `BabelfishClient`
  (interface update in `domain/BabelfishClient.kt`).

### 2.3 `presentation/MainViewModel.kt`

- Proxy `val utteranceTelemetry: StateFlow<UtteranceTelemetry> = babelfishClient.utteranceTelemetry`.

---

## Phase 3: UI — `ui/components/TranscriptTelemetryCard.kt` (new, JVM)

Collapsible `Card` slotted directly below `StatusCard` in `App.kt` (top of the
`Column`, above `ConfigForm`).

```
┌ ▾ Ghost pass — current utterance ────────────────────────┐
│  1 · Ghost increments (raw window transcriptions)        │
│     👻 t=+210ms  "hello wor"                             │
│     👻 t=+430ms  "hello word this"                       │
│     👻 t=+780ms  "word this is a"   ← overlapping!       │
│  2 · On-progress ghost (composed, live)                  │
│     hello word |this is a|   committed vs revisable tail │
│  3 · Final                                               │
│     ✅ Hello word, this is a test.                       │
│     ⏱ onset→ghost 210ms · final pass 480ms · dur 1.24s   │
└──────────────────────────────────────────────────────────┘
```

- **Collapse:** header row with chevron (`▾`/`▸`) toggling `AnimatedVisibility`;
  `remember { mutableStateOf(true) }` — open at every startup, stays open until the
  user collapses it. No `VogonConfig` change.
- **Styling (gruvbox palette, matching `TranscriptionWindow` conventions):**
  - Section 1 rows: gray italic, one row per raw ghost, kept for the whole utterance.
  - Section 2: single live line — `committed` in normal fg color, `live` in gray
    italic. Updated in place with each ghost; never cleared by the final.
  - Section 3: `finalText` bold `GruvboxFg0`, plus the timing strip
    (onset→first ghost · final-pass ms · utterance duration).
- **Empty state:** "waiting for voice…" only when the latest `utteranceId` has no
  increments yet.
- Wire into `App.kt` between `StatusCard` and `ConfigForm`.

---

## Phase 4: Tests (VogonPoet)

- `commonTest`: telemetry reducer — payload parsing, `utteranceId` reset on
  `transcription_reset` / new listening edge, final arrival **keeps** increments
  (not a replace), arrival offset stamping.
- JVM compile check: `./gradlew :composeApp:compileKotlinJvm`
- JVM tests: `./gradlew :composeApp:cleanJvmTest :composeApp:jvmTest`

---

## Out of scope (parked)

- Audio-coverage bar under Section 2 reading `window` / `utteranceDurationMs`
  (how much of the utterance audio the live window covers).
- Persisting the collapsed state in `VogonConfig` (decided: session only).
- Any change to the existing `TranscriptionWindow` (separate opt-in window keeps
  today's replace-on-final behavior).
- Non-JVM targets for this panel (JVM-first; `App.kt` layout is JVM).

## Execution order

1. Babelfish Phase 1 (payload + timings + pytest) — natural first PR.
2. VogonPoet Phase 2 (domain + client parsing).
3. Phase 3 (panel UI) + Phase 4 (tests) together.
