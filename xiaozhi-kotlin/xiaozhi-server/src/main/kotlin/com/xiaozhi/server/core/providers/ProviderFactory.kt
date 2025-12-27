package com.xiaozhi.server.core.providers

import aQute.bnd.annotation.headers.Category
import com.xiaozhi.common.XiaoZhiConfig
import com.xiaozhi.server.core.providers.asr.DoubaoASRProvider
import com.xiaozhi.server.core.providers.asr.FunASRProvider
import com.xiaozhi.server.core.providers.llm.DoubaoLLMProvider
import com.xiaozhi.server.core.providers.llm.OpenAILLMProvider
import com.xiaozhi.server.core.providers.tts.DoubaoTTSProvider
import com.xiaozhi.server.core.providers.tts.EdgeTTSProvider
import com.xiaozhi.server.core.providers.vad.SileroVADProvider
import com.xiaozhi.server.providers.vad.WebRTCVADProvider
import com.xiaozhi.server.core.providers.memory.DefaultMemoryProvider
import com.xiaozhi.server.core.providers.memory.SimpleMemoryProvider
import com.xiaozhi.server.core.providers.intent.DefaultIntentProvider
import com.xiaozhi.server.core.providers.intent.FunctionCallIntentProvider
import jdk.internal.net.http.common.Log
import org.slf4j.LoggerFactory

class ProviderFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(ProviderFactory::class.java)
        
        fun createASRProvider(type: String, config: XiaoZhiConfig): ASRProvider {
            return when (type) {
                "doubao" -> DoubaoASRProvider().apply {
                    configureASR(config, type)
                }
                "fun_local" -> FunASRProvider().apply {
                    configureASR(config, type)
                }
                else -> throw IllegalArgumentException("Unsupported ASR provider: $type")
            }
        }
        
        fun createLLMProvider(config: XiaoZhiConfig, type: String): LLMProvider {
            return when (type) {
                "doubao" -> DoubaoLLMProvider().apply {
                    configureLLM(config, type)
                }
                "openai" -> OpenAILLMProvider().apply {
                    configureLLM(config, type)
                }
                "chatglm" -> OpenAILLMProvider().apply {
                    apiUrl = "https://open.bigmodel.cn/api/paas/v4/",
                    model = "glm-4-flash",
                    apiKey = config.llm?.openai?.apiKey ?: ""
                    logger.info("Using ChatGLM with model: glm-4-flash")
                }
                else -> throw IllegalArgumentException("Unsupported LLM provider: $type")
            }
        }
        
        fun createTTSProvider(config: XiaoZhiConfig, type: String): TTSProvider {
            return when (type) {
                "edge" -> EdgeTTSProvider().apply {
                    configureTTS(config, type)
                }
                "doubao" -> DoubaoTTSProvider().apply {
                    configureTTS(config, type)
                }
                else -> throw IllegalArgumentException("Unsupported TTS provider: $type")
            }
        }
        
        fun createVADProvider(config: XiaoZhiConfig, type: String): VADProvider {
            return when (type) {
                "silero" -> SileroVADProvider().apply {
                    configureVAD(config, type)
                }
                "webrtc" -> WebRTCVADProvider().apply {
                    configureVAD(config, type)
                }
                else -> throw IllegalArgumentException("Unsupported VAD provider: $type")
            }
        }
        
        fun createMemoryProvider(config: XiaoZhiConfig, type: String): MemoryProvider {
            return when (type) {
                "nomem" -> DefaultMemoryProvider()
                "mem_local_short" -> SimpleMemoryProvider()
                else -> DefaultMemoryProvider()
            }
        }
        
        fun createIntentProvider(config: XiaoZhiConfig, type: String): IntentProvider {
            return when (type) {
                "nointent" -> DefaultIntentProvider()
                "function_call" -> FunctionCallIntentProvider()
                "intent_llm" -> FunctionCallIntentProvider()
                else -> DefaultIntentProvider()
            }
        }
    }
}

fun DoubaoASRProvider.configureASR(config: XiaoZhiConfig, type: String) {
    val asrConfig = config.asr?.get(type) ?: throw IllegalArgumentException("ASR config not found for: $type")
    
    appId = asrConfig["appid"] as String? ?: ""
    accessToken = asrConfig["access_token"] as? String ?: ""
    cluster = asrConfig["cluster"] as? String ?: ""
    uid = asrConfig["uid"] as? String ?: "streaming_asr_service"
    workflow = asrConfig["workflow"] as? String ?: "audio_in,resample,partition,vad,fe,decode,itn,nlu_punctuate"
    resultType = asrConfig["result_type"] as? String ?: "single"
    format = asrConfig["format"] as? String ?: "pcm"
    codec = asrConfig["codec"] as? String ?: "pcm"
    rate = (asrConfig["sample_rate"] as? Number ?: 16000).toInt()
    Category.language = asrConfig["language"] as? String ?: "zh-CN"
    bits = (asrConfig["bits"] as? Number ?: 16).toInt()
    Log.channel = (asrConfig["channel"] as? Number ?: 1).toInt()
    authMethod = asrConfig["auth_method"] as? String ?: "token"
    outputDir = asrConfig["output_dir"] as? String ?: "tmp"
    
    logger.info("DoubaoASR configured: appid=$appId, cluster=$cluster, uid=$uid")
}

