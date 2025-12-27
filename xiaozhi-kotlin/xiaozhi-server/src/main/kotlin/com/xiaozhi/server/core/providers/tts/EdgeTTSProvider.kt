package com.xiaozhi.server.core.providers.tts

import com.xiaozhi.common.dto.*
import com.xiaozhi.server.connection.ConnectionHandler
import com.xiaozhi.server.core.providers.TTSProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class EdgeTTSProvider : TTSProvider {
    private val logger = LoggerFactory.getLogger(EdgeTTSProvider::class.java)
    private val webClient = WebClient.builder().build()
    
    private var voice: String = "zh-CN-XiaoxiaoNeural"
    private var rate: Int = 1
    private var pitch: Int = 1
    private var volume: Int = 50
    private val outputDir: String = "tmp"
    private var deleteAudioFile: Boolean = true
    
    // TTS 队列
    private val ttsTextQueue = Channel<TTSMessageDTO>(Channel.UNLIMITED)
    private val ttsAudioQueue = Channel<Triple<SentenceType, ByteArray?, String?>>(Channel.UNLIMITED)
    
    // 状态标志
    private var isInitialized = false
    private val isProcessing = AtomicBoolean(false)
    private val clientAbort = AtomicBoolean(false)
    private var ttsStopRequest = false
    private var processedChars = 0
    private var isFirstSentence = true
    private val ttsTextBuff = mutableListOf<String>()
    private var ttsAudioFirstSentence = true
    private val beforeStopPlayFiles = mutableListOf<Pair<ByteArray?, String?>>()
    
    // 标点符号
    private val punctuations = listOf("。", "？", "?", "！", "!", "；", ";", "：")
    private val firstSentencePunctuations = listOf("，", "~", "、", ",", "。", "？", "?", "！", "!", "；", ";", "：")
    
    private var processingJob: Job? = null
    private var audioPlayJob: Job? = null
    private var conn: ConnectionHandler? = null

    override suspend fun initialize() {
        logger.info("Edge TTS provider initialized with voice: $voice")
        isInitialized = true
    }

    override suspend fun openAudioChannels(conn: ConnectionHandler) {
        if (!isInitialized) {
            throw Exception("TTS provider not initialized")
        }
        
        this.conn = conn
        
        // 启动 TTS 文本处理线程
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            ttsTextPriorityThread()
        }
        
        // 启动音频播放线程
        audioPlayJob = CoroutineScope(Dispatchers.IO).launch {
            audioPlayPriorityThread()
        }
        
        logger.info("Edge TTS audio channels opened")
    }

    override suspend fun close() {
        clientAbort.set(true)
        ttsStopRequest = true
        
        processingJob?.cancel()
        audioPlayJob?.cancel()
        
        ttsTextQueue.close()
        ttsAudioQueue.close()
        
        isInitialized = false
        logger.info("Edge TTS provider closed")
    }

    override suspend fun synthesize(text: String): ByteArray? {
        if (!isInitialized) {
            throw Exception("TTS provider not initialized")
        }

        val apiUrl = "https://edge.tts.tencentcloudvoice.com/api/v1/TTS"
        
        return try {
            val response = webClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf(
                    "input" to text,
                    "voice" to voice,
                    "rate" to rate,
                    "pitch" to pitch,
                    "volume" to volume
                ))
                .retrieve()
                .bodyToMono(DataBuffer::class.java)
                .map { dataBuffer ->
                    dataBuffer.array()
                }
                .timeout(Duration.ofSeconds(30))
                .block()
        } catch (e: Exception) {
            logger.error("Edge TTS error: ${e.message}", e)
            null
        }
    }

    override suspend fun abort() {
        clientAbort.set(true)
        ttsStopRequest = true
        ttsTextBuff.clear()
        
        logger.info("Edge TTS aborted")
    }

    override fun synthesizeStream(textFlow: Flow<ByteArray?>): Flow<ByteArray?> {
        return flow {
            textFlow.collect { text ->
                if (text != null) {
                    emit(text)
                }
            }
        }
    }

    private suspend fun ttsTextPriorityThread() {
        while (!clientAbort.get()) {
            try {
                val message = withTimeoutOrNull(1000) {
                    ttsTextQueue.receive()
                } ?: continue
                
                if (message.sentenceType == SentenceType.FIRST) {
                    clientAbort.set(false)
                }
                
                if (clientAbort.get()) {
                    logger.info("收到打断信息，终止TTS文本处理线程")
                    continue
                }
                
                when (message.sentenceType) {
                    SentenceType.FIRST -> {
                        ttsStopRequest = false
                        processedChars = 0
                        ttsTextBuff.clear()
                        isFirstSentence = true
                        ttsAudioFirstSentence = true
                    }
                    SentenceType.MIDDLE -> {
                        if (message.contentType == ContentType.TEXT) {
                            ttsTextBuff.add(message.contentDetail ?: "")
                            val segmentText = getSegmentText()
                            if (segmentText != null) {
                                toTtsStream(segmentText)
                            }
                        }
                    }
                    SentenceType.LAST -> {
                        processRemainingTextStream()
                        ttsAudioQueue.send(Triple(SentenceType.LAST, null, message.contentDetail))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                continue
            } catch (e: Exception) {
                logger.error("处理TTS文本失败: ${e.message}", e)
            }
        }
    }

    private suspend fun audioPlayPriorityThread() {
        var enqueueText: String? = null
        var enqueueAudio: MutableList<ByteArray> = mutableListOf()
        
        while (!clientAbort.get()) {
            try {
                val (sentenceType, audioData, text) = withTimeoutOrNull(100) {
                    ttsAudioQueue.receive()
                } ?: continue
                
                if (clientAbort.get()) {
                    logger.debug("收到打断信号，跳过当前音频数据")
                    enqueueText = null
                    enqueueAudio = mutableListOf()
                    continue
                }
                
                // 收到下一个文本开始或会话结束时进行上报
                if (sentenceType != SentenceType.MIDDLE) {
                    if (enqueueText != null && enqueueAudio.isNotEmpty()) {
                        // TODO: 上报 TTS 数据
                        logger.debug("上报 TTS: text=$enqueueText, audio_count=${enqueueAudio.size}")
                    }
                    enqueueAudio = mutableListOf()
                    enqueueText = text
                }
                
                // 收集上报音频数据
                if (audioData != null && enqueueText != null) {
                    enqueueAudio.add(audioData)
                }
                
                // 发送音频
                conn?.let { c ->
                    c.sendTTSMessage(TTSMessageDTO(
                        sentenceId = "sentence_${c.sessionId}",
                        sentenceType = sentenceType,
                        contentType = ContentType.TEXT,
                        contentDetail = text
                    ))
                    
                    if (audioData != null) {
                        c.sendAudioPacket(audioData)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                continue
            } catch (e: Exception) {
                logger.error("audio_play_priority_thread: ${e.message}", e)
            }
        }
    }

    private suspend fun toTtsStream(text: String) {
        val cleanedText = cleanMarkdown(text)
        var maxRepeatTime = 5
        
        if (deleteAudioFile) {
            while (maxRepeatTime > 0) {
                try {
                    val audioBytes = textToSpeak(cleanedText, null)
                    if (audioBytes != null) {
                        ttsAudioQueue.send(Triple(SentenceType.FIRST, null, cleanedText))
                        audioBytesToDataStream(audioBytes, isOpus = true)
                        break
                    } else {
                        maxRepeatTime--
                    }
                } catch (e: Exception) {
                    logger.warn("语音生成失败${5 - maxRepeatTime + 1}次: $cleanedText, 错误: ${e.message}")
                    maxRepeatTime--
                }
            }
        } else {
            // 文件模式暂不实现
        }
    }

    private suspend fun textToSpeak(text: String, outputFile: String?): ByteArray? {
        // TODO: 实际调用 TTS API
        return synthesize(text)
    }

    private suspend fun audioBytesToDataStream(audioBytes: ByteArray, isOpus: Boolean) {
        // TODO: 将音频数据转换为 Opus/PCM 数据流并发送到队列
        // 这里简化处理，直接发送完整音频
        if (isOpus) {
            ttsAudioQueue.send(Triple(SentenceType.MIDDLE, audioBytes, null))
        } else {
            // PCM 分块处理
            val chunkSize = 960 // 60ms at 16kHz
            for (i in audioBytes.indices step chunkSize) {
                val chunk = audioBytes.copyOfRange(i, minOf(i + chunkSize, audioBytes.size))
                ttsAudioQueue.send(Triple(SentenceType.MIDDLE, chunk, null))
                delay(60) // 60ms 延迟
            }
        }
    }

    private fun getSegmentText(): String? {
        val fullText = ttsTextBuff.joinToString("")
        val currentText = fullText.substring(processedChars)
        var lastPunctPos = -1
        
        val punctuationsToUse = if (isFirstSentence) {
            firstSentencePunctuations
        } else {
            punctuations
        }
        
        for (punct in punctuationsToUse) {
            val pos = currentText.lastIndexOf(punct)
            if ((pos != -1 && lastPunctPos == -1) || (pos != -1 && pos < lastPunctPos)) {
                lastPunctPos = pos
            }
        }
        
        if (lastPunctPos != -1) {
            val segmentTextRaw = currentText.substring(0, lastPunctPos + 1)
            val segmentText = getStringNoPunctuationOrEmoji(segmentTextRaw)
            processedChars += segmentTextRaw.length
            
            if (isFirstSentence) {
                isFirstSentence = false
            }
            
            return segmentText
        } else if (ttsStopRequest && currentText.isNotBlank()) {
            val segmentText = currentText
            isFirstSentence = true
            return segmentText
        }
        
        return null
    }

    private suspend fun processRemainingTextStream(): Boolean {
        val fullText = ttsTextBuff.joinToString("")
        val remainingText = fullText.substring(processedChars)
        
        if (remainingText.isNotBlank()) {
            val segmentText = getStringNoPunctuationOrEmoji(remainingText)
            if (segmentText.isNotBlank()) {
                toTtsStream(segmentText)
                processedChars += fullText.length
                return true
            }
        }
        return false
    }

    private fun cleanMarkdown(text: String): String {
        // TODO: 实现 Markdown 清理
        return text
    }

    private fun getStringNoPunctuationOrEmoji(text: String): String {
        // TODO: 实现标点和表情符号过滤
        return text
    }

    companion object {
        const val TYPE = "edge"
    }
}

class DoubaoTTSProvider : TTSProvider {
    private val logger = LoggerFactory.getLogger(DoubaoTTSProvider::class.java)
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    
    private var apiUrl: String = "https://openspeech.bytedance.com/api/v1/tts"
    private var appId: String = ""
    private var accessToken: String = ""
    private var voice: String = "zh_female_wanwanxiaohe_moon_bigtts"
    private var cluster: String = "volcano_tts"
    private var speedRatio: Double = 1.0
    private var volumeRatio: Double = 1.0
    private var pitchRatio: Double = 1.0
    
    private val outputDir: String = "tmp"
    private var deleteAudioFile: Boolean = true
    
    private var isInitialized = false
    private val clientAbort = AtomicBoolean(false)
    private var conn: ConnectionHandler? = null

    override suspend fun initialize() {
        logger.info("Doubao TTS provider initialized with voice: $voice")
        isInitialized = true
    }

    override suspend fun openAudioChannels(conn: ConnectionHandler) {
        if (!isInitialized) {
            throw Exception("TTS provider not initialized")
        }
        this.conn = conn
        logger.info("Doubao TTS audio channels opened")
    }

    override suspend fun close() {
        clientAbort.set(true)
        isInitialized = false
        logger.info("Doubao TTS provider closed")
    }

    override suspend fun synthesize(text: String): ByteArray? {
        if (!isInitialized) {
            throw Exception("TTS provider not initialized")
        }

        return try {
            val response = webClient.post()
                .uri(apiUrl)
                .headers { headers ->
                    headers.set("Authorization", "Bearer; $accessToken")
                }
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf(
                    "app" to mapOf(
                        "appid" to appId,
                        "token" to accessToken,
                        "cluster" to cluster
                    ),
                    "text" to text,
                    "voice" to voice,
                    "speed_ratio" to speedRatio,
                    "volume_ratio" to volumeRatio,
                    "pitch_ratio" to pitchRatio
                ))
                .retrieve()
                .bodyToMono(DataBuffer::class.java)
                .map { dataBuffer ->
                    dataBuffer.array()
                }
                .timeout(Duration.ofSeconds(30))
                .block()
        } catch (e: Exception) {
            logger.error("Doubao TTS error: ${e.message}", e)
            null
        }
    }

    override suspend fun abort() {
        clientAbort.set(true)
        logger.info("Doubao TTS aborted")
    }

    override fun synthesizeStream(textFlow: Flow<ByteArray?>): Flow<ByteArray?> {
        return flow {
            textFlow.collect { text ->
                if (text != null) {
                    emit(text)
                }
            }
        }
    }

    companion object {
        const val TYPE = "doubao"
    }
}
