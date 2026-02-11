# Implementation Plan: Unified ONNX STT Implementation

#### Phase 1: Dependency & Model Infrastructure
- [x] **Task 1.1: Refactor `pyproject.toml`**
- [x] **Task 1.2: Update `bootstrap.py` - Hardware Logic**
- [x] **Task 1.3: Update `bootstrap.py` - Model Provisioning**
- [ ] Task: Conductor - User Manual Verification 'Phase 1'

#### Phase 2: Core Engine Migration
- [x] **Task 2.1: Unified Hardware Classification (`hardware.py`)**
- [x] **Task 2.2: Implement `SherpaOnnxSTTEngine` (`engine_onnx.py`)**
- [x] **Task 2.3: Decouple VAD (`vad.py`)**
- [ ] Task: Conductor - User Manual Verification 'Phase 2'

#### Phase 3: Integration & Clean-up
- [x] **Task 3.1: Update Application Entrypoint (`main.py`)**
- [x] **Task 3.2: Configuration Schema Update**
- [x] **Task 3.3: Final Code Purge**
- [x] Task: Conductor - User Manual Verification 'Phase 3'

#### Phase 4: Final Verification
- [x] **Verification:** Validate that transcription accuracy and latency meet the targets across simulated OS/GPU environments.
- [x] **Verification:** Confirm the total installation size is under the 1GB threshold.
