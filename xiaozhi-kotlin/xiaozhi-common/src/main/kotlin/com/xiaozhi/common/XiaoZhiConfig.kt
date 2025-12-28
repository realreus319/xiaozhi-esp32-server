package com.xiaozhi.common

data class XiaoZhiConfig(
    val server: ServerConfig = ServerConfig(),
    val log: LogConfig = LogConfig(),
    val xiaozhi: XiaoZhiProtocol = XiaoZhiProtocol(),
    val selectedModule: SelectedModule = SelectedModule(),
    val prompt: String = "",
    val exitCommands: List<String> = listOf("退出", "关闭"),
    val wakeupWords: List<String> = listOf("你好小智", "嘿你好呀"),
    val plugins: PluginsConfig = PluginsConfig(),
    val ASR: Map<String, Map<String, Any>> = emptyMap(),
    val LLM: Map<String, Map<String, Any>> = emptyMap(),
    val VLLM: Map<String, Map<String, Any>> = emptyMap(),
    val TTS: Map<String, Map<String, Any>> = emptyMap(),
    val VAD: Map<String, Map<String, Any>> = emptyMap(),
    val Memory: Map<String, Map<String, Any>> = emptyMap(),
    val Intent: Map<String, Map<String, Any>> = emptyMap(),
    val voiceprint: VoiceprintConfig = VoiceprintConfig(),
    val moduleTest: ModuleTestConfig = ModuleTestConfig(),
    val contextProviders: List<ContextProvider> = emptyList(),
    val mcpEndpoint: String = "",
    val endPrompt: EndPromptConfig = EndPromptConfig(),
    val deleteAudio: Boolean = true,
    val closeConnectionNoVoiceTime: Int = 120,
    val ttsTimeout: Int = 10,
    val enableWakeupWordsResponseCache: Boolean = true,
    val enableGreeting: Boolean = true,
    val enableStopTtsNotify: Boolean = false,
    val stopTtsNotifyVoice: String = "config/assets/tts_notify.mp3",
    val enableWebsocketPing: Boolean = false,
    val ttsAudioSendDelay: Int = 0,
    val promptTemplate: String = "agent-base-prompt.txt",
    val readConfigFromApi: Boolean = false
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
    val mqttSignatureKey: String? = null,
    val udpGateway: String? = null
)

data class AuthConfig(
    val enabled: Boolean = false,
    val allowedDevices: List<String> = emptyList(),
    val expireSeconds: Long? = null
)

data class LogConfig(
    val logFormat: String = "<green>{time:YYMMDD HH:mm:ss}</green>[{version}_{selected_module}][<light-blue>{extra[tag]}</light-blue>]-<level>{level}</level>-<light-green>{message}</light-green>",
    val logFormatFile: String = "{time:YYYY-MM-DD HH:mm:ss} - {version}_{selected_module} - {name} - {level} - {extra[tag]} - {message}",
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

// 插件配置类
data class PluginsConfig(
    val getWeather: WeatherPluginConfig = WeatherPluginConfig(),
    val getNewsFromChinanews: NewsChinanewsPluginConfig = NewsChinanewsPluginConfig(),
    val getNewsFromNewsnow: NewsNewsnowPluginConfig = NewsNewsnowPluginConfig(),
    val homeAssistant: HomeAssistantPluginConfig = HomeAssistantPluginConfig(),
    val playMusic: PlayMusicPluginConfig = PlayMusicPluginConfig(),
    val searchFromRagflow: RagflowPluginConfig = RagflowPluginConfig()
)

// 天气插件配置
data class WeatherPluginConfig(
    val apiHost: String = "mj7p3y7naa.re.qweatherapi.com",
    val apiKey: String = "a861d0d5e7bf4ee1a83d9a9e4f96d4da",
    val defaultLocation: String = "广州"
)

// 新闻插件配置（中国新闻网）
data class NewsChinanewsPluginConfig(
    val defaultRssUrl: String = "https://www.chinanews.com.cn/rss/society.xml",
    val societyRssUrl: String = "https://www.chinanews.com.cn/rss/society.xml",
    val worldRssUrl: String = "https://www.chinanews.com.cn/rss/world.xml",
    val financeRssUrl: String = "https://www.chinanews.com.cn/rss/finance.xml"
)

// 新闻插件配置（NewsNow）
data class NewsNewsnowPluginConfig(
    val url: String = "https://newsnow.busiyi.world/api/s?id=",
    val newsSources: String = "澎湃新闻;百度热搜;财联社"
)

// Home Assistant插件配置
data class HomeAssistantPluginConfig(
    val devices: List<String> = emptyList(),
    val baseUrl: String = "http://homeassistant.local:8123",
    val apiKey: String = ""
)

// 音乐播放插件配置
data class PlayMusicPluginConfig(
    val musicDir: String = "./music",
    val musicExt: List<String> = listOf(".mp3", ".wav", ".p3"),
    val refreshTime: Int = 300
)

// Ragflow插件配置
data class RagflowPluginConfig(
    val description: String = "当用户问xxx时，调用本方法，使用知识库中的信息回答问题",
    val baseUrl: String = "http://192.168.0.8",
    val apiKey: String = "ragflow-xxx",
    val datasetIds: List<String> = listOf("123456789")
)

// 模块测试配置
data class ModuleTestConfig(
    val testSentences: List<String> = listOf(
        "你好，请介绍一下你自己",
        "What's the weather like today?",
        "请用100字概括量子计算的基本原理和应用前景"
    )
)

// 上下文提供者配置
data class ContextProvider(
    val url: String = "",
    val headers: Map<String, String> = emptyMap()
)

// 结束语配置
data class EndPromptConfig(
    val enable: Boolean = true,
    val prompt: String = "请你以\"时间过得真快\"未来头，用富有感情、依依不舍的话来结束这场对话吧！"
)