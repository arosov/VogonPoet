# Bootstrap Improvements Plan

## Overview
Implement a user-friendly first-boot experience for VogonPoet with:
- LWJGL-based native file chooser
- First-boot detection
- Custom storage path configuration
- Simplified connection state handling

## Implementation Steps

### 1. LWJGL File Chooser Integration
- Add dependency to `gradle/libs.versions.toml`:
```toml
[libraries]
lwjgl-tinyfd = { module = "org.lwjgl:lwjgl-tinyfd", version = "3.3.7" }
```
- Replace Swing file chooser in `ConfigForm.kt` with:
```kotlin
import org.lwjgl.system.tinyfd.*
val path = tinyfd_open_dialog("Select Storage Directory", "", null, true)
```

### 2. First-Boot Detection
- Create `infrastructure/SettingsRepository.kt`:
```kotlin
data class VogonSettings(
    val isFirstBoot: Boolean = true,
    val storagePath: String? = null
)

object SettingsRepository {
    private const val SETTINGS_PATH = "~/.vogonpoet/settings.json"
    fun load(): VogonSettings { /* implementation */ }
    fun save(settings: VogonSettings) { /* implementation */ }
}
```

### 3. Bootstrap Process Enhancements
- Modify `BackendManager.kt` to pass storage path:
```kotlin
val env = mutableMapOf("VOGON_STORAGE_DIR" to (settings.storagePath ?: defaultPath))
pb.environment().putAll(env)
```
- Update `bootstrap.py` to respect storage directory

### 4. Connection State Refinement
- Update `KwBabelfishClient.kt` to handle new status messages:
```kotlin
"status" -> {
    val isBootstrapComplete = element["bootstrap_complete"]?.jsonPrimitive?.boolean ?: false
    if (isBootstrapComplete) _connectionState.value = ConnectionState.Connected
}
```

### 5. UI Flow Overhaul
- Implement `FirstBootOverlay` in `App.kt`:
```kotlin
if (settings.isFirstBoot) FirstBootOverlay() else MainContent()
```
- Simplify `StatusCard.kt` to show only "Preparing AI Models..." during bootstrap

## Critical Path
1. User launches VogonPoet → detects first boot
2. Shows native file chooser for storage path
3. User confirms → saves settings and starts backend
4. Backend provisions models in selected directory
5. Client waits for bootstrap completion before showing main UI

## Verification
- [ ] First boot shows native file chooser
- [ ] Settings persist after selection
- [ ] Models download to custom directory
- [ ] No connection flicker during bootstrap
- [ ] Main UI appears only after successful setup