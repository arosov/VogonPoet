package ovh.devcraft.vogonpoet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.devcraft.vogonpoet.domain.VogonSettings
import ovh.devcraft.vogonpoet.infrastructure.SettingsRepository
import ovh.devcraft.vogonpoet.ui.theme.*
import ovh.devcraft.vogonpoet.ui.utils.SystemFilePicker

@Composable
fun FirstBootScreen(onFinished: () -> Unit) {
    var storageDir by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val defaultStorageDir =
        remember {
            val home = System.getProperty("user.home")
            val osName = System.getProperty("os.name").lowercase()
            when {
                osName.contains("win") -> System.getenv("LOCALAPPDATA")?.let { "$it\\VogonPoet" } ?: "$home\\AppData\\Local\\VogonPoet"
                osName.contains("mac") -> "$home/Library/Application Support/VogonPoet"
                else -> "$home/.local/share/vogonpoet"
            }
        }

    MaterialTheme(colorScheme = GruvboxDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Welcome to VogonPoet",
                    style = MaterialTheme.typography.headlineLarge,
                    color = GruvboxYellowDark,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = GruvboxBg1,
                        ),
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Initial Setup Required",
                            style = MaterialTheme.typography.titleLarge,
                            color = GruvboxYellowDark,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "VogonPoet needs to download fairly large AI models and dependencies (approx. 1.5GB) to function. Please choose a directory where you'd like to store these assets.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = GruvboxFg1,
                            textAlign = TextAlign.Start,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Storage Dir
                        Text("Data Storage Directory", style = MaterialTheme.typography.labelLarge, color = GruvboxGreenDark)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = storageDir ?: defaultStorageDir,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (storageDir == null) GruvboxGray else GruvboxFg1,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    SystemFilePicker.selectFolder("Select Storage Directory", storageDir ?: defaultStorageDir)?.let {
                                        storageDir = it
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GruvboxBlueDark),
                            ) {
                                Text("Browse")
                            }

                            TextButton(
                                onClick = { storageDir = null },
                                enabled = storageDir != null && storageDir != defaultStorageDir,
                            ) {
                                Text(
                                    "Reset",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (storageDir != null && storageDir != defaultStorageDir) GruvboxRedDark else GruvboxGray,
                                )
                            }
                        }

                        Text(
                            text = "VogonPoet will create 'uv' and 'models' subdirectories here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GruvboxGray,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val finalBaseDir = storageDir ?: defaultStorageDir
                        val baseFile = java.io.File(finalBaseDir)
                        val uvDir = java.io.File(baseFile, "uv")
                        val modelsDir = java.io.File(baseFile, "models")

                        if (!uvDir.exists()) uvDir.mkdirs()
                        if (!modelsDir.exists()) modelsDir.mkdirs()

                        scope.launch {
                            SettingsRepository.save(
                                VogonSettings(
                                    isFirstBoot = false,
                                    uvCacheDir = uvDir.absolutePath,
                                    modelsDir = modelsDir.absolutePath,
                                ),
                            )
                            onFinished()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.5f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GruvboxGreenDark),
                ) {
                    Text("OK", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
