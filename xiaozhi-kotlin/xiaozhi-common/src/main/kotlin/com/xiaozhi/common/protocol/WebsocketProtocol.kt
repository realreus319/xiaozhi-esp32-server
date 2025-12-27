package com.xiaozhi.common.protocol

data class ServerHelloMessage(
    val type: String = "hello",
    val version: Int = 1,
    val transport: String = "websocket",
    val audioParams: AudioParams = AudioParams(),
    val sessionId: String? = null
)

data class AudioParams(
    val format: String = "opus",
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val frameDuration: Int = 60
)

data class ServerResponse(
    val type: String,
    val status: String,
    val message: String? = null,
    val content: Map<String, Any>? = null
)

data class ErrorMessage(
    val type: String = "error",
    val error: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TTSAudioMessage(
    val type: String = "tts_audio",
    val sessionId: String,
    val sentenceId: String,
    val audioData: String, // Base64 encoded
    val sampleRate: Int = 16000,
    val channels: Int = 1
)

data class TextMessage(
    val type: String = "text",
    val sessionId: String,
    val text: String,
    val role: String = "assistant"
)

data class StateMessage(
    val type: String = "state",
    val sessionId: String,
    val state: String, // "listening", "processing", "speaking"
    val timestamp: Long = System.currentTimeMillis()
)

data class ClientTextMessage(
    val type: String = "text",
    val text: String
)

data class ClientConfigMessage(
    val type: String = "config",
    val features: Map<String, Boolean> = emptyMap()
)
