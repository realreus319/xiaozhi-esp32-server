package com.xiaozhi.server.core.providers.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.xiaozhi.server.core.providers.FunctionDef
import com.xiaozhi.server.core.providers.LLMProvider
import com.xiaozhi.server.core.providers.LLMResponse
import com.xiaozhi.server.core.providers.Message
import com.xiaozhi.server.providers.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import java.util.UUID

class DoubaoLLMProvider : LLMProvider {
    private val logger = LoggerFactory.getLogger(DoubaoLLMProvider::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    private lateinit var webClient: WebClient
    private var apiUrl: String = ""
    private var model: String = ""
    private var apiKey: String = ""
    
    // 可选参数
    var maxTokens: Int? = null
    var temperature: Double? = null
    var topP: Double? = null
    var frequencyPenalty: Double? = null
    var timeout: Int = 300
    
    private var isInitialized = false

    override suspend fun initialize() {
        // TODO: 从配置加载参数
        apiUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
        model = "doubao-1.5-pro-32k-250115"
        apiKey = "your_api_key"
        maxTokens = 4096
        temperature = 0.7
        topP = 0.9
        
        webClient = WebClient.builder()
            .baseUrl(apiUrl.substringBeforeLast("/"))
            .codecs { configurer ->
                configurer.defaultCodecs { 
                    it.maxInMemorySize(16 * 1024 * 1024) // 16MB
                }
            }
            .build()
        
        isInitialized = true
        logger.info("Doubao LLM provider initialized: model=$model, api=$apiUrl")
    }

    override suspend fun close() {
        isInitialized = false
        logger.info("Doubao LLM provider closed")
    }

    override suspend fun generate(sessionId: String, messages: List<Message>): Flow<String> {
        if (!isInitialized) {
            throw Exception("LLM provider not initialized")
        }

        return flow {
            val requestParams = buildRequestParams(messages, functions = null)
            
            try {
                val response = webClient.post()
                    .uri(apiUrl.substringAfterLast("/"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers { headers ->
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                    }
                    .bodyValue(requestParams)
                    .retrieve()
                    .bodyToFlux(String::class.java)
                    .flatMap { chunk ->
                        parseStreamChunk(chunk)
                    }
                    .collect { text ->
                        if (text.isNotBlank()) {
                            emit(text)
                        }
                    }
            } catch (e: WebClientResponseException) {
                logger.error("Doubao LLM error: status=${e.statusCode}, body=${e.responseBodyAsString}", e)
                emit("抱歉，我暂时无法回复。")
            } catch (e: Exception) {
                logger.error("Doubao LLM error: ${e.message}", e)
                emit("抱歉，我暂时无法回复。")
            }
        }
    }

    override suspend fun generateWithFunctions(
        sessionId: String,
        messages: List<Message>,
        functions: List<FunctionDef>
    ): Flow<LLMResponse> {
        if (!isInitialized) {
            throw Exception("LLM provider not initialized")
        }

        return flow {
            val requestParams = buildRequestParams(messages, functions)
            
            try {
                webClient.post()
                    .uri(apiUrl.substringAfterLast("/"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers { headers ->
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                    }
                    .bodyValue(requestParams)
                    .retrieve()
                    .bodyToFlux(String::class.java)
                    .flatMap { chunk ->
                        parseFunctionStreamChunk(chunk)
                    }
                    .collect { response ->
                        emit(response)
                    }
            } catch (e: WebClientResponseException) {
                logger.error("Doubao LLM function call error: status=${e.statusCode}, body=${e.responseBodyAsString}", e)
                emit(LLMResponse("抱歉，我暂时无法回复。", null))
            } catch (e: Exception) {
                logger.error("Doubao LLM function call error: ${e.message}", e)
                emit(LLMResponse("抱歉，我暂时无法回复。", null))
            }
        }
    }

    private fun buildRequestParams(messages: List<Message>, functions: List<FunctionDef>?): Map<String, Any> {
        val normalizedMessages = messages.map { msg ->
            val map = mutableMapOf<String, Any>(
                "role" to msg.role,
                "content" to (msg.content ?: "")
            )
            
            // 添加 tool_calls
            if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                map["tool_calls"] = msg.toolCalls.map { toolCall ->
                    mapOf(
                        "id" to (toolCall.id ?: ""),
                        "type" to "function",
                        "function" to mapOf(
                            "name" to toolCall.function.name,
                            "arguments" to toolCall.function.arguments
                        )
                    )
                }
            }
            
            // 添加 tool_call_id
            if (msg.toolCallId != null) {
                map["tool_call_id"] = msg.toolCallId
            }
            
            map
        }
        
        val params = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to normalizedMessages,
            "stream" to true
        )
        
        // 添加可选参数
        maxTokens?.let { params["max_tokens"] = it }
        temperature?.let { params["temperature"] = it }
        topP?.let { params["top_p"] = it }
        frequencyPenalty?.let { params["frequency_penalty"] = it }
        
        // 添加函数定义
        if (functions != null && functions.isNotEmpty()) {
            params["tools"] = functions.map { func ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to func.name,
                        "description" to (func.description ?: ""),
                        "parameters" to parseParameters(func.arguments)
                    )
                )
            }
            params["tool_choice"] = "auto"
        }
        
        return params
    }

    private fun parseStreamChunk(chunk: String): Flux<String> {
        return Flux.create { sink ->
            // SSE 格式: data: {...}
            val lines = chunk.split("\n")
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") {
                        sink.complete()
                        return@create
                    }
                    
                    try {
                        val jsonNode = objectMapper.readTree(data)
                        val choices = jsonNode.get("choices")
                        if (choices != null && choices.isArray && choices.size() > 0) {
                            val delta = choices[0].get("delta")
                            if (delta != null) {
                                val content = delta.get("content")?.asText()
                                if (content != null && content.isNotBlank()) {
                                    sink.next(content)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Failed to parse SSE chunk: ${e.message}")
                    }
                }
            }
        }
    }

    private fun parseFunctionStreamChunk(chunk: String): Flux<LLMResponse> {
        return Flux.create { sink ->
            val lines = chunk.split("\n")
            
            // 累积的工具调用数据
            val toolCallsMap = mutableMapOf<Int, ToolCallBuilder>()
            
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") {
                        // 处理所有累积的工具调用
                        toolCallsMap.values.forEach { builder ->
                            sink.next(LLMResponse(null, listOf(builder.build())))
                        }
                        sink.complete()
                        return@create
                    }
                    
                    try {
                        val jsonNode = objectMapper.readTree(data)
                        val choices = jsonNode.get("choices")
                        if (choices != null && choices.isArray && choices.size() > 0) {
                            val delta = choices[0].get("delta")
                            if (delta != null) {
                                // 处理文本内容
                                val content = delta.get("content")?.asText()
                                if (content != null && content.isNotBlank()) {
                                    sink.next(LLMResponse(content, null))
                                }
                                
                                // 处理工具调用
                                val toolCalls = delta.get("tool_calls")
                                if (toolCalls != null && toolCalls.isArray) {
                                    for (toolCallNode in toolCalls) {
                                        val index = toolCallNode.get("index")?.asInt() ?: 0
                                        val function = toolCallNode.get("function")
                                        
                                        if (function != null) {
                                            val builder = toolCallsMap.getOrPut(index) { ToolCallBuilder() }
                                            
                                            // 更新 ID
                                            val id = toolCallNode.get("id")?.asText()
                                            if (id != null) {
                                                builder.id = id
                                            }
                                            
                                            // 更新函数名
                                            val name = function.get("name")?.asText()
                                            if (name != null) {
                                                builder.functionName = name
                                            }
                                            
                                            // 追加参数
                                            val arguments = function.get("arguments")?.asText()
                                            if (arguments != null) {
                                                builder.functionArguments += arguments
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Failed to parse function chunk: ${e.message}")
                    }
                }
            }
        }
    }

    private fun parseParameters(argumentsJson: String): Map<String, Any> {
        return try {
            val jsonNode = objectMapper.readTree(argumentsJson)
            objectMapper.convertValue(jsonNode, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            logger.warn("Failed to parse parameters: ${e.message}")
            emptyMap()
        }
    }

    // 辅助类用于构建 ToolCall
    private data class ToolCallBuilder(
        var id: String = "",
        var functionName: String = "",
        var functionArguments: String = ""
    ) {
        fun build(): ToolCall {
            return ToolCall(
                id = id.ifBlank { UUID.randomUUID().toString() },
                function = FunctionDef(
                    name = functionName,
                    arguments = functionArguments,
                    description = null
                )
            )
        }
    }

    companion object {
        const val TYPE = "doubao"
    }
}

class OpenAILLMProvider : LLMProvider {
    private val logger = LoggerFactory.getLogger(OpenAILLMProvider::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    private lateinit var webClient: WebClient
    private var baseUrl: String = ""
    private var model: String = ""
    private var apiKey: String = ""
    
    var maxTokens: Int? = null
    var temperature: Double? = null
    var topP: Double? = null
    var frequencyPenalty: Double? = null
    var timeout: Int = 300
    
    private var isInitialized = false

    override suspend fun initialize() {
        // TODO: 从配置加载参数
        baseUrl = "https://api.openai.com/v1"
        model = "gpt-4o-mini"
        apiKey = "your_openai_key"
        maxTokens = 4096
        temperature = 0.7
        
        webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .codecs { configurer ->
                configurer.defaultCodecs { 
                    it.maxInMemorySize(16 * 1024 * 1024)
                }
            }
            .build()
        
        isInitialized = true
        logger.info("OpenAI LLM provider initialized: model=$model")
    }

    override suspend fun close() {
        isInitialized = false
        logger.info("OpenAI LLM provider closed")
    }

    override suspend fun generate(sessionId: String, messages: List<Message>): Flow<String> {
        if (!isInitialized) {
            throw Exception("LLM provider not initialized")
        }

        return flow {
            val requestParams = buildRequestParams(messages, functions = null)
            
            try {
                webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers { headers ->
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                    }
                    .bodyValue(requestParams)
                    .retrieve()
                    .bodyToFlux(String::class.java)
                    .flatMap { chunk ->
                        parseOpenAIStreamChunk(chunk)
                    }
                    .collect { text ->
                        if (text.isNotBlank()) {
                            emit(text)
                        }
                    }
            } catch (e: WebClientResponseException) {
                logger.error("OpenAI LLM error: status=${e.statusCode}", e)
                emit("抱歉，我暂时无法回复。")
            } catch (e: Exception) {
                logger.error("OpenAI LLM error: ${e.message}", e)
                emit("抱歉，我暂时无法回复。")
            }
        }
    }

    override suspend fun generateWithFunctions(
        sessionId: String,
        messages: List<Message>,
        functions: List<FunctionDef>
    ): Flow<LLMResponse> {
        if (!isInitialized) {
            throw Exception("LLM provider not initialized")
        }

        return flow {
            val requestParams = buildRequestParams(messages, functions)
            
            try {
                webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers { headers ->
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                    }
                    .bodyValue(requestParams)
                    .retrieve()
                    .bodyToFlux(String::class.java)
                    .flatMap { chunk ->
                        parseOpenAIFunctionStreamChunk(chunk)
                    }
                    .collect { response ->
                        emit(response)
                    }
            } catch (e: WebClientResponseException) {
                logger.error("OpenAI LLM function call error: status=${e.statusCode}", e)
                emit(LLMResponse("抱歉，我暂时无法回复。", null))
            } catch (e: Exception) {
                logger.error("OpenAI LLM function call error: ${e.message}", e)
                emit(LLMResponse("抱歉，我暂时无法回复。", null))
            }
        }
    }

    private fun buildRequestParams(messages: List<Message>, functions: List<FunctionDef>?): Map<String, Any> {
        val normalizedMessages = messages.map { msg ->
            val map = mutableMapOf<String, Any>(
                "role" to msg.role,
                "content" to (msg.content ?: "")
            )
            
            if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                map["tool_calls"] = msg.toolCalls.map { toolCall ->
                    mapOf(
                        "id" to (toolCall.id ?: ""),
                        "type" to "function",
                        "function" to mapOf(
                            "name" to toolCall.function.name,
                            "arguments" to toolCall.function.arguments
                        )
                    )
                }
            }
            
            if (msg.toolCallId != null) {
                map["tool_call_id"] = msg.toolCallId
            }
            
            map
        }
        
        val params = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to normalizedMessages,
            "stream" to true
        )
        
        maxTokens?.let { params["max_tokens"] = it }
        temperature?.let { params["temperature"] = it }
        topP?.let { params["top_p"] = it }
        frequencyPenalty?.let { params["frequency_penalty"] = it }
        
        if (functions != null && functions.isNotEmpty()) {
            params["tools"] = functions.map { func ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to func.name,
                        "description" to (func.description ?: ""),
                        "parameters" to parseParameters(func.arguments)
                    )
                )
            }
            params["tool_choice"] = "auto"
        }
        
        return params
    }

    private fun parseOpenAIStreamChunk(chunk: String): Flux<String> {
        // OpenAI 的 SSE 格式与 Doubao 相同
        return Flux.create { sink ->
            val lines = chunk.split("\n")
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") {
                        sink.complete()
                        return@create
                    }
                    
                    try {
                        val jsonNode = objectMapper.readTree(data)
                        val choices = jsonNode.get("choices")
                        if (choices != null && choices.isArray && choices.size() > 0) {
                            val delta = choices[0].get("delta")
                            if (delta != null) {
                                val content = delta.get("content")?.asText()
                                if (content != null && content.isNotBlank()) {
                                    sink.next(content)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Failed to parse OpenAI SSE chunk: ${e.message}")
                    }
                }
            }
        }
    }

    private fun parseOpenAIFunctionStreamChunk(chunk: String): Flux<LLMResponse> {
        // OpenAI 的 function call 格式与 Doubao 相同
        return Flux.create { sink ->
            val lines = chunk.split("\n")
            val toolCallsMap = mutableMapOf<Int, ToolCallBuilder>()
            
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") {
                        toolCallsMap.values.forEach { builder ->
                            sink.next(LLMResponse(null, listOf(builder.build())))
                        }
                        sink.complete()
                        return@create
                    }
                    
                    try {
                        val jsonNode = objectMapper.readTree(data)
                        val choices = jsonNode.get("choices")
                        if (choices != null && choices.isArray && choices.size() > 0) {
                            val delta = choices[0].get("delta")
                            if (delta != null) {
                                val content = delta.get("content")?.asText()
                                if (content != null && content.isNotBlank()) {
                                    sink.next(LLMResponse(content, null))
                                }
                                
                                val toolCalls = delta.get("tool_calls")
                                if (toolCalls != null && toolCalls.isArray) {
                                    for (toolCallNode in toolCalls) {
                                        val index = toolCallNode.get("index")?.asInt() ?: 0
                                        val function = toolCallNode.get("function")
                                        
                                        if (function != null) {
                                            val builder = toolCallsMap.getOrPut(index) { ToolCallBuilder() }
                                            
                                            val id = toolCallNode.get("id")?.asText()
                                            if (id != null) {
                                                builder.id = id
                                            }
                                            
                                            val name = function.get("name")?.asText()
                                            if (name != null) {
                                                builder.functionName = name
                                            }
                                            
                                            val arguments = function.get("arguments")?.asText()
                                            if (arguments != null) {
                                                builder.functionArguments += arguments
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Failed to parse OpenAI function chunk: ${e.message}")
                    }
                }
            }
        }
    }

    private fun parseParameters(argumentsJson: String): Map<String, Any> {
        return try {
            val jsonNode = objectMapper.readTree(argumentsJson)
            objectMapper.convertValue(jsonNode, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            logger.warn("Failed to parse parameters: ${e.message}")
            emptyMap()
        }
    }

    private data class ToolCallBuilder(
        var id: String = "",
        var functionName: String = "",
        var functionArguments: String = ""
    ) {
        fun build(): ToolCall {
            return ToolCall(
                id = id.ifBlank { UUID.randomUUID().toString() },
                function = FunctionDef(
                    name = functionName,
                    arguments = functionArguments,
                    description = null
                )
            )
        }
    }

    companion object {
        const val TYPE = "openai"
    }
}
