package com.xiaozhi.server.core.providers.asr

import com.xiaozhi.server.connection.ConnectionHandler
import com.xiaozhi.server.core.providers.ASRProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DefaultASRProvider : ASRProvider {

    override suspend fun initialize() {
        println("Default ASR provider initialized")
    }

    override suspend fun openAudioChannels() {
        println("Audio channels opened")
    }

    override suspend fun close() {
        println("Default ASR provider closed")
    }

    override suspend fun transcribe(audioData: ByteArray): String? {
        return null
    }

    override suspend fun receiveAudio(
        conn: ConnectionHandler,
        audioData: ByteArray,
        hasVoice: Boolean
    ) {
        TODO("Not yet implemented")
    }


    override fun transcribeStream(audioFlow: Flow<ByteArray>): Flow<String?> {
        return flowOf()
    }
}
