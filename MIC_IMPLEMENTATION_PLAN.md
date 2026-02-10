# Microphone Selection & Bootstrapping Fix Plan

## 1. Backend Enhancements (babelfish)
### 1.1 Microphone Metadata
Modify `babelfish/src/babelfish_stt/hardware.py` to include default device information in the list.
- **Action:** Update `list_microphones()` to call `find_best_microphone()`.
- **Action:** Add `"is_default": bool` to each dictionary in the returned list.

### 1.2 Configuration Clarification
- The field is named `vram_limit_gb` in the current schema/models.
- A `null` value is intentional and represents "no limit" (unbounded VRAM usage).
- *Optional:* Check if `vram_limit_gpu` exists anywhere as a legacy field and clean up if necessary.

## 2. Frontend Enhancements (VogonPoet)
### 2.1 Early Hardware Discovery
Modify `VogonPoet/composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/components/ConfigForm.kt`.
- **Action:** Update `LaunchedEffect(connectionState)` to trigger on `ConnectionState.Bootstrapping`.
- **Impact:** Microphones and hardware lists will populate as soon as the WebTransport session is established, even if the STT engine is still loading models.

### 2.2 Test Button Logic
- The test button is already usable if the connection state is `Connected` or `Bootstrapping` (via the `isReady` variable).
- Ensure the backend handles `set_mic_test` properly even when the pipeline is just starting. (Current implementation in `BabelfishServer` already persists `mic_test_enabled` and applies it to the pipeline once created).

## 3. Verification
- Start `babelfish` server.
- Start `VogonPoet` client.
- Observe that the microphone dropdown is populated while "Loading..." messages are still appearing.
- Verify that the "Test Mic" button works immediately.
