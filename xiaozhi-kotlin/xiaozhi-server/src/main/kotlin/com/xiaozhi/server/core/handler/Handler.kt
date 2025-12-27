package com.xiaozhi.server.core.handler

import com.xiaozhi.common.dto.*
import com.xiaozhi.server.connection.ConnectionHandler
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

interface AudioHandler {
    suspend fun handleAudio(conn: ConnectionHandler, audioData: ByteArray, hasVoice: Boolean)
}

interface TextHandler {
    suspend fun handleText(conn: ConnectionHandler, text: String)
}

class AudioHandlerImpl : AudioHandler {
    private val logger = LoggerFactory.getLogger(AudioHandlerImpl::class.java)
    private val textHandler: TextHandler = TextHandlerImpl()

    override suspend fun handleAudio(conn: ConnectionHandler, audioData: ByteArray, hasVoice: Boolean) {
        try {
            // 如果设备刚刚被唤醒，短暂忽略VAD检测
            if (conn.justWokenUp) {
                conn.asrAudio.clear()
                if (!conn.vadResumeTaskActive) {
                    conn.vadResumeTaskActive = true
                    conn.handlerScope.launch {
                        delay(2000)
                        conn.justWokenUp = false
                        conn.vadResumeTaskActive = false
                        logger.debug("VAD detection resumed")
                    }
                }
                return
            }

            // 检测到语音且正在播放，且不是 manual 模式，打断当前播放
            if (hasVoice && conn.clientIsSpeaking && conn.listenMode != "manual") {
                handleAbort(conn)
            }

            // 设备长时间空闲检测
            checkNoVoiceTimeout(conn, hasVoice)

            // 接收音频数据到 ASR
            conn.asr?.receiveAudio(conn, audioData, hasVoice)
        } catch (e: Exception) {
            logger.error("Audio handling error: ${e.message}", e)
        }
    }

    private suspend fun handleAbort(conn: ConnectionHandler) {
        try {
            conn.tts?.abort()
            conn.clearSpeakStatus()
            logger.info("Aborted current TTS playback")
        } catch (e: Exception) {
            logger.error("Error aborting: ${e.message}", e)
        }
    }

    private suspend fun checkNoVoiceTimeout(conn: ConnectionHandler, hasVoice: Boolean) {
        if (hasVoice) {
            conn.updateLastActivityTime()
            return
        }

        val noVoiceTime = System.currentTimeMillis() - conn.lastActivityTime.get()
        if (!conn.closeAfterChat && noVoiceTime > (conn.closeConnectionNoVoiceTime * 1000)) {
            conn.closeAfterChat = true
            conn.clientAbort.set(false)
            
            val endPromptEnabled = conn.endPrompt?.get("enable") as? Boolean ?: true
            if (!endPromptEnabled) {
                logger.info("Ending conversation without end prompt")
                conn.close()
                return
            }
            
            val prompt = (conn.endPrompt?.get("prompt") as? String)
                ?: "请你以```时间过得真快```未来头，用富有感情、依依不舍的话来结束这场对话吧。！"
            
            textHandler.handleText(conn, prompt)
        }
    }
}

class TextHandlerImpl : TextHandler {
    private val logger = LoggerFactory.getLogger(TextHandlerImpl::class.java)
    private val intentHandler = IntentHandlerImpl()

    override suspend fun handleText(conn: ConnectionHandler, text: String) {
        try {
            // 解析 JSON 格式（包含说话人信息）
            val (actualText, speakerName) = parseSpeakerInfo(text)
            
            // 保存说话人信息
            conn.currentSpeaker = speakerName

            // 检查是否需要绑定设备
            if (conn.needBind) {
                checkBindDevice(conn)
                return
            }

            // 检查输出字数限制
            if (conn.maxOutputSize > 0 && checkOutputLimit(conn)) {
                handleMaxOutputSize(conn)
                return
            }

            // 正在播放且不是 manual 模式，打断
            if (conn.clientIsSpeaking && conn.listenMode != "manual") {
                handleAbort(conn)
            }

            // 发送 STT 消息
            conn.sendSTTMessage(actualText)

            // 首先进行意图分析
            val intentHandled = intentHandler.handleIntent(conn, actualText)
            
            if (intentHandled) {
                // 意图已被处理，不再进行聊天
                return
            }

            // 意图未被处理，继续常规聊天流程
            conn.handlerScope.launch {
                conn.chat(actualText)
            }
        } catch (e: Exception) {
            logger.error("Text handling error: ${e.message}", e)
        }
    }

    private fun parseSpeakerInfo(text: String): Pair<String, String?> {
        val trimmedText = text.trim()
        
        if (trimmedText.startsWith("{") && trimmedText.endsWith("}")) {
            return try {
                val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                val json = mapper.readTree(trimmedText)
                val content = json.get("content")?.asText() ?: trimmedText
                val speaker = json.get("speaker")?.asText()
                Pair(content, speaker)
            } catch (e: Exception) {
                Pair(trimmedText, null)
            }
        }
        
        return Pair(trimmedText, null)
    }

