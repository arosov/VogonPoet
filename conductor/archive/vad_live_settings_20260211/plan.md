# Implementation Plan - VAD Window Fix & Settings Refactor

## Phase 1: VAD Window Live Feedback
- [x] Task: Switch `VadWindow` to observe `draftConfig`
    - Update `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/windows/VadWindow.kt` to use `viewModel.draftConfig` instead of `viewModel.config`.
    - Verify null handling for `draftConfig` matches existing `config` logic.

## Phase 2: Refactor Advanced Settings
- [x] Task: Move Storage Configuration to `AdvancedSettingsPanel`
    - Update `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/components/AdvancedSettingsPanel.kt`.
    - Add `SettingsRepository` logic for `uvCacheDir` and `modelsDir`.
    - Add a "System" section or group "Hardware" and "Storage".
- [x] Task: Implement Critical Setting Warning
    - Add `AlertDialog` state in `AdvancedSettingsPanel`.
    - Trigger dialog on changes to **Hardware Acceleration** or **Storage Directory**.
    - Implement `onConfirm` action: Save config & call `viewModel.restartBackend()` (or equivalent save+restart logic).

## Phase 3: Refactor Config Form (Live Updates)
- [x] Task: Remove Legacy UI Elements
    - Remove "Save Configuration" button from `ConfigForm.kt`.
    - Remove "Data Storage Directory" section from `ConfigForm.kt`.
- [x] Task: Implement Live Updates for Text Fields
    - Refactor `Wakeword`, `Stop Words`, and `Shortcut` fields.
    - Implement `onFocusChanged` (blur) and `KeyboardActions.onDone` to trigger save.
    - Ensure local state handles typing without jitter.
- [x] Task: Implement Live Updates for Other Inputs
    - Refactor `Sensitivity` slider to save on `onValueChangeFinished`.
    - Refactor `Microphone` dropdown to save immediately on selection.
- [x] Task: Update Component Signatures
    - Update `ConfigForm` and `AdvancedSettingsPanel` to accept `onConfigChange` callback.
    - Verify `App.kt` passes the correct `updateDraft` + `saveConfig` sequence.

## Phase 4: Verification
- [ ] Task: Verify Live Updates
    - Check that VAD window updates immediately when toggling "Icon Only" in settings.
    - Check that text fields save correctly on blur.
    - Check that Restart Warning appears for Hardware/Storage changes.
    - Check that backend restarts (or re-initializes) upon confirmation.
