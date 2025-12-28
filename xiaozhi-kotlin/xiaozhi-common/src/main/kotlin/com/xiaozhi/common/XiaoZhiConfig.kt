package com.xiaozhi.common

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo


// ASR配置密封接口
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = FunAsrConfig::class, name = "fun_local"),
    JsonSubTypes.Type(value = FunAsrServerConfig::class, name = "fun_server"),
    JsonSubTypes.Type(value = SherpaAsrConfig::class, name = "sherpa_onnx_local"),
    JsonSubTypes.Type(value = SherpaParaformerAsrConfig::class, name = "sherpa_onnx_local"),
    JsonSubTypes.Type(value = DoubaoAsrConfig::class, name = "doubao"),
    JsonSubTypes.Type(value = DoubaoStreamAsrConfig::class, name = "doubao_stream"),
    JsonSubTypes.Type(value = TencentAsrConfig::class, name = "tencent"),
    JsonSubTypes.Type(value = AliyunAsrConfig::class, name = "aliyun"),
    JsonSubTypes.Type(value = AliyunStreamAsrConfig::class, name = "aliyun_stream"),
    JsonSubTypes.Type(value = BaiduAsrConfig::class, name = "baidu"),
    JsonSubTypes.Type(value = OpenaiAsrConfig::class, name = "openai"),
    JsonSubTypes.Type(value = GroqAsrConfig::class, name = "openai"),
    JsonSubTypes.Type(value = VoskAsrConfig::class, name = "vosk"),
    JsonSubTypes.Type(value = Qwen3AsrFlashConfig::class, name = "qwen3_asr_flash"),
    JsonSubTypes.Type(value = XunfeiStreamAsrConfig::class, name = "xunfei_stream")
)
sealed interface AsrConfig

// LLM配置密封接口
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = AliLlmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = AliAppLlmConfig::class, name = "AliBL"),
    JsonSubTypes.Type(value = DoubaoLlmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = DeepSeekLlmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = ChatGlmLlmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = OllamaLlmConfig::class, name = "ollama"),
    JsonSubTypes.Type(value = DifyLlmConfig::class, name = "dify"),
    JsonSubTypes.Type(value = GeminiLlmConfig::class, name = "gemini"),
    JsonSubTypes.Type(value = CozeLlmConfig::class, name = "coze"),
    JsonSubTypes.Type(value = VolcesAiGatewayLlmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = LmStudioLlmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = HomeAssistantLlmConfig::class, name = "homeassistant"),
    JsonSubTypes.Type(value = FastgptLlmConfig::class, name = "fastgpt"),
    JsonSubTypes.Type(value = XinferenceLlmConfig::class, name = "xinference"),
    JsonSubTypes.Type(value = XinferenceSmallLlmConfig::class, name = "xinference")
)
sealed interface LlmConfig

// VLLM配置密封接口
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatGlmVllmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = QwenVlVllmConfig::class, name = "openai"),
    JsonSubTypes.Type(value = XunfeiSparkLlmConfig::class, name = "openai")
)
sealed interface VllmConfig

// TTS配置密封接口
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = EdgeTtsConfig::class, name = "edge"),
    JsonSubTypes.Type(value = DoubaoTtsConfig::class, name = "doubao"),
    JsonSubTypes.Type(value = HuoshanDoubleStreamTtsConfig::class, name = "huoshan_double_stream"),
    JsonSubTypes.Type(value = CosyVoiceSiliconflowConfig::class, name = "siliconflow"),
    JsonSubTypes.Type(value = CozeCnTtsConfig::class, name = "cozecn"),
    JsonSubTypes.Type(value = VolcesAiGatewayTtsConfig::class, name = "openai"),
    JsonSubTypes.Type(value = FishSpeechConfig::class, name = "fishspeech"),
    JsonSubTypes.Type(value = GptSovitsV2Config::class, name = "gpt_sovits_v2"),
    JsonSubTypes.Type(value = GptSovitsV3Config::class, name = "gpt_sovits_v3"),
    JsonSubTypes.Type(value = MinimaxTtsHttpStreamConfig::class, name = "minimax_httpstream"),
    JsonSubTypes.Type(value = AliyunTtsConfig::class, name = "aliyun"),
    JsonSubTypes.Type(value = AliyunStreamTtsConfig::class, name = "aliyun_stream"),
    JsonSubTypes.Type(value = TencentTtsConfig::class, name = "tencent"),
    JsonSubTypes.Type(value = Tts302AiConfig::class, name = "doubao"),
    JsonSubTypes.Type(value = GizwitsTtsConfig::class, name = "doubao"),
    JsonSubTypes.Type(value = AcgnTtsConfig::class, name = "ttson"),
    JsonSubTypes.Type(value = OpenAiTtsConfig::class, name = "openai"),
    JsonSubTypes.Type(value = CustomTtsConfig::class, name = "custom"),
    JsonSubTypes.Type(value = LinkeraiTtsConfig::class, name = "linkerai"),
    JsonSubTypes.Type(value = PaddleSpeechTtsConfig::class, name = "paddle_speech"),
    JsonSubTypes.Type(value = IndexStreamTtsConfig::class, name = "index_stream"),
    JsonSubTypes.Type(value = AliBlTtsConfig::class, name = "alibl_stream"),
    JsonSubTypes.Type(value = XunFeiTtsConfig::class, name = "xunfei_stream")
)
sealed interface TtsConfig

