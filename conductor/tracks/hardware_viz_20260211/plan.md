# Plan: Unified Hardware Memory Visualization and Smart Restart Logic

## Phase 1: Backend Memory Detection & Logic [checkpoint: 1d4feec]
- [x] Task: Implement `get_memory_usage` in `hardware.py` supporting NVIDIA, ROCm, macOS, and Windows PowerShell counters deea16b
- [x] Task: Update `HardwareConfig` schema in `config.py` to include runtime status fields (`active_device`, `vram_*`) deea16b
- [x] Task: Update `STTEngine` to capture and report baseline vs. model memory footprint deea16b
- [x] Task: Refine `BabelfishServer.reconfigure` to prevent restarts when the active hardware hasn't changed deea16b
- [x] Task: Conductor - User Manual Verification 'Backend Logic' (Protocol in workflow.md) 1d4feec

## Phase 2: Protocol Synchronization
- [~] Task: Regenerate JSON schema from Pydantic models and propagate to client resources
- [ ] Task: Regenerate Kotlin data classes to match the new schema
- [ ] Task: Conductor - User Manual Verification 'Protocol Sync' (Protocol in workflow.md)

## Phase 3: Frontend UI Components
- [ ] Task: Update `StatusCard` to display active hardware subtext
- [ ] Task: Implement `VramUsageBar` component in `AdvancedSettingsPanel`
- [ ] Task: Integrate VRAM visualization into the settings layout with graceful hiding
- [ ] Task: Conductor - User Manual Verification 'Frontend UI' (Protocol in workflow.md)
