# Plan: Architectural Cleanup and Stability Improvements

## Phase 1: Domain Layer Refactoring [checkpoint: c6c917b]
*Goal: Establish clean domain boundaries and models.*

- [x] Task: Create `VogonConfig` domain model and related entities in `ovh.devcraft.vogonpoet.domain.model` (30c18f4)
- [x] Task: Define `BackendRepository` interface in `ovh.devcraft.vogonpoet.domain` (99ac129)
- [x] Task: Update `BabelfishClient` domain interface to use `VogonConfig` instead of the infrastructure model (c927539)
- [x] Task: Conductor - User Manual Verification 'Phase 1: Domain Layer Refactoring' (Protocol in workflow.md)

## Phase 2: Infrastructure Layer Refactoring [checkpoint: 73fbbe5]
*Goal: Implement repositories and mapping logic.*

- [x] Task: Implement `VogonConfig` mappers in `infrastructure` layer (c6c917b)
- [x] Task: Implement `BackendRepositoryImpl` (actual/expect) that wraps `BackendController` (73fbbe5)
- [x] Task: Update `BabelfishClientImpl` to implement the updated domain interface with mapping (c6c917b)
- [x] Task: Conductor - User Manual Verification 'Phase 2: Infrastructure Layer Refactoring' (Protocol in workflow.md)

## Phase 3: Presentation and Threading Optimization [checkpoint: 4546c0d]
*Goal: Decouple ViewModel and offload I/O to background threads.*

- [x] Task: Refactor `MainViewModel` to use `BackendRepository` and domain models (0f015a4)
- [x] Task: Wrap `SettingsRepository` blocking calls in `withContext(Dispatchers.IO)` (9d11c88)
- [x] Task: Wrap `BackendManager` process lifecycle and I/O calls in `Dispatchers.IO` (4546c0d)
- [x] Task: Refactor `BackendManager` log parsing to use Coroutines/Flows instead of raw threads (4546c0d)
- [x] Task: Eliminate improper `runBlocking` usage in UI components (AdvancedSettingsPanel, FirstBootScreen, main.kt) (4546c0d)
- [x] Task: Conductor - User Manual Verification 'Phase 3: Presentation and Threading Optimization' (Protocol in workflow.md)

## Phase 4: Resource and Memory Management Fixes [checkpoint: final]
*Goal: Resolve leaks and optimize performance.*

- [x] Task: Refactor `BackendManager` to use a single persistent Shutdown Hook (4546c0d)
- [x] Task: Implement proper `close()` logic for `PrintWriter` log streams in `BackendManager` (4546c0d)
- [x] Task: Add cleanup/dispose logic for `HttpClient` in `BabelfishClient` (de76fb3)
- [x] Task: Optimize `ProtocolMessage` log storage (replace List with capped ArrayDeque/Collection) (de76fb3)
- [x] Task: Conductor - User Manual Verification 'Phase 4: Resource and Memory Management Fixes' (Protocol in workflow.md) [checkpoint: de76fb3]
