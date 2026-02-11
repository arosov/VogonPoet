# Specification: VAD Window Fix & Settings Refactor

## 1. Overview
The current VAD (Voice Activity Detection) window does not reflect configuration changes (like "Icon Only" mode) until they are explicitly saved and propagated from the backend. This track aims to make the settings experience "live" by applying changes immediately, removing the explicit "Save" button from the main configuration form, and reorganizing critical system settings (Storage, Hardware) into the Advanced Settings panel with appropriate safety warnings.

## 2. Functional Requirements

### 2.1 Live Configuration (ConfigForm)
- **Remove Save Button:** The "Save Configuration" button must be removed.
- **Immediate Updates:**
    - **Toggles/Dropdowns:** Changes (e.g., Microphone selection) must trigger a configuration save immediately.
    - **Text Fields:** Changes (Wakeword, Stop Words, Shortcuts) must trigger a save on **focus loss (blur)** or "Enter" key press to prevent saving incomplete states.
    - **Sliders:** Changes (Sensitivity) must trigger a save on **release** (`onValueChangeFinished`).
- **Statelessness:** The form components should be controlled by the parent (`App.kt` / `MainViewModel`), observing the current configuration state.

### 2.2 Immediate Visual Feedback (VadWindow)
- **Live Preview:** The `VadWindow` must observe the local `draftConfig` (or equivalent "live" state) instead of the committed remote `config`. This ensures that toggling "Icon Only" or "Overlay Mode" in the settings is instantly reflected in the window.

### 2.3 Advanced Settings & Safety
- **Storage Directory:** The "Data Storage Directory" configuration must be moved from `ConfigForm` to `AdvancedSettingsPanel`.
    - **Behavior:** Changing the directory switches the path for future operations. Existing files are **NOT** moved automatically.
- **Safety Warnings:**
    - A confirmation dialog (`AlertDialog`) must appear when changing **Hardware Acceleration (GPU)** or **Data Storage Directory**.
    - **Message:** Warn the user that this action requires a system restart/re-initialization.
    - **Action:** On confirmation, save the configuration and **automatically restart** the backend/process.

## 3. Non-Functional Requirements
- **Responsiveness:** The UI must remain responsive during save operations.
- **Stability:** Invalid states (e.g., empty wakeword if not allowed) should be handled gracefully or validated before saving.

## 4. UI/UX
- **Config Form:** Cleaner, less cluttered interface without the large "Save" button.
- **Advanced Panel:** Logical grouping of "System" settings (Hardware, Storage).
- **Feedback:** Visual feedback (e.g., loading state or toast) when a critical restart is triggered is desirable but not strictly required if the restart is fast.

## 5. Out of Scope
- Moving existing models/cache files when storage directory changes.
- Complex validation logic beyond current implementation.