    private suspend fun handleAbort(conn: ConnectionHandler) {
        try {
            conn.tts?.abort()
            conn.clearSpeakStatus()
            logger.info("Aborted current TTS playback")
        } catch (e: Exception) {
            logger.error("Error aborting: ${e.message}", e)
        }
    }

    private fun checkOutputLimit(conn: ConnectionHandler): Boolean {
        // TODO: 实现输出字数限制检查
        // 这里需要检查设备当天的输出字数
        return false
    }

    private suspend fun handleMaxOutputSize(conn: ConnectionHandler) {
        try {
            conn.clientAbort.set(false)
            val text = "不好意思，我现在有点事情要忙，明天这个时候我们再聊，约好了哦！明天不见不散，拜拜！"
            conn.sendSTTMessage(text)
            
            // TODO: 播放 max_output_size.wav 音频文件
            // val opusPackets = loadAudioFile("config/assets/max_output_size.wav")
            // conn.sendAudioPackets(opusPackets)
            
            conn.closeAfterChat = true
        } catch (e: Exception) {
            logger.error("Error handling max output size: ${e.message}", e)
        }
    }

    private suspend fun checkBindDevice(conn: ConnectionHandler) {
        conn.bindCode?.let { code ->
            if (code.length != 6) {
                logger.error("Invalid bind code format: $code")
                val text = "绑定码格式错误，请检查配置。"
                conn.sendSTTMessage(text)
                return
            }

            val text = "请登录控制面板，输入$code，绑定设备。"
            conn.sendSTTMessage(text)
            
            // TODO: 播放 bind_code.wav 音频文件
            // val opusPackets = loadAudioFile("config/assets/bind_code.wav")
            // conn.sendAudioPackets(opusPackets)
            
            // 播放数字
            for (digit in code) {
                try {
                    // TODO: 播放数字音频
                    // val numPath = "config/assets/bind_code/$digit.wav"
                    // val numPackets = loadAudioFile(numPath)
                    // conn.sendAudioPackets(numPackets)
                    delay(500) // 数字之间的间隔
                } catch (e: Exception) {
                    logger.error("Error playing digit audio: $digit", e)
                }
            }
        } ?: run {
            val text = "没有找到该设备的版本信息，请正确配置 OTA地址，然后重新编译固件。"
            conn.sendSTTMessage(text)
            
            // TODO: 播放 bind_not_found.wav 音频文件
            // val opusPackets = loadAudioFile("config/assets/bind_not_found.wav")
            // conn.sendAudioPackets(opusPackets)
        }
    }
}

class IntentHandlerImpl {
    private val logger = LoggerFactory.getLogger(IntentHandlerImpl::class.java)

    suspend fun handleIntent(conn: ConnectionHandler, text: String): Boolean {
        val trimmedText = text.trim()

        // 检查退出命令
        if (isExitCommand(conn, trimmedText)) {
            logger.info("Exit command detected: $trimmedText")
            conn.close()
            return true
        }

        // 检查唤醒词
        if (isWakeupWord(conn, trimmedText)) {
            logger.info("Wakeup word detected: $trimmedText")
            conn.justWokenUp = true
            return true
        }

        // 检查是否是结束提示语
        if (isEndPrompt(conn, trimmedText)) {
            conn.sendTTSState("start")
            return true
        }

        // 使用意图识别器
        val intent = conn.intent?.detectIntent(trimmedText)
        if (intent != null) {
            handleIntentResult(conn, intent, trimmedText)
            return true
        }

        return false
    }

    private fun isExitCommand(conn: ConnectionHandler, text: String): Boolean {
        return conn.exitCommands.any { text.equals(it, ignoreCase = true) }
    }

    private fun isWakeupWord(conn: ConnectionHandler, text: String): Boolean {
        return conn.wakeupWords.any { text.contains(it, ignoreCase = true) }
    }

    private fun isEndPrompt(conn: ConnectionHandler, text: String): Boolean {
        val endPromptText = conn.endPrompt?.get("prompt") as? String
        return endPromptText != null && text == endPromptText
    }

    private suspend fun handleIntentResult(
        conn: ConnectionHandler,
        intent: IntentResult,
        originalText: String
    ) {
        when (intent.name) {
            "play_music" -> {
                val song = intent.parameters["song"] as? String ?: "默认歌曲"
                val responseText = "正在播放：$song"
                conn.sendSTTMessage(responseText)
                // TODO: 实际播放音乐
            }
            "get_weather" -> {
                val location = intent.parameters["location"] as? String ?: "广州"
                val responseText = "$location 今天天气晴朗，气温 25°C"
                conn.sendSTTMessage(responseText)
                // TODO: 实际查询天气
            }
            else -> {
                // 其他意图，交给 LLM 处理
                conn.handlerScope.launch {
                    conn.chat(originalText)
                }
            }
        }
    }
}
