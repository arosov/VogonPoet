# Plan: Distributable Packaging with Hydraulic Conveyor

## Phase 1: Preparation & Configuration
- [x] Task: Create `conveyor.conf` at project root with basic Windows/Linux configuration (f8d7e6a)
- [x] Task: Configure Windows targeting in `conveyor.conf` (a1b2c3d)
- [x] Task: Configure Linux targeting in `conveyor.conf` (e5f6g7h)
- [~] Task: Conductor - User Manual Verification 'Phase 1: Preparation & Configuration' (Protocol in workflow.md)


## Phase 2: Build Integration & GitHub Actions
- [ ] Task: Update Gradle build to ensure `babelfish.zip` is available in `jvmMain/resources`
    - Verify existing task/resource configuration.
    - Ensure `processResources` includes the zip.
- [ ] Task: Create GitHub Actions workflow `.github/workflows/release.yml`
    - Trigger on `release` (types: [published]) or tag push.
    - Job: `build-and-package`
        - Step: Checkout code.
        - Step: Setup Java/Gradle.
        - Step: Build project (produce JARs/distributions).
        - Step: Run Hydraulic Conveyor Action (`hydraulic-software/conveyor/action`).
            - Pass `AGREE_TO_LICENSE` if required.
            - Pass necessary secrets (if any).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Build Integration & GitHub Actions' (Protocol in workflow.md)

## Phase 3: Verification & Cleanup
- [ ] Task: Verify Local Build (Optional/Dry Run)
    - Attempt a local build of the Conveyor package (if Conveyor is installed locally, else verify config syntax).
- [ ] Task: Documentation
    - Update `README.md` with instructions on how the release process works.
    - Document the `conveyor.conf` basics for future maintainers.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Verification & Cleanup' (Protocol in workflow.md)
