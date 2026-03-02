# DirectML GPU Splitting Implementation Plan

## Goal
Split the DirectML hardware options so each GPU detected on Windows gets its own explicit identifier (`dml:0`, `dml:1`, etc.) rather than being grouped under a single `dml` identifier. Update the auto mode selection and UI logic to handle this clearly.

## Changes Required

### 1. Update `hardware.py`

#### A. DirectML Enumeration
Rewrite `get_windows_gpu_names()` to return a list of tuples or dictionaries containing the index and name instead of just strings, or update `list_hardware()` to enumerate over the results.
- In `list_hardware()`, loop over Windows GPU names.
- Yield items like `{"id": f"dml:{i}", "name": f"{name} (DirectML)"}` for each GPU index `i`.

#### B. DirectML Memory Tracking
Update `_get_windows_memory(device_index: int = 0)` to accept a device index.
- PowerShell has to fetch memory for a specific GPU index rather than taking the global maximum. We can fetch an array using `Get-CimInstance` and index into it.

#### C. DirectML Naming
Update `get_device_name()` to handle `dml:X` format, similar to how it handles `cuda:X`.
- Split the `device_type` on `:` to get the index.
- Call the updated `get_windows_gpu_names()` and return the name at the specific index.

### 2. Update `engine.py`

#### A. Provider Initialization
Update `_get_providers()` to parse `dml:X` and pass the `device_id` parameter to `DmlExecutionProvider`.
- Currently it returns `["DmlExecutionProvider", "CPUExecutionProvider"]`.
- It should return `[("DmlExecutionProvider", {"device_id": dev_id}), "CPUExecutionProvider"]`.

#### B. Auto Resolution
Update `_resolve_device(device: str) -> str`.
- Auto mode should prioritize `cuda:X` explicitly.
- If CUDA is not available, it should fallback to explicitly identifying the best DirectML GPU (`dml:X`) rather than returning the generic string `dml`.
- The fallback logic for "CUDA requested but not available" should map to the explicit `dml:0` (or best DML GPU) instead of generic `dml`.

### 3. Update VogonPoet Client UI (AdvancedSettingsPanel.kt)
The UI logic in Kotlin already parses the `hardwareList` dynamically, so splitting DirectML into `dml:0` and `dml:1` from the backend `list_hardware()` response will inherently populate the UI correctly.
- Ensure the selected device string is saved and mapped properly without dropping the suffix. (It already treats the string as opaque, so `dml:0` will be saved natively).

## Execution Steps

1. **Modify `hardware.py`**:
   - Add parsing for `dml:X` in `get_device_name()` and `get_memory_usage()`.
   - Update `_get_windows_memory()` to support indexing via PowerShell arrays.
   - Update `get_windows_gpu_names()` to guarantee consistent ordering.
   - Modify `list_hardware()` to append `dml:X` dicts instead of a single merged `dml`.

2. **Modify `engine.py`**:
   - Refactor `_get_providers()` for `dml:X` to pass `{"device_id": dev_id}`.
   - Refactor `_resolve_device()` to return explicit indices like `dml:0` instead of `dml`.

3. **Validation**:
   - Run tests.
   - Verify `uv run python -c "from babelfish_stt.hardware import list_hardware; print(list_hardware())"` outputs the correct `dml:0` and `dml:1` structures.