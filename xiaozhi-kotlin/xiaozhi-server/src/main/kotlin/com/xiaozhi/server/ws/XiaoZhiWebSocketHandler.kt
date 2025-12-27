package com.xiaozhi.server.ws

import com.xiaozhi.server.config.XiaoZhiConfigProperties
import com.xiaozhi.server.config.WebSocketConfig
import com.xiaozhi.server.config.WebSocketConfig
import com.xiaozhi.server.connection.ConnectionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.HandlerFunction
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.RequestUpgrade
import org.springframework.web.reactive.socket.server.support
import org.springframework.web.reactive.socket.server.support
import reactor.core.publisher.Mono

@Component
class XiaoZhiWebSocketHandler : WebSocketHandler {
    private val logger = LoggerFactory.getLogger(XiaoZhiWebSocketHandler::class.java)
    
    private val connectionManager = ConnectionManager()
    private val config: XiaoZhiConfigProperties
    
    constructor(config: XiaoZhiConfigProperties) {
        this.config = config
    }

    override fun handle(session: WebSocketSession): Mono<Void> {
        return session.send(Mono.empty<Void>())
    }
    
    override fun handle(session: WebSocketSession): Mono<Void> {
        logger.info("New WebSocket connection from ${session.handshakeInfo.remoteAddress}")
        
        val deviceId = session.handshakeInfo.headers["device-id"]?.firstOrNull()
        val clientId = session.handshakeInfo.headers["client-id"]?.firstOrNull()
        val clientIp = session.handshakeInfo.remoteAddress?.address
        
        if (deviceId == null) {
            logger.warn("Connection rejected: missing device-id header")
            session.close()
            return Mono.error(Exception("Missing device-id header"))
        }
        
        try {
            return connectionManager.createConnection(session, deviceId, clientId).then { handler ->
                logger.info("Connection handler created for device: $deviceId")
            }
        } catch (e: Exception) {
            logger.error("Failed to create connection handler: ${e.message}", e)
            session.close()
            return Mono.error(e)
        }
    }
    
    override fun supportsPartialMessages(): Boolean {
        return false
    }
    
    override fun getSubProtocols(): List<String> {
        return listOf("ws")
    }
}

@Component
class XiaoZhiWebSocketConfigurer {
    private val logger = LoggerFactory.getLogger(XiaoZhiWebSocketConfigurer::class.java)
    
    @Bean
    fun webSocketHandler(handler: XiaoZhiWebSocketHandler): WebSocketHandler {
        return org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService(
            XiaoZhiWebSocketHandler::class.java,
            handler
        )
    }
}
