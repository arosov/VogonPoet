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

- [x] Task: Create `BabelfishClient` interface and its implementation in `commonMain`. 4716173
- [x] Task: Implement TDD for `BabelfishClient` connection logic. 446d634
    - [x] Write tests for initial connection attempt to `localhost:8123`.
    - [x] Implement `kwtransport` client initialization.
- [x] Task: Implement reconnection logic with exponential backoff. 144aa9a
    - [x] Write tests for retry behavior on connection failure.
    - [x] Implement retry loop in `BabelfishClient`.
- [x] Task: Implement VAD state streaming from WebTransport. 144aa9a
    - [x] Write tests for VAD state updates via incoming bidirectional streams.
    - [x] Implement VAD state parsing in the client using `acceptBi()`.
- [x] Task: Conductor - User Manual Verification 'Phase 2: WebTransport Client Implementation' (Protocol in workflow.md)

## Phase 3: Presentation & UI State [checkpoint: e96e272]
Bridge the domain logic to the UI using ViewModels and state flows.

- [x] Task: Create `MainViewModel` to manage application-wide status. e96e272
- [x] Task: Implement TDD for `MainViewModel` state mapping. e96e272
    - [x] Write tests ensuring `BabelfishClient` states are correctly exposed as UI state.
    - [x] Implement `MainViewModel` using `BabelfishClient`.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Presentation & UI State' (Protocol in workflow.md)

## Phase 4: Status UI & System Tray [checkpoint: 9a3b4ea]
Implement the visual components for the status card and system integration.

- [x] Task: Create `StatusCard` Composable. 9a3b4ea
    - [x] Implement visual states (colors/animations) based on MD3 guidelines and spec.
- [x] Task: Implement System Tray integration for JVM/Desktop. 9a3b4ea
    - [x] Create `VogonPoetTray` Composable in `jvmMain`.
    - [x] Connect `VogonPoetTray` to the application state.
- [x] Task: Integrate `StatusCard` into the main `App` entry point. 9a3b4ea
- [x] Task: Conductor - User Manual Verification 'Phase 4: Status UI & System Tray' (Protocol in workflow.md)
