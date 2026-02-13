package ovh.devcraft.vogonpoet.infrastructure.mapper

import ovh.devcraft.vogonpoet.domain.model.VogonConfig
import ovh.devcraft.vogonpoet.infrastructure.model.Babelfish

fun Babelfish.toDomain(): VogonConfig =
    VogonConfig(
        hardware =
            hardware?.let { h ->
                VogonConfig.Hardware(
                    device = h.device,
                    autoDetect = h.auto_detect,
                    microphoneName = h.microphone_name,
                    onnxModelDir = h.onnx_model_dir,
                    onnxExecutionProvider = h.onnx_execution_provider,
                    quantization = h.quantization,
                    activeDevice = h.active_device,
                    activeDeviceName = h.active_device_name,
                    vramTotalGb = h.vram_total_gb,
                    vramUsedBaselineGb = h.vram_used_baseline_gb,
                    vramUsedModelGb = h.vram_used_model_gb,
                )
            } ?: VogonConfig.Hardware(),
        pipeline =
            pipeline?.let { p ->
                VogonConfig.Pipeline(
                    silenceThresholdMs = p.silence_threshold_ms,
                    updateIntervalMs = p.update_interval_ms,
                    performance =
                        p.performance?.let { perf ->
                            VogonConfig.Performance(
                                ghostThrottleMs = perf.ghost_throttle_ms,
                                ghostWindowS = perf.ghost_window_s,
                                minPaddingS = perf.min_padding_s,
                                tier = perf.tier,
                            )
                        } ?: VogonConfig.Performance(),
                )
            } ?: VogonConfig.Pipeline(),
        systemInput =
            system_input?.let { si ->
                VogonConfig.SystemInput(
                    enabled = si.enabled,
                    typeGhost = si.type_ghost,
                    strategy = VogonConfig.InputStrategy.entries.find { it.value == si.strategy } ?: VogonConfig.InputStrategy.CLIPBOARD,
                )
            } ?: VogonConfig.SystemInput(),
        voice =
            voice?.let { v ->
                VogonConfig.Voice(
                    wakeword = v.wakeword,
                    stopWakeword = v.stop_wakeword,
                    wakewordSensitivity = v.wakeword_sensitivity,
                    stopWakewordSensitivity = v.stop_wakeword_sensitivity,
                    stopWords = v.stop_words,
                )
            } ?: VogonConfig.Voice(),
        ui =
            ui?.let { u ->
                VogonConfig.Ui(
                    verbose = u.verbose,
                    showTimestamps = u.show_timestamps,
                    notifications = u.notifications,
                    shortcuts =
                        u.shortcuts?.let { s ->
                            VogonConfig.Shortcuts(
                                toggleListening = s.toggle_listening,
                                forceListen = s.force_listen,
                            )
                        } ?: VogonConfig.Shortcuts(),
                    activationDetection =
                        u.activation_detection?.let { ad ->
                            VogonConfig.ActivationDetection(
                                iconOnly = ad.icon_only,
                                overlayMode = ad.overlay_mode,
                            )
                        } ?: VogonConfig.ActivationDetection(),
                    transcriptionWindow =
                        u.transcription_window?.let { tw ->
                            VogonConfig.TranscriptionWindow(
                                alwaysOnTop = tw.always_on_top,
                            )
                        } ?: VogonConfig.TranscriptionWindow(),
                )
            } ?: VogonConfig.Ui(),
        server =
            server?.let { s ->
                VogonConfig.Server(
                    host = s.host,
                    port = s.port,
                )
            } ?: VogonConfig.Server(),
        cache =
            cache?.let { c ->
                VogonConfig.Cache(
                    cacheDir = c.cache_dir,
                )
            } ?: VogonConfig.Cache(),
    )

fun VogonConfig.toInfrastructure(): Babelfish =
    Babelfish(
        hardware =
            hardware.let { h ->
                Babelfish.Hardware(
                    device = h.device,
                    auto_detect = h.autoDetect,
                    microphone_name = h.microphoneName,
                    onnx_model_dir = h.onnxModelDir,
                    onnx_execution_provider = h.onnxExecutionProvider,
                    quantization = h.quantization,
                    active_device = h.activeDevice,
                    active_device_name = h.activeDeviceName,
                    vram_total_gb = h.vramTotalGb,
                    vram_used_baseline_gb = h.vramUsedBaselineGb,
                    vram_used_model_gb = h.vramUsedModelGb,
                )
            },
        pipeline =
            pipeline.let { p ->
                Babelfish.Pipeline(
                    silence_threshold_ms = p.silenceThresholdMs,
                    update_interval_ms = p.updateIntervalMs,
                    performance =
                        p.performance.let { perf ->
                            Babelfish.Performance(
                                ghost_throttle_ms = perf.ghostThrottleMs,
                                ghost_window_s = perf.ghostWindowS,
                                min_padding_s = perf.minPaddingS,
                                tier = perf.tier,
                            )
                        },
                )
            },
        system_input =
            systemInput.let { si ->
                Babelfish.System_input(
                    enabled = si.enabled,
                    type_ghost = si.typeGhost,
                    strategy = si.strategy.value,
                )
            },
        voice =
            voice.let { v ->
                Babelfish.Voice(
                    wakeword = v.wakeword,
                    stop_wakeword = v.stopWakeword,
                    wakeword_sensitivity = v.wakewordSensitivity,
                    stop_wakeword_sensitivity = v.stopWakewordSensitivity,
                    stop_words = v.stopWords,
                )
            },
        ui =
            ui.let { u ->
                Babelfish.Ui(
                    verbose = u.verbose,
                    show_timestamps = u.showTimestamps,
                    notifications = u.notifications,
                    shortcuts =
                        u.shortcuts.let { s ->
                            Babelfish.Shortcuts(
                                toggle_listening = s.toggleListening,
                                force_listen = s.forceListen,
                            )
                        },
                    activation_detection =
                        u.activationDetection.let { ad ->
                            Babelfish.Activation_detection(
                                icon_only = ad.iconOnly,
                                overlay_mode = ad.overlayMode,
                            )
                        },
                    transcription_window =
                        u.transcriptionWindow.let { tw ->
                            Babelfish.Transcription_window(
                                always_on_top = tw.alwaysOnTop,
                            )
                        },
                )
            },
        server =
            server.let { s ->
                Babelfish.Server(
                    host = s.host,
                    port = s.port,
                )
            },
        cache =
            cache.let { c ->
                Babelfish.Cache(
                    cache_dir = c.cacheDir,
                )
            },
    )
