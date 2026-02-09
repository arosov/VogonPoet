# Microphone Selection & Test Mode Implementation Plan

## Phase 1: Backend Fixes & Core Logic (Babelfish)

1.  **Fix Server Command Processing (`server.py`)**
    *   **Context:** Currently, `on_data_received` buffers data but never calls `process_command`.
    *   **Action:** Implement a line-reading loop in `on_data_received` to parse JSON commands and execute `process_command`.
    *   **Clarification:** Ensure the method name `process_command` refers strictly to handling WebTransport JSON commands (e.g., `update_config`, `list_microphones`). This is distinct from any voice command logic in the pipeline.

2.  **Implement Microphone Listing (`server.py`)**
    *   Import `list_microphones` from `babelfish_stt.hardware`.
    *   Add a handler for the command `{"type": "list_microphones"}`.
    *   Return `{"type": "microphones_list", "data": [...]}`.

3.  **Enable Microphone Hot-Swapping (`audio.py`, `main.py`)**
    *   **Goal:** Change input device without restarting the application.
    *   **Action:** Make `AudioStreamer` inherit from `Reconfigurable`.
    *   **Logic:** In `reconfigure()`, if `microphone_index` changes:
        *   Stop and close the existing `sounddevice` stream.
        *   Re-initialize the stream with the new index.
        *   Start the new stream.
    *   **Main:** Register the `streamer` instance with `config_manager` in `main.py`.
    *   **Server:** Update `BabelfishServer.reconfigure` to remove `microphone_index` from the "restart required" triggers.

4.  **Implement Test Mode Pipeline (`pipeline.py`)**
    *   **Goal:** Run VAD but drop audio to avoid transcription overhead/logs during testing.
    *   **Action:** Add `set_test_mode(enabled: bool)` to the `Pipeline` class.
    *   **Logic:** In `process_chunk`:
        *   If `test_mode` is True:
            *   Run VAD update (`self.vad.process(...)`).
            *   Broadcast state change (Idle <-> Listening).
            *   **Drop** the audio chunk (do not append to buffer).
            *   Return early (skip transcription logic).
    *   **Server:** Add handler for `{"type": "set_mic_test", "enabled": bool}` to call `pipeline.set_test_mode(enabled)`.

## Phase 2: Frontend Implementation (VogonPoet)

1.  **Data Layer (`BabelfishClient`, `MainViewModel`)**
    *   Define `Microphone` data model.
    *   Add `listMicrophones()` method to client.
    *   Add `setMicTest(enabled: Boolean)` method to client.
    *   Update `MainViewModel` to hold `microphoneList` and `isMicTesting` state.

2.  **UI: Microphone Panel (`ConfigForm.kt`)**
    *   Add a new "Microphone" panel to the settings form.
    *   **Dropdown:** Lists available microphones. Updating this sends a standard `saveConfig` (triggering the backend hot-swap).
    *   **Test Button:** A toggle button "Test Microphone".
        *   *On Click:* Calls `setMicTest(true)`.
        *   *Visual:* Changes to "Stop Test".
    *   **Feedback:** Show a small indicator (e.g., "Voice Detected!") in the panel when `vadState` is `Listening` during test mode.

## Phase 3: Integration

1.  **Protocol Definition**
    *   `list_microphones` -> `microphones_list`
    *   `set_mic_test` (payload: `{enabled: boolean}`)

2.  **Execution Order**
    *   Backend fixes & features.
    *   Frontend model & client updates.
    *   Frontend UI implementation.
