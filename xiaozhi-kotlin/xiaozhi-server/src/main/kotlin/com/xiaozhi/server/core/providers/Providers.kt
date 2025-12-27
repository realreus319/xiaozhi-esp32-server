package com.xiaozhi.server.core.providers

import com.xiaozhi.common.dto.ToolCall
import com.xiaozhi.server.connection.ConnectionHandler
import kotlinx.coroutines.flow.Flow

interface ASRProvider {
    suspend fun initialize()
    suspend fun openAudioChannels()
    suspend fun close()
    suspend fun transcribe(audioData: ByteArray): String?
    suspend fun receiveAudio(conn: ConnectionHandler, audioData: ByteArray, hasVoice: Boolean)
    fun transcribeStream(audioFlow: Flow<ByteArray>): Flow<String?>
}

interface LLMProvider {
    suspend fun initialize()
    suspend fun close()
    suspend fun generate(sessionId: String, messages: List<Message>): Flow<String>
    suspend fun generateWithFunctions(
        sessionId: String,
        messages: List<Message>,
        functions: List<FunctionDef>
    ): Flow<LLMResponse>
}

interface TTSProvider {
    suspend fun initialize()
    suspend fun openAudioChannels()
    suspend fun close()
    suspend fun synthesize(text: String): ByteArray?
    suspend fun abort()
    fun synthesizeStream(textFlow: Flow<String>): Flow<ByteArray?>
}

interface VADProvider {
    suspend fun initialize()
    suspend fun close()
    suspend fun detect(audioData: ByteArray): VoiceActivityResult
    suspend fun isVad(conn: Any, audioData: ByteArray): Boolean
    fun detectStream(audioFlow: Flow<ByteArray>): Flow<VoiceActivityResult>
}

interface MemoryProvider {
    suspend fun initMemory(roleId: String, summaryConfig: Map<String, Any>? = null)
    suspend fun saveMemory(dialogue: List<Message>, sessionId: String)
    suspend fun queryMemory(query: String): String?
    suspend fun setLLM(llm: LLMProvider)
    suspend fun close()
}

interface IntentProvider {
    suspend fun initialize()
    suspend fun close()
    suspend fun setLLM(llm: LLMProvider)
    suspend fun detectIntent(text: String): IntentResult?
    fun getFunctions(): List<FunctionDef>
}

data class Message(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
)


data class FunctionDef(
    val name: String,
    val arguments: String,
    val description: String? = null
)

data class LLMResponse(
    val content: String?,
    val toolCalls: List<ToolCall>?
)

data class VoiceActivityResult(
    val hasVoice: Boolean,
    val confidence: Float,
    val timestamp: Long
)

data class IntentResult(
    val name: String,
    val confidence: Float,
    val parameters: Map<String, Any> = emptyMap()
)

enum class InterfaceType {
    LOCAL,
    REMOTE
}

// LLM 响应类型（用于流式响应）
sealed class StreamingResponse
data class TextResponse(val text: String) : StreamingResponse()
data class ToolCallResponse(val toolCall: ToolCall) : StreamingResponse()
data class CombinedResponse(val text: String, val toolCall: ToolCall?) : StreamingResponse()

