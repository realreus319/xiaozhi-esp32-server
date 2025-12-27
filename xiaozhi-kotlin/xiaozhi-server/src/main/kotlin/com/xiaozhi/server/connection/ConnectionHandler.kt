package com.xiaozhi.server.connection

import com.xiaozhi.common.dto.DialogueMessage
import com.xiaozhi.common.dto.TTSMessageDTO
import com.xiaozhi.server.core.dialogue.DialogueManager
import com.xiaozhi.server.core.handler.AudioHandlerImpl
import com.xiaozhi.server.core.handler.IntentHandlerImpl
import com.xiaozhi.server.core.handler.TextHandlerImpl
import com.xiaozhi.server.core.providers.ASRProvider
import com.xiaozhi.server.core.providers.IntentProvider
import com.xiaozhi.server.core.providers.LLMProvider
import com.xiaozhi.server.core.providers.MemoryProvider
import com.xiaozhi.server.core.providers.TTSProvider
import com.xiaozhi.server.core.providers.VADProvider
import com.xiaozhi.server.core.rate.AudioRateController
import com.xiaozhi.server.plugins.PluginFunctionExecutor
import com.xiaozhi.server.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.socket.WebSocketSession
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.xiaozhi.server.core.providers.Message as ProviderMessage

class ConnectionHandler(
    val sessionId: String,
    val deviceId: String,
    val clientId: String?,
    val session: WebSocketSession,
    private val objectMapper: JsonMapper
) {
    private val logger = LoggerFactory.getLogger(ConnectionHandler::class.java)
    
    val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        const val MAX_DEPTH = 5
        const val PRE_BUFFER_COUNT = 5
        const val MAX_TIMESTAMP_BUFFER_SIZE = 20
    }

    // 音频队列 - 使用 Channel 替代 Python 的 queue.Queue
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val textQueue = Channel<String>(Channel.UNLIMITED)
    
    // 上报队列
    private val reportQueue = Channel<ReportItem>(Channel.UNLIMITED)

    // 状态标志
    private val isStopped = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)
    private val clientIsSpeaking = AtomicBoolean(false)
    var listenMode = "auto"

    // 时间戳 - 使用毫秒时间戳
    private val firstActivityTime = AtomicLong(System.currentTimeMillis())
    private val lastActivityTime = AtomicLong(System.currentTimeMillis())

    // VAD 相关
    private var clientAudioBuffer = mutableListOf<Byte>()
    private var clientHaveVoice = false
    private val clientVoiceWindow = ArrayDeque<Boolean>(5)
    private var clientVoiceStop = false
    private var lastIsVoice = false

    // ASR 相关
    var asrAudio = mutableListOf<Byte>()
    var asrVoiceprintBuffer = ConcurrentHashMap<String, MutableList<Byte>>()

    // LLM 相关
    private var llmFinishTask = AtomicBoolean(true)
    private var currentSentenceId: String? = null
    private var sentenceIdCounter = 0

    // TTS 相关
    private var ttsMessageText = ""

    // 提供商
    var vad: VADProvider? = null
    var asr: ASRProvider? = null
    var llm: LLMProvider? = null
    var tts: TTSProvider? = null
    var memory: MemoryProvider? = null
    var intent: IntentProvider? = null

    // 音频速率控制器
    private var audioRateController: AudioRateController? = null
    private var audioFlowControl: AudioFlowControl? = null

    // 对话管理
    private val dialogueManager = DialogueManager()
    val dialogue: DialogueManager
        get() = dialogueManager

    // 设备绑定
    var needBind: Boolean = false
    var bindCode: String? = null
    private val bindCompletedEvent = CompletableDeferred<Unit>()
    private var lastBindPromptTime = AtomicLong(0)
    private val bindPromptInterval = 60L // 秒

    // 配置
    var exitCommands: List<String> = listOf("退出", "关闭", "exit", "quit")
    var wakeupWords: List<String> = listOf("你好小智", "嘿你好呀", "你好小志")
    var maxOutputSize: Int = 0
    var closeAfterChat: Boolean = false
    var closeConnectionNoVoiceTime: Int = 120
    private val timeoutSeconds: Int = closeConnectionNoVoiceTime + 60

    // MQTT Gateway 相关
    var connFromMqttGateway = false
    private val audioTimestampBuffer = ConcurrentHashMap<Long, ByteArray>()
    private var lastProcessedTimestamp = 0L
    private val maxTimestampBufferSize = MAX_TIMESTAMP_BUFFER_SIZE

    // 插件执行器
    private var pluginExecutor: PluginFunctionExecutor? = null

    // 超时任务
    private var timeoutJob: Job? = null

    // 客户端中止标志
    var clientAbort = AtomicBoolean(false)

    // 上报配置
    var reportAsrEnabled = false
    var reportTtsEnabled = false
    var chatHistoryConf = 0

    // VAD 恢复任务状态
    var justWokenUp = false
    var vadResumeTaskActive = false

    // 结束提示配置
    var endPrompt: Map<String, Any>? = null

    // 当前说话人
    var currentSpeaker: String? = null

    init {
        startAudioProcessing()
        startTextProcessing()
        startReportWorker()
        startTimeoutCheck()
    }

    suspend fun handleAudio(audioData: ByteArray) {
        if (isStopped.get()) return

        updateLastActivityTime()

        // 检查是否需要绑定
        if (!bindCompletedEvent.isCompleted) {
            bindCompletedEvent.await()
        }

        if (needBind) {
            checkBindDevicePrompt()
            return
        }

        // 处理来自 MQTT Gateway 的音频包
        if (connFromMqttGateway && audioData.size >= 16) {
            val handled = processMqttAudioMessage(audioData)
            if (handled) return
        }

        // 不需要头部处理或没有头部时，直接处理原始消息
        audioQueue.trySend(audioData).getOrThrow()
    }

    suspend fun handleText(text: String) {
        if (isStopped.get()) return

        updateLastActivityTime()

        // 检查是否需要绑定
        if (!bindCompletedEvent.isCompleted) {
            bindCompletedEvent.await()
        }

        if (needBind) {
            checkBindDevicePrompt()
            return
        }

        // 发送 STT 消息
        sendSTTMessage(text)

        // 首先进行意图分析
        val intentHandler = IntentHandlerImpl()
        val intentHandled = intentHandler.handleIntent(this, text)

        if (intentHandled) {
            // 意图已被处理，不再进行聊天
            return
        }

        // 意图未被处理，继续常规聊天流程
        handlerScope.launch {
            chat(text)
        }
    }

    fun chat(string: String){
    }

    private fun processMqttAudioMessage(message: ByteArray): Boolean {
        return try {
            // 提取头部信息 (big endian)
            val timestamp = ByteBuffer.wrap(message, 8, 4).order(ByteOrder.BIG_ENDIAN).getLong()
            val audioLength = ByteBuffer.wrap(message, 12, 4).order(ByteOrder.BIG_ENDIAN).getInt()
            
            // 提取音频数据
            val audioData = if (audioLength > 0 && message.size >= 16 + audioLength) {
                message.copyOfRange(16, 16 + audioLength)
            } else if (message.size > 16) {
                message.copyOfRange(16, message.size)
            } else {
                return false
            }
            
            // 基于时间戳进行排序处理
            if (timestamp >= lastProcessedTimestamp) {
                // 时间戳递增，直接处理
                val success = try {
                    audioQueue.trySend(audioData).getOrThrow()
                    lastProcessedTimestamp = timestamp
                    true
                } catch (e: Exception) {
                    logger.error("Failed to add audio to queue: ${e.message}", e)
                    false
                }
                
                if (success) {
                    // 处理后续包
                    while (true) {
                        val ts = audioTimestampBuffer.keys.sorted().find { it > lastProcessedTimestamp }
                        if (ts != null) {
                            val bufferedAudio = audioTimestampBuffer.remove(ts)
                            if (bufferedAudio != null) {
                                audioQueue.trySend(bufferedAudio).getOrThrow()
                                lastProcessedTimestamp = ts
                            }
                        } else {
                            // 缓冲区无数据，退出
                            break
                        }
                    }
                }
            } else {
                // 乱序包，暂存
                if (audioTimestampBuffer.size < maxTimestampBufferSize) {
                    audioTimestampBuffer[timestamp] = audioData
                } else {
                    // 缓冲区满，直接处理
                    audioQueue.trySend(audioData).getOrThrow()
                }
            }
            
            true
        } catch (e: Exception) {
            logger.error("解析 MQTT 音频包失败: ${e.message}", e)
            false
        }
    }

    private fun startAudioProcessing() {
        val audioHandler = AudioHandlerImpl()
        
        handlerScope.launch {
            for (audioData in audioQueue) {
                if (isStopped.get()) break
                
                try {
                    // VAD 检测
                    val vadResult = vad?.detect(audioData)
                    val hasVoice = vadResult?.hasVoice ?: true
                    
                    // 更新语音窗口
                    clientVoiceWindow.addLast(hasVoice)
                    if (clientVoiceWindow.size > 5) {
                        clientVoiceWindow.removeFirst()
                    }
                    
                    // 检测语音停止
                    if (!hasVoice && clientHaveVoice && clientVoiceWindow.all { !it }) {
                        clientVoiceStop = true
                    }
                    
                    clientHaveVoice = hasVoice
                    
                    // 处理音频
                    audioHandler.handleAudio(this@ConnectionHandler, audioData, hasVoice)
                    
                } catch (e: Exception) {
                    logger.error("Error processing audio: ${e.message}", e)
                }
            }
        }
    }

    private fun startTextProcessing() {
        val textHandler = TextHandlerImpl()
        
        handlerScope.launch {
            for (text in textQueue) {
                if (isStopped.get()) break
                
                try {
                    textHandler.handleText(this@ConnectionHandler, text)
                } catch (e: Exception) {
                    logger.error("Error processing text: ${e.message}", e)
                }
            }
        }
    }

    private fun startReportWorker() {
        handlerScope.launch {
            while (!isStopped.get()) {
                try {
                    val item = reportQueue.receive()
                    
                    // TODO: 实现上报逻辑
                    logger.debug("Processing report: type=${item.type}, text=${item.text}")
                } catch (e: Exception) {
                    logger.error("Report worker error: ${e.message}", e)
                }
            }
        }
    }

    private fun startTimeoutCheck() {
        timeoutJob = handlerScope.launch {
            while (!isStopped.get()) {
                delay(10000) // 每 10 秒检查一次
                
                val lastActivity = if (needBind) {
                    firstActivityTime.get()
                } else {
                    lastActivityTime.get()
                }
                
                if (lastActivity > 0) {
                    val inactiveTime = System.currentTimeMillis() - lastActivity
                    if (inactiveTime > timeoutSeconds * 1000L) {
                        if (!isStopped.get()) {
                            logger.info("Connection timeout, closing...")
                            close()
                            break
                        }
                    }
                }
            }
        }
    }

    private fun checkBindDevicePrompt() {
        val currentTime = System.currentTimeMillis() / 1000
        if (currentTime - lastBindPromptTime.get() >= bindPromptInterval) {
            lastBindPromptTime.set(currentTime)
            handlerScope.launch {
                try {
                    // TODO: 播放绑定提示音
                    logger.info("播放绑定提示")
                } catch (e: Exception) {
                    logger.error("Error playing bind prompt: ${e.message}", e)
                }
            }
        }
    }

    fun generateSentenceId(): String {
        sentenceIdCounter++
        currentSentenceId = "sentence_${sessionId}_$sentenceIdCounter"
        return currentSentenceId ?: ""
    }

    suspend fun sendText(text: String) {
        try {
            val message = mapOf(
                "type" to "text",
                "session_id" to sessionId,
                "text" to text,
                "role" to "assistant"
            )
            val payload = objectMapper.writeValueAsString(message)
            session.sendText(payload)
        } catch (e: Exception) {
            logger.error("Error sending text: ${e.message}", e)
        }
    }

    suspend fun sendTTSMessage(message: TTSMessageDTO) {
        try {
            val payload = objectMapper.writeValueAsString(message)
            session.sendText(payload)
        } catch (e: Exception) {
            logger.error("Error sending TTS message: ${e.message}", e)
        }
    }

    suspend fun sendSTTMessage(text: String) {
        try {
            val message = mapOf(
                "type" to "stt",
                "session_id" to sessionId,
                "text" to text
            )
            val payload = objectMapper.writeValueAsString(message)
            session.sendText(payload)
        } catch (e: Exception) {
            logger.error("Error sending STT message: ${e.message}", e)
        }
    }

    suspend fun sendTTSState(state: String, text: String? = null) {
        try {
            val message = mutableMapOf(
                "type" to "tts",
                "state" to state,
                "session_id" to sessionId
            )
            if (text != null) {
                message["text"] = text
            }
            val payload = objectMapper.writeValueAsString(message)
            session.sendText(payload)
        } catch (e: Exception) {
            logger.error("Error sending TTS state: ${e.message}", e)
        }
    }

    suspend fun synthesizeAndSendAudio(text: String) {
        isSpeaking.set(true)
        clientIsSpeaking.set(true)
        try {
            val audioFlow = tts?.synthesizeStream(flowOf(text))
                ?: flow { null }
            
            audioFlow.collect { audioData ->
                if (audioData != null && !clientAbort.get()) {
                    sendAudioPacket(audioData)
                }
            }
        } finally {
            isSpeaking.set(false)
            clientIsSpeaking.set(false)
        }
    }

    suspend fun sendAudioPacket(audioData: ByteArray) {
        try {
            if (connFromMqttGateway) {
                // MQTT Gateway 模式，添加 16 字节头部
                sendToMqttGateway(audioData)
            } else {
                // 直接发送 opus 数据包
                session.sendBuffer(audioData)
            }
            updateLastActivityTime()
        } catch (e: Exception) {
            logger.error("Error sending audio packet: ${e.message}", e)
        }
    }

    private suspend fun sendToMqttGateway(opusPacket: ByteArray) {
        try {
            val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            
            // header.put(0x01) // version = 1, header size = 1
            // header.putShort(0x02) // message_type = 2 (audio frame)
            // header.putShort(0x03) // serial_method = 1 (raw), compression_type = 1 (gzip)
            
            // payload length (4 bytes, big endian)
            header.putShort(12, opusPacket.size.toShort())
            // opus length (4 bytes, big endian)
            header.putInt(16, opusPacket.size) // opus length
            
            // timestamp (8 bytes, big endian) - 使用毫秒时间戳
            val timestamp = (System.currentTimeMillis() % (2L shl 32)).toInt()
            header.putInt(20, timestamp)
            
            // sequence (4 bytes, big endian)
            val currentSeq = (audioFlowControl?.sequence ?: 0) + 1
            header.putInt(24, currentSeq)
            audioFlowControl?.sequence = currentSeq
            audioFlowControl?.packetCount = 0
            
            val completePacket = ByteArray(16 + opusPacket.size)
            System.arraycopy(header.array(), 0, completePacket, 0, 16)
            System.arraycopy(opusPacket, 0, completePacket, 16, opusPacket.size)
            
            session.sendBuffer(completePacket)
            
            // 更新序列号
            audioFlowControl?.let { audioFlowControl?.sequence++ }
        } catch (e: Exception) {
            logger.error("Error sending to MQTT gateway: ${e.message}", e)
        }
    }

    suspend fun abortSpeaking() {
        clientAbort.set(true)
        
        // 清空音频队列
        while (audioQueue.tryReceive() != null) {
            // drain queue
        }
        
        logger.info("Aborting current speech")
        clearSpeakStatus()
    }

    fun clearSpeakStatus() {
        isSpeaking.set(false)
        clientIsSpeaking.set(false)
        logger.debug("Clear speaking status")
    }

    fun updateLastActivityTime() {
        lastActivityTime.set(System.currentTimeMillis())
    }

    fun getDialogue(): List<DialogueMessage> {
        return dialogueManager.getDialogue(sessionId)
    }

    fun addDialogueMessage(message: DialogueMessage) {
        dialogueManager.addMessage(sessionId, message)
    }

    fun addUserMessage(text: String) {
        dialogueManager.addUserMessage(sessionId, text)
    }

    fun addAssistantMessage(text: String) {
        dialogueManager.addAssistantMessage(sessionId, text)
    }

    fun addProviderMessage(message: ProviderMessage) {
        dialogueManager.addProviderMessage(sessionId, message)
    }

    fun resetVadStates() {
        clientAudioBuffer.clear()
        clientHaveVoice = false
        clientVoiceWindow.clear()
        clientVoiceStop = false
        logger.debug("VAD states reset")
    }

    suspend fun close() {
        if (!isStopped.compareAndSet(false, true)) return

        try {
            // 清空队列
            audioQueue.close()
            textQueue.close()
            reportQueue.close()
            audioTimestampBuffer.clear()
            
            // 取消超时任务
            timeoutJob?.cancel()
            
            // 停止音频速率控制器
            audioRateController?.stop()
            
            // 关闭提供商
            vad?.close()
            asr?.close()
            llm?.close()
            tts?.close()
            memory?.close()
            intent?.close()
            pluginExecutor?.cleanup()
            
            // 取消协程作用域
            handlerScope.cancel()
            
            logger.info("Connection closed: $sessionId")
        } catch (e: Exception) {
            logger.error("Error closing connection: ${e.message}", e)
        }
    }

    fun setProviders(
        vad: VADProvider?,
        asr: ASRProvider?,
        llm: LLMProvider?,
        tts: TTSProvider?,
        memory: MemoryProvider?,
        intent: IntentProvider?
    ) {
        this.vad = vad
        this.asr = asr
        this.llm = llm
        this.tts = tts
        this.memory = memory
        this.intent = intent
        
        // 初始化插件执行器
        pluginExecutor = PluginFunctionExecutor(this)
        handlerScope.launch {
            pluginExecutor?.initialize()
        }
    }

    fun updateConfig(config: Map<String, Any>) {
        config["exit_commands"]?.let {
            @Suppress("UNCHECKED_CAST")
            exitCommands = it as List<String>
        }
        config["wakeup_words"]?.let {
            @Suppress("UNCHECKED_CAST")
            wakeupWords = it as List<String>
        }
        config["max_output_size"]?.let {
            @Suppress("UNCHECKED_CAST")
            maxOutputSize = (it as Number).toInt()
        }
        config["close_connection_no_voice_time"]?.let {
            @Suppress("UNCHECKED_CAST")
            closeConnectionNoVoiceTime = (it as Number).toInt()
        }
        config["end_prompt"]?.let {
            @Suppress("UNCHECKED_CAST")
            endPrompt = it as? Map<String, Any>
        }
    }

    // 内部数据类
    data class AudioFlowControl(
        var packetCount: Int = 0,
        var sequence: Int = 0,
        var sentenceId: String? = null
    )

    data class ReportItem(
        val type: String,
        val text: String,
        val audioData: ByteArray? = null,
        val reportTime: Long = System.currentTimeMillis()
    )
}
