# Specification - Configurable Input Injection Strategies

## Overview
This track introduces configurable strategies for injecting text into system windows. The goal is to decouple text injection from keyboard layouts and encoding issues by offering robust alternatives to direct keystroke simulation.

## Functional Requirements

### 1. Input Strategies
Babelfish shall support the following injection strategies, selectable via configuration:

*   **Direct Input (pynput)**: Simulates individual keystrokes. Fast but susceptible to layout/encoding issues.
*   **Clipboard Paste (Default)**: Copies text to the system clipboard and simulates a "Paste" command (Ctrl+V / Cmd+V). Most robust for special characters.
*   **Native External Tools**: Uses platform-specific CLI tools to inject text.
    *   **Linux**: `xdotool`
    *   **Windows**: PowerShell `Send-Keys`
    *   **macOS**: AppleScript `osascript`
*   **Hybrid**: 
    *   Checks if characters are in a "safe" static map (standard ASCII).
    *   Uses **Direct Input** for safe characters.
    *   Uses **Clipboard Paste** for special characters.

### 2. Behavior Logic
*   **Ghost Text**: ALWAYS uses **Direct Input** (pynput). 
    *   *Reasoning*: Performance and smoothness. Ghost updates are frequent and transient; flooding the clipboard or spawning subprocesses for every character update is non-viable.
*   **Finalized Text**: Uses the **User Selected Strategy**.
*   **Ghost Replacement**: When finalizing, the system must still "backspace" the ghost text (using Direct Input) before injecting the final text using the selected strategy.

### 3. Configuration & Persistence
*   **Location**: `BabelfishConfig` (Server-side).
*   **New Field**: `system_input.strategy` (Enum: `direct`, `clipboard`, `native`, `hybrid`).
*   **Default**: `clipboard`.

### 4. VogonPoet UI
*   **Advanced Settings**: Add a dropdown menu in the "System-wide Input" section to select the strategy.

## Non-Functional Requirements
*   **Clipboard Restoration**: (Nice to have) If possible, the Clipboard strategy should attempt to restore the previous clipboard content after pasting, though this may be racy.
*   **Latency**: Native strategy (subprocess calls) might introduce slight latency; this is acceptable for finalized text.

## Acceptance Criteria
- [ ] User can select "Clipboard Paste" in VogonPoet settings.
- [ ] When "Clipboard Paste" is selected:
    - [ ] Ghost text is typed letter-by-letter.
    - [ ] Final text is pasted at once.
- [ ] When "Hybrid" is selected:
    - [ ] Simple text is typed.
    - [ ] Complex text (e.g., emojis, accents) is pasted.
- [ ] Native tools work on their respective platforms (if tools are installed).
