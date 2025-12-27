package com.xiaozhi.server

import com.xiaozhi.server.config.XiaoZhiConfigProperties
import com.xiaozhi.server.config.WebSocketConfig
import com.xiaozhi.server.connection.ConnectionManager
import com.xiaozhi.server.ws.XiaoZhiWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.EnableWebFlux

@SpringBootApplication
@EnableWebFlux
@ComponentScan(basePackages = ["com.xiaozhi.server"])
class XiaoZhiServerApplication {
    
    companion object {
        private val logger = LoggerFactory.getLogger(XiaoZhiServerApplication::class.java)
        
        @JvmStatic
        fun main(args: Array<String>) {
            logger.info("Starting XiaoZhi Server...")
            org.springframework.boot.run(
                XiaoZhiServerApplication::class.java,
                *args
            )
        }
    }
}
