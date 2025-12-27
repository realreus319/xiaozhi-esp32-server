package com.xiaozhi.server.plugins

import com.xiaozhi.server.plugins.Plugin

class GetWeatherPlugin : Plugin {
    override val name = "get_weather"
    override val description = "获取天气信息"
    override val parameters = listOf(
        PluginParameter(
            name = "location",
            type = "string",
            required = false,
            description = "城市名称，如：北京、上海"
        )
    )

    override suspend fun initialize() {
        // 初始化天气 API 连接
    }

    override suspend fun execute(arguments: Map<String, Any>): PluginResult {
        val location = arguments["location"] as? String ?: "广州"
        
        // TODO: 调用实际的天气 API
        val weatherInfo = "$location 今天天气晴朗，气温 25°C，湿度 60%"
        
        return PluginResult(
            success = true,
            response = weatherInfo
        )
    }

    override fun cleanup() {
        // 清理资源
    }
}

class PlayMusicPlugin : Plugin {
    override val name = "play_music"
    override val description = "播放音乐"
    override val parameters = listOf(
        PluginParameter(
            name = "song",
            type = "string",
            required = true,
            description = "歌曲名称或搜索关键词"
        ),
        PluginParameter(
            name = "volume",
            type = "integer",
            required = false,
            description = "音量 (0-100)"
        )
    )

    override suspend fun initialize() {
        // 初始化音乐播放器连接
    }

    override suspend fun execute(arguments: Map<String, Any>): PluginResult {
        val song = arguments["song"] as? String
            ?: return PluginResult(
                success = false,
                error = "缺少必需参数: song"
            )
        
        val volume = (arguments["volume"] as? Number)?.toInt() ?: 50
        
        // TODO: 调用音乐播放 API
        val response = "正在播放：$song，音量 $volume"
        
        return PluginResult(
            success = true,
            response = response
        )
    }

    override fun cleanup() {
        // 清理资源
    }
}

class HandleExitIntentPlugin : Plugin {
    override val name = "handle_exit_intent"
    override val description = "处理退出意图"
    override val parameters = emptyList()

    override suspend fun initialize() {}

    override suspend fun execute(arguments: Map<String, Any>): PluginResult {
        return PluginResult(
            success = true,
            response = "再见！"
        )
    }

    override fun cleanup() {}
}
