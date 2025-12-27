package com.xiaozhi.server.config


import com.xiaozhi.common.XiaoZhiConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue
import java.io.File

@Component
class XiaoZhiConfigLoader(
) {
    var configPath: String = "config/config.yaml"
    var dataDir: String = "data"

    private val logger = LoggerFactory.getLogger(XiaoZhiConfigLoader::class.java)

    private val objectMapper = JsonConfig.yamlMapper
    fun load(): XiaoZhiConfig {
        val mapper = objectMapper

        val configFiles = listOf(
            File("$dataDir/.config.yaml"),
            File(configPath)
        )

        var config: XiaoZhiConfig? = null

        for (file in configFiles) {
            if (file.exists()) {
                try {
                    config = mapper.readValue(file)
                    logger.info("Loaded config from: ${file.absolutePath}")
                    break
                } catch (e: Exception) {
                    logger.warn("Failed to load config from ${file.absolutePath}: ${e.message}")
                }
            }
        }

        return config ?: XiaoZhiConfig().also {
            logger.warn("Using default config")
        }
    }

    fun save(config: XiaoZhiConfig) {
        val mapper = objectMapper

        val file = File("$dataDir/.config.yaml")
        file.parentFile?.mkdirs()

        try {
            mapper.writeValue(file, config)
            logger.info("Saved config to: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to save config: ${e.message}", e)
        }
    }
}
