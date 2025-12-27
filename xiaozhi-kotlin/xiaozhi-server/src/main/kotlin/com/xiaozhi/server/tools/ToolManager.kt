package com.xiaozhi.server.tools

import com.xiaozhi.common.exception.XiaoZhiException
import com.xiaozhi.server.connection.ConnectionHandler
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import kotlin.reflect.fullname

enum class ToolType {
    SERVER_PLUGIN,
    SERVER_MCP,
    DEVICE_IOT,
    DEVICE_MCP,
    MCP_ENDPOINT
}

data class ToolDescriptor(
    val name: String,
    val description: String,
    val type: ToolType,
    val parameters: Map<String, Any> = emptyMap()
)

data class ToolExecutionResult(
    val action: ToolAction,
    val response: String? = null,
    val result: String? = null
)

enum class ToolAction {
    RESPONSE,
    REQUEST_LLM,
    NOT_FOUND,
    ERROR,
    NONE
}

interface ToolExecutor {
    suspend fun initialize()
    suspend fun execute(name: String, arguments: Map<String, Any>): ToolExecutionResult
    suspend fun cleanup()
}

class ToolManager(
    private val conn: ConnectionHandler
) {
    private val logger = LoggerFactory.getLogger(ToolManager::class.java)
    private val executors: MutableMap<ToolType, ToolExecutor> = mutableMapOf()

    private val toolDescriptors: MutableMap<String, ToolDescriptor> = mutableMapOf()

    init {
        registerDefaultExecutors()
    }

    private fun registerDefaultExecutors() {
        logger.info("Registering default tool executors")
    }

    fun registerExecutor(type: ToolType, executor: ToolExecutor) {
        executors[type] = executor
        logger.info("Registered tool executor: ${type.name}")
    }

    fun registerTool(descriptor: ToolDescriptor) {
        toolDescriptors[descriptor.name] = descriptor
        logger.info("Registered tool: ${descriptor.name}")
    }

    suspend fun executeTool(name: String, arguments: Map<String, Any>): ToolExecutionResult {
        val descriptor = toolDescriptors[name]
            if (descriptor == null) {
                logger.warn("Tool not found: $name")
                return ToolExecutionResult(
                    action = ToolAction.NOT_FOUND,
                    response = "工具未找到: $name"
                )
            }

        val executor = executors[descriptor.type]
            if (executor == null) {
                logger.error("No executor registered for type: ${descriptor.type}")
                return ToolExecutionResult(
                    action = ToolAction.ERROR,
                    response = "工具执行器未注册: ${descriptor.type.name}"
                )
            }

        try {
            return executor.execute(name, arguments)
        } catch (e: Exception) {
            logger.error("Error executing tool $name: ${e.message}", e)
            return ToolExecutionResult(
                action = ToolAction.ERROR,
                response = "工具执行失败: ${e.message}"
            )
        }
    }

    fun getToolDescriptions(): List<ToolDescriptor> {
        return toolDescriptors.values.toList()
    }

    fun getSupportedToolNames(): List<String> {
        return toolDescriptors.keys.toList()
    }

    fun hasTool(name: String): Boolean {
        return toolDescriptors.containsKey(name)
    }

    suspend fun cleanup() {
        executors.values.forEach { it.cleanup() }
        logger.info("Tool manager cleanup completed")
    }

    fun refreshTools() {
        logger.debug("Refreshing tool descriptors")
    }

    fun getToolStatistics(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        toolDescriptors.keys.forEach { stats[it] = 0 }
        return stats
    }
}
