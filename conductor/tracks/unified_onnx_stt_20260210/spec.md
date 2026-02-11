# Spec: Unified ONNX STT Implementation

## Overview
This track transforms the Babelfish audio pipeline into a unified, high-performance ONNX-based architecture. By standardizing on `sherpa-onnx` and ONNX Runtime, we eliminate the massive dependency footprint of NVIDIA NeMo/PyTorch while providing full hardware acceleration for NVIDIA, AMD, and Intel GPUs across both Windows and Linux.

## Functional Requirements
1.  **Unified Inference Engine:** Replace the existing `STTEngine` (NeMo) with a `SherpaOnnxSTTEngine`.
2.  **Dynamic Dependency Management:** 
    *   Refactor `pyproject.toml` to move hardware runtimes into optional extras (`nvidia`, `dml`).
    *   Update `bootstrap.py` to detect local hardware and trigger the correct `uv sync --extra` command.
3.  **Intelligent Model Provisioning:**
    *   Implement an automated model downloader in `bootstrap.py`.
    *   **GPU Path:** Download the **FP16 Multilingual Parakeet-TDT 0.6B (v3)** ONNX model when a valid GPU is detected.
    *   **CPU Path:** Download the **INT8 Multilingual Parakeet-TDT 0.6B (v3)** (quantized) model when no GPU is available or forced CPU mode is active.
4.  **Hardware Targeting:**
    *   **Windows:** Default to `onnxruntime-directml` for universal support (AMD/Intel/NVIDIA).
    *   **Linux/NVIDIA:** Use `onnxruntime-gpu` (CUDA) for maximum performance.
5.  **VAD Optimization:** Force Silero VAD to CPU execution to maintain stability and avoid VRAM/dependency conflicts with ONNX runtimes.

## Non-Functional Requirements
*   **Multilingual Support:** Maintain support for 25 European languages via the Parakeet-TDT v3 weights.
*   **Installation Footprint:** Reduce the base installation size from >5GB to <1GB.
*   **Transcription Latency:** Maintain sub-200ms latency for real-time "Anchor" passes.
*   **Startup Speed:** Improve initialization speed by removing the heavy NeMo/PyTorch loading sequence.

## Acceptance Criteria
*   [ ] `uv sync` installs only the vendor-specific ONNX runtime required for the current machine.
*   [ ] NVIDIA users on Linux successfully transcribe using the CUDA provider in ONNX.
*   [ ] AMD/Intel users on Windows successfully transcribe using the DirectML provider in ONNX.
*   [ ] Systems with <6GB VRAM fallback to CPU and use the INT8 quantized model.
*   [ ] All references to `nemo_toolkit` and `parakeet-stream` are removed from the codebase.
*   [ ] Transcription accuracy is verified to be consistent with the previous NeMo implementation.

## Out of Scope
*   Switching the Wake-Word engine to ONNX (currently using `openwakeword`).
*   Implementing "True Streaming" (incremental token updates) for Parakeet-TDT (staying with the stable VAD-chunked "Anchor" pass).
