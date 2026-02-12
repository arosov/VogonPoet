package ovh.devcraft.vogonpoet.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import ovh.devcraft.vogonpoet.domain.model.*

interface BabelfishClient {
    val connectionState: StateFlow<ConnectionState>
    val vadState: StateFlow<VadState>
    val engineMode: StateFlow<EngineMode>
    val events: SharedFlow<EngineEvent>
    val messages: StateFlow<List<ProtocolMessage>>
    val config: StateFlow<VogonConfig?>

    suspend fun connect()

    fun disconnect()

    suspend fun saveConfig(config: VogonConfig)

    suspend fun listMicrophones(): List<Microphone>

    suspend fun listHardware(): List<HardwareDevice>

    suspend fun listWakewords(): List<String>

    suspend fun setMicTest(enabled: Boolean)

    suspend fun forceListen()

    suspend fun toggleListening()

    fun notifyBootstrap()
}

data class Microphone(
    val index: Int,
    val name: String,
    val isDefault: Boolean,
)

data class HardwareDevice(
    val id: String,
    val name: String,
)
