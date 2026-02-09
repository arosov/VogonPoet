# Development Environment & Workflow

This document provides a comprehensive guide to the VogonPoet (Client) and Babelfish (Server) development environment, including build commands and message serialization workflows.

## 1. Overview

The system consists of a Kotlin Multiplatform client (**VogonPoet**) communicating with a local Python STT server (**Babelfish**) via WebTransport. Both projects reside as sibling directories along with a shared dependency (**pywebtransport**).

## 2. Directory Structure

The development environment is structured as follows:

```
/home/arosovsky/dev/VogonPoet/
├── VogonPoet/          # Client (Kotlin Multiplatform) - Current Directory
├── babelfish/          # Server (Python/UV)
└── pywebtransport/     # Python WebTransport Dependency (Local Path Dependency)
```

## 3. VogonPoet (Client)

*   **Technology:** Kotlin Multiplatform (KMP), Gradle.
*   **Target:** JVM / Desktop.
*   **Location:** `./` (Current directory)

### Key Commands (Run from `VogonPoet/`)

*   **Build (Assemble):**
    ```bash
    ./gradlew :composeApp:assemble
    ```

*   **Run Client:**
    ```bash
    ./gradlew :composeApp:run
    ```

*   **Clean:**
    ```bash
    ./gradlew clean
    ```

*   **Run Tests:**
    ```bash
    ./gradlew :composeApp:test
    ```

*   **Generate Kotlin Code from Schema:**
    ```bash
    ./gradlew :composeApp:generate
    ```
    *Note: This is automatically run during build, but useful to run manually after updating schema files.*

## 4. Babelfish (Server)

*   **Technology:** Python, `uv` package manager, PyTorch, Parakeet-Stream.
*   **Location:** `../babelfish`

### Key Commands (Run from `../babelfish`)

*   **Install Dependencies (Build):**
    ```bash
    uv sync
    ```

*   **Run Server:**
    ```bash
    uv run babelfish
    ```
    *Entry point defined in `pyproject.toml`.*

*   **Clean Cache:**
    ```bash
    uv cache clean
    ```
    *Alternatively, use the provided script: `./wipe_uv_cache.sh`*

*   **Generate Schema:**
    ```bash
    uv run python scripts/generate_schema.py --output babelfish_schema.json
    ```

## 5. Integration Testing

A script is provided in the `VogonPoet` root to run both the server and client simultaneously for integration testing.

*   **Script:** `test_config_sync.py`
*   **Usage:**
    ```bash
    python3 test_config_sync.py
    ```
    *   Starts `babelfish` server on port 8123.
    *   Waits for readiness.
    *   Starts `VogonPoet` client.
    *   Verifies configuration sync via stdout logs.
    *   Handles cleanup of processes.

---

## 6. Message Serialization & Modification Workflow

The communication between Client and Server relies on JSON messages sent over WebTransport streams. The structure of these messages (specifically the configuration) is strictly defined by a JSON Schema.

### Architecture

1.  **Source of Truth:** The Python Pydantic models in `babelfish/src/babelfish_stt/config.py`.
2.  **Schema Definition:** `babelfish_schema.json` (generated from Pydantic models).
3.  **Client Implementation:** Kotlin data classes in `ovh.devcraft.vogonpoet.infrastructure.model` are **generated** from the JSON schema using the `net.pwall.json.kotlin.codegen` Gradle plugin.

### Protocol Basics

*   **Transport:** WebTransport Bidirectional Streams.
*   **Format:** Newline-delimited JSON (NDJSON).
*   **Message Structure:**
    ```json
    {
      "type": "config",
      "data": { ... configuration object ... }
    }
    ```
    *   `type`: Identifies the message purpose (e.g., `config`, `update_config`, `hello`).
    *   `data`: The payload, often conforming to the `BabelfishConfig` schema.

### Workflow: How to Modify Messages

To add, remove, or modify fields in the configuration message, follow this strict sequence:

#### Step 1: Modify Python Models (Server)
Edit `babelfish/src/babelfish_stt/config.py` to add or change fields in the Pydantic models (`BabelfishConfig`, `HardwareConfig`, etc.).

#### Step 2: Regenerate JSON Schema (Server)
Run the generation script from the `babelfish` directory to update `babelfish_schema.json`.

```bash
cd ../babelfish
uv run python scripts/generate_schema.py --output babelfish_schema.json
```

#### Step 3: Propagate Schema to Client (Manual Step)
Copy the updated schema file to the client's resource directory.

```bash
# Assuming you are in VogonPoet root
cp ../babelfish/babelfish_schema.json composeApp/src/commonMain/resources/schema/babelfish_schema.json
```

#### Step 4: Regenerate Kotlin Code (Client)
Run the Gradle task to update the Kotlin data classes.

```bash
./gradlew :composeApp:generate
```
*Verify the changes by checking the generated code in `build/generated/sources/json-kotlin` or by trying to use the new fields in your IDE.*

#### Step 5: Update Application Logic
*   **Server:** Update `babelfish/src/babelfish_stt/server.py` or relevant logic to handle the new fields.
*   **Client:** Update `KwBabelfishClient.kt` or UI components to use the new fields available in the `Babelfish` object.

### Example: Adding a New Field

1.  **Python:** Add `new_field: str = "default"` to `HardwareConfig` in `config.py`.
2.  **Shell:** `uv run python scripts/generate_schema.py ...`
3.  **Shell:** `cp .../babelfish_schema.json .../resources/schema/`
4.  **Shell:** `./gradlew :composeApp:generate`
5.  **Kotlin:** Access `config.hardware.newField` in your code.
