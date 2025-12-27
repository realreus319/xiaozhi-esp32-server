package com.xiaozhi.server.tools

import com.xiaozhi.server.connection.ConnectionHandler
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.reflect.fullname

class ServerPluginExecutor(
    private val conn: ConnectionHandler
) : ToolExecutor {
    private val logger = LoggerFactory.getLogger(ServerPluginExecutor::class.java)

    private val tools = mutableMapOf<String, ToolExecutor>()

    override suspend fun initialize() {
        logger.info("Initializing server plugin executor")
        registerBuiltInTools()
    }

    override suspend fun execute(name: String, arguments: Map<String, Any>): ToolExecutionResult {
        val tool = tools[name]
        if (tool == null) {
            return ToolExecutionResult(
                action = ToolAction.NOT_FOUND,
                response = "插件未找到: $name"
            )
        }

        return tool.execute(name, arguments)
    }

    override suspend fun cleanup() {
        tools.values.forEach { it.cleanup() }
    }

    private fun registerBuiltInTools() {
        val getWeatherTool = object : ToolExecutor {
            override suspend fun initialize() {}
            override suspend fun execute(name: String, arguments: Map<String, Any>): ToolExecutionResult {
                val location = arguments["location"] as? String ?: "广州"
                val weatherInfo = "$location 今天天气晴朗，气温 25°C"
                return ToolExecutionResult(
                    action = ToolAction.RESPONSE,
                    response = weatherInfo
                )
            }
            override suspend fun cleanup() {}
        }

        val playMusicTool = object : ToolExecutor {
            override suspend fun initialize() {}
            override suspend fun execute(name: String, arguments: Map<String, Any>): ToolExecutionResult {
                val song = arguments["song"] as? String ?: "默认歌曲"
                val response = "正在播放：$song"
                return ToolExecutionResult(
                    action = ToolAction.RESPONSE,
                    response = response
                )
            }
            override suspend fun cleanup() {}
        }

        tools["get_weather"] = getWeatherTool
        tools["play_music"] = playMusicTool

        logger.info("Registered built-in tools: ${tools.keys}")
    }

    fun registerTool(name: String, tool: ToolExecutor) {
        tools[name] = tool
        logger.info("Registered plugin tool: $name")
    }

    fun getToolNames(): List<String> {
        return tools.keys.toList()
    }
}
