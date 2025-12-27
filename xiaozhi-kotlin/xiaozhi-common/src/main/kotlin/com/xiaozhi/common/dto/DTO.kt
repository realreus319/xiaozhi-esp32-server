package com.xiaozhi.common.dto

data class TTSMessageDTO(
    val sentenceId: String,
    val sentenceType: SentenceType,
    val contentType: ContentType,
    val contentDetail: String? = null
)

enum class SentenceType {
    FIRST,
    MIDDLE,
    LAST
}

enum class ContentType {
    ACTION,
    TEXT
}

data class DialogueMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ToolCall(
    val id: String? = null,
    val function: FunctionDef,
    val index: Int? = null
)

data class FunctionDef(
    val name: String,
    val arguments: String,
    val description: String? = null
)

data class LLMFunctionCall(
    val id: String = "",
    val function: FunctionDef
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
