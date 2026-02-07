# Initial Concept
The name of this project is VogonPoet. It aims to provide a minimalist frontend to babelfish a modern STT server.
Communication with babelfish happens over webtransport to battletest kwtransport.

# Product Definition - VogonPoet

## Vision
VogonPoet is a minimalist, high-performance desktop frontend for the Babelfish STT server. It serves as a visual bridge for the Babelfish backend, prioritizing low-latency feedback and a focused user experience. While Babelfish handles the heavy lifting of audio processing and transcription, VogonPoet provides the essential controls and real-time visualization required for a seamless system-wide dictation experience.

## Target Audience
- **Desktop Power Users:** Individuals on Linux, Windows, or macOS who require a lightweight, non-intrusive interface for system-wide speech-to-text.

## Core Goals
- **Extreme Minimalist UI:** A distraction-free interface designed to stay out of the way, allowing users to focus on their primary tasks while dictating.
- **Low Latency Interaction:** The UI is optimized to match the speed of the Babelfish backend, ensuring visual feedback for audio events (like VAD triggers) feels instantaneous.

## Key Features
- **Visual Status Indicators:** Provides clear, real-time visual signals for the backend state, including:
    - **VAD State:** Visual cues when the system is actively "listening."
    - **Wake-Word Detection:** Confirmation when the backend has been triggered by a wake-word.
    - **Connectivity Status:** Immediate feedback on the WebTransport connection to the Babelfish server.
- **Backend Management:**
    - **Always-on Status Overlay:** A persistent, minimal indicator of the system's current state.
    - **Configuration Dashboard:** A dedicated interface to toggle backend modes (e.g., Two-Pass refinement, Wake-word activation) and manage hardware/audio device selection.

## Visual Identity & UX
- **Retro-Technical Utility:** Combines **Material Design 3** components with the **Gruvbox Dark** color palette for a high-contrast, technical aesthetic that minimizes screen glare.
- **Compact Presence:** Designed to function efficiently in a **Compact/Overlay Mode**, minimizing its footprint on the user's workspace.