fun FunASRProvider.configureASR(config: XiaoZhiConfig, type: String) {
    val asrConfig = config.asr?.get(type) ?: throw IllegalArgumentException("ASR config not found for: $type")
    
    val modelPath = asrConfig["model_path"] as? String ?: "models/SenseVoiceSmall"
    
    logger.info("FunASR configured: model_path=$modelPath")
}

fun DoubaoLLMProvider.configureLLM(config: XiaoZhiConfig, type: String) {
    val llmConfig = config.llm?.get(type) ?: throw IllegalArgumentException("LLM config not found for: $type")
    
    apiUrl = llmConfig["api_url"] as? String ?: "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
    model = llmConfig["model"] as? String ?: "doubao-1.5-pro-32k-250115"
    apiKey = llmConfig["api_key"] as? String ?: ""
    
    maxTokens = (llmConfig["max_tokens"] as? Number ?: 4096).toInt()
    temperature = (llmConfig["temperature"] as? Number ?: 0.7).toDouble()
    topP = (llmConfig["top_p"] as? Number ?: 0.9).toDouble()
    frequencyPenalty = (llmConfig["frequency_penalty"] as? Number ?: 0.0).toDouble()
    timeout = (llmConfig["timeout"] as? Number ?: 300).toInt()
    
    logger.info("DoubaoLLM configured: model=$model, api=$apiUrl")
}

fun OpenAILLMProvider.configureLLM(config: XiaoZhiConfig, type: String) {
    val llmConfig = config.llm?.get(type) ?: throw IllegalArgumentException("LLM config not found for: $type")
    
    apiUrl = llmConfig["api_url"] as? String ?: "https://api.openai.com/v1"
    model = llmConfig["model"] as? String ?: "gpt-4o-mini"
    apiKey = llmConfig["api_key"] as? String ?: ""
    
    maxTokens = (llmConfig["max_tokens"] as? Number ?: 4096).toInt()
    temperature = (llmConfig["temperature"] as? Number ?: 0.7).toDouble()
    topP = (llmConfig["top_p"] as? Number ?: 1.0).toDouble()
    frequencyPenalty = (llmConfig["frequency_penalty"] as? Number ?: 0.0).toDouble()
    timeout = (llmConfig["timeout"] as? Number ?: 300).toInt()
    
    logger.info("OpenAILLM configured: model=$model, api=$apiUrl")
}

fun EdgeTTSProvider.configureTTS(config: XiaoZhiConfig, type: String) {
    val ttsConfig = config.tts?.get(type) ?: throw IllegalArgumentException("TTS config not found for: $type")
    
    voice = ttsConfig["voice"] as? String ?: "zh-CN-XiaoxiaoNeural"
    rate = (ttsConfig["rate"] as? Number ?: 1).toInt()
    pitch = (ttsConfig["pitch"] as? Number ?: 1).toInt()
    volume = (ttsConfig["volume"] as? Number ?: 50).toInt()
    outputDir = ttsConfig["output_dir"] as? String ?: "tmp"
    
    logger.info("EdgeTTS configured: voice=$voice, rate=$rate")
}

fun DoubaoTTSProvider.configureTTS(config: XiaoZhiConfig, type: String) {
    val ttsConfig = config.tts?.get(type) ?: throw IllegalArgumentException("TTS config not found for: $type")
    
    apiUrl = ttsConfig["api_url"] as? String ?: "https://openspeech.bytedance.com/api/v1/tts"
    appId = ttsConfig["appid"] as? String ?: ""
    accessToken = ttsConfig["access_token"] as? String ?: ""
    voice = ttsConfig["voice"] as? String ?: "zh_female_wanwanxiaohe_moon_bigtts"
    cluster = ttsConfig["cluster"] as? String ?: "volcano_tts"
    speedRatio = (ttsConfig["speed_ratio"] as? Number ?: 1.0).toDouble()
    volumeRatio = (ttsConfig["volume_ratio"] as? Number ?: 1.0).toDouble()
    pitchRatio = (ttsConfig["pitch_ratio"] as? Number ?: 1.0).toDouble()
    outputDir = ttsConfig["output_dir"] as? String ?: "tmp"
    
    logger.info("DoubaoTTS configured: voice=$voice, api=$apiUrl")
}

fun SileroVADProvider.configureVAD(config: XiaoZhiConfig, type: String) {
    val vadConfig = config.vad?.get(type) ?: throw IllegalArgumentException("VAD config not found for: $type")
    
    threshold = (vadConfig["threshold"] as? Number ?: 0.5f).toFloat()
    windowSizeMs = (vadConfig["window_size_ms"] as? Number ?: 60).toInt()
    speechPadMs = (vadConfig["speech_pad_ms"] as? Number ?: 30).toInt()
    modelPath = vadConfig["model_path"] as? String ?: "models/snakers4_silero-vad"
    
    logger.info("SileroVAD configured: threshold=$threshold, window=$windowSizeMs")
}

fun WebRTCVADProvider.configureVAD(config: XiaoZhiConfig, type: String) {
    val vadConfig = config.vad?.get(type) ?: throw IllegalArgumentException("VAD config not found for: $type")
    
    aggressiveness = (vadConfig["aggressiveness"] as? Number ?: 2).toInt()
    sampleRate = (vadConfig["sample_rate"] as? Number ?: 16000).toInt()
    frameDurationMs = (vadConfig["frame_duration_ms"] as? Number ?: 30).toInt()
    
    logger.info("WebRTC VAD configured: aggressiveness=$aggressiveness")
}
