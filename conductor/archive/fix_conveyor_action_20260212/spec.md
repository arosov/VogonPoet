# Specification: Fix Hydraulic Conveyor GitHub Action Reference

## 1. Overview
The GitHub Actions workflow `.github/workflows/release.yml` fails because it references an incorrect path for the Hydraulic Conveyor action (`hydraulic-software/conveyor/action@master`). This track will correct the reference and ensure all required inputs are provided.

## 2. Problem Statement
- **Error**: `Can't find 'action.yml', 'action.yaml' or 'Dockerfile' for action 'hydraulic-software/conveyor/action@master'.`
- **Cause**: The action is located at `actions/build` in the `conveyor` repository, not `action`.
- **Additional Issue**: The action requires a `signing_key` input which is currently missing in the workflow.

## 3. Requirements

### 3.1. Correct Action Reference
- Update the `uses` clause in `.github/workflows/release.yml` to:
  `hydraulic-software/conveyor/actions/build@master`

### 3.2. Provide Required Inputs
- Add the `signing_key` input to the `with` block.
- Set `signing_key` to `internal` (or match the configuration in `conveyor.conf`).

## 4. Verification
- Verify the syntax of the modified `.github/workflows/release.yml`.
- (Implicit) The fix is verified if the user reports the build passes (or we can't fully test CI locally).