// VAD配置密封接口
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SileroVadConfig::class, name = "silero")
)
sealed interface VadConfig

// Memory配置密封接口
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Mem0AiConfig::class, name = "mem0ai"),
    JsonSubTypes.Type(value = NoMemConfig::class, name = "nomem"),
    JsonSubTypes.Type(value = MemLocalShortConfig::class, name = "mem_local_short")
)
sealed interface MemoryConfig

// Intent配置密封接口
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = NoIntentConfig::class, name = "nointent"),
    JsonSubTypes.Type(value = IntentLlmConfig::class, name = "intent_llm"),
    JsonSubTypes.Type(value = FunctionCallConfig::class, name = "function_call")
)
sealed interface IntentConfig

data class XiaoZhiConfig(
    val server: ServerConfig = ServerConfig(),
    val log: LogConfig = LogConfig(),
    val xiaozhi: XiaoZhiProtocol = XiaoZhiProtocol(),
    val selectedModule: SelectedModule = SelectedModule(),
    val prompt: String = "",
    val exitCommands: List<String> = listOf("退出", "关闭"),
    val wakeupWords: List<String> = listOf("你好小智", "嘿你好呀"),
    val plugins: PluginsConfig = PluginsConfig(),
    val ASR: Map<String, AsrConfig> = emptyMap(),
    val LLM: Map<String, LlmConfig> = emptyMap(),
    val VLLM: Map<String, VllmConfig> = emptyMap(),
    val TTS: Map<String, TtsConfig> = emptyMap(),
    val VAD: Map<String, VadConfig> = emptyMap(),
    val Memory: Map<String, MemoryConfig> = emptyMap(),
    val Intent: Map<String, IntentConfig> = emptyMap(),
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

// ASR配置实现类
data class FunAsrConfig(
    val type: String = "fun_local",
    @JsonProperty("model_dir") val modelDir: String = "models/SenseVoiceSmall",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class FunAsrServerConfig(
    val type: String = "fun_server",
    val host: String = "127.0.0.1",
    val port: Int = 10096,
    @JsonProperty("is_ssl") val isSsl: Boolean = true,
    @JsonProperty("api_key") val apiKey: String = "none",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class SherpaAsrConfig(
    val type: String = "sherpa_onnx_local",
    @JsonProperty("model_dir") val modelDir: String = "models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("model_type") val modelType: String = "sense_voice"
) : AsrConfig

data class SherpaParaformerAsrConfig(
    val type: String = "sherpa_onnx_local",
    @JsonProperty("model_dir") val modelDir: String = "models/sherpa-onnx-paraformer-zh-small-2024-03-09",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("model_type") val modelType: String = "paraformer"
) : AsrConfig

data class DoubaoAsrConfig(
    val type: String = "doubao",
    val appid: String = "",
    @JsonProperty("access_token") val accessToken: String = "",
    val cluster: String = "volcengine_input_common",
    @JsonProperty("boosting_table_name") val boostingTableName: String? = null,
    @JsonProperty("correct_table_name") val correctTableName: String? = null,
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class DoubaoStreamAsrConfig(
    val type: String = "doubao_stream",
    val appid: String = "",
    @JsonProperty("access_token") val accessToken: String = "",
    val cluster: String = "volcengine_input_common",
    @JsonProperty("boosting_table_name") val boostingTableName: String? = null,
    @JsonProperty("correct_table_name") val correctTableName: String? = null,
    @JsonProperty("end_window_size") val endWindowSize: Int = 200,
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class TencentAsrConfig(
    val type: String = "tencent",
    val appid: String = "",
    @JsonProperty("secret_id") val secretId: String = "",
    @JsonProperty("secret_key") val secretKey: String = "",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class AliyunAsrConfig(
    val type: String = "aliyun",
    val appkey: String = "",
    val token: String = "",
    @JsonProperty("access_key_id") val accessKeyId: String = "",
    @JsonProperty("access_key_secret") val accessKeySecret: String = "",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class AliyunStreamAsrConfig(
    val type: String = "aliyun_stream",
    val appkey: String = "",
    val token: String = "",
    @JsonProperty("access_key_id") val accessKeyId: String = "",
    @JsonProperty("access_key_secret") val accessKeySecret: String = "",
    val host: String = "nls-gateway-cn-shanghai.aliyuncs.com",
    @JsonProperty("max_sentence_silence") val maxSentenceSilence: Int = 800,
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class BaiduAsrConfig(
    val type: String = "baidu",
    @JsonProperty("app_id") val appId: String = "",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("secret_key") val secretKey: String = "",
    @JsonProperty("dev_pid") val devPid: Int = 1537,
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class OpenaiAsrConfig(
    val type: String = "openai",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("base_url") val baseUrl: String = "https://api.openai.com/v1/audio/transcriptions",
    @JsonProperty("model_name") val modelName: String = "gpt-4o-mini-transcribe",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class GroqAsrConfig(
    val type: String = "openai",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("base_url") val baseUrl: String = "https://api.groq.com/openai/v1/audio/transcriptions",
    @JsonProperty("model_name") val modelName: String = "whisper-large-v3-turbo",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class VoskAsrConfig(
    val type: String = "vosk",
    @JsonProperty("model_path") val modelPath: String = "",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

data class Qwen3AsrFlashConfig(
    val type: String = "qwen3_asr_flash",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("base_url") val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    @JsonProperty("model_name") val modelName: String = "qwen3-asr-flash",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("enable_lid") val enableLid: Boolean = true,
    @JsonProperty("enable_itn") val enableItn: Boolean = true,
    val context: String = ""
) : AsrConfig

data class XunfeiStreamAsrConfig(
    val type: String = "xunfei_stream",
    @JsonProperty("app_id") val appId: String = "",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("api_secret") val apiSecret: String = "",
    val domain: String = "slm",
    val language: String = "zh_cn",
    val accent: String = "mandarin",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : AsrConfig

// LLM配置实现类
data class AliLlmConfig(
    val type: String = "openai",
    @JsonProperty("base_url") val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    @JsonProperty("model_name") val modelName: String = "qwen-turbo",
    @JsonProperty("api_key") val apiKey: String = "",
    val temperature: Double = 0.7,
    @JsonProperty("max_tokens") val maxTokens: Int = 500,
    @JsonProperty("top_p") val topP: Double = 1.0,
    @JsonProperty("frequency_penalty") val frequencyPenalty: Double = 0.0
) : LlmConfig

data class AliAppLlmConfig(
    val type: String = "AliBL",
    @JsonProperty("base_url") val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    @JsonProperty("app_id") val appId: String = "",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("is_no_prompt") val isNoPrompt: Boolean = true,
    @JsonProperty("ali_memory_id") val aliMemoryId: String? = null
) : LlmConfig

data class DoubaoLlmConfig(
    val type: String = "openai",
    @JsonProperty("base_url") val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3",
    @JsonProperty("model_name") val modelName: String = "doubao-1-5-pro-32k-250115",
    @JsonProperty("api_key") val apiKey: String = ""
) : LlmConfig

data class DeepSeekLlmConfig(
    val type: String = "openai",
    @JsonProperty("model_name") val modelName: String = "deepseek-chat",
    val url: String = "https://api.deepseek.com",
    @JsonProperty("api_key") val apiKey: String = ""
) : LlmConfig

data class ChatGlmLlmConfig(
    val type: String = "openai",
    @JsonProperty("model_name") val modelName: String = "glm-4-flash",
    val url: String = "https://open.bigmodel.cn/api/paas/v4/",
    @JsonProperty("api_key") val apiKey: String = ""
) : LlmConfig

data class OllamaLlmConfig(
    val type: String = "ollama",
    @JsonProperty("model_name") val modelName: String = "qwen2.5",
    @JsonProperty("base_url") val baseUrl: String = "http://localhost:11434"
) : LlmConfig

data class DifyLlmConfig(
    val type: String = "dify",
    @JsonProperty("base_url") val baseUrl: String = "https://api.dify.ai/v1",
    @JsonProperty("api_key") val apiKey: String = "",
    val mode: String = "chat-messages"
) : LlmConfig

data class GeminiLlmConfig(
    val type: String = "gemini",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("model_name") val modelName: String = "gemini-2.0-flash",
    @JsonProperty("http_proxy") val httpProxy: String = "",
    @JsonProperty("https_proxy") val httpsProxy: String = ""
) : LlmConfig

data class CozeLlmConfig(
    val type: String = "coze",
    @JsonProperty("bot_id") val botId: String = "",
    @JsonProperty("user_id") val userId: String = "",
    @JsonProperty("personal_access_token") val personalAccessToken: String = ""
) : LlmConfig

data class VolcesAiGatewayLlmConfig(
    val type: String = "openai",
    @JsonProperty("base_url") val baseUrl: String = "https://ai-gateway.vei.volces.com/v1",
    @JsonProperty("model_name") val modelName: String = "doubao-pro-32k-functioncall",
    @JsonProperty("api_key") val apiKey: String = ""
) : LlmConfig

data class LmStudioLlmConfig(
    val type: String = "openai",
    @JsonProperty("model_name") val modelName: String = "deepseek-r1-distill-llama-8b@q4_k_m",
    val url: String = "http://localhost:1234/v1",
    @JsonProperty("api_key") val apiKey: String = "lm-studio"
) : LlmConfig

data class HomeAssistantLlmConfig(
    val type: String = "homeassistant",
    @JsonProperty("base_url") val baseUrl: String = "http://homeassistant.local:8123",
    @JsonProperty("agent_id") val agentId: String = "conversation.chatgpt",
    @JsonProperty("api_key") val apiKey: String = ""
) : LlmConfig

data class FastgptLlmConfig(
    val type: String = "fastgpt",
    @JsonProperty("base_url") val baseUrl: String = "https://host/api/v1",
    @JsonProperty("api_key") val apiKey: String = "",
    val variables: Map<String, String> = mapOf("k" to "v", "k2" to "v2")
) : LlmConfig

data class XinferenceLlmConfig(
    val type: String = "xinference",
    @JsonProperty("model_name") val modelName: String = "qwen2.5:72b-AWQ",
    @JsonProperty("base_url") val baseUrl: String = "http://localhost:9997"
) : LlmConfig

data class XinferenceSmallLlmConfig(
    val type: String = "xinference",
    @JsonProperty("model_name") val modelName: String = "qwen2.5:3b-AWQ",
    @JsonProperty("base_url") val baseUrl: String = "http://localhost:9997"
) : LlmConfig

// VLLM配置实现类
data class ChatGlmVllmConfig(
    val type: String = "openai",
    @JsonProperty("model_name") val modelName: String = "glm-4v-flash",
    val url: String = "https://open.bigmodel.cn/api/paas/v4/",
    @JsonProperty("api_key") val apiKey: String = ""
) : VllmConfig

data class QwenVlVllmConfig(
    val type: String = "openai",
    @JsonProperty("model_name") val modelName: String = "qwen2.5-vl-3b-instruct",
    @JsonProperty("base_url") val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    @JsonProperty("api_key") val apiKey: String = ""
) : VllmConfig

data class XunfeiSparkLlmConfig(
    val type: String = "openai",
    @JsonProperty("base_url") val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3",
    @JsonProperty("model_name") val modelName: String = "lite",
    @JsonProperty("api_key") val apiKey: String = ""
) : VllmConfig

// TTS配置实现类
data class EdgeTtsConfig(
    val type: String = "edge",
    val voice: String = "zh-CN-XiaoxiaoNeural",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

data class DoubaoTtsConfig(
    val type: String = "doubao",
    @JsonProperty("api_url") val apiUrl: String = "https://openspeech.bytedance.com/api/v1/tts",
    val voice: String = "BV001_streaming",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    val authorization: String = "Bearer;",
    val appid: String = "",
    @JsonProperty("access_token") val accessToken: String = "",
    val cluster: String = "volcano_tts",
    @JsonProperty("speed_ratio") val speedRatio: Double = 1.0,
    @JsonProperty("volume_ratio") val volumeRatio: Double = 1.0,
    @JsonProperty("pitch_ratio") val pitchRatio: Double = 1.0
) : TtsConfig

data class HuoshanDoubleStreamTtsConfig(
    val type: String = "huoshan_double_stream",
    @JsonProperty("ws_url") val wsUrl: String = "wss://openspeech.bytedance.com/api/v3/tts/bidirection",
    val appid: String = "",
    @JsonProperty("access_token") val accessToken: String = "",
    @JsonProperty("resource_id") val resourceId: String = "volc.service_type.10029",
    val speaker: String = "zh_female_wanwanxiaohe_moon_bigtts",
    @JsonProperty("enable_ws_reuse") val enableWsReuse: Boolean = true,
    @JsonProperty("speech_rate") val speechRate: Int = 0,
    @JsonProperty("loudness_rate") val loudnessRate: Int = 0,
    val pitch: Int = 0
) : TtsConfig

data class CosyVoiceSiliconflowConfig(
    val type: String = "siliconflow",
    val model: String = "FunAudioLLM/CosyVoice2-0.5B",
    val voice: String = "FunAudioLLM/CosyVoice2-0.5B:alex",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("access_token") val accessToken: String = "",
    @JsonProperty("response_format") val responseFormat: String = "wav"
) : TtsConfig

data class CozeCnTtsConfig(
    val type: String = "cozecn",
    val voice: String = "7426720361733046281",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("access_token") val accessToken: String = "",
    @JsonProperty("response_format") val responseFormat: String = "wav"
) : TtsConfig

data class VolcesAiGatewayTtsConfig(
    val type: String = "openai",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("api_url") val apiUrl: String = "https://ai-gateway.vei.volces.com/v1/audio/speech",
    val model: String = "doubao-tts",
    val voice: String = "zh_male_shaonianzixin_moon_bigtts",
    val speed: Int = 1,
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

data class FishSpeechConfig(
    val type: String = "fishspeech",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("response_format") val responseFormat: String = "wav",
    @JsonProperty("reference_id") val referenceId: String? = null,
    @JsonProperty("reference_audio") val referenceAudio: List<String> = listOf("config/assets/wakeup_words.wav"),
    @JsonProperty("reference_text") val referenceText: List<String> = listOf("哈啰啊，我是小智啦，声音好听的台湾女孩一枚，超开心认识你耶，最近在忙啥，别忘了给我来点有趣的料哦，我超爱听八卦的啦"),
    val normalize: Boolean = true,
    @JsonProperty("max_new_tokens") val maxNewTokens: Int = 1024,
    @JsonProperty("chunk_length") val chunkLength: Int = 200,
    @JsonProperty("top_p") val topP: Double = 0.7,
    @JsonProperty("repetition_penalty") val repetitionPenalty: Double = 1.2,
    val temperature: Double = 0.7,
    val streaming: Boolean = false,
    @JsonProperty("use_memory_cache") val useMemoryCache: String = "on",
    val seed: Int? = null,
    val channels: Int = 1,
    val rate: Int = 44100,
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("api_url") val apiUrl: String = "http://127.0.0.1:8080/v1/tts"
) : TtsConfig

data class GptSovitsV2Config(
    val type: String = "gpt_sovits_v2",
    val url: String = "http://127.0.0.1:9880/tts",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("text_lang") val textLang: String = "auto",
    @JsonProperty("ref_audio_path") val refAudioPath: String = "demo.wav",
    @JsonProperty("prompt_text") val promptText: String = "",
    @JsonProperty("prompt_lang") val promptLang: String = "zh",
    @JsonProperty("top_k") val topK: Int = 5,
    @JsonProperty("top_p") val topP: Double = 1.0,
    @JsonProperty("temperature") val temperature: Double = 1.0,
    @JsonProperty("text_split_method") val textSplitMethod: String = "cut0",
    @JsonProperty("batch_size") val batchSize: Int = 1,
    @JsonProperty("batch_threshold") val batchThreshold: Double = 0.75,
    @JsonProperty("split_bucket") val splitBucket: Boolean = true,
    @JsonProperty("return_fragment") val returnFragment: Boolean = false,
    @JsonProperty("speed_factor") val speedFactor: Double = 1.0,
    @JsonProperty("streaming_mode") val streamingMode: Boolean = false,
    val seed: Int = -1,
    @JsonProperty("parallel_infer") val parallelInfer: Boolean = true,
    @JsonProperty("repetition_penalty") val repetitionPenalty: Double = 1.35,
    @JsonProperty("aux_ref_audio_paths") val auxRefAudioPaths: List<String> = emptyList()
) : TtsConfig

data class GptSovitsV3Config(
    val type: String = "gpt_sovits_v3",
    val url: String = "http://127.0.0.1:9880",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("text_language") val textLanguage: String = "auto",
    @JsonProperty("refer_wav_path") val referWavPath: String = "caixukun.wav",
    @JsonProperty("prompt_language") val promptLanguage: String = "zh",
    @JsonProperty("prompt_text") val promptText: String = "",
    @JsonProperty("top_k") val topK: Int = 15,
    @JsonProperty("top_p") val topP: Double = 1.0,
    @JsonProperty("temperature") val temperature: Double = 1.0,
    @JsonProperty("cut_punc") val cutPunc: String = "",
    val speed: Double = 1.0,
    @JsonProperty("inp_refs") val inpRefs: List<String> = emptyList(),
    @JsonProperty("sample_steps") val sampleSteps: Int = 32,
    @JsonProperty("if_sr") val ifSr: Boolean = false
) : TtsConfig

data class MinimaxTtsHttpStreamConfig(
    val type: String = "minimax_httpstream",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("group_id") val groupId: String = "",
    @JsonProperty("api_key") val apiKey: String = "",
    val model: String = "speech-01-turbo",
    @JsonProperty("voice_id") val voiceId: String = "female-shaonv"
) : TtsConfig

data class AliyunTtsConfig(
    val type: String = "aliyun",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    val appkey: String = "",
    val token: String = "",
    val voice: String = "xiaoyun",
    @JsonProperty("access_key_id") val accessKeyId: String = "",
    @JsonProperty("access_key_secret") val accessKeySecret: String = ""
) : TtsConfig

data class AliyunStreamTtsConfig(
    val type: String = "aliyun_stream",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    val appkey: String = "",
    val token: String = "",
    val voice: String = "longxiaochun",
    @JsonProperty("access_key_id") val accessKeyId: String = "",
    @JsonProperty("access_key_secret") val accessKeySecret: String = "",
    val host: String = "nls-gateway-cn-beijing.aliyuncs.com"
) : TtsConfig

data class TencentTtsConfig(
    val type: String = "tencent",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    val appid: String = "",
    @JsonProperty("secret_id") val secretId: String = "",
    @JsonProperty("secret_key") val secretKey: String = "",
    val region: String = "ap-guangzhou",
    val voice: String = "101001"
) : TtsConfig

data class Tts302AiConfig(
    val type: String = "doubao",
    @JsonProperty("api_url") val apiUrl: String = "https://api.302ai.cn/doubao/tts_hd",
    val authorization: String = "Bearer ",
    val voice: String = "zh_female_wanwanxiaohe_moon_bigtts",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("access_token") val accessToken: String = ""
) : TtsConfig

data class GizwitsTtsConfig(
    val type: String = "doubao",
    @JsonProperty("api_url") val apiUrl: String = "https://bytedance.gizwitsapi.com/api/v1/tts",
    val authorization: String = "Bearer ",
    val voice: String = "zh_female_wanwanxiaohe_moon_bigtts",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    @JsonProperty("access_token") val accessToken: String = ""
) : TtsConfig

data class AcgnTtsConfig(
    val type: String = "ttson",
    val token: String = "",
    @JsonProperty("voice_id") val voiceId: String = "1695",
    @JsonProperty("speed_factor") val speedFactor: Double = 1.0,
    @JsonProperty("pitch_factor") val pitchFactor: Double = 0.0,
    @JsonProperty("volume_change_dB") val volumeChangeDb: Double = 0.0,
    @JsonProperty("to_lang") val toLang: String = "ZH",
    val url: String = "https://u95167-bd74-2aef8085.westx.seetacloud.com:8443/flashsummary/tts?token=",
    val format: String = "mp3",
    @JsonProperty("output_dir") val outputDir: String = "tmp/",
    val emotion: Int = 1
) : TtsConfig

data class OpenAiTtsConfig(
    val type: String = "openai",
    @JsonProperty("api_key") val apiKey: String = "",
    @JsonProperty("api_url") val apiUrl: String = "https://api.openai.com/v1/audio/speech",
    val model: String = "tts-1",
    val voice: String = "onyx",
    val speed: Double = 1.0,
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

data class CustomTtsConfig(
    val type: String = "custom",
    val method: String = "POST",
    val url: String = "http://127.0.0.1:8880/v1/audio/speech",
    val params: Map<String, String> = mapOf(
        "input" to "{prompt_text}",
        "response_format" to "mp3",
        "download_format" to "mp3",
        "voice" to "zf_xiaoxiao",
        "lang_code" to "z",
        "return_download_link" to "true",
        "speed" to "1",
        "stream" to "false"
    ),
    val headers: Map<String, String> = emptyMap(),
    val format: String = "mp3",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

data class LinkeraiTtsConfig(
    val type: String = "linkerai",
    @JsonProperty("api_url") val apiUrl: String = "https://tts.linkerai.cn/tts",
    @JsonProperty("audio_format") val audioFormat: String = "pcm",
    @JsonProperty("access_token") val accessToken: String = "U4YdYXVfpwWnk2t5Gp822zWPCuORyeJL",
    val voice: String = "OUeAo1mhq6IBExi",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

data class PaddleSpeechTtsConfig(
    val type: String = "paddle_speech",
    val protocol: String = "websocket",
    val url: String = "ws://127.0.0.1:8092/paddlespeech/tts/streaming",
    @JsonProperty("spk_id") val spkId: Int = 0,
    @JsonProperty("sample_rate") val sampleRate: Int = 24000,
    val speed: Double = 1.0,
    val volume: Double = 1.0,
    @JsonProperty("save_path") val savePath: String? = null
) : TtsConfig

data class IndexStreamTtsConfig(
    val type: String = "index_stream",
    @JsonProperty("api_url") val apiUrl: String = "http://127.0.0.1:11996/tts",
    @JsonProperty("audio_format") val audioFormat: String = "pcm",
    val voice: String = "jay_klee",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

data class AliBlTtsConfig(
    val type: String = "alibl_stream",
    @JsonProperty("api_key") val apiKey: String = "",
    val model: String = "cosyvoice-v2",
    val voice: String = "longcheng_v2",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

data class XunFeiTtsConfig(
    val type: String = "xunfei_stream",
    @JsonProperty("api_url") val apiUrl: String = "wss://cbm01.cn-huabei-1.xf-yun.com/v1/private/mcd9m97e6",
    @JsonProperty("app_id") val appId: String = "",
    @JsonProperty("api_secret") val apiSecret: String = "",
    @JsonProperty("api_key") val apiKey: String = "",
    val voice: String = "x5_lingxiaoxuan_flow",
    @JsonProperty("output_dir") val outputDir: String = "tmp/"
) : TtsConfig

// VAD配置实现类
data class SileroVadConfig(
    val type: String = "silero",
    val threshold: Double = 0.5,
    @JsonProperty("threshold_low") val thresholdLow: Double = 0.3,
    @JsonProperty("model_dir") val modelDir: String = "models/snakers4_silero-vad",
    @JsonProperty("min_silence_duration_ms") val minSilenceDurationMs: Int = 200
) : VadConfig

// Memory配置实现类
data class Mem0AiConfig(
    val type: String = "mem0ai",
    @JsonProperty("api_key") val apiKey: String = ""
) : MemoryConfig

data class NoMemConfig(
    val type: String = "nomem"
) : MemoryConfig

data class MemLocalShortConfig(
    val type: String = "mem_local_short",
    val llm: String = "ChatGLMLLM"
) : MemoryConfig

// Intent配置实现类
data class NoIntentConfig(
    val type: String = "nointent"
) : IntentConfig

data class IntentLlmConfig(
    val type: String = "intent_llm",
    val llm: String = "ChatGLMLLM",
    val functions: List<String> = emptyList()
) : IntentConfig

data class FunctionCallConfig(
    val type: String = "function_call",
    val functions: List<String> = emptyList()
) : IntentConfig

data class ServerConfig(
    val ip: String = "0.0.0.0",
    val port: Int = 8000,
    @JsonProperty("http_port") val httpPort: Int = 8003,
    val websocket: String = "",
    @JsonProperty("vision_explain") val visionExplain: String = "",
    @JsonProperty("timezone_offset") val timezoneOffset: String = "+8",
    val auth: AuthConfig = AuthConfig(),
    @JsonProperty("mqtt_gateway") val mqttGateway: String? = null,
    @JsonProperty("mqtt_signature_key") val mqttSignatureKey: String? = null,
    @JsonProperty("udp_gateway") val udpGateway: String? = null
)

data class AuthConfig(
    val enabled: Boolean = false,
    @JsonProperty("allowed_devices") val allowedDevices: List<String> = emptyList(),
    @JsonProperty("expire_seconds") val expireSeconds: Long? = null
)

data class LogConfig(
    @JsonProperty("log_format") val logFormat: String = "<green>{time:YYMMDD HH:mm:ss}</green>[{version}_{selected_module}][<light-blue>{extra[tag]}</light-blue>]-<level>{level}</level>-<light-green>{message}</light-green>",
    @JsonProperty("log_format_file") val logFormatFile: String = "{time:YYYY-MM-DD HH:mm:ss} - {version}_{selected_module} - {name} - {level} - {extra[tag]} - {message}",
    @JsonProperty("log_level") val logLevel: String = "INFO",
    @JsonProperty("log_dir") val logDir: String = "tmp",
    @JsonProperty("log_file") val logFile: String = "server.log",
    @JsonProperty("data_dir") val dataDir: String = "data"
)

data class XiaoZhiProtocol(
    val type: String = "hello",
    val version: Int = 1,
    val transport: String = "websocket",
    @JsonProperty("audio_params") val audioParams: AudioFormat = AudioFormat.OPUS_16K
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
    @JsonProperty("similarity_threshold") val similarityThreshold: Double = 0.4
)

data class Speaker(
    @JsonProperty("speaker_id") val speakerId: String = "",
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
    @JsonProperty("get_weather") val getWeather: WeatherPluginConfig = WeatherPluginConfig(),
    @JsonProperty("get_news_from_chinanews") val getNewsFromChinanews: NewsChinanewsPluginConfig = NewsChinanewsPluginConfig(),
    @JsonProperty("get_news_from_newsnow") val getNewsFromNewsnow: NewsNewsnowPluginConfig = NewsNewsnowPluginConfig(),
    @JsonProperty("home_assistant") val homeAssistant: HomeAssistantPluginConfig = HomeAssistantPluginConfig(),
    @JsonProperty("play_music") val playMusic: PlayMusicPluginConfig = PlayMusicPluginConfig(),
    @JsonProperty("search_from_ragflow") val searchFromRagflow: RagflowPluginConfig = RagflowPluginConfig()
)

// 天气插件配置
data class WeatherPluginConfig(
    @JsonProperty("api_host") val apiHost: String = "mj7p3y7naa.re.qweatherapi.com",
    @JsonProperty("api_key") val apiKey: String = "a861d0d5e7bf4ee1a83d9a9e4f96d4da",
    @JsonProperty("default_location") val defaultLocation: String = "广州"
)

// 新闻插件配置（中国新闻网）
data class NewsChinanewsPluginConfig(
    @JsonProperty("default_rss_url") val defaultRssUrl: String = "https://www.chinanews.com.cn/rss/society.xml",
    @JsonProperty("society_rss_url") val societyRssUrl: String = "https://www.chinanews.com.cn/rss/society.xml",
    @JsonProperty("world_rss_url") val worldRssUrl: String = "https://www.chinanews.com.cn/rss/world.xml",
    @JsonProperty("finance_rss_url") val financeRssUrl: String = "https://www.chinanews.com.cn/rss/finance.xml"
)

// 新闻插件配置（NewsNow）
data class NewsNewsnowPluginConfig(
    val url: String = "https://newsnow.busiyi.world/api/s?id=",
    @JsonProperty("news_sources") val newsSources: String = "澎湃新闻;百度热搜;财联社"
)

// Home Assistant插件配置
data class HomeAssistantPluginConfig(
    val devices: List<String> = emptyList(),
    @JsonProperty("base_url") val baseUrl: String = "http://homeassistant.local:8123",
    @JsonProperty("api_key") val apiKey: String = ""
)

// 音乐播放插件配置
data class PlayMusicPluginConfig(
    @JsonProperty("music_dir") val musicDir: String = "./music",
    @JsonProperty("music_ext") val musicExt: List<String> = listOf(".mp3", ".wav", ".p3"),
    @JsonProperty("refresh_time") val refreshTime: Int = 300
)

// Ragflow插件配置
data class RagflowPluginConfig(
    val description: String = "当用户问xxx时，调用本方法，使用知识库中的信息回答问题",
    @JsonProperty("base_url") val baseUrl: String = "http://192.168.0.8",
    @JsonProperty("api_key") val apiKey: String = "ragflow-xxx",
    @JsonProperty("dataset_ids") val datasetIds: List<String> = listOf("127.0.0.1")
)

// 模块测试配置
data class ModuleTestConfig(
    @JsonProperty("test_sentences") val testSentences: List<String> = listOf(
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