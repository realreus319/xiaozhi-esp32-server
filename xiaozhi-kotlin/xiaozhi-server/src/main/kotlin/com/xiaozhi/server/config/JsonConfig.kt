package com.xiaozhi.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.datatype.jsr310.JavaTimeModule
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class JsonConfig {
    companion object {
        val objectMapper: JsonMapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JavaTimeModule())
            .build()

        val yamlMapper: YAMLMapper = YAMLMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JavaTimeModule())
            .build()
    }

    @get:Bean
    val objectMapper = JsonConfig.objectMapper
    @get:Bean
    val yamlMapper: YAMLMapper = JsonConfig.yamlMapper
}