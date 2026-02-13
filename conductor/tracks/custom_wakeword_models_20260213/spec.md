# Track: Custom Wake Word Models Support

## Overview

Add the ability for users to drop custom wake word models into a designated directory and have them automatically discovered and available for selection in VogonPoet. Custom models will be displayed alongside built-in models with a `*` suffix to indicate they are user-provided.

## Directory Structure

Custom models are stored in the app data directory under `openwakeword_models/`:

```
~/.config/vogonpoet/openwakeword_models/
├── start/
│   ├── en/
│   │   └── alexa_custom/
│   │       ├── alexa.onnx
│   │       └── README.md
│   └── fr/
│       └── bonjour/
│           ├── bonjour.onnx
│           └── README.md
└── stop/
    ├── en/
    │   └── thanks/
    │       ├── thanks.onnx
    │       └── README.md
    └── de/
        └── danke/
            └── danke.onnx
```

## Functional Requirements

### Backend (babelfish)

1. **Model Discovery**
   - Scan `openwakeword_models/start/` and `openwakeword_models/stop/` recursively
   - Find all `.onnx` and `.tflite` model files
   - Extract model names from directory structure
   - Return full paths for loading

2. **Model Loading**
   - Support both `.onnx` (inference_framework="onnx") and `.tflite` (inference_framework="tflite") files
   - Auto-detect framework from file extension
   - Load custom models alongside built-in pretrained models
   - Log errors for invalid/unreadable models without crashing

3. **API Changes**
   - Update `list_wakewords()` to accept `app_data_dir` parameter
   - Return merged list of built-in + custom models
   - Custom models marked with `*` suffix (e.g., "alexa_custom*")
   - Include metadata in response: `{is_custom: bool, path: string}`

### Frontend (VogonPoet)

1. **Directory Management**
   - Create `openwakeword_models/start/` and `openwakeword_models/stop/` on startup
   - Provide "Open Models Folder" button to open directory in file manager

2. **UI Updates**
   - Display custom models with `*` suffix in dropdown
   - Group models: Built-in first, then Custom
   - Show model path tooltip for custom models
   - Support both start and stop wakeword selection

3. **Data Model Updates**
   - Parse new `list_wakewords` response format with metadata
   - Track `isCustom` flag for each wakeword
   - Handle `*` suffix in model names

## Non-Functional Requirements

- Models are discovered at runtime (each time dropdown opens) - no restart required
- Invalid models are logged but don't prevent other models from loading
- Custom models don't conflict with built-in models (they get `*` suffix)
- File system errors are handled gracefully

## Acceptance Criteria

- [ ] User can place `.onnx` or `.tflite` files in `openwakeword_models/start/en/my_model/` and see "my_model*" in the start wakeword dropdown
- [ ] User can place models in `openwakeword_models/stop/` for stop wakewords
- [ ] Custom models display with `*` suffix in UI
- [ ] "Open Models Folder" button opens the correct directory in file manager
- [ ] Both `.onnx` and `.tflite` formats work
- [ ] Model selection persists correctly (without `*` in saved config)
- [ ] Errors in custom models are logged but don't crash the app

## Out of Scope

- Model validation beyond file existence/readability
- Automatic model downloading from repositories
- Model metadata parsing beyond file path
- UI refresh button (models discovered on each dropdown open)
