# Spec: Architectural Cleanup and Stability Improvements

## Overview
This track focuses on hardening the VogonPoet architecture by enforcing Clean Architecture boundaries, optimizing thread management to prevent UI blocking, and fixing identified memory and resource leaks.

## Functional Requirements

### 1. Architectural Integrity
- **Domain Model Mapping:** Create a pure Kotlin data class `VogonConfig` in the `domain` layer. Implement mapping logic in the `infrastructure` layer to convert between the generated `Babelfish` model and `VogonConfig`.
- **Backend Abstraction:** Introduce a `BackendRepository` interface in the `domain` layer to manage backend lifecycle (start, stop, restart) and status.
- **ViewModel Decoupling:** Refactor `MainViewModel` to depend only on `BabelfishClient` and the new `BackendRepository`, removing direct dependencies on `BackendController` and infrastructure models.

### 2. Threading Optimization
- **Non-blocking I/O:** Ensure all file system operations in `SettingsRepository` and `BackendManager` (extraction, logging, reading config) are executed on `Dispatchers.IO`.
- **Async Lifecycle:** Offload backend process management (start/stop/restart) to background threads to prevent UI hangs.
- **Coroutines for Logging:** Update `BackendManager` to use Coroutines/Dispatchers for log processing instead of raw daemon threads where applicable.

### 3. Resource & Memory Management
- **Shutdown Hook Fix:** Refactor `BackendManager` to prevent the accumulation of multiple shutdown hooks on backend restarts.
- **Resource Cleanup:** 
    - Implement proper `close()` logic for `PrintWriter` log streams.
    - Ensure `HttpClient` in `BabelfishClient` is properly disposed of during application teardown.
- **GC Optimization:** Replace the current list-concatenation strategy for the protocol message log with a more efficient structure (e.g., `ArrayDeque`) to reduce object allocation overhead.

## Acceptance Criteria
- [ ] `domain` layer has zero dependencies on `infrastructure` or generated models.
- [ ] `MainViewModel` has no imports from `ovh.devcraft.vogonpoet.infrastructure` (except for DI purposes if necessary).
- [ ] Application starts and restarts the backend without blocking the UI thread (verified by logs or lack of "Not Responding" state).
- [ ] Multiple backend restarts do not increase the number of active JVM shutdown hooks.
- [ ] All log files are correctly flushed and closed.

## Out of Scope
- Adding new features to the backend or STT engine.
- Redesigning the UI layout or theme.
