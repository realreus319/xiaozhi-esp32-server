package com.xiaozhi.server.core.providers.llm

import com.xiaozhi.server.core.providers.LLMProvider
import com.xiaozhi.server.core.providers.LLMResponse
import com.xiaozhi.server.core.providers.Message
import com.xiaozhi.server.providers.FunctionCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.text.iterator

class DefaultLLMProvider : LLMProvider {

    override suspend fun initialize() {
        println("Default LLM provider initialized")
    }

    override suspend fun close() {
        println("Default LLM provider closed")
    }

    override suspend fun generate(sessionId: String, messages: List<Message>): Flow<String> {
        return flow {
            val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
            val response = "你好，我是小智。你说：$lastUserMessage"

            for (char in response) {
                emit(char.toString())
                delay(50)
            }
        }
    }

    override suspend fun generateWithFunctions(
        sessionId: String,
        messages: List<Message>,
        functions: List<FunctionCall>
    ): Flow<LLMResponse> {
        return flow {
            val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
            emit(LLMResponse(
                content = "你好，我是小智。你说：$lastUserMessage",
                toolCalls = null
            ))
        }
    }
}
