package com.xiaozhi.server.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.xiaozhi.common.dto.ToolCall
import com.xiaozhi.server.connection.ConnectionHandler
import com.xiaozhi.server.core.providers.FunctionDef
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

enum class Action {
    NONE,
    RESPONSE,
    REQLLM,
    NOTFOUND,
    ERROR
}

data class ActionResponse(
    val action: Action,
    val response: String? = null,
    val result: String? = null
)

enum class ToolType {
    SERVER_PLUGIN,
    SERVER_MCP,
    DEVICE_IOT,
    DEVICE_MCP,
    MCP_ENDPOINT
}

class ToolManager {
    private val logger = LoggerFactory.getLogger(ToolManager::class.java)
    private val objectMapper = ObjectMapper()
    
    private val executors = ConcurrentHashMap<ToolType, ToolExecutor>()
    private val tools = ConcurrentHashMap<String, FunctionDef>()
    private val conn: ConnectionHandler
    
    constructor(conn: ConnectionHandler) {
        this.conn = conn
    }
    
    fun registerExecutor(type: ToolType, executor: ToolExecutor) {
        executors[type] = executor
        logger.debug("Registered executor for type: $type")
    }
    
    suspend fun initialize() {
        // 初始化所有执行器
        for ((type, executor) in executors) {
            try {
                executor.initialize()
                logger.info("Initialized executor: $type")
            } catch (e: Exception) {
                logger.error("Failed to initialize executor $type: ${e.message}", e)
            }
        }
        
        // 刷新工具列表
        refreshTools()
        
        // 输出支持的工具
        val supportedTools = getSupportedToolNames()
        logger.info("Supported tools: $supportedTools")
    }
    
    fun refreshTools() {
        tools.clear()
        
        // 从所有执行器收集工具
        for (executor in executors.values) {
            val executorTools = executor.getTools()
            for (tool in executorTools) {
                tools[tool.name] = tool
            }
        }
    }
    
    fun getFunctionDescriptions(): List<Map<String, Any>> {
        return tools.values.map { tool ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to (tool.description ?: ""),
                    "parameters" to parseParameters(tool.arguments)
                )
            )
        }
    }
    
    fun getSupportedToolNames(): List<String> {
        return tools.keys.toList()
    }
    
    fun hasTool(toolName: String): Boolean {
        return tools.containsKey(toolName)
    }
    
    suspend fun executeTool(toolName: String, arguments: Map<String, Any>): ActionResponse {
        logger.debug("Executing tool: $toolName, args: $arguments")
        
        val tool = tools[toolName]
        if (tool == null) {
            logger.warn("Tool not found: $toolName")
            return ActionResponse(action = Action.NOTFOUND, response = "Tool not found")
        }
        
        // 查找对应的执行器
        for (executor in executors.values) {
            if (executor.canExecute(toolName)) {
                try {
                    return executor.execute(toolName, arguments)
                } catch (e: Exception) {
                    logger.error("Error executing tool $toolName: ${e.message}", e)
                    return ActionResponse(action = Action.ERROR, response = "Execution error: ${e.message}")
                }
            }
        }
        
        return ActionResponse(action = Action.NOTFOUND, response = "No executor found for tool")
    }
    
    fun cleanup() {
        for (executor in executors.values) {
            try {
                executor.cleanup()
            } catch (e: Exception) {
                logger.error("Error cleaning up executor: ${e.message}", e)
            }
        }
        executors.clear()
        tools.clear()
    }
    
    private fun parseParameters(argumentsJson: String): Map<String, Any> {
        return try {
            objectMapper.readTree(argumentsJson).let { jsonNode ->
                val result = mutableMapOf<String, Any>()
                val fieldNames = jsonNode.fieldNames()
                for (fieldName in fieldNames) {
                    val field = jsonNode.get(fieldName)
                    result[fieldName] = when {
                        field.isBoolean -> field.asBoolean()
                        field.isInt -> field.asInt()
                        field.isDouble -> field.asDouble()
                        field.isTextual -> field.asText()
                        else -> field.toString()
                    }
                }
                result
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse parameters: ${e.message}")
            emptyMap()
        }
    }
}

interface ToolExecutor {
    suspend fun initialize()
    fun getTools(): List<FunctionDef>
    fun canExecute(toolName: String): Boolean
    suspend fun execute(toolName: String, arguments: Map<String, Any>): ActionResponse
    fun cleanup()
}

class ServerPluginExecutor : ToolExecutor {
    private val logger = LoggerFactory.getLogger(ServerPluginExecutor::class.java)
    private lateinit var conn: ConnectionHandler
    
