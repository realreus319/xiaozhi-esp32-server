package com.xiaozhi.server.connection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.awt.SystemColor.text

/**
 * 发送单条文本消息
 */
suspend fun WebSocketSession.sendText(text: String) {
    val msg = this.textMessage(text)
    send(Mono.just(msg)).awaitSingle()
}

/**
 * 发送文本消息流
 */
suspend fun WebSocketSession.sendText(flow: Flow<String>) {
    // Flow<String> -> Flow<TextMessage> -> Flux<TextMessage>
    val flux = flow.map{ this.textMessage(it) }.asFlux()
    send(flux).awaitSingle()
}

suspend fun WebSocketSession.sendBuffer(bytes: ByteArray) {
    val msg = binaryMessage { factory -> factory.wrap(bytes) } // ✅
    send(Mono.just(msg)).awaitSingle()
}

/**
 * 发送二进制消息流
 */
suspend fun WebSocketSession.sendBuffer(flow: Flow<ByteArray>) {
    val flux = flow.map { bytes -> binaryMessage { factory -> factory.wrap(bytes) } }.asFlux()
    send(flux).awaitSingle()
}
