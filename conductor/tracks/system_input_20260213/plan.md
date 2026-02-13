# Plan: System Input & Transcription Window

## Phase 1: Babelfish Infrastructure & Configuration [checkpoint: 486e581]
- [x] Task: Add `pynput` dependency to `babelfish/pyproject.toml` (bbf0ff7)
- [x] Task: Update `babelfish/src/babelfish_stt/config.py` with system input settings (9b1e22a)
- [x] Task: Generate updated `babelfish_schema.json` and propagate to VogonPoet (1233173)
- [x] Task: Regenerate VogonPoet Kotlin models (1233173)
- [x] Task: Conductor - User Manual Verification 'Phase 1: Babelfish Infrastructure & Configuration' (Protocol in workflow.md)


## Phase 2: Babelfish - System Input Implementation
- [ ] Task: Implement `InputSimulator` in `babelfish/src/babelfish_stt/input_manager.py`
    - Logic for typing strings and sending backspaces based on previous ghost length.
- [ ] Task: Write unit tests for `InputSimulator` (mocking `pynput.keyboard.Controller`)
- [ ] Task: Create `InputDisplay` in `babelfish/src/babelfish_stt/display.py`
    - A new display backend that handles the typing logic.
- [ ] Task: Integrate `InputDisplay` into `MultiDisplay` in `main.py`
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Babelfish - System Input Implementation' (Protocol in workflow.md)

## Phase 3: VogonPoet - Settings & Tray Integration
- [ ] Task: Update `AdvancedSettingsPanel.kt` to include new toggles
    - Enable System Input, Type Ghost Output, Transcription Window Always on Top.
- [ ] Task: Update `VogonPoetTray.kt` to include "Transcription Window" menu item
- [ ] Task: Conductor - User Manual Verification 'Phase 3: VogonPoet - Settings & Tray Integration' (Protocol in workflow.md)

## Phase 4: VogonPoet - Transcription Window UI
- [ ] Task: Create `TranscriptionWindow.kt` in `ovh.devcraft.vogonpoet.ui.windows`
    - Basic window setup with persistence and "Always on Top" support.
- [ ] Task: Implement "Single Flow" text display logic
    - Highlight ghost text differently from final text.
- [ ] Task: Connect `MainViewModel` transcription state to `TranscriptionWindow`
- [ ] Task: Conductor - User Manual Verification 'Phase 4: VogonPoet - Transcription Window UI' (Protocol in workflow.md)

## Phase 5: Final Integration & E2E Verification
- [ ] Task: Verify end-to-end flow from audio input to system-wide typing
- [ ] Task: Verify ghost text replacement (backspacing) accuracy
- [ ] Task: Verify "Always on Top" and setting persistence
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Final Integration & E2E Verification' (Protocol in workflow.md)
