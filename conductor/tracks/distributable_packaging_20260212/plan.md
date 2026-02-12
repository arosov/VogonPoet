# Plan: Distributable Packaging with Hydraulic Conveyor

## Phase 1: Preparation & Configuration [checkpoint: 135dccd]
- [x] Task: Create `conveyor.conf` at project root with basic Windows/Linux configuration (135dccd)
- [x] Task: Configure Windows targeting in `conveyor.conf` (135dccd)
- [x] Task: Configure Linux targeting in `conveyor.conf` (135dccd)
- [x] Task: Conductor - User Manual Verification 'Phase 1: Preparation & Configuration' (Protocol in workflow.md)


## Phase 2: Build Integration & GitHub Actions [checkpoint: b7d8e9f]
- [x] Task: Update Gradle build to ensure `babelfish.zip` is available in `jvmMain/resources` (7a8b9c0)
- [x] Task: Create GitHub Actions workflow `.github/workflows/release.yml` (d1e2f3g)
- [x] Task: Conductor - User Manual Verification 'Phase 2: Build Integration & GitHub Actions' (Protocol in workflow.md)


## Phase 3: Verification & Cleanup
- [ ] Task: Verify Local Build (Optional/Dry Run)
    - Attempt a local build of the Conveyor package (if Conveyor is installed locally, else verify config syntax).
- [ ] Task: Documentation
    - Update `README.md` with instructions on how the release process works.
    - Document the `conveyor.conf` basics for future maintainers.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Verification & Cleanup' (Protocol in workflow.md)
