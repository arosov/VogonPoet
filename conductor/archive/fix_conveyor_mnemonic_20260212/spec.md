# Specification: Fix Hydraulic Conveyor Mnemonic Word Count

## 1. Overview
The GitHub Actions workflow fails because the `signing_key` mnemonic length is not a multiple of three.

## 2. Problem Statement
- **Error**: `The root signing key has a bad mnemonic code format. Mnemonic Length: Word list size must be multiple of three words, was 5`
- **Cause**: The `signing_key` provided in `.github/workflows/release.yml` has 5 words (`vogon poet packaging release internal`), but Hydraulic Conveyor requires the word count to be a multiple of three (e.g., 3, 6, 9, 12).

## 3. Requirements

### 3.1. Update Mnemonic
- Update the `signing_key` in `.github/workflows/release.yml` to have exactly 6 words.
- New key: `vogon poet packaging release internal automated / 2026-02-12T21:00:00Z`

## 4. Verification
- Verify the word count in the updated workflow file.
- The build should proceed past the mnemonic validation step in GitHub Actions.
