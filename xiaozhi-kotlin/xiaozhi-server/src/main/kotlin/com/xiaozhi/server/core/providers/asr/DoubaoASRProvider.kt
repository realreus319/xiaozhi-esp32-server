package com.xiaozhi.server.core.providers.asr

import com.xiaozhi.server.connection.ConnectionHandler
import com.xiaozhi.server.core.providers.ASRProvider
import com.xiaozhi.server.core.providers.InterfaceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import org.springframework.web.reactive.socket.client.WebSocketClient
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import tools.jackson.datatype.jsr310.JavaTimeModule
import tools.jackson.module.kotlin.KotlinModule
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream as JavaGZIPInputStream

class DoubaoASRProvider : ASRProvider {
    private val logger = LoggerFactory.getLogger(DoubaoASRProvider::class.java)
    val objectMapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .addModule(JavaTimeModule())
        .build()
    
    private lateinit var webSocketClient: WebSocketClient
    private val sampleRate = 16000
    private val channels = 1
    
    // 配置参数
    private var appId: String = ""
    private var accessToken: String = ""
    private var cluster: String = ""
    private var uid: String = "streaming_asr_service"
    private var workflow: String = "audio_in,resample,partition,vad,fe,decode,itn,nlu_punctuate"
    private var resultType: String = "single"
    private var format: String = "pcm"
    private var codec: String = "pcm"
    private var language: String = "zh-CN"
    private var bits: Int = 16
    private var endWindowSize: Int = 200
    private var boostingTableName: String = ""
    private var correctTableName: String = ""
    private var outputDir: String = "tmp"
    private var deleteAudioFile: Boolean = true
    
    // WebSocket 会话
    private var wsSession: WebSocketSession? = null
    private var isProcessing = false
    private var currentText = ""
    private var forwardJob: Job? = null
    
    // Opus 解码器（简化版，实际需要 JNI 调用）
    // private var opusDecoder: OpusDecoder? = null
    
    private var isInitialized = false

    override suspend fun initialize() {
        // TODO: 从配置加载参数
        appId = "your_appid"
        accessToken = "your_access_token"
        cluster = "volcengine_input_common"
        
        // 初始化 WebSocket 客户端
        webSocketClient = ReactorNettyWebSocketClient()
        
        isInitialized = true
        logger.info("Doubao ASR provider initialized")
    }

    override suspend fun openAudioChannels() {
        if (!isInitialized) {
            throw Exception("ASR provider not initialized")
        }
        logger.info("Doubao ASR audio channels opened")
    }

    override suspend fun close() {
        try {
            forwardJob?.cancel()
            wsSession?.close()
            isProcessing = false
            logger.info("Doubao ASR provider closed")
        } catch (e: Exception) {
            logger.error("Error closing Doubao ASR: ${e.message}", e)
        }
    }

    override suspend fun transcribe(audioData: ByteArray): String? {
        if (!isInitialized) {
            throw Exception("ASR provider not initialized")
        }
        return currentText
    }

    override suspend fun receiveAudio(conn: ConnectionHandler, audioData: ByteArray, hasVoice: Boolean) {
        if (!isInitialized) {
            throw Exception("ASR provider not initialized")
        }

        // 缓存音频数据
        conn.asrAudio.addAll(audioData.toList())
        
        // 保持最近的 10 个音频包
        if (conn.asrAudio.size > 10 * 960) { // 960 samples per packet at 16kHz
            conn.asrAudio = conn.asrAudio.takeLast(10 * 960).toMutableList()
        }
        
        // 自动/实时模式：使用 VAD 检测
        if (conn.listenMode == "manual") {
            // 手动模式：缓存音频用于 ASR 识别
            if (!conn.asrVoiceprintBuffer.containsKey(conn.sessionId)) {
                conn.asrVoiceprintBuffer[conn.sessionId] = mutableListOf()
            }
            conn.asrVoiceprintBuffer[conn.sessionId]?.addAll(audioData.toList())
        } else {
            // 自动模式
            if (!hasVoice && !conn.clientHaveVoice) {
                if (conn.asrAudio.size > 10 * 960) {
                    conn.asrAudio = conn.asrAudio.takeLast(10 * 960).toMutableList()
                }
                return
            }
            
            // VAD 检测到语音停止时触发识别
            if (conn.clientVoiceStop) {
                val audioTask = conn.asrAudio.toByteArray()
                conn.asrAudio.clear()
                conn.resetVadStates()
                
                if (audioTask.size > 15 * 960) { // 至少 15 个包
                    handleVoiceStop(conn, audioTask)
                }
            }
        }
    }

