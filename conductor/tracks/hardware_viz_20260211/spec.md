# Spec: Unified Hardware Memory Visualization and Smart Restart Logic

## Overview
This track provides a comprehensive hardware awareness system for VogonPoet. It implements cross-platform memory reporting (VRAM/Unified Memory) and a "smart restart" mechanism that prevents unnecessary backend re-initialization when switching from automatic hardware detection to an explicit selection of the same device.

## Functional Requirements

### 1. Cross-Platform Memory Detection (Backend)
- Implement a generic memory reporting utility supporting:
    - **NVIDIA:** via `nvidia-smi` (Linux/Windows).
    - **AMD:** via `rocm-smi` (Linux).
    - **Apple Silicon:** via `sysctl` and `vm_stat` (macOS Unified Memory).
    - **Windows DirectML:** via PowerShell Performance Counters (`GPU Adapter Memory`).
- Capture baseline memory usage before the AI model is loaded.
- Capture total and current memory usage after the model is loaded.

### 2. Smart Restart Logic (Backend)
- Compare the requested hardware device against the currently active device.
- If the target device matches the active device (e.g., switching from `auto` to `cuda:0` when `cuda:0` is already active), update the configuration file without triggering a server restart.

### 3. Visual Feedback (Frontend)
- **Active Device Subtext:** Update the `StatusCard` to display the resolved hardware device (e.g., "RUNNING ON CUDA:0") when the backend is ready.
- **VRAM Usage Bar:** Add a segmented progress bar in `AdvancedSettingsPanel`:
    - **System Segment:** Memory used by the OS and other apps.
    - **Babelfish Segment:** Memory used specifically by the STT model.
    - **Free Segment:** Remaining available hardware memory.
- **Graceful Degradation:** Hide the memory bar entirely if the platform is unsupported or memory stats cannot be retrieved.

## Acceptance Criteria
- Switching from "Auto" to the detected GPU in settings does not trigger a "Restarting..." state.
- The `StatusCard` correctly identifies the active acceleration provider.
- The VRAM bar accurately reflects the memory footprint on Windows (AMD/Intel/NVIDIA), Linux (AMD/NVIDIA), and macOS.
- No crashes occur if external tools (like `nvidia-smi`) are missing.

## Out of Scope
- Per-process memory breakdown beyond the Babelfish baseline.
- Support for multiple GPUs simultaneously in the UI bar (reports the primary active device).
