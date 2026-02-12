# Spec: Status Card Events Integration & Engine Mode

## Context
The `StatusCard` currently displays a static "Ready" or "Transcribing..." text based on the connection and VAD state. The user wants more visibility into the engine's internal states and specific events like wakeword or stop word detection. 
Additionally, there is a need to distinguish between two "Ready" states based on the engine's mode (Wakeword vs Dictation).

## Requirements
- Display engine state and events in the `StatusCard` using the format "Ready - <state/event>".
- States to display: "idle", "listening".
- Events to display: "wakeword detected", "stop word detected".
- Events must be transient: display for 2 seconds before reverting to the current state.
- Integration must happen through `KwBabelfishClient` (parsing) and `MainViewModel` (timing/state management).
- Distinguish between "Wakeword" mode and "Active" (Dictation) mode.
    - If `EngineMode.Wakeword`: Display "Ready - Idle".
    - If `EngineMode.Active`:
        - If `VadState.Idle`: Display "Ready - Listening".
        - If `VadState.Listening`: Display "Listening" (or transcribing text).

## Technical Details
### Backend (Babelfish)
- **pipeline.py**: Add `on_mode_change` callback to `Pipeline` class and trigger it in `set_idle`.
- **server.py**: 
    - Subscribe to `pipeline.on_mode_change`.
    - Update `broadcast_status` to include a `mode` field (`"wakeword"` or `"active"`).
    - Ensure `mode` is sent in the initial state and updates.

### Frontend (VogonPoet)
- **EngineMode.kt**: New enum (`Wakeword`, `Active`).
- **KwBabelfishClient.kt**: 
    - Add `engineMode` state flow.
    - Parse `mode` from `status` message.
- **MainViewModel.kt**: Expose `engineMode`.
- **StatusCard.kt**: Update UI logic to combine `EngineMode` and `VadState`.

## Constraints
- Adhere to Clean Architecture.
- Maintain existing styling and animations.
- The "Ready" part should persist for idle/listening states as "Ready - idle" or "Ready - listening" when not transcribing.
