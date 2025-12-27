package com.xiaozhi.server.core.providers.tts

import com.xiaozhi.server.core.providers.TTSProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

class DefaultTTSProvider : TTSProvider {

    override suspend fun initialize() {
        println("Default TTS provider initialized")
    }

    override suspend fun openAudioChannels() {
        println("Audio channels opened")
    }

    override suspend fun close() {
        println("Default TTS provider closed")
    }

    override suspend fun synthesize(text: String): ByteArray? {
        return null
    }

    override fun synthesizeStream(textFlow: Flow<String>): Flow<ByteArray?> {
        return flow {
            textFlow.collect { text ->
                emit(generateFakeAudio(text))
                delay(100)
            }
        }
    }

    private fun generateFakeAudio(text: String): ByteArray {
        return ByteArray(text.length * 100).also { Random().nextBytes(it) }
    }
}
