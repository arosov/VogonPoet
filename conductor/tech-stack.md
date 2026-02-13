# Tech Stack - VogonPoet

## Core UI & Logic
- **Language:** Kotlin 2.3.0
- **UI Framework:** Compose Multiplatform 1.10.0
- **Design System:** Material Design 3 with **Gruvbox Dark** custom theme
- **Lifecycle Management:** AndroidX Lifecycle (Runtime & ViewModel) 2.9.6

## Concurrency & Networking
- **Asynchronous Programming:** Kotlinx Coroutines 1.10.2
- **Communication Protocol:** WebSockets via **Ktor** (for low-latency backend streaming)

## Build System & Infrastructure
- **Build Tool:** Gradle (Kotlin DSL)
- **Dependency Management:** Gradle Version Catalogs (`libs.versions.toml`)
- **Primary Target:** JVM / Desktop (Linux, Windows, macOS)

## Babelfish (Backend)
- **Language:** Python 3.12
- **Input Simulation:** pynput, pyperclip (for clipboard-based injection)
- **ASR Engine:** onnx-asr
