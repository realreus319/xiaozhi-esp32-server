package com.xiaozhi.server.connection

import com.xiaozhi.common.protocol.ServerHelloMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentHashMap

@Component
class ConnectionManager(
    private val objectMapper: JsonMapper
) {
    private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)
    private val connections: ConcurrentHashMap<String, ConnectionHandler> = ConcurrentHashMap()

    suspend fun getOrCreateConnection(
        session: WebSocketSession,
        deviceId: String,
        clientId: String?
    ): ConnectionHandler {
        val connectionId = "$deviceId-$clientId"
        return connections.getOrPut(connectionId) {
            ConnectionHandler(
                sessionId = session.id,
                deviceId = deviceId,
                clientId = clientId,
                session = session,
                objectMapper = objectMapper
            ).also {
                logger.info("Created new connection: $connectionId")
            }
        }
    }

    suspend fun removeConnection(sessionId: String) {
        connections.remove(sessionId)?.let { handler ->
            handler.close()
            logger.info("Removed connection: $sessionId")
        }
    }

    fun getConnection(sessionId: String): ConnectionHandler? {
        return connections[sessionId]
    }

    fun getAllConnections(): List<ConnectionHandler> {
        return connections.values.toList()
    }

    suspend fun closeAll() {
        connections.values.forEach { it.close() }
        connections.clear()
        logger.info("Closed all connections")
    }

    fun sendHello(session: WebSocketSession, hello: ServerHelloMessage) {
        try {
            val payload = objectMapper.writeValueAsString(hello)
            session.send(Mono.just(session.textMessage(payload)))
            logger.info("Sent hello message to session: ${session.id}")
        } catch (e: Exception) {
            logger.error("Error sending hello message: ${e.message}", e)
        }
    }
}
