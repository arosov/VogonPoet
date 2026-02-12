# Plan: Architectural Cleanup and Stability Improvements

## Phase 1: Domain Layer Refactoring
*Goal: Establish clean domain boundaries and models.*

- [x] Task: Create `VogonConfig` domain model and related entities in `ovh.devcraft.vogonpoet.domain.model` (30c18f4)
- [x] Task: Define `BackendRepository` interface in `ovh.devcraft.vogonpoet.domain` (99ac129)
- [ ] Task: Update `BabelfishClient` domain interface to use `VogonConfig` instead of the infrastructure model
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Domain Layer Refactoring' (Protocol in workflow.md)

## Phase 2: Infrastructure Layer Refactoring
*Goal: Implement repositories and mapping logic.*

- [ ] Task: Implement `VogonConfig` mappers in `infrastructure` layer
- [ ] Task: Implement `BackendRepositoryImpl` (actual/expect) that wraps `BackendController`
- [ ] Task: Update `BabelfishClientImpl` to implement the updated domain interface with mapping
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Infrastructure Layer Refactoring' (Protocol in workflow.md)

## Phase 3: Presentation and Threading Optimization
*Goal: Decouple ViewModel and offload I/O to background threads.*

- [ ] Task: Refactor `MainViewModel` to use `BackendRepository` and domain models
- [ ] Task: Wrap `SettingsRepository` blocking calls in `withContext(Dispatchers.IO)`
- [ ] Task: Wrap `BackendManager` process lifecycle and I/O calls in `Dispatchers.IO`
- [ ] Task: Refactor `BackendManager` log parsing to use Coroutines/Flows instead of raw threads
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Presentation and Threading Optimization' (Protocol in workflow.md)

## Phase 4: Resource and Memory Management Fixes
*Goal: Resolve leaks and optimize performance.*

- [ ] Task: Refactor `BackendManager` to use a single persistent Shutdown Hook
- [ ] Task: Implement proper `close()` logic for `PrintWriter` log streams in `BackendManager`
- [ ] Task: Add cleanup/dispose logic for `HttpClient` in `BabelfishClient`
- [ ] Task: Optimize `ProtocolMessage` log storage (replace List with capped ArrayDeque/Collection)
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Resource and Memory Management Fixes' (Protocol in workflow.md)
