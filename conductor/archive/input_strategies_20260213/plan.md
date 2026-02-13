# Plan: Configurable Input Strategies

## Phase 1: Babelfish Infrastructure & Config
- [x] Task: Update `babelfish/src/babelfish_stt/config.py` (6a2dedc)
    - Define `InputStrategy` Enum (`direct`, `clipboard`, `native`, `hybrid`).
    - Add `strategy` field to `SystemInputConfig` (default: `clipboard`).
- [x] Task: Generate updated `babelfish_schema.json` and propagate to VogonPoet. (842d8ad)
- [x] Task: Regenerate VogonPoet Kotlin models. (842d8ad)
- [x] Task: Conductor - User Manual Verification 'Phase 1: Babelfish Infrastructure & Config' (Protocol in workflow.md) [checkpoint: e70b5c3]

## Phase 2: Babelfish Strategy Implementation
- [x] Task: Create `babelfish/src/babelfish_stt/input_strategies.py` (b7289e2)
    - Implement abstract base class `InputStrategy`.
    - Implement `DirectStrategy` (wraps existing pynput logic).
    - Implement `ClipboardStrategy` (using `pyperclip` or internal logic + pynput for Paste shortcut).
    - Implement `NativeStrategy` (detects OS and calls `xdotool`, `powershell`, or `osascript`).
    - Implement `HybridStrategy` (checks char safety map).
- [x] Task: Refactor `InputSimulator` in `input_manager.py` (b7289e2)
    - To use the selected strategy for `finalize()`.
    - To ALWAYS use `DirectStrategy` for `update_ghost()` and `_clear_previous()`.
- [x] Task: Write unit tests for new strategies in `tests/test_input_strategies.py`. (b7289e2)
- [x] Task: Conductor - User Manual Verification 'Phase 2: Babelfish Strategy Implementation' (Protocol in workflow.md) [checkpoint: b7289e2]

## Phase 3: VogonPoet UI Integration
- [x] Task: Update `AdvancedSettingsPanel.kt` (9dec87e)
    - Add dropdown menu for `Strategy` in the "System-wide Input" section.
    - Bind to `config.systemInput.strategy`.
- [x] Task: Conductor - User Manual Verification 'Phase 3: VogonPoet UI Integration' (Protocol in workflow.md) [checkpoint: e70b5c3]

## Phase 4: Verification [checkpoint: 569fa1e]
- [x] Task: Verify Ghost text still types smoothly (Direct input).
- [x] Task: Verify Final text pastes correctly (Clipboard strategy).
- [x] Task: Verify Special characters (emoji, accents) work with Clipboard/Hybrid.
- [x] Task: Conductor - User Manual Verification 'Phase 4: Verification' (Protocol in workflow.md)
