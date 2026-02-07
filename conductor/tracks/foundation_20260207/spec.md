# Track Specification - WebTransport Foundation & Status UI

## Overview
This track focuses on establishing the core communication bridge between VogonPoet and the Babelfish backend. Using the `kwtransport` library, we will implement a robust WebTransport client that automatically connects to the local backend. The UI will provide high-visibility feedback for connection health and Voice Activity Detection (VAD) states, ensuring the user is always aware of the system's readiness.

## Functional Requirements
- **Automated WebTransport Client:**
    - Implement a background service using `kwtransport`.
    - Automatically attempt to connect to `https://localhost:8123` on application startup.
    - Implement an exponential backoff retry logic for connection failures.
- **State Management:**
    - Track connection states: `Connecting`, `Connected`, `Disconnected`, `Error`.
    - Track VAD states: `Idle`, `Listening` (active audio detected).
- **High-Visibility UI (Central Status Card):**
    - A prominent MD3 card that serves as the visual centerpiece.
    - **Color-coded feedback:**
        - Gray: Disconnected.
        - Blue: Connecting.
        - Green: Connected.
        - Pulsing Green: Listening (VAD Active).
        - Red: Connection Error.
- **System Tray Integration:**
    - Implement a system tray icon that reflects the connection/VAD state.
    - Provide a basic context menu with "Exit" and "Reconnect" options.

## Non-Functional Requirements
- **Low Latency:** State transitions (VAD) must be reflected in the UI with minimal delay (<50ms).
- **Robustness:** Connection drops should be handled gracefully without crashing the UI.

## Acceptance Criteria
- [ ] VogonPoet connects to a local server at `localhost:8123` automatically on launch.
- [ ] The Central Status Card correctly displays all defined connection and VAD states.
- [ ] The system tray icon updates in sync with the main UI's status card.
- [ ] Manual "Reconnect" from the tray context menu successfully resets the connection attempt.

## Out of Scope
- Displaying transcribed text (Ghost/Anchor text).
- Detailed configuration dashboard (hardware/audio selection).
