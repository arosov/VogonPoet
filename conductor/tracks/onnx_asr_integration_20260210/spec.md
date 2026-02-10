# Spec: Integration onnx-asr in Audio Pipeline

## Overview
This track replaces the current `sherpa-onnx` block in the Babelfish audio pipeline with `onnx-asr`. The goal is to leverage the higher-level API of `onnx-asr` while maintaining and improving the multiplatform, multi-GPU capabilities of the system. It specifically addresses the quantization requirements for different hardware backends.

## Functional Requirements
1. **Engine Replacement:** Replace `sherpa-onnx` with `onnx-asr` in `babelfish/src/babelfish_stt/engine.py`.
2. **Multi-GPU Capability:** 
   - Support NVIDIA GPUs via CUDA and TensorRT.
   - Support AMD and Intel GPUs via DirectML (Windows).
   - Support AMD GPUs via ROCm (Linux).
   - Support Intel GPUs via OpenVINO (Linux/Windows).
3. **Quantization Logic:**
   - **CPU Mode:** Allow all available quantizations (`int8`, `fp16`, `fp32`).
   - **GPU Mode:** Force the use of the highest available precision (typically `fp16` or `fp32` depending on the provider and hardware support) to ensure maximum accuracy and performance.
4. **Dynamic Model Provisioning:** Update `bootstrap.py` to handle `onnx-asr` model formats and quantization variants.
5. **Configuration Schema:** Update the `babelfish_schema.json` and `config.py` to reflect the new quantization and backend options.

## Non-Functional Requirements
- **Latency:** Maintain or improve on the current sub-200ms latency for transcription chunks.
- **Accuracy:** Ensure that the switch to `onnx-asr` does not degrade transcription quality.
- **Portability:** Ensure the solution remains compatible with both Windows and Linux across diverse hardware.

## Acceptance Criteria
- [ ] `STTEngine` successfully uses `onnx_asr.load_model`.
- [ ] Quantization is correctly restricted on GPU (only highest precision allowed).
- [ ] All quantization levels are selectable when running on CPU.
- [ ] Successful transcription verified on NVIDIA (CUDA), AMD (DirectML), and CPU backends.
- [ ] Installation footprint remains optimized.

## Out of Scope
- Migrating the Wake-Word engine.
- Changes to the frontend UI layout beyond adding/updating settings.
