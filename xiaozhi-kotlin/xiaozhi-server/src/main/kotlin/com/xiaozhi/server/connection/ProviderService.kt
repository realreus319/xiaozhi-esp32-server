package com.xiaozhi.server.connection

import com.xiaozhi.server.core.providers.ASRProvider
import com.xiaozhi.server.core.providers.IntentProvider
import com.xiaozhi.server.core.providers.LLMProvider
import com.xiaozhi.server.core.providers.MemoryProvider
import com.xiaozhi.server.providers.*
import com.xiaozhi.server.core.providers.ProviderFactory
import com.xiaozhi.server.core.providers.TTSProvider
import com.xiaozhi.server.core.providers.VADProvider
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.collections.emptyMap

@Service
class ProviderService {
    private val logger = LoggerFactory.getLogger(ProviderService::class.java)
    
    private val config by lazy {
        loadConfig()
    }

    fun loadConfig(): com.xiaozhi.common.XiaoZhiConfig {
        val loader = com.xiaozhi.server.config.XiaoZhiConfigProperties()
        return loader.loadConfig()
    }

    fun createASRProvider(): ASRProvider {
        val selectedType = config.selectedModule.ASR
        val asrConfig= config.ASR[selectedType] ?: emptyMap()
        return ProviderFactory.createASRProvider(selectedType, asrConfig).also {
            runBlocking { it.initialize() }
        }
    }

    fun createLLMProvider(): LLMProvider {
        val selectedType = config.selectedModule.LLM
        val llmConfig = config.LLM[selectedType] ?: emptyMap()
        return ProviderFactory.createLLMProvider(selectedType, llmConfig).also {
            runBlocking { it.initialize() }
        }
    }

    fun createTTSProvider(): TTSProvider {
        val selectedType = config.selectedModule.TTS
        val ttsConfig = config.TTS[selectedType] ?: emptyMap()
        return ProviderFactory.createTTSProvider(selectedType, ttsConfig).also {
            runBlocking { it.initialize() }
        }
    }

    fun createVADProvider(): VADProvider {
        val selectedType = config.selectedModule.VAD
        val vadConfig = config.VAD[selectedType] ?: emptyMap()
        return ProviderFactory.createVADProvider(selectedType, vadConfig).also {
            runBlocking { it.initialize() }
        }
    }

    fun createMemoryProvider(): MemoryProvider {
        val selectedType = config.selectedModule.Memory
        val memoryConfig = config.Memory[selectedType] ?: emptyMap()
        return ProviderFactory.createMemoryProvider(selectedType, memoryConfig).also {
            runBlocking { it.initialize() }
        }
    }

    fun createIntentProvider(): IntentProvider {
        val selectedType = config.selectedModule.Intent
        val intentConfig = config.Intent[selectedType] ?: emptyMap()
        return ProviderFactory.createIntentProvider(selectedType, intentConfig).also {
            runBlocking { it.initialize() }
        }
    }

    fun getAllProviders(): Map<String, Any?> {
        return mapOf(
            "asr" to createASRProvider(),
            "llm" to createLLMProvider(),
            "tts" to createTTSProvider(),
            "vad" to createVADProvider(),
            "memory" to createMemoryProvider(),
            "intent" to createIntentProvider()
        )
    }
}