    override fun transcribeStream(audioFlow: Flow<ByteArray>): Flow<String?> {
        return flow {
            if (!isInitialized) {
                throw Exception("ASR provider not initialized")
            }
            
            audioFlow.collect { audioData ->
                // TODO: 流式发送音频并返回识别结果
                emit(null)
            }
        }
    }

    private suspend fun handleVoiceStop(conn: ConnectionHandler, audioData: ByteArray) {
        try {
            logger.debug("Handling voice stop for session: ${conn.sessionId}, audio size: ${audioData.size}")
            
            // 准备音频数据
            val pcmData = if (format == "pcm") {
                audioData
            } else {
                // TODO: Opus 解码
                audioData
            }
            
            // 执行 ASR 识别
            val result = speechToText(listOf(pcmData), conn.sessionId, format)
            val (rawText, speakerName) = result
            
            if (rawText != null && rawText.isNotBlank()) {
                logger.info("Recognized text: $rawText")
                
                // 构建包含说话人信息的文本
                val enhancedText = if (speakerName != null && speakerName.isNotBlank()) {
                    """{"speaker": "$speakerName", "content": "$rawText"}"""
                } else {
                    rawText
                }
                
                // 发送识别结果到文本队列
                conn.handleText(enhancedText)
            }
        } catch (e: Exception) {
            logger.error("Error handling voice stop: ${e.message}", e)
        }
    }

    private suspend fun speechToText(
        opusData: List<ByteArray>,
        sessionId: String,
        audioFormat: String
    ): Pair<String?, String?> {
        // TODO: 实现实际的 ASR 识别
        // 这里需要建立 WebSocket 连接并发送音频数据
        
        // 简化版：返回空结果
        return null to null
    }

    private fun connectToASR(sessionId: String) {
        // TODO: 建立 WebSocket 连接到 Doubao ASR 服务
        val wsUrl = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"
        
        webSocketClient.execute(URI.create(wsUrl)) { session ->
            // 发送初始化请求
            val requestId = UUID.randomUUID().toString()
            val request = constructRequest(requestId)
            val requestJson = objectMapper.writeValueAsString(request)
            val compressedRequest = compressGzip(requestJson.toByteArray())
            
            val header = generateHeader()
            val fullRequest = ByteBuffer.allocate(header.size + 4 + compressedRequest.size)
                .order(ByteOrder.BIG_ENDIAN)
                .put(header)
                .putInt(compressedRequest.size)
                .put(compressedRequest)
                .array()
            
            session.send(Mono.just(session.binaryMessage(fullRequest))).block()
            
            // 接收响应
            val response = session.receive().timeout(Duration.ofSeconds(30)).blockFirst()
            val responseData = response.payload.asByteBuffer().array()
            val parsedResponse = parseResponse(responseData)
            
            logger.info("ASR init response: $parsedResponse")
            
            true
        }
    }

    private fun constructRequest(requestId: String): Map<String, Any> {
        return mapOf(
            "app" to mapOf(
                "appid" to appId,
                "cluster" to cluster,
                "token" to accessToken
            ),
            "user" to mapOf("uid" to uid),
            "request" to mapOf(
                "reqid" to requestId,
                "workflow" to workflow,
                "show_utterances" to true,
                "result_type" to resultType,
                "sequence" to 1,
                "boosting_table_name" to boostingTableName,
                "correct_table_name" to correctTableName,
                "end_window_size" to endWindowSize
            ),
            "audio" to mapOf(
                "format" to format,
                "codec" to codec,
                "rate" to sampleRate,
                "language" to language,
                "bits" to bits,
                "channel" to channels,
                "sample_rate" to sampleRate
            )
        )
    }

