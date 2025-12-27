package com.xiaozhi.server.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.xiaozhi.common.XiaoZhiConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.io.File

@Component
@ConfigurationProperties(prefix = "xiaozhi")
class XiaoZhiConfigProperties(
    @Value("\${xiaozhi.config.path:config/config.yaml}")
    var configPath: String = "config/config.yaml",
    
    @Value("\${xiaozhi.data.dir:data}")
    var dataDir: String = "data",
    
    @Value("\${xiaozhi.config.server.port:8080}")
    var serverPort: Int = 8080
) {
    private val logger = LoggerFactory.getLogger(XiaoZhiConfigProperties::class.java)
    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

    fun loadConfig(): XiaoZhiConfig {
        // 尝试从多个位置加载配置
        val configFiles = listOf(
            File(dataDir).resolve(".config.yaml"),
            File(configPath)
        )
        
        for (file in configFiles) {
            if (file.exists()) {
                try {
                    val config = mapper.readValue(file, XiaoZhiConfig::class.java)
                    logger.info("Loaded config from: ${file.absolutePath}")
                    return config
                } catch (e: Exception) {
                    logger.warn("Failed to load config from ${file.absolutePath}: ${e.message}")
                }
            }
        }
        
        logger.warn("Using default config")
        return XiaoZhiConfig()
    }

    fun saveConfig(config: XiaoZhiConfig) {
        val file = File(dataDir).resolve(".config.yaml")
        file.parentFile?.mkdirs()
        
        try {
            mapper.writeValue(file, config)
            logger.info("Saved config to: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to save config: ${e.message}", e)
        }
    }
}
