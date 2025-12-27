package com.xiaozhi.server.core.api

import io.netty.handler.codec.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

// WebFilter统一处理CORS OPTIONS请求
@Component
class CorsFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val headers = exchange.response.headers
        headers.add("Access-Control-Allow-Headers", "client-id, content-type, device-id, authorization")
        headers.add("Access-Control-Allow-Credentials", "true")
        headers.add("Access-Control-Allow-Origin", "*")
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

        return if (exchange.request.method == HttpMethod.OPTIONS) {
            exchange.response.statusCode = org.springframework.http.HttpStatus.OK
            exchange.response.setComplete()
        } else {
            chain.filter(exchange)
        }
    }
}