# Multiplatform GPU Acceleration Plan (NVIDIA, AMD, Intel)

This document outlines the strategy for achieving full hardware acceleration for the Babelfish STT server across all major GPU vendors on Windows and Linux, while maintaining **PyTorch 2.6.0** stability.

## 1. Architectural Strategy

To maximize performance while minimizing vendor-specific friction, Babelfish will employ a **Dual-Engine Strategy**:

*   **NVIDIA Path (Native):** Continues using `parakeet-stream` (NVIDIA NeMo) with native CUDA kernels. This provides the best possible performance and lowest latency for NVIDIA users.
*   **AMD/Intel Path (ONNX):** Uses a new engine based on **onnx-asr** and **ONNX Runtime**. 
    *   **Windows:** Accelerated via the **DirectML** (DirectX 12) execution provider.
    *   **Linux:** Accelerated via **ROCm** (AMD) or **OpenVINO** (Intel) execution providers.
*   **VAD (Voice Activity Detection):** Standardized on **CPU execution** for Silero VAD. This eliminates dependency conflicts with vendor-specific PyTorch builds and maintains sub-1% CPU overhead.

---

## 2. Implementation Phases

### Phase 1: Dependency & Model Infrastructure
*   **Dependencies:** Add `onnx-asr` to core dependencies and `onnxruntime-directml` as an optional Windows extra.
*   **Bootstrapper:** Update `bootstrap.py` to:
    *   Detect GPU vendor (NVIDIA vs. AMD/Intel).
    *   Download the appropriate ONNX model (approx. 600MB) from the `onnx-asr` compatible model zoo if a non-NVIDIA GPU is detected.
    *   Install the appropriate ONNX Runtime variant during `uv sync`.

### Phase 2: The Cross-Vendor Engine (`engine_onnx.py`)
*   Create a new `SherpaOnnxSTTEngine` implementing the `Reconfigurable` interface (now using `onnx-asr` backend).
*   Handle the conversion of the streaming "Ghost/Anchor" logic to the ONNX offline transcription model by managing rolling audio buffers and text slicing.
*   Ensure interface parity with the existing `STTEngine` class.

### Phase 3: Hardware Orchestration
*   **`hardware.py`:** Update `HardwareManager` to classify GPUs into `NATIVE_CUDA` and `ONNX_COMPATIBLE`.
*   **`main.py`:** Update the engine factory to instantiate the correct engine based on the `HardwareManager` probe result.
*   **`vad.py`:** Force Silero VAD to CPU to ensure stability across all platforms without needing specialized `torch-directml` builds.

### Phase 4: UI & Configuration
*   Update `config.py` and the JSON schema to expose backend-specific settings (e.g., ONNX execution provider choice).
*   Provide real-time feedback in the VogonPoet client regarding which acceleration backend is active.

---

## 3. Performance & Stability Targets

| Platform | Vendor | Technology | Expected Status |
| :--- | :--- | :--- | :--- |
| **Windows** | NVIDIA | Native CUDA | **Full Acceleration** |
| **Windows** | AMD | ONNX + DirectML | **Full Acceleration** |
| **Windows** | Intel | ONNX + DirectML | **Full Acceleration** |
| **Linux** | NVIDIA | Native CUDA | **Full Acceleration** |
| **Linux** | AMD | ONNX + ROCm | **Full Acceleration** |
| **Linux** | Intel | ONNX + OpenVINO | **Full Acceleration** |

## 4. Maintenance
By using ONNX Runtime for non-NVIDIA hardware, we decouple the heavy STT acceleration from the PyTorch version, ensuring that future updates to `torch` 2.6+ won't break AMD or Intel support.
