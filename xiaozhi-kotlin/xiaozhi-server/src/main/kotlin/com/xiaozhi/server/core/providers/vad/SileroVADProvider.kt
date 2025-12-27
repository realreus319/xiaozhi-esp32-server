package com.xiaozhi.server.core.providers.vad

import com.xiaozhi.server.connection.ConnectionHandler
import com.xiaozhi.server.core.providers.VADProvider
import com.xiaozhi.server.core.providers.VoiceActivityResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.io.File

class SileroVADProvider : VADProvider {
    private val logger = LoggerFactory.getLogger(SileroVADProvider::class.java)
    
    private var modelPath: String = "models/snakers4_silero-vad"
    private var threshold: Float = 0.5f
    private var minSilenceDurationMs: Int = 200
    
    private var isInitialized = false

    override suspend fun initialize() {
        // TODO: 加载 Silero VAD 模型
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            logger.warn("Silero VAD model not found: $modelPath")
        }
        
        isInitialized = true
        logger.info("Silero VAD provider initialized with threshold: $threshold")
    }

    override suspend fun isVad(conn: ConnectionHandler, audioData: ByteArray): Boolean {
        val result = detect(audioData)
        return result.hasVoice
    }

    override suspend fun close() {
        isInitialized = false
        logger.info("Silero VAD provider closed")
    }

    override suspend fun detect(audioData: ByteArray): VoiceActivityResult {
        if (!isInitialized) {
            return VoiceActivityResult(
                hasVoice = true,
                confidence = 1.0f,
                timestamp = System.currentTimeMillis()
            )
        }

        // TODO: 使用 Silero VAD 模型进行检测
        val confidence = 0.5f
        val hasVoice = confidence > threshold
        
        return VoiceActivityResult(
            hasVoice = hasVoice,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )
    }

    override fun detectStream(audioFlow: Flow<ByteArray>): Flow<VoiceActivityResult> {
        return flow {
            audioFlow.collect { audioData ->
                emit(detect(audioData))
            }
        }
    }

    companion object {
        const val TYPE = "silero"
    }
}
