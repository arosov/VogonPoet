# Plan: Ghost Text & State Sanitation

## Phase 1: Display & Simulator Interface [checkpoint: d066ec3]
- [x] Task: Update `InputSimulator` in `input_manager.py` (d066ec3)
    - Add `reset()`: Sets `last_ghost_length = 0` (Silent).
    - Ensure `_clear_previous()` remains the mechanism for active erasure.
- [x] Task: Expand Display interface in `display.py` (d066ec3)
    - Add `reset()` to `TerminalDisplay`, `ServerDisplay`, `MultiDisplay`, and `InputDisplay`.
    - `InputDisplay.reset()` must call `self.simulator.reset()`.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Display & Simulator Interface' (Protocol in workflow.md)

## Phase 2: Pipeline Sanitation & Timeout [checkpoint: d249b53]
- [x] Task: Integrate resets into `pipeline.py` (d249b53)
    - Call `self.display.reset()` inside `StandardPipeline.reset_state()`.
- [x] Task: Implement Ghost Timeout logic (d249b53)
    - In `process_chunk`, if `is_speaking` but `now_ms - last_speech_time > 5000`, trigger `self.display.reset()` and `self.reset_state()`.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Pipeline Sanitation & Timeout' (Protocol in workflow.md)

## Phase 3: Client-side Reset Handling [checkpoint: d44086b]
- [x] Task: Update `BabelfishClient.kt` in VogonPoet (d44086b)
    - Handle new message type or state reset signal from server to clear the `transcription` flow.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Client-side Reset Handling' (Protocol in workflow.md)

## Phase 4: Verification
- [x] Task: Verify that toggling Listening OFF immediately clears ghost tracking.
- [x] Task: Verify that long pauses (5s) clear any stuck ghost text in both the system input and the VogonPoet window.
- [x] Task: Verify that jumping lines manually doesn't cause "line jumping" on the next utterance.
- [x] Task: Conductor - User Manual Verification 'Phase 4: Verification' (Protocol in workflow.md)
