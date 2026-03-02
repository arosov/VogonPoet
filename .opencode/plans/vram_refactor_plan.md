# VRAM & Hardware Config Refactor Plan

Based on the goal to sanitize VRAM usage metrics and simplify hardware configuration, here is the detailed implementation plan:

## 1. Prevent VRAM Fields from Persisting to Disk
- **Issue:** `vram_total_gb`, `vram_used_baseline_gb`, and `vram_used_model_gb` are currently saved to `babelfish.config.json`. They should be runtime-only status variables.
- **Fix:** In `babelfish/src/babelfish_stt/config_manager.py` within the `save()` method, pass the `exclude` parameter to Pydantic's `model_dump()`. This will stop VRAM and `active_device` fields from being written to disk, but they will still exist in memory and be transmitted over websockets to the client.

## 2. Simplify Hardware Configuration Schema
- **Issue:** The triplet of `device`, `auto_detect`, and `active_device` is overly complex. The backend only needs a single persisted identifier (`device: str`).
- **Fix:** 
  - Remove `auto_detect: bool = True` from `HardwareConfig` in `babelfish/src/babelfish_stt/config.py`.
  - The default `device` will just be `"auto"`. If the user changes it to `"dml"`, `"cuda:0"`, etc., that single identifier is saved.
  - Update `engine.py`, `server.py`, `main.py`, and `config_manager.py` to remove references and fallback logic associated with `auto_detect`. The `device` string handles it inherently (where `"auto"` means auto-detect).
  - Update tests (`test_startup_config.py`, `test_smart_restart.py`, etc.) to remove `auto_detect`.

## 3. Propagate Schema Changes to the Kotlin Client
- **Generate Schema:** Run `uv run python scripts/generate_schema.py --output babelfish_schema.json` in the `babelfish` directory.
- **Copy Schema:** Copy the updated `babelfish_schema.json` into `VogonPoet/composeApp/src/commonMain/resources/schema/`.
- **Regenerate Models:** Run `./gradlew :composeApp:generate` in the `VogonPoet` directory to regenerate the Kotlin `BabelfishConfig` classes.

## 4. Update the Client UI (`AdvancedSettingsPanel.kt`)
- **Fix:** Remove `isAutoDetect` related logic. The UI will now simply read the `device` string. 
- If `device == "auto"`, the dropdown reflects "Auto Detect".
- If the user changes the dropdown, it just updates the `device` string in the config.
- The VRAM metrics bar will continue to function seamlessly as it reads from the runtime properties still provided by the `config` websocket payload.