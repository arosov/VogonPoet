# Spec: Name-Based Microphone Selection

## Overview
PORTAUDIO indices are transient and change across reboots or when hardware is plugged/unplugged. This track migrates the configuration to use the microphone's string name as the primary identifier, ensuring persistence across sessions and environment changes.

## Functional Requirements
1. **Schema Migration:** Replace `microphone_index` (int) with `microphone_name` (string) in the configuration.
2. **Name Resolution:** Implement a robust resolution mechanism that maps the configured name back to a transient index at runtime.
3. **Graceful Fallback:** If the configured name is not found (e.g., device unplugged), fallback to the system default microphone.
4. **Auto-Migration:** Automatically convert existing index-based configurations to the new name-based format on first run.
5. **UI Synchronization:** Update the frontend to select and display microphones based on their names.

## Acceptance Criteria
- Microphone selection persists even if its PortAudio index changes.
- Unplugging a selected microphone falls back to default without crashing.
- Re-plugging the selected microphone (even if at a new index) restores selection on next backend start/reconfig.
- Old configuration files are transparently upgraded.
