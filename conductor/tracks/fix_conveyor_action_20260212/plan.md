# Plan: Fix Hydraulic Conveyor GitHub Action Reference

## Phase 1: Implementation
- [~] Task: Update `.github/workflows/release.yml` with correct action path and inputs
    - Change `uses: hydraulic-software/conveyor/action@master` to `uses: hydraulic-software/conveyor/actions/build@master`.
    - Add `signing_key: internal` to `with` block.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Implementation' (Protocol in workflow.md)
