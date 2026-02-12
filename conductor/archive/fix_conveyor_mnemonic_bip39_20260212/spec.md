# Specification: Fix Hydraulic Conveyor Mnemonic using BIP39 words

## 1. Overview
The GitHub Actions workflow fails because the `signing_key` mnemonic words are not from the BIP39 wordlist.

## 2. Problem Statement
- **Error**: `The root signing key has a bad mnemonic code format. Error: Mnemonic Word`
- **Cause**: Hydraulic Conveyor requires root key mnemonics to use words from the standard BIP39 dictionary. My previous attempts used custom words ("vogon", "poet") which are not in that list.

## 3. Requirements

### 3.1. Update Mnemonic to BIP39
- Update the `signing_key` in `.github/workflows/release.yml` to use 12 valid BIP39 words.
- New key: `abandon ability able about above absent absorb abstract absurd abuse access accident / 2026-02-12T22:00:00Z`

## 4. Verification
- The build should proceed past the mnemonic validation step in GitHub Actions.
