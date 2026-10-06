# Plan: Remove Push-to-Talk + Make Ghost Typing Throttle Configurable

Two changes, spanning VogonPoet (frontend) and the bundled babelfish backend (`F:\dev\babelfish`, sibling repo).

## Part 1: Remove push-to-talk (`force_listen`) entirely

PTT is implemented as `force_listen` (held key, default Left Shift) in both the frontend and the backend. The separate `toggle_listening` shortcut (default Ctrl+Space) **stays**.

### VogonPoet frontend

1. `composeApp/src/commonMain/kotlin/ovh/devcraft/vogonpoet/domain/model/VogonConfig.kt:71` — drop `Shortcuts.forceListen` (keep `toggleListening`)
2. `composeApp/src/commonMain/kotlin/ovh/devcraft/vogonpoet/infrastructure/mapper/VogonConfigMapper.kt:67,161` — drop both `force_listen ↔ forceListen` mappings
3. `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/components/ConfigForm.kt:211-230` — remove the "Push to Talk" selector block from the Shortcuts card (card stays for Toggle Listening; helper text at 232-237 describes toggle behavior and stays)
4. Delete `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/components/SingleKeySelector.kt` — widget used only by the PTT selector
5. `composeApp/src/commonMain/kotlin/ovh/devcraft/vogonpoet/domain/BabelfishClient.kt:31` + `composeApp/src/commonMain/kotlin/ovh/devcraft/vogonpoet/infrastructure/BabelfishClient.kt:448-453` — remove the `forceListen()` interface method and its `{"type":"force_listen"}` message
6. `composeApp/src/commonMain/kotlin/ovh/devcraft/vogonpoet/presentation/MainViewModel.kt:208-217` — remove `forceListen()` (dead code, no UI callers)
7. `composeApp/src/commonTest/kotlin/ovh/devcraft/vogonpoet/presentation/MainViewModelTest.kt:86` — remove the fake override
8. `composeApp/src/commonMain/resources/schema/babelfish_schema.json` — remove `ShortcutsConfig.force_listen` (regenerated, see backend)

### Babelfish backend

9. `src/babelfish_stt/config.py:77` — drop `ShortcutsConfig.force_listen` field
10. `src/babelfish_stt/hotkey_manager.py` — remove all PTT logic:
    - `ptt_key` field, PTT branches in `_on_press`/`_on_release` (106-126 logic ranges)
    - PTT setup in `start()` (`ptt_str = config.ui.shortcuts.force_listen`, `self.ptt_key = ...`)
    - `Starting HotkeyManager: PTT=..., Toggle=...` log line → `Starting HotkeyManager: Toggle=...`
    - `_parse_key` + `_key_map` (only used by PTT)
    - update class docstring ("Supports both 'Hold to Talk' (PTT) and ...")
11. `src/babelfish_stt/server.py:268-278` — remove the `force_listen` websocket message branch (keep `toggle_listening`)
12. `src/babelfish_stt/main.py:115` — `Waiting for trigger (Hotkey/PTT)...` → strip PTT mention
13. Regenerate `babelfish/babelfish_schema.json` via `scripts/generate_schema.py` and copy to VogonPoet's `composeApp/src/commonMain/resources/schema/`
14. Docs: remove PTT references in `babelfish/AGENTS.md:23`, `readme.md:58,167`, `launcher.md:139`; optionally write a short ADR

### Regenerate frontend codegen

After schema change, the generated `Babelfish` model (from `JSONSchemaCodegen`, `composeApp/build.gradle.kts:265-276`) regenerates on next build — no checked-in source.

### Migration

None needed:
- Pydantic silently ignores unknown keys → stale `force_listen` in old `babelfish.config.json` keeps loading
- Kotlin parses server config with `ignoreUnknownKeys = true` (`BabelfishClient.kt:30`)

### No VogonPoet tests assert shortcuts

`BabelfishClientTest.kt`, `DomainModelTest.kt` — no shortcut assertions (verified).

---

## Part 2: Make the input-manager ghost throttle configurable

The delay is `TYPING_THROTTLE_S = 0.35` (hardcoded, `babelfish/src/babelfish_stt/input_manager.py:37`) — the minimum spacing between keystroke applies into the focused application.

### Babelfish backend

1. `src/babelfish_stt/config.py` → `SystemInputConfig` gets a new field:
   `typing_throttle_ms: int = 350` (milliseconds, consistent with `silence_threshold_ms`; lives next to `enabled`/`type_ghost` since it governs typed output)
2. Regenerate schema + copy to VogonPoet
3. `src/babelfish_stt/display.py` → `InputDisplay.update()`: before enqueuing, read `config.system_input.typing_throttle_ms` and set `self.simulator.throttle_s = typing_throttle_ms / 1000.0`. InputDisplay reads `config_manager.config` on every frame → hot-reloadable, no restart
4. `src/babelfish_stt/input_manager.py` keeps `throttle_s` as an instance attribute (already is); default stays `TYPING_THROTTLE_S = 0.35`
5. Optional: test in `tests/test_display_input.py` that changing the config hot-swaps the simulator's throttle

### VogonPoet UI (replicates the Silence Threshold slider pattern)

6. `domain/model/VogonConfig.kt` → `SystemInput` gets `typingThrottleMs: Long = 350`
7. `VogonConfigMapper.kt` — map `system_input.typing_throttle_ms ↔ typingThrottleMs` both directions
8. `AdvancedSettingsPanel.kt` → new slider in the **Pipeline Tuning** section right below Silence Threshold:
   - Label: `Typing Throttle: 350ms`
   - Range **50..600ms**, snapped to 50ms steps
   - Same `remember`-transient-state + `onValueChangeFinished` + Gruvbox slider colors pattern as lines 431-466
   - `enabled = isReady`
   - Flows through existing `onConfigChange → updateDraft + saveConfig` websocket path (`App.kt:120-126`)

### Explicitly NOT touched

`PerformanceProfile.ghost_throttle_ms` — owned by the startup calibration benchmark (ADR 0011), server-calibrated, throttles submits to the *STT worker*, not typing into apps. VogonPoet mirrors it display-only.

---

## Open questions / judgment calls (from planning discussion)

1. **Slider range** — proposed 50..600ms snapped to 50. 0ms deliberately excluded: `input_manager.py` comments document that unpaced ~50ms applies flood editors and lost modifier key-ups cause catastrophic word-deletion backspace bugs. Default stays at the proven 350ms. Widen if more headroom wanted (e.g. up to 1000ms).
2. **Slider placement** — Pipeline Tuning section (next to Silence Threshold) rather than next to the "Type Ghost Output" toggle in ConfigForm.
3. **`ghost_throttle_ms` left alone** per ADR 0011 (calibration wins over local edits).

## Verification

- Babelfish: run test suite (esp. `test_input_simulator.py`, `test_display_input.py`, `test_schema.py` after regen)
- VogonPoet: `./gradlew :composeApp:cleanJvmTest :composeApp:jvmTest`, then `./gradlew :composeApp:run` and check the settings UI: PTT selector gone, Typing Throttle slider works and persists across restart
