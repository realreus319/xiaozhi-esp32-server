package com.xiaozhi.server.core.dialogue

import com.xiaozhi.common.dto.DialogueMessage
import com.xiaozhi.server.core.providers.Message as ProviderMessage

data class DialogueState(
    val sessionId: String,
    val deviceId: String,
    val clientId: String? = null,
    val lastActivityTime: Long = System.currentTimeMillis(),
    val isSpeaking: Boolean = false,
    val isListening: Boolean = true,
    val listenMode: String = "auto"
)

data class DialogueContext(
    val sessionId: String,
    val dialogue: List<DialogueMessage> = emptyList(),
    val memoryContext: String? = null,
    val speakerInfo: Map<String, String> = emptyMap()
)

class DialogueManager {
    private val dialogues: MutableMap<String, MutableList<DialogueMessage>> = mutableMapOf()

    fun getDialogue(sessionId: String): List<DialogueMessage> {
        return dialogues[sessionId]?.toList() ?: emptyList()
    }

    fun addMessage(sessionId: String, message: DialogueMessage) {
        dialogues.getOrPut(sessionId) { mutableListOf() }.add(message)
    }

    fun addUserMessage(sessionId: String, text: String) {
        addMessage(sessionId, DialogueMessage(role = "user", content = text))
    }

    fun addAssistantMessage(sessionId: String, text: String) {
        addMessage(sessionId, DialogueMessage(role = "assistant", content = text))
    }

    fun addSystemMessage(sessionId: String, prompt: String) {
        addMessage(sessionId, DialogueMessage(role = "system", content = prompt))
    }

    fun clearDialogue(sessionId: String) {
        dialogues.remove(sessionId)
    }

    fun getDialogueWithMemory(sessionId: String, memoryStr: String?): List<ProviderMessage> {
        val dialogue = getDialogue(sessionId)
        val result = mutableListOf<ProviderMessage>()
        
        // 添加系统消息（如果有）
        if (dialogue.isNotEmpty() && dialogue[0].role == "system") {
            result.add(ProviderMessage(
                role = "system",
                content = dialogue[0].content
            ))
        }
        
        // 添加记忆上下文
        if (!memoryStr.isNullOrBlank()) {
            result.add(ProviderMessage(
                role = "system",
                content = "[记忆上下文]\n$memoryStr"
            ))
        }
        
        // 添加对话历史
        for (msg in dialogue) {
            if (msg.role != "system") {
                result.add(ProviderMessage(
                    role = msg.role,
                    content = msg.content,
                    toolCalls = msg.toolCalls,
                    toolCallId = msg.toolCallId
                ))
            }
        }
        
        return result
    }
    
    fun addProviderMessage(sessionId: String, message: ProviderMessage) {
        addMessage(sessionId, DialogueMessage(
            role = message.role,
            content = message.content,
            toolCalls = message.toolCalls,
            toolCallId = message.toolCallId
        ))
    }

    fun getContext(sessionId: String, speakerConfig: Map<String, Any>? = null): DialogueContext {
        val dialogue = getDialogue(sessionId)
        val memory = "" // TODO: 从 memory provider 获取
        
        val speakerInfo = mutableMapOf<String, String>()
        speakerConfig?.get("speakers") as? List<Map<String, Any>>?.forEach { speaker ->
            val name = speaker["name"] as? String ?: ""
            val description = speaker["description"] as? String ?: ""
            if (name.isNotBlank()) {
                speakerInfo[name] = description
            }
        }

        return DialogueContext(
            sessionId = sessionId,
            dialogue = dialogue,
            memoryContext = memory,
            speakerInfo = speakerInfo
        )
    }
}