    private val plugins = ConcurrentHashMap<String, PluginFunction>()
    
    override suspend fun initialize() {
        // TODO: 自动加载插件模块
        // 这里硬编码一些内置插件
        registerBuiltInPlugins()
        logger.info("ServerPluginExecutor initialized with ${plugins.size} plugins")
    }
    
    override fun getTools(): List<FunctionDef> {
        return plugins.values.map { it.toFunctionDef() }
    }
    
    override fun canExecute(toolName: String): Boolean {
        return plugins.containsKey(toolName)
    }
    
    override suspend fun execute(toolName: String, arguments: Map<String, Any>): ActionResponse {
        val plugin = plugins[toolName]
        if (plugin == null) {
            return ActionResponse(action = Action.NOTFOUND, response = "Plugin not found")
        }
        
        return try {
            val result = plugin.execute(arguments)
            ActionResponse(action = Action.RESPONSE, response = result)
        } catch (e: Exception) {
            logger.error("Plugin execution error: ${e.message}", e)
            ActionResponse(action = Action.ERROR, response = e.message)
        }
    }
    
    override fun cleanup() {
        plugins.clear()
    }
    
    private fun registerBuiltInPlugins() {
        // 注册内置插件
        plugins["get_time"] = GetTimePlugin()
        plugins["get_weather"] = GetWeatherPlugin()
        plugins["play_music"] = PlayMusicPlugin()
        // TODO: 从 plugins_func/functions 自动加载插件
    }
}

class PluginFunction {
    abstract fun execute(arguments: Map<String, Any>): String
    
    abstract fun toFunctionDef(): FunctionDef
}

class GetTimePlugin : PluginFunction() {
    override fun execute(arguments: Map<String, Any>): String {
        val now = java.time.LocalDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return "现在是 ${now.format(formatter)}"
    }
    
    override fun toFunctionDef(): FunctionDef {
        return FunctionDef(
            name = "get_time",
            description = "获取当前时间",
            arguments = """{"type": "object", "properties": {}}"""
        )
    }
}

class GetWeatherPlugin : PluginFunction() {
    override fun execute(arguments: Map<String, Any>): String {
        val location = arguments["location"] as? String ?: "广州"
        // TODO: 实际调用天气 API
        return "$location 今天天气晴朗，气温 25°C"
    }
    
    override fun toFunctionDef(): FunctionDef {
        return FunctionDef(
            name = "get_weather",
            description = "获取指定城市的天气信息",
            arguments = """{"type": "object", "properties": {"location": {"type": "string", "description": "城市名称"}}}"""
        )
    }
}

class PlayMusicPlugin : PluginFunction() {
    override fun execute(arguments: Map<String, Any>): String {
        val song = arguments["song"] as? String ?: "默认歌曲"
        // TODO: 实际播放音乐
        return "正在播放：$song"
    }
    
    override fun toFunctionDef(): FunctionDef {
        return FunctionDef(
            name = "play_music",
            description = "播放音乐",
            arguments = """{"type": "object", "properties": {"song": {"type": "string", "description": "歌曲名称"}}}"""
        )
    }
}

class PluginFunctionExecutor(conn: ConnectionHandler) {
    private val logger = LoggerFactory.getLogger(PluginFunctionExecutor::class.java)
    private val toolManager = ToolManager(conn)
    
    private val serverPluginExecutor = ServerPluginExecutor()
    
    init {
        toolManager.registerExecutor(ToolType.SERVER_PLUGIN, serverPluginExecutor)
    }
    
    suspend fun initialize() {
        serverPluginExecutor.conn = conn
        toolManager.initialize()
    }
    
    fun getFunctions(): List<FunctionDef> {
        return toolManager.getFunctionDescriptions().map { desc ->
            val funcMap = (desc["function"] as Map<*, *>)
            FunctionDef(
                name = funcMap["name"] as String,
                arguments = objectMapper.writeValueAsString(funcMap.get("parameters") ?: emptyMap<String, Any>()),
                description = funcMap["description"] as? String
            )
        }
    }
    
    suspend fun handleLLMFunctionCall(toolCall: ToolCall): ActionResponse {
        val args = try {
            objectMapper.readValue(toolCall.function.arguments, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any>>() {})
        } catch (e: Exception) {
            emptyMap<String, Any>()
        }
        
        return toolManager.executeTool(toolCall.function.name, args)
    }
    
    fun cleanup() {
        toolManager.cleanup()
    }
    
    private val objectMapper = ObjectMapper()
}
