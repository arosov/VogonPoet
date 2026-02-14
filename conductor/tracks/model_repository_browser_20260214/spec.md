# Model Repository Browser & Downloader

## Overview
Implement a UI feature that allows users to browse and download wake word models from remote repositories. This feature will start with support for the OpenWakeWord Community English models hosted on GitHub, with an extensible architecture for adding more sources later.

## Functional Requirements

### 1. Repository Discovery
- **Source**: Use GitHub API to discover models in `https://github.com/fwartner/home-assistant-wakewords-collection/tree/main/en`
- **Structure**: Each model is in its own directory (e.g., `computer/`, `hey_siri/`)
- **Files**: Each model directory contains versioned `.onnx` and `.tflite` file pairs (e.g., `computer_v1.onnx`, `computer_v1.tflite`, `computer_v2.onnx`, `computer_v2.tflite`)

### 2. Version Selection
- Only the latest version of each model should be discovered and displayed
- Version determination: Parse filename pattern `{model_name}_v{N}.onnx` and select highest N

### 3. Download Target
- Download to the existing wakeword models directory: `~/.config/vogonpoet/openwakeword_models/`
- Since OpenWakeWord community models are English wake words, save to: `openwakeword_models/start/en/{model_name}/`
- Download both `.onnx` and `.tflite` files for the selected version

### 4. UI Flow

#### Phase 1: Source Selection
- Add a new button in Advanced Settings labeled "Browse Model Repositories"
- Clicking opens a new window/dialog titled "Model Repository Browser"
- Display a list of available repository sources as buttons:
  - "OpenWakeWord Community - EN" (hardcoded for now)

#### Phase 2: Model Selection
- After clicking a source button, display a scrollable list of all available models
- List header contains a "Select All" checkbox/button
- Each model row displays:
  - Model name (e.g., "computer", "hey_siri")
  - Checkbox for selection
  - Version info (latest version only)
- Include "Download Selected" button at bottom
- Include "Cancel" button to return to source selection

#### Phase 3: Download Progress
- Show download progress for each selected model
- Display success/failure status per model
- Auto-refresh the main settings wakeword dropdown after successful downloads

### 5. Model Naming
- Display models with 2-letter language tag: "computer [en]", "hey_siri [en]"
- This aligns with the existing custom model feature's language classification

## Non-Functional Requirements
- Downloads should be cancellable
- Handle network failures gracefully with retry option
- Validate downloaded files (check file sizes)
- Don't re-download models that already exist locally (check before download)

## Acceptance Criteria
- [ ] User can open Model Repository Browser from Advanced Settings
- [ ] "OpenWakeWord Community - EN" button is displayed and functional
- [ ] Clicking the source shows a list of all available models (latest version only)
- [ ] "Select All" button selects/deselects all models
- [ ] User can select individual models via checkboxes
- [ ] Downloaded models appear in the wakeword dropdown with "[en]" suffix
- [ ] Both .onnx and .tflite files are downloaded for each selected model
- [ ] Models are saved to the correct subdirectory structure
- [ ] Duplicate downloads are prevented (models already present are skipped)

## Out of Scope
- Custom repository URL input (will be added later)
- Stop word models from this source (future enhancement)
- Non-English models from this specific repository
- Model metadata display (descriptions, accuracy scores)
- Model preview/testing before download
