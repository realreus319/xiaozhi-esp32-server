package com.xiaozhi.server.core.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import java.util.Collections.emptyMap

// ---------- 配置类 ----------

data class ServerConfig(
    val authKey: String,
    val port: Int = 8000,
    val httpPort: Int = 8003,
    val timezoneOffset: Int = 8,
    val websocket: String = "",
    val mqttGateway: String = "",
    val mqttSignatureKey: String = "",
    val auth: AuthConfig = AuthConfig(),
    val visionExplain: String = ""
)

data class AuthConfig(
    val enabled: Boolean = false,
    val allowedDevices: Set<String> = emptySet(),
    val expireSeconds: Long? = null
)

data class AppConfig(
    val server: ServerConfig,
    val selectedModule: Map<String, String> = emptyMap(),
    val vllm: Map<String, Map<String, Any>> = emptyMap(),
    val readConfigFromApi: Boolean = false,
    val firmwareCacheTtl: Long = 30
)

// ---------- 基础Handler ----------

open class BaseHandler(val config: AppConfig) {
    val logger = LoggerFactory.getLogger(this::class.java)

    fun addCorsHeaders(responseHeaders: HttpHeaders) {
        responseHeaders.add("Access-Control-Allow-Headers", "client-id, content-type, device-id, authorization")
        responseHeaders.add("Access-Control-Allow-Credentials", "true")
        responseHeaders.add("Access-Control-Allow-Origin", "*")
        responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }
}

// ---------- VLLM接口和实现 ----------
interface VLLM {
    fun response(question: String, imageBase64: String): String
}

class OpenAIVLLM(private val config: Map<String, Any>) : VLLM {
    override fun response(question: String, imageBase64: String): String {
        // 这里应该实现实际的OpenAI API调用
        // 为简化，我们返回一个模拟的结果
        return "模拟的视觉分析结果：$question"
    }
}

class ChatGLMVLLM(private val config: Map<String, Any>) : VLLM {
    override fun response(question: String, imageBase64: String): String {
        // 这里应该实现实际的ChatGLM API调用
        // 为简化，我们返回一个模拟的结果
        return "模拟的视觉分析结果：$question"
    }
}

fun createVllmInstance(vllmType: String, config: Map<String, Any>): VLLM {
    return when (vllmType.lowercase()) {
        "openai" -> OpenAIVLLM(config)
        "chatglm" -> ChatGLMVLLM(config)
        else -> OpenAIVLLM(config) // 默认使用OpenAI实现
    }
}



