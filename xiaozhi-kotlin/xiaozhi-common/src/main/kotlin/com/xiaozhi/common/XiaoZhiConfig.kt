package com.xiaozhi.common

data class XiaoZhiConfig(
    val server: ServerConfig = ServerConfig(),
    val log: LogConfig = LogConfig(),
    val xiaozhi: XiaoZhiProtocol = XiaoZhiProtocol(),
    val selectedModule: SelectedModule = SelectedModule(),
    val prompt: String = "",
    val exitCommands: List<String> = listOf("退出", "关闭"),
    val wakeupWords: List<String> = listOf("你好小智", "嘿你好呀"),
    val plugins: Map<String, Any> = emptyMap(),
    val ASR: Map<String, Any> = emptyMap(),
    val LLM: Map<String, Any> = emptyMap(),
    val VLLM: Map<String, Any> = emptyMap(),
    val TTS: Map<String, Any> = emptyMap(),
    val VAD: Map<String, Any> = emptyMap(),
    val Memory: Map<String, Any> = emptyMap(),
    val Intent: Map<String, Any> = emptyMap(),
    val voiceprint: VoiceprintConfig = VoiceprintConfig()
)

data class ServerConfig(
    val ip: String = "0.0.0.0",
    val port: Int = 8000,
    val httpPort: Int = 8003,
    val websocket: String = "",
    val visionExplain: String = "",
    val timezoneOffset: String = "+8",
    val auth: AuthConfig = AuthConfig(),
    val mqttGateway: String? = null,
    val udpGateway: String? = null,
    val authKey: String = ""
)

data class AuthConfig(
    val enabled: Boolean = false,
    val allowedDevices: List<String> = emptyList(),
    val expireSeconds: Long? = null
)

data class LogConfig(
    val logFormat: String = "{time:YYYY-MM-DD HH:mm:ss} - {level} - {message}",
    val logLevel: String = "INFO",
    val logDir: String = "tmp",
    val logFile: String = "server.log",
    val dataDir: String = "data"
)

data class XiaoZhiProtocol(
    val type: String = "hello",
    val version: Int = 1,
    val transport: String = "websocket",
    val audioParams: AudioFormat = AudioFormat.OPUS_16K
)

data class SelectedModule(
    val VAD: String = "SileroVAD",
    val ASR: String = "FunASR",
    val LLM: String = "ChatGLMLLM",
    val VLLM: String = "ChatGLMVLLM",
    val TTS: String = "EdgeTTS",
    val Memory: String = "nomem",
    val Intent: String = "function_call"
)

data class VoiceprintConfig(
    val url: String = "",
    val speakers: List<Speaker> = emptyList(),
    val similarityThreshold: Double = 0.4
)

data class Speaker(
    val speakerId: String = "",
    val name: String = "",
    val description: String = ""
) {
    companion object {
        fun fromString(str: String): Speaker {
            val parts = str.split(",")
            return Speaker(
                speakerId = parts.getOrNull(0) ?: "",
                name = parts.getOrNull(1) ?: "",
                description = parts.getOrNull(2) ?: ""
            )
        }
    }
}
