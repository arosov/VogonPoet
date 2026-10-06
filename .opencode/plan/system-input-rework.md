# Plan: System Input Mode Rework

> Rework of the system-wide input panel (VogonPoet UI) plus a matching new
> output-mode feature in babelfish. Status: planned, not started.

## Decisions (confirmed)

1. **No "off" state**: clipboard mode replaces "off". The radio pair always has
   one selection; the UI always sends `enabled = true` for this panel.
2. **Ghost default flips to `true`** in babelfish's schema (fresh installs get
   ghost typing on; existing configs keep their saved `type_ghost` value).
3. **Clipboard semantics**: every finished transcript **overwrites** the
   clipboard (final text + configured `separator`); nothing is typed anywhere,
   no paste chord, no backspaces. User pastes manually with Ctrl+V.
4. **Notification checkbox (new)**: a checkbox in the system-wide input panel,
   visible/active only when the clipboard radio is selected. Controls whether
   babelfish fires a systray notification each time a final transcript is
   inserted into the clipboard. **Default ON.** (No other master on/off
   control is being added — radio pair stays always-on.)

## Design model

New babelfish config field: `system_input.output` with values
`keyboard` / `clipboard`, default `keyboard`.

- Deliberately **not** reusing the removed `strategy` key (ADR 0009 in
  babelfish removed `strategy`: direct/clipboard/native/hybrid). A fresh field
  avoids ambiguity with the old dead values, which `config.py` ignores.
- The wire-level `enabled` field stays in the schema (babelfish CLI/headless
  usage still needs `enabled = false`, e.g. replay mode). Only the VogonPoet
  UI always sends `enabled = true`.

Semantics:

