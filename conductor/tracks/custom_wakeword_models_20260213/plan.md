# Implementation Plan: Custom Wake Word Models

## Phase 1: Backend Discovery and Loading ✅ COMPLETED

### Task 1.1: Create wakeword_discovery.py ✅
- [x] Create `babelfish/src/babelfish_stt/wakeword_discovery.py`
- [x] Implement `scan_custom_models(app_data_dir: str, model_type: str) -> Dict[str, str]`
  - Scan `openwakeword_models/{model_type}/` recursively
  - Find `.onnx` and `.tflite` files
  - Return dict: `{model_name*: full_path}`
- [x] Implement `validate_model_file(path: str) -> bool`
  - Check file exists and is readable
  - Log errors for invalid files
- [x] Add unit tests for discovery functions (22 tests passing)

### Task 1.2: Update wakeword.py ✅
- [x] Import and use `wakeword_discovery.scan_custom_models`
- [x] Update `list_wakewords(app_data_dir=None)` signature
- [x] Merge pretrained models with custom models (custom get `*` suffix)
- [x] Update `WakeWordEngine._get_target_path()` to handle `*` suffix
- [x] Add auto-detection of inference framework from file extension

### Task 1.3: Update server.py ✅
- [x] Get `VOGON_APP_DATA_DIR` from environment
- [x] Pass to `list_wakewords()` call
- [x] Update response format to include metadata:
  ```json
  {
    "type": "wakewords_list",
    "data": ["alexa", "hey_jarvis", "custom_model*"],
    "metadata": {
      "custom_model*": {"is_custom": true, "path": "/full/path"}
    }
  }
  ```

### Task 1.4: Phase Completion Checkpoint
- [x] Task: Conductor - User Manual Verification 'Phase 1: Backend Discovery and Loading' (Protocol in workflow.md)

## Phase 2: Frontend Data Layer ✅ COMPLETED

### Task 2.1: Update SettingsRepository ✅
- [x] Add `openwakewordModelsDir` property
- [x] Create `start/` and `stop/` subdirectories on init
- [x] Add helper to open models folder in file manager (platform-specific)

### Task 2.2: Update Data Models ✅
- [x] Create `WakewordInfo` data class with `name`, `isCustom`, `path` fields
- [x] Update `BabelfishClient.listWakewords()` to return `List<WakewordInfo>`
- [x] Parse new response format with metadata
- [x] Update `MainViewModel` to use new data structure
- [x] Update tests for new data model

### Task 2.3: Phase Completion Checkpoint
- [x] Task: Conductor - User Manual Verification 'Phase 2: Frontend Data Layer' (Protocol in workflow.md)

## Phase 3: UI Implementation ✅ COMPLETED

### Task 3.1: Update ConfigForm UI ✅
- [x] Modify wakeword dropdown to display `*` suffix for custom models
- [x] Group models: Built-in first, Custom second

### Task 3.2: Handle Model Selection ✅
- [x] Strip `*` suffix when displaying in text field (saved with suffix in config)
- [x] Ensure both start and stop wakeword selectors work correctly

### Task 3.3: Phase Completion Checkpoint
- [x] Task: Conductor - User Manual Verification 'Phase 3: UI Implementation' (Protocol in workflow.md)

## Phase 4: Integration and Testing ✅ COMPLETED

### Task 4.1: End-to-End Testing ✅
- [x] Test placing `.onnx` model in `openwakeword_models/start/en/test/`
- [x] Verify model appears as "test*" in dropdown
- [x] Test selecting and using the custom model
- [x] Test stop wakeword with custom model
- [x] Test `.tflite` format support

### Task 4.2: Error Handling Testing ✅
- [x] Test with corrupted model file (should log error, not crash)
- [x] Test with missing directory (should handle gracefully)
- [x] Test with empty model directory

### Task 4.3: Update Documentation ⏳ SKIP
- [ ] Add section to README about custom wake word models
- [ ] Document directory structure
- [ ] Provide example of adding custom model

### Task 4.4: Phase Completion Checkpoint
- [x] Task: Conductor - User Manual Verification 'Phase 4: Integration and Testing' (Protocol in workflow.md)

---

## Summary

**Implemented:**

### Backend (babelfish)
1. **wakeword_discovery.py** - Module for scanning and validating custom wake word models
2. **Updated wakeword.py** - Integration with custom model discovery, auto-detection of inference framework (.onnx vs .tflite)
3. **Updated server.py** - Enhanced `list_wakewords` endpoint with metadata support
4. **23 unit tests** for the discovery module (including exclusion tests)

### Frontend (VogonPoet)
1. **SettingsRepository.kt** - Added `openwakewordModelsDir` property with automatic directory creation
2. **VogonConfig.kt** - Added `WakewordInfo` data class
3. **BabelfishClient.kt** - Updated to parse new response format with metadata
4. **MainViewModel.kt** - Updated to use `List<WakewordInfo>`
5. **ConfigForm.kt** - Updated UI with:
   - Grouped dropdown (Built-in / Custom)
   - `*` suffix indicator for custom models
6. **MainViewModelTest.kt** - Updated test mocks

### Directory Structure
```
~/.config/vogonpoet/openwakeword_models/
├── start/
│   └── {lang}/
│       └── {modelname}/
│           └── model.onnx
└── stop/
    └── {lang}/
        └── {modelname}/
            └── model.onnx
```

**All automated tests passing:**
- 23 discovery module tests
- JVM tests for composeApp

**Build verification:**
- Kotlin code compiles successfully
- Babelfish bundles successfully
