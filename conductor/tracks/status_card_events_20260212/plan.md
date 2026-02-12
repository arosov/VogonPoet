# Plan: Status Card Events Integration & Engine Mode

## Phase 1: Backend Implementation (Babelfish)
- [x] Modify `src/babelfish_stt/pipeline.py`: Add `on_mode_change` callback to `Pipeline` class and trigger it in `set_idle`.
- [x] Modify `src/babelfish_stt/server.py`: Subscribe to `pipeline.on_mode_change`.
- [x] Modify `src/babelfish_stt/server.py`: Update `broadcast_status` to include a `mode` field (`"wakeword"` or `"active"`).
- [x] Ensure `mode` is sent in the initial state and updates in `server.py`.

## Phase 2: Domain & Infrastructure (VogonPoet)
- [x] Create `domain/model/EngineMode.kt` enum (`Wakeword`, `Active`).
- [x] Modify `KwBabelfishClient.kt`: Add `_engineMode` state flow.
- [x] Modify `KwBabelfishClient.kt`: Parse `mode` from `status` message.
- [x] Update `KwBabelfishClientTest` to verify mode parsing.

## Phase 3: Presentation Logic
- [x] Modify `MainViewModel.kt`: Expose `engineMode`.
- [x] Ensure transient events ("wakeword detected", "stop word detected") still work with the new mode logic.

## Phase 4: UI Implementation
- [x] Modify `StatusCard.kt`: 
    - If `EngineMode.Wakeword`: Display "Ready - Idle".
    - If `EngineMode.Active`:
        - If `VadState.Idle`: Display "Ready - Listening".
        - If `VadState.Listening`: Display "Listening" (or transcribing text).

## Phase 5: Verification
- [x] Run `./gradlew :composeApp:test` to ensure no regressions.
- [x] Verify UI behavior manually.