| Mode        | Ghost typing            | Final transcript                        |
|-------------|-------------------------|-----------------------------------------|
| `keyboard`  | typed live into focused window (today's behavior) | pasted into focused window + separator |
| `clipboard` | never typed, never erased | overwrites clipboard (final + separator), no paste |

- `type_ghost` has no effect in clipboard mode; the ghost checkbox is inert
  (disabled) there.
- New `system_input.notify_final` (boolean, default `true`): fire a systray
  notification on each final transcript copied to the clipboard. Only
  meaningful in clipboard mode.
- Mode changes take effect live (hot-reload, same pattern as
  `typing_throttle_ms`).

## Babelfish changes (`F:\dev\babelfish`)

1. **`src/babelfish_stt/config.py`** (~lines 144–156, `SystemInputConfig`):
   - Add `output: Literal["keyboard", "clipboard"] = "keyboard"`.
   - Change `type_ghost` default `False` → `True`.
   - Stale-key ignorance already handles old configs; no migration needed.
2. **`src/babelfish_stt/display.py`**:
   - `InputDisplay.update()` (~315–333): add `output == "clipboard"` to the
     early-return gate (alongside `enabled` / `type_ghost`) — no ghost enqueue.
   - `InputDisplay.finalize()` (~335–346): new clipboard branch — copy final
     text (with separator policy applied) to the clipboard; do **not** enqueue
     typing. **Exception**: if ghost text is currently on screen (user flipped
     mode mid-utterance), still enqueue one erase so nothing stale lingers.
     - Same branch fires the systray notification when
       `config.notify_final` is true (copy first, then notify).
3. **`src/babelfish_stt/input_strategies.py`**: add a clipboard-copy helper —
   plain `pyperclip.copy`, no paste chord, no clipboard restore (overwriting
   the clipboard is the point of the mode).
4. **Notifications** in babelfish:
   - Add a small notifier wrapper (e.g. `src/babelfish_stt/notifications.py`)
     around `notify-py` (already in `pyproject.toml:21` but currently unused
     anywhere in `src`). Guard failures (some Linux daemons missing) so a
     failed notify never breaks transcription. Fire per final insertion in
     clipboard mode, message style: "Babelfish — transcript copied".
5. **ADR**: new ADR pairing with ADR 0009 ("the input strategy is the
   clipboard") documenting the `output` + `notify_final` fields and that
   clipboard mode leaves the input simulator entirely idle.
6. **Schema**: add `output` and `notify_final` to the babelfish schema copy
   alongside `config.py`.
7. **Tests**: finalize in clipboard mode copies without typing; mode switch
   mid-utterance erases lingering ghost text; notification fires exactly once
   per final when `notify_final` is true, and not at all when false.

## VogonPoet changes

1. **Schema copy** `composeApp/src/commonMain/resources/schema/babelfish_schema.json`
   (`SystemInputConfig`, lines ~165–188): add `output` enum field and
   `notify_final` boolean (default `true`), flip `type_ghost` default to
   `true`. Regenerate wire classes via codegen (`codegen-config.json`) so
   `Babelfish.System_input` gains `output` + `notify_final`.
2. **`VogonConfig.kt`**
   (`composeApp/src/commonMain/kotlin/ovh/devcraft/vogonpoet/domain/model/VogonConfig.kt`,
   lines ~40–44): `SystemInput` gains `outputMode` (enum
   `KEYBOARD` / `CLIPBOARD`) and `notifyFinal: Boolean = true`;
   `typeGhost` default → `true`.
3. **`VogonConfigMapper.kt`**
   (`composeApp/src/commonMain/kotlin/ovh/devcraft/vogonpoet/infrastructure/mapper/VogonConfigMapper.kt`,
   ~lines 34–41 and 118–125): map `output ↔ outputMode` and
   `notify_final ↔ notifyFinal` in both directions.
4. **`ConfigForm.kt`**
   (`composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/components/ConfigForm.kt`,
   lines ~239–318) — the visible rework:
   - Replace the "Enable System Input" `Switch` with a **radio pair** (first
     radio usage in the app):
     - *"Type into focused window"* → `outputMode = KEYBOARD`
     - *"Copy transcript to clipboard"* → `outputMode = CLIPBOARD`
   - **New checkbox ("Notify on transcription")** under/next to the
     *Copy transcript to clipboard* radio: controls `notifyFinal` (systray
     notification per final inserted into the clipboard). Gating:
     - Only visible/enabled when radio = `CLIPBOARD` (it has no meaning in
       keyboard mode, where nothing is inserted into the clipboard).
     - Also gated on `isReady` like the other controls.
     - Default comes from `notifyFinal = true` via the new schema default.
   - Style to match existing tokens:
     `RadioButtonDefaults.colors(selectedColor = GruvboxGreenDark,
     unselectedColor = GruvboxGray)` (matches the `Checkbox` colors in
     `ModelRepositoryBrowserWindow.kt:286`).
   - Selection always saves `enabled = true` + chosen `outputMode` (+ the
     `notifyFinal` flag) via the existing immediate-save path
     (`App.kt:80–83` → `viewModel.updateDraft` + `viewModel.saveConfig`).
   - *Type Ghost Output* becomes a `Checkbox` (default-checked via new
     default), enabled only when radio = `KEYBOARD` **and** `isReady`
     (`Connected || Bootstrapping`, line 53) — same gating shape as today
     (line 311).
5. **Optional polish**: in `AdvancedSettingsPanel.kt`, dim/annotate the
   typing-throttle slider while in clipboard mode (it has no effect there).

## Consequence (intended, flagged)

Once the user touches this panel, there is **no UI path back to
`enabled = false`** — output is always live in one of the two modes. Turning
output off remains possible only via babelfish's own headless config.

## Verification

- `./gradlew :composeApp:cleanJvmTest :composeApp:jvmTest`
- `./gradlew :composeApp:build`
- Babelfish: run its test suite per babelfish repo conventions; manual test —
  connect, switch to clipboard mode, dictate a sentence, confirm Ctrl+V pastes
  exactly one transcript, confirm the systray notification fired once
  (and does not fire when the "Notify on transcription" checkbox is
  unchecked), and confirm on-screen ghost text is erased if the
  mode was flipped mid-utterance.
