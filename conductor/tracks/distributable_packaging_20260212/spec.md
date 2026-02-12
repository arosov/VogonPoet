# Specification: Distributable Packaging with Hydraulic Conveyor

## 1. Overview
This track aims to establish a robust distribution pipeline for VogonPoet using Hydraulic Conveyor. The goal is to produce installable packages for Windows (MSI/EXE) and Linux (AppImage) via GitHub Actions, ensuring the `babelfish` backend is correctly bundled and usable at runtime.

## 2. Functional Requirements

### 2.1. Backend Availability
*   **Mechanism**: The application will use the existing `babelfish.zip` located in the `jvmMain` resources.
*   **Runtime**: Ensure the application can locate, extract (if necessary), and execute the bundled babelfish backend.

### 2.2. Hydraulic Conveyor Configuration
*   **Config File**: Create a `conveyor.conf` file at the project root.
*   **Targets**:
    *   **Windows**: Generate Windows installers (MSI/EXE).
        *   *Signing*: Configure self-signed certificates for the initial setup.
    *   **Linux**: Generate AppImage packages.
        *   *Supersession*: This replaces the need for the manual Gradle AppImage script, consolidating packaging into Conveyor.
*   **Updates**: Enable Conveyor's self-update capabilities (Sparkle for Windows, standard repositories for Linux).

### 2.3. CI/CD Integration (GitHub Actions)
*   **Workflow**: Create or update a GitHub Actions workflow (e.g., `.github/workflows/release.yml`).
*   **Trigger**: The workflow should trigger on Release creation or specific tags.
*   **Action**: Use the `hydraulic-software/conveyor/action` to build and release the binaries.
*   **Artifacts**: The build artifacts (installers) should be attached to the GitHub Release.

## 3. Non-Functional Requirements
*   **Cross-Platform Build**: The packaging process must run successfully on GitHub Actions (likely `ubuntu-latest`).
*   **Clean Architecture**: Packaging logic should not pollute the core application code; use Gradle plugins or separate scripts where appropriate.

## 4. Out of Scope
*   MacOS packaging (for this specific track, though Conveyor supports it).
*   Official Code Signing (using valid PFX certificates) - Self-signed is accepted for this iteration.
*   Generation of `babelfish.zip` (it is assumed to be present in the repo).
