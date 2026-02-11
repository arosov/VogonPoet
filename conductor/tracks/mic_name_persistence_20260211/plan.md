# Plan: Mic Name Persistence

## Phase 1: Backend Refactoring
- [x] Task: Update `HardwareConfig` in `config.py` to use `microphone_name`
- [x] Task: Implement name resolution in `hardware.py`
- [x] Task: Update `AudioStreamer` in `audio.py` to handle name-based initialization
- [x] Task: Implement migration and validation logic in `config_manager.py`
- [x] Task: Conductor - User Manual Verification 'Backend Mic Logic'

## Phase 2: Protocol & Frontend
- [x] Task: Regenerate JSON schema and Kotlin models
- [x] Task: Update `ConfigForm.kt` UI to bind to `microphone_name`
- [x] Task: Final verification of persistent selection after index shift
- [x] Task: Conductor - User Manual Verification 'Frontend Mic Logic'
