# Specification: Use GitHub Secret for Conveyor Signing Key

## 1. Overview
Migrate the hardcoded dummy signing key in the GitHub Actions workflow to a GitHub Secret for better security and flexibility.

## 2. Requirements
- Update `.github/workflows/release.yml` to use `${{ secrets.CONVEYOR_SIGNING_KEY }}` instead of a hardcoded value.
- The user will provide the actual value in the GitHub repository settings.

## 3. Verification
- Verify the workflow file contains the secret reference.
- Verify the workflow remains syntactically correct.
