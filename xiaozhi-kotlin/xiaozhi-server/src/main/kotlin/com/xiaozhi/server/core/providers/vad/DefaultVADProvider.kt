package com.xiaozhi.server.core.providers.vad

import com.xiaozhi.server.core.providers.VADProvider
import com.xiaozhi.server.providers.VoiceActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

class DefaultVADProvider : VADProvider {

    override suspend fun initialize() {
        println("Default VAD provider initialized")
    }

    override suspend fun close() {
        println("Default VAD provider closed")
    }

    override suspend fun detect(audioData: ByteArray): Boolean {
        return true
    }

    override fun detectStream(audioFlow: Flow<ByteArray>): Flow<VoiceActivity> {
        return flow {
            audioFlow.collect { audioData ->
                emit(VoiceActivity(
                    hasVoice = true,
                    confidence = 1.0f,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }
}
