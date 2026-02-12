# Specification: Fix Hydraulic Conveyor Root Key Format

## 1. Overview
The GitHub Actions workflow fails because the `signing_key` input passed to the Hydraulic Conveyor action is "internal", which does not match the required root key format (words followed by a timestamp).

## 2. Problem Statement
- **Error**: `A root key should be a string consisting of words followed by a timestamp, separated by a forward slash. Was 'internal'`
- **Cause**: The Conveyor GitHub Action requires a validly formatted root key string in its `signing_key` input. Passing "internal" works in `conveyor.conf` for local development but is rejected by the action's validation or the way it passes it to the CLI.

## 3. Requirements

### 3.1. Provide a Formatted Root Key
- Generate or provide a dummy root key in the format: `word1 word2 word3 word4 / YYYY-MM-DDTHH:MM:SSZ`.
- This key will be used for self-signing in the CI environment.

### 3.2. Update Workflow
- Update `.github/workflows/release.yml` to use this formatted key instead of "internal".

## 4. Verification
- Verify that the workflow file is updated.
- The build should proceed past the key validation step in GitHub Actions.