    private fun generateHeader(
        version: Int = 0x01,
        messageType: Int = 0x01,
        messageTypeSpecificFlags: Int = 0x00,
        serialMethod: Int = 0x01,
        compressionType: Int = 0x01,
        reservedData: Int = 0x00,
        extensionHeader: ByteArray = ByteArray(0)
    ): ByteArray {
        val headerSize = extensionHeader.size / 4 + 1
        val header = ByteArray(headerSize + 4 + extensionHeader.size)
        
        header[0] = ((version shl 4) or headerSize).toByte()
        header[1] = ((messageType shl 4) or messageTypeSpecificFlags).toByte()
        header[2] = ((serialMethod shl 4) or compressionType).toByte()
        header[3] = reservedData.toByte()
        
        if (extensionHeader.isNotEmpty()) {
            System.arraycopy(extensionHeader, 0, header, 4, extensionHeader.size)
        }
        
        return header
    }

    private fun generateAudioHeader(): ByteArray {
        return generateHeader(
            version = 0x01,
            messageType = 0x02,
            messageTypeSpecificFlags = 0x00,
            serialMethod = 0x01,
            compressionType = 0x01
        )
    }

    private fun generateLastAudioHeader(): ByteArray {
        return generateHeader(
            version = 0x01,
            messageType = 0x02,
            messageTypeSpecificFlags = 0x02,  // 结束标记
            serialMethod = 0x01,
            compressionType = 0x01
        )
    }

    private fun parseResponse(response: ByteArray): Map<String, Any> {
        if (response.size < 4) {
            logger.error("Response length too short: ${response.size}")
            return mapOf("error" to "Response length too short")
        }
        
        val header = response.sliceArray(0..3)
        val messageType = (header[1].toInt() shr 4) and 0x0F
        
        // 检查是否是错误响应
        if (messageType == 0x0F) {
            val code = ByteBuffer.wrap(response, 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val msgLength = ByteBuffer.wrap(response, 8, 4).order(ByteOrder.BIG_ENDIAN).int
            val errorMsg = String(response.sliceArray(12 until 12 + msgLength))
            return mapOf(
                "code" to code,
                "msg_length" to msgLength,
                "payload_msg" to errorMsg
            )
        }
        
        // 解析 JSON 数据（跳过 12 字节头部）
        return try {
            val jsonData = String(response.sliceArray(12 until response.size))
            val jsonNode = objectMapper.readTree(jsonData)
            mapOf("payload_msg" to jsonNode)
        } catch (e: Exception) {
            logger.error("Failed to parse response JSON: ${e.message}")
            mapOf("error" to e.message)
        }
    }

    private fun compressGzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(data)
        }
        return bos.toByteArray()
    }

    private fun decompressGzip(data: ByteArray): ByteArray {
        return JavaGZIPInputStream(data.inputStream()).use { gzip ->
            gzip.readAllBytes()
        }
    }

    private fun decodeOpus(opusData: List<ByteArray>): List<ByteArray> {
        // TODO: 实现 Opus 解码
        // 实际需要使用 JNI 调用 libopus
        return opusData
    }

    companion object {
        const val TYPE = "doubao"
        val INTERFACE_TYPE = InterfaceType.REMOTE
    }
}

class FunASRProvider : ASRProvider {
    private val logger = LoggerFactory.getLogger(FunASRProvider::class.java)
    private val modelDir: String = "models/SenseVoiceSmall"
    
    private var isInitialized = false

    override suspend fun initialize() {
        // TODO: 加载本地 FunASR 模型
        logger.info("FunASR provider initialized with model: $modelDir")
        isInitialized = true
    }

    override suspend fun openAudioChannels() {
        if (!isInitialized) {
            throw Exception("ASR provider not initialized")
        }
        logger.info("FunASR audio channels opened")
    }

    override suspend fun close() {
        isInitialized = false
        logger.info("FunASR provider closed")
    }

    override suspend fun transcribe(audioData: ByteArray): String? {
        if (!isInitialized) {
            throw Exception("ASR provider not initialized")
        }
        
        // TODO: 使用本地模型进行识别
        return null
    }

    override fun transcribeStream(audioFlow: Flow<ByteArray>): Flow<String?> {
        return flow {
            if (!isInitialized) {
                throw Exception("ASR provider not initialized")
            }
            
            audioFlow.collect { audioData ->
                // TODO: 使用本地模型流式识别
                emit(null)
            }
        }
    }

    override suspend fun receiveAudio(conn: ConnectionHandler, audioData: ByteArray, hasVoice: Boolean) {
        // 本地 ASR 可以直接处理音频流
        // TODO: 实现本地模型推理
    }

    companion object {
        const val TYPE = "fun_local"
        val INTERFACE_TYPE = InterfaceType.LOCAL
    }
}
