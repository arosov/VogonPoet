# Implementation Plan: Integration onnx-asr in Audio Pipeline

#### Phase 1: Dependency & Infrastructure Updates
- [ ] **Task 1.1: Update `pyproject.toml`**
  - Add `onnx-asr` to dependencies.
  - Evaluate if `sherpa-onnx` can be removed or kept for specific utilities.
- [ ] **Task 1.2: Refactor `bootstrap.py`**
  - Update model download logic for `onnx-asr` compatible models.
  - Implement quantization-aware model provisioning.
- [ ] **Task 1.3: Update Configuration & Schema**
  - Update `babelfish_schema.json` to include new quantization levels and backend settings.
  - Update `config.py` to handle the new options.

#### Phase 2: Core Engine Refactor
- [ ] **Task 2.1: Implement `STTEngine` with `onnx-asr`**
  - Replace `sherpa_onnx` calls with `onnx_asr.load_model`.
  - Implement the dynamic quantization selection logic based on hardware.
- [ ] **Task 2.2: Refine Hardware Mapping**
  - Ensure all execution providers (CUDA, DirectML, ROCm, etc.) are correctly passed to `onnx-asr`.

#### Phase 3: Integration & Testing
- [ ] **Task 3.1: Pipeline Integration**
  - Verify `STTEngine` integration with `pipeline.py`.
- [ ] **Task 3.2: Verification on Hardware**
  - [ ] Verify CPU mode with `int8`, `fp16`, `fp32`.
  - [ ] Verify NVIDIA GPU mode (CUDA/TensorRT).
  - [ ] Verify Windows GPU mode (DirectML).

#### Phase 4: Documentation & Cleanup
- [ ] **Task 4.1: Update project documentation** (Done in setup phase)
- [ ] **Task 4.2: Code Cleanup**
  - Remove legacy `sherpa-onnx` code and models.
