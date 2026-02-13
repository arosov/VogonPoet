# Specification - Ghost Text & State Sanitation

## Overview
This track addresses issues where ghost text persists across line breaks or utterances, causing erratic cursor behavior (e.g., text jumping up). It introduces a formal reset mechanism across the pipeline and display layers.

## Functional Requirements

### 1. Display Interface Expansion
- Add a mandatory `reset()` method to the display interface (and all implementations: `TerminalDisplay`, `ServerDisplay`, `InputDisplay`, `MultiDisplay`).
- **InputDisplay.reset()**: 
    - Immediately sets `last_ghost_length = 0` WITHOUT sending backspaces.
    - This is used for "Emergency/Manual" resets where the cursor position might have changed.

### 2. Input Simulator Logic
- Implement `clear_ghost()`: Sends backspaces to erase the current ghost and resets the counter.
- **Natural Finalization**: Use `clear_ghost()` then type final text.
- **Forced/Manual Reset**: Use `reset()` (set length to 0).

### 3. Pipeline Sanitation
- Trigger `display.reset()` in `StandardPipeline.reset_state()`.
- Ensure `is_speaking` and `last_update_time` are consistently cleared.

### 4. Ghost Timeout
- Add a safety timeout (default 5s).
- If `is_speaking` is True but no new ghost update or speech is detected within the timeout, trigger a `display.reset()` to prevent "ghost drift".

### 5. Space Prepending
- **Policy**: Continue prepending a space if the last character typed by Babelfish was not whitespace, regardless of pauses or resets. (Ensures sentence continuity across manual line breaks).

## Acceptance Criteria
- [ ] Manual "Listen" toggle OFF immediately resets ghost tracking in Babelfish.
- [ ] Jumping lines manually and starting a new sentence does not result in the new text "jumping back" to the previous line.
- [ ] No ghost text persists in the VogonPoet Transcription window after a pipeline reset.
- [ ] Long pauses (5s+) without speech during an "active" block clear any pending ghost state.
