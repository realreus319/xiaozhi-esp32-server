package com.xiaozhi.server.config

import com.xiaozhi.common.XiaoZhiConfig
import com.xiaozhi.server.connection.ConnectionHandler
import com.xiaozhi.server.core.providers.ProviderFactory
import com.xiaozhi.server.plugins.PluginFunctionExecutor
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.WebSocketClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import java.util.concurrent.ConcurrentHashMap

class WebSocketConfig {
    private val logger = LoggerFactory.getLogger(WebSocketConfig::class.java)
    
    var host: String = "0.0.0.0"
    var port: Int = 8080
    var path: String = "/ws"
    
    fun buildUrl(): String {
        return "ws://$host:$port$path"
    }
}

class XiaoZhiWebSocketHandler {
    private val logger = LoggerFactory.getLogger(XiaoZhiWebSocketHandler::class.java)
    private val webSocketClient: WebSocketClient = ReactorNettyWebSocketClient()
    
    private val connectionManager = ConnectionManager()
    
    suspend fun handleConnection(session: WebSocketSession) {
        try {
            val deviceId = session.handshakeInfo.headers["device-id"]?.firstOrNull()
            val clientId = session.handshakeInfo.headers["client-id"]?.firstOrNull()
            val clientIp = session.handshakeInfo.remoteAddress?.address?.hostAddress ?: "unknown"
            
            logger.info("New connection from $clientIp, deviceId: $deviceId, clientId: $clientId")
            
            val handler = connectionManager.createConnection(session, deviceId, clientId)
            
            session.receive().collect { message ->
                when {
                    message.type.isText -> {
                        val text = message.payloadAsText
                        handler.handleText(text)
                    }
                    message.type.isBinary -> {
                        val data = message.payloadAsData.array()
                        handler.handleAudio(data)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("WebSocket connection error: ${e.message}", e)
        } finally {
            connectionManager.removeConnection(deviceId)
        }
    }
}

class ConnectionManager {
    private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)
    private val connections = ConcurrentHashMap<String, ConnectionHandler>()
    
    private val providerFactory = ProviderFactory()
    private val config = XiaoZhiConfig()
    
    suspend fun createConnection(session: WebSocketSession, deviceId: String, clientId: String?): ConnectionHandler {
        val sessionId = generateSessionId()
        
        val handler = ConnectionHandler(
            sessionId = sessionId,
            deviceId = deviceId,
            clientId = clientId,
            session = session
        )
        
        // 初始化 Providers
        val llm = providerFactory.createLLMProvider(config.selectedModule?.llm ?: "doubao", config)
        val tts = providerFactory.createTTSProvider(config.selectedModule?.tts ?: "edge", config)
        val vad = providerFactory.createVADProvider(config.selectedModule?.vad ?: "silero", config)
        val asr = providerFactory.createASRProvider(config.selectedModule?.asr ?: "doubao", config)
        val intent = providerFactory.createIntentProvider(config.selectedModule?.intent ?: "function_call", config)
        val memory = providerFactory.createMemoryProvider(config.selectedModule?.memory ?: "nomem", config)
        
        llm.initialize()
        tts.initialize()
        vad.initialize()
        asr.initialize()
        intent.initialize()
        memory.initMemory(deviceId, config.memory, false)
        
        handler.setProviders(vad, asr, llm, tts, memory, intent)
        
        // 更新配置
        handler.updateConfig(
            mapOf(
                "exit_commands" to config.xiaozhi?.exitCommands ?: listOf("退出", "关闭", "exit", "quit"),
                "wakeup_words" to config.xiaozhi?.wakeupWords ?: listOf("你好小智", "嘿你好呀", "你好小志"),
                "max_output_size" to (config.xiaozhi?.maxOutputSize ?: 0),
                "close_connection_no_voice_time" to (config.xiaozhi?.closeConnectionNoVoiceTime ?: 120),
                "end_prompt" to (config.xiaozhi?.endPrompt ?: emptyMap())
            )
        )
        
        // 初始化插件执行器
        val pluginExecutor = PluginFunctionExecutor(handler)
        pluginExecutor.initialize()
        
        connections[sessionId] = handler
        
        logger.info("Created connection handler for device: $deviceId")
        
        return handler
    }
    
    fun removeConnection(sessionId: String?) {
        if (sessionId != null) {
            val handler = connections.remove(sessionId)
            handler?.close()
            logger.info("Removed connection: $sessionId")
        }
    }
    
    fun getConnection(sessionId: String): ConnectionHandler? {
        return connections[sessionId]
    }
    
    fun getAllConnections(): List<ConnectionHandler> {
        return connections.values.toList()
    }
    
    fun getConnectionCount(): Int {
        return connections.size
    }
    
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }
}
