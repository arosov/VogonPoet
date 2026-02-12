package ovh.devcraft.vogonpoet.infrastructure

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import ovh.devcraft.vogonpoet.domain.HardwareDevice
import ovh.devcraft.vogonpoet.domain.Microphone
import ovh.devcraft.vogonpoet.domain.model.*
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish
import java.util.concurrent.ConcurrentHashMap
import ovh.devcraft.vogonpoet.domain.BabelfishClient as IBabelfishClient

class BabelfishClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : IBabelfishClient {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
            encodeDefaults = true
        }

    private val client =
        HttpClient(CIO) {
            install(WebSockets) {
                pingIntervalMillis = 15000
            }
        }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _vadState = MutableStateFlow(VadState.Idle)
    override val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private val _engineMode = MutableStateFlow(EngineMode.Wakeword)
    override val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>()
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val _messages = MutableStateFlow<List<ProtocolMessage>>(emptyList())
    override val messages: StateFlow<List<ProtocolMessage>> = _messages.asStateFlow()

    private val _config = MutableStateFlow<Babelfish?>(null)
    override val config: StateFlow<Babelfish?> = _config.asStateFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val responseHandlers = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()

    private val SERVER_URL = "ws://127.0.0.1:8123/config"

    override suspend fun connect() {
        if (connectionJob?.isActive == true) return

        connectionJob =
            scope.launch {
                // Monitor server status
                launch {
                    BackendController.serverStatus.collectLatest { status ->
                        when (status) {
                            ServerStatus.INITIALIZING -> {
                                _connectionState.value = ConnectionState.Disconnected
                            }

                            ServerStatus.BOOTSTRAPPING -> {
                                // Connection loop will handle connecting to bootstrap server
                            }

                            ServerStatus.STARTING -> {
                                _connectionState.value = ConnectionState.BabelfishRestarting
                            }

                            ServerStatus.STOPPED -> {
                                if (_connectionState.value !is ConnectionState.Error) {
                                    _connectionState.value = ConnectionState.Disconnected
                                }
                            }

                            ServerStatus.READY -> {
                                // Handled by the connection loop
                            }
                        }
                    }
                }

                while (isActive) {
                    val currentStatus = BackendController.serverStatus.value

                    if (currentStatus != ServerStatus.READY && currentStatus != ServerStatus.BOOTSTRAPPING) {
                        delay(500)
                        continue
                    }

                    _connectionState.value = ConnectionState.Connecting
                    VogonLogger.i("Attempting to connect to Babelfish at $SERVER_URL...")

                    try {
                        client.webSocket(SERVER_URL) {
                            session = this

                            val status = BackendController.serverStatus.value
                            if (status == ServerStatus.READY) {
                                // Even if the server is listening, it might still be loading models.
                                // We stay in a starting state until we get the first real status message.
                                _connectionState.value = ConnectionState.Bootstrapping("Starting engines...")
                                VogonLogger.i("Connected to Babelfish, waiting for engine ready...")
                            } else {
                                _connectionState.value = ConnectionState.Bootstrapping("Connected to bootstrap...")
                                VogonLogger.i("Successfully connected to Bootstrap Server.")
                            }

                            // Send HELLO
                            val helloMsg = "{\"type\":\"hello\"}"
                            send(helloMsg)
                            logMessage(MessageDirection.Sent, helloMsg)

                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        handleIncomingLine(text)
                                    }

                                    else -> {}
                                }
                            }
                        }
                        VogonLogger.i("Connection session ended.")
                    } catch (e: Exception) {
                        VogonLogger.e("Connection failed or lost", e)
                    } finally {
                        session = null
                        delay(1000) // Fixed retry delay
                    }
                }
            }
    }

    internal fun handleIncomingLine(line: String) {
        if (line.isBlank()) return
        logMessage(MessageDirection.Received, line)
        try {
            val element = json.parseToJsonElement(line)
            if (element is JsonObject) {
                val type = element["type"]?.jsonPrimitive?.contentOrNull ?: return

                responseHandlers[type]?.complete(element)

                when (type) {
                    "config" -> {
                        val configData = element["data"]
                        if (configData != null) {
                            val config = json.decodeFromJsonElement<Babelfish>(configData)
                            _config.value = config
                        }
                    }

                    "status" -> {
                        val message = element["message"]?.jsonPrimitive?.contentOrNull
                        val vadStateStr = element["vad_state"]?.jsonPrimitive?.contentOrNull
                        val engineState = element["engine_state"]?.jsonPrimitive?.contentOrNull
                        val modeStr = element["mode"]?.jsonPrimitive?.contentOrNull

                        if (vadStateStr == "listening" || vadStateStr == "idle" ||
                            engineState == "ready" || message == "Engine Ready!"
                        ) {
                            _connectionState.value = ConnectionState.Connected
                            when (vadStateStr) {
                                "listening" -> _vadState.value = VadState.Listening
                                "idle" -> _vadState.value = VadState.Idle
                            }
                            when (modeStr) {
                                "wakeword" -> _engineMode.value = EngineMode.Wakeword
                                "active" -> _engineMode.value = EngineMode.Active
                            }
                        } else if (message != null) {
                            _connectionState.value = ConnectionState.Bootstrapping(message)

                            // If we receive "Starting Babelfish...", it means the bootstrap server is about to close.
                            // We should stop the current session and wait for the real server.
                            if (message.contains("Starting Babelfish...")) {
                                scope.launch {
                                    session?.close(CloseReason(CloseReason.Codes.NORMAL, "Bootstrap completed"))
                                }
                            }
                        }
                    }

                    "event" -> {
                        val eventName = element["event"]?.jsonPrimitive?.contentOrNull ?: return
                        _connectionState.value = ConnectionState.Connected
                        scope.launch {
                            when (eventName) {
                                "wakeword_detected" -> {
                                    _events.emit(EngineEvent.WakewordDetected)
                                }

                                "stop_word_detected" -> {
                                    _events.emit(EngineEvent.StopWordDetected)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            VogonLogger.e("Error parsing message", e)
        }
    }

    override fun disconnect() {
        VogonLogger.i("Disconnecting from Babelfish...")
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = ConnectionState.Disconnected
        _vadState.value = VadState.Idle
        _engineMode.value = EngineMode.Wakeword
        session = null
    }

    override suspend fun saveConfig(config: Babelfish) {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val configJson = json.encodeToString(Babelfish.serializer(), config)
        val message = """{"type":"update_config","data":$configJson}"""
        currentSession.send(message)
        logMessage(MessageDirection.Sent, message)
    }

    private suspend fun <T> requestResponse(
        requestType: String,
        responseType: String,
        payload: String = "{}",
        timeoutMs: Long = 5000,
        parser: (JsonObject) -> T,
    ): T {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val deferred = CompletableDeferred<JsonObject>()
        responseHandlers[responseType] = deferred

        try {
            val request = if (payload == "{}") "{\"type\":\"$requestType\"}" else payload
            currentSession.send(request)
            logMessage(MessageDirection.Sent, request)

            return withTimeout(timeoutMs) {
                val response = deferred.await()
                parser(response)
            }
        } finally {
            responseHandlers.remove(responseType)
        }
    }

    override suspend fun listMicrophones(): List<Microphone> =
        requestResponse("list_microphones", "microphones_list") { response ->
            response["data"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                Microphone(
                    index = obj["index"]?.jsonPrimitive?.int ?: 0,
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                    isDefault = obj["is_default"]?.jsonPrimitive?.boolean ?: false,
                )
            } ?: emptyList()
        }

    override suspend fun listHardware(): List<HardwareDevice> =
        requestResponse("list_hardware", "hardware_list") { response ->
            response["data"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                HardwareDevice(
                    id = obj["id"]?.jsonPrimitive?.content ?: "unknown",
                    name = obj["name"]?.jsonPrimitive?.content ?: "Unknown Device",
                )
            } ?: emptyList()
        }

    override suspend fun listWakewords(): List<String> =
        requestResponse("list_wakewords", "wakewords_list") { response ->
            response["data"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }

    override suspend fun setMicTest(enabled: Boolean) {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val message = """{"type":"set_mic_test","enabled":$enabled}"""
        currentSession.send(message)
        logMessage(MessageDirection.Sent, message)
    }

    override suspend fun forceListen() {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val message = """{"type":"force_listen"}"""
        currentSession.send(message)
        logMessage(MessageDirection.Sent, message)
    }

    override suspend fun toggleListening() {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val message = """{"type":"toggle_listening"}"""
        currentSession.send(message)
        logMessage(MessageDirection.Sent, message)
    }

    override fun notifyBootstrap() {
        // No longer strictly needed as we watch BackendController.serverStatus
    }

    private fun logMessage(
        direction: MessageDirection,
        content: String,
    ) {
        val now = System.currentTimeMillis()
        val msg =
            ProtocolMessage(
                timestamp = now,
                direction = direction,
                content = content,
            )
        _messages.value = (_messages.value + msg).takeLast(100)
    }
}
