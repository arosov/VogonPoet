# Specification - System Input & Transcription Window

## Overview
This track introduces the ability for Babelfish to inject transcribed text directly into any focused system window. It also adds a dedicated "Transcription Window" to VogonPoet for real-time visualization of the transcription flow (ghost and final text).

## Functional Requirements

### Babelfish (Server)
- **Input Simulation**: Integrate `pynput` to simulate keyboard typing across Linux, Windows, and macOS.
- **Ghost Typing**: 
    - When ghost output is received, type it into the focused window.
    - Track the length of the current ghost string.
    - Before typing updated ghost text or final text, send the appropriate number of backspaces to "erase" the previous ghost text.
- **Configuration**:
    - Add `enable_system_input` (bool) to control if Babelfish should type at all.
    - Add `type_ghost_output` (bool) to control if intermediate results should be typed or only final ones.
- **Protocol**: Continue sending both ghost and final updates to VogonPoet via WebSockets.

### VogonPoet (Client)
- **Transcription Window**:
    - New window accessible via the System Tray.
    - Displays a "Single Flow" of text: Finalized sentences followed by the current ghost text.
    - Finalized text uses standard Gruvbox colors; ghost text is dimmed (Gruvbox Gray).
- **Settings**:
    - Add "Enable System Input" toggle to Advanced Settings.
    - Add "Type Ghost Output" toggle to Advanced Settings.
    - Add "Transcription Window Always on Top" toggle to Advanced Settings.
- **Window Management**:
    - Persistent overlay mode similar to the VAD window.
    - Configurable "Always on Top" behavior.

## Non-Functional Requirements
- **Low Latency**: Input simulation should not introduce perceptible delay to the UI updates.
- **Robustness**: Ensure backspace tracking remains accurate (reset tracking on every finalization).

## Acceptance Criteria
- [ ] Babelfish successfully types final transcription into a text editor.
- [ ] Babelfish successfully "replaces" ghost text by backspacing if enabled.
- [ ] VogonPoet tray menu has a "Transcription Window" item.
- [ ] The Transcription Window shows the combined flow of final and ghost text.
- [ ] Toggles in Advanced Settings correctly control Babelfish behavior and Window behavior.

## Out of Scope
- Support for complex rich text formatting during injection.
- Focus-aware input (handling specific shortcuts per-app).
