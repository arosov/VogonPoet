# UI Refactoring Plan

## Phase 1: Protocol Log Extraction
1.  **Create `ProtocolLogWindow.kt`**
    *   **Location:** `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/windows/`
    *   **Purpose:** A dedicated window wrapper for the `MessageInspector` component.

2.  **Update `VogonPoetTray.kt`**
    *   **Add Callback:** `onOpenProtocolLog: () -> Unit`.
    *   **Add Menu Item:** `Item("Protocol Log", onClick = onOpenProtocolLog)`.

3.  **Update `main.kt`**
    *   **State:** Manage `showProtocolLog` state.
    *   **Window Logic:** Conditionally render `ProtocolLogWindow`.
    *   **Tray Connection:** Connect tray action to state toggle.

4.  **Update `App.kt`**
    *   **Cleanup:** Remove `MessageInspector` from the main `Column`.

## Phase 2: Configuration Schema Update
1.  **Update `babelfish_schema.json`**
    *   Add `shortcuts` object to `UIConfig` (or a new `ShortcutsConfig` section if preferred, but UI section seems appropriate for now or a top-level `control`).
    *   Structure: `{"toggle_listening": "string", "force_listen": "string"}`.

## Phase 3: Interactive Configuration Form (`ConfigForm`)
1.  **Create `ConfigForm.kt`**
    *   **Location:** `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/components/`
    *   **Architecture:** Split into collapsible/outlined panels.

2.  **Panel 1: Hardware Acceleration**
    *   **GPU Selector:** Mocked dropdown (CPU vs NVIDIA).
    *   **Logic:** Controls `hardware.device` ("cpu" vs "cuda").
    *   **Status:** Display "No GPU Support" if applicable.

3.  **Panel 2: Pipeline Strategy**
    *   **Mode Selector:** Single Pass vs Double Pass.
    *   **Logic:** Double pass disabled if CPU is selected.
    *   **Sliders:** `anchor_trigger_interval_ms` (Double pass only).
    *   **Presets:** Dropdown for `ghost_preset` and `anchor_preset`.

4.  **Panel 3: Voice Triggers**
    *   **Wakeword:** Optional Text Field.
    *   **Stop Words:** Optional Text Field (comma-separated).

5.  **Panel 4: Shortcuts (New)**
    *   **Components:** Two "Key Sequence Recorders" (Custom UI component).
    *   **Actions:**
        *   "Toggle Listening"
        *   "Push-to-Talk" (or Force Listen)

## Phase 4: Settings Window Transformation
1.  **Refactor `App.kt`**
    *   Rename to `SettingsScreen.kt` (conceptually, file rename optional).
    *   **Header:** `StatusCard`.
    *   **Body:** `ConfigForm`.
    *   **Footer:** "Save Config" button.

## Phase 5: Activation Detection Window
1.  **Create `VadWindow.kt`**
    *   **Location:** `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/windows/`
    *   **Content:** A large visual indicator of VAD state (Idle/Listening).

2.  **Update Tray & Main**
    *   Add "Activation Detection" menu item.
    *   Manage window state in `main.kt`.

## Phase 6: Final Cleanup
1.  **Tray Cleanup:** Remove "Reconnect" and "Restart Engine" (handled within Settings or auto-managed).
2.  **Verify:** Ensure all windows open/close correctly and config saves propagate.
