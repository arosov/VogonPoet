package ovh.devcraft.vogonpoet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ovh.devcraft.vogonpoet.presentation.MainViewModel
import ovh.devcraft.vogonpoet.ui.components.ConfigForm
import ovh.devcraft.vogonpoet.ui.components.StatusCard
import ovh.devcraft.vogonpoet.ui.theme.*

@Composable
fun App(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val vadState by viewModel.vadState.collectAsState()
    val config by viewModel.config.collectAsState()

    MaterialTheme(
        colorScheme = GruvboxDarkColorScheme,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                StatusCard(
                    connectionState = connectionState,
                    vadState = vadState,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Configuration Form
                ConfigForm(
                    config = config,
                    onSave = { newConfig ->
                        viewModel.saveConfig(newConfig)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
