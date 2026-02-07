# Implementation Plan - WebTransport Foundation & Status UI

## Phase 1: Dependency & Domain Model [checkpoint: 02e12cf]
Establish the project structure and core data models for connection and VAD states.

- [x] Task: Add `kwtransport` and necessary dependencies to `libs.versions.toml` and `composeApp/build.gradle.kts`. 22a5ca8
- [x] Task: Define the domain models for `ConnectionState` and `VadState`. 8db235a
    - [x] Create `ConnectionState` sealed class (Disconnected, Connecting, Connected, Error).
    - [x] Create `VadState` enum (Idle, Listening).
- [x] Task: Conductor - User Manual Verification 'Phase 1: Dependency & Domain Model' (Protocol in workflow.md)

## Phase 2: WebTransport Client Implementation
Implement the core networking logic using `kwtransport` with automatic connection and retry logic.

- [~] Task: Create `BabelfishClient` interface and its implementation in `commonMain`.
- [ ] Task: Implement TDD for `BabelfishClient` connection logic.
    - [ ] Write tests for initial connection attempt to `localhost:8123`.
    - [ ] Implement `kwtransport` client initialization.
- [ ] Task: Implement reconnection logic with exponential backoff.
    - [ ] Write tests for retry behavior on connection failure.
    - [ ] Implement retry loop in `BabelfishClient`.
- [ ] Task: Implement VAD state streaming from WebTransport.
    - [ ] Write tests for VAD state updates via incoming streams/datagrams.
    - [ ] Implement VAD state parsing in the client.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: WebTransport Client Implementation' (Protocol in workflow.md)

## Phase 3: Presentation & UI State
Bridge the domain logic to the UI using ViewModels and state flows.

- [ ] Task: Create `MainViewModel` to manage application-wide status.
- [ ] Task: Implement TDD for `MainViewModel` state mapping.
    - [ ] Write tests ensuring `BabelfishClient` states are correctly exposed as UI state.
    - [ ] Implement `MainViewModel` using `BabelfishClient`.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Presentation & UI State' (Protocol in workflow.md)

## Phase 4: Status UI & System Tray
Implement the visual components for the status card and system integration.

- [ ] Task: Create `StatusCard` Composable.
    - [ ] Implement visual states (colors/animations) based on MD3 guidelines and spec.
- [ ] Task: Implement System Tray integration for JVM/Desktop.
    - [ ] Create `TrayManager` to handle icon updates and context menu.
    - [ ] Connect `TrayManager` to the application state.
- [ ] Task: Integrate `StatusCard` into the main `App` entry point.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Status UI & System Tray' (Protocol in workflow.md)
