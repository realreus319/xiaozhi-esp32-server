package com.xiaozhi.server.core.api

import com.xiaozhi.server.core.utils.AuthToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.net.InetAddress
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import java.util.Collections.emptyMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ---------- 配置类 ----------

data class ServerConfig(
    val authKey: String,
    val port: Int = 8000,
    val httpPort: Int = 8003,
    val timezoneOffset: Int = 8,
    val websocket: String = "",
    val mqttGateway: String = "",
    val mqttSignatureKey: String = "",
    val auth: AuthConfig = AuthConfig()
)

data class AuthConfig(
    val enabled: Boolean = false,
    val allowedDevices: Set<String> = emptySet(),
    val expireSeconds: Long? = null
)

data class AppConfig(
    val server: ServerConfig,
    val firmwareCacheTtl: Long = 30
)

// ---------- 基础Handler ----------

open class BaseHandler(val config: AppConfig) {
    val logger = LoggerFactory.getLogger(this::class.java)

    fun addCorsHeaders(responseHeaders: HttpHeaders) {
        responseHeaders.add("Access-Control-Allow-Headers", "client-id, content-type, device-id, authorization")
        responseHeaders.add("Access-Control-Allow-Credentials", "true")
        responseHeaders.add("Access-Control-Allow-Origin", "*")
        responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }
}

// ---------- OTAHandler ----------

data class BinCacheInfo(
    var updated_at: Long, // timestamp
    var ttl: Long, // seconds
    val files_by_model: MutableMap<String, List<Pair<String, String>>> = emptyMap(),  // { model: [(version, filename), ...] }
)

@Component
@RestController
@RequestMapping("/xiaozhi/ota")
class OTAHandler(
    val appConfig: AppConfig,
    val objectMapper: JsonMapper
) : BaseHandler(appConfig) {

    private val binDir: String = Paths.get(System.getProperty("user.dir"), "data", "bin").toString()
    // # cache structure: { 'updated_at': timestamp, 'ttl': seconds, 'files_by_model': { model: [(version, filename), ...] } }
    private val binCache = BinCacheInfo(
        updated_at = 0,
        ttl = appConfig.firmwareCacheTtl,
        files_by_model = emptyMap()
    )
    private var binCacheUpdatedAt: Long = 0

    // -------- 辅助函数 --------
    private fun safeBasename(filename: String): String = File(filename).name

    private fun parseVersion(ver: String): List<Int> =
        Regex("\\d+").findAll(ver).map { it.value.toInt() }.toList().ifEmpty { listOf(0) }

    private fun isHigherVersion(a: String, b: String): Boolean {
        val ta = parseVersion(a)
        val tb = parseVersion(b)
        val maxLen = maxOf(ta.size, tb.size)
        for (i in 0 until maxLen) {
            val ai = if (i < ta.size) ta[i] else 0
            val bi = if (i < tb.size) tb[i] else 0
            if (ai > bi) return true
            if (ai < bi) return false
        }
        return false
    }

    private fun refreshBinCacheIfNeeded() {
        val now = Instant.now().epochSecond
        if (now - binCache.updated_at < binCache.ttl && binCache.files_by_model.isEmpty()) return

        val filesByModel = mutableMapOf<String, MutableList<Pair<String, String>>>()
        val dir = File(binDir)
        if (!dir.exists()) dir.mkdirs()

        dir.listFiles { f -> f.extension == "bin" }?.forEach { file ->
            val m = Regex("^(.+?)_([0-9][A-Za-z0-9._-]*)\\.bin$").find(file.name)
            if (m != null) {
                val model = m.groupValues[1]
                val version = m.groupValues[2]
                filesByModel.computeIfAbsent(model) { mutableListOf() }.add(version to file.name)
            }
        }
        filesByModel.values.forEach { it.sortByDescending { pair -> parseVersion(pair.first) } }
        binCache.clear()
        binCache.putAll(filesByModel)
        binCacheUpdatedAt = now
        logger.info("Firmware cache refreshed: ${binCache.size} models")
    }

    private fun generatePasswordSignature(content: String, secretKey: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val hash = mac.doFinal(content.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(hash)
        } catch (e: Exception) {
            logger.error("生成MQTT密码签名失败: ${e.message}")
            ""
        }
    }

    private fun getLocalIp(): String = InetAddress.getLocalHost().hostAddress

    private fun getWebsocketUrl(localIp: String, port: Int): String =
        if (appConfig.server.websocket.contains("你的")) "ws://$localIp:$port/xiaozhi/v1/"
        else appConfig.server.websocket

    // -------- OTA GET --------
    @GetMapping
    suspend fun handleGet(): ResponseEntity<String> {
        val websocketUrl = getWebsocketUrl(getLocalIp(), appConfig.server.port)
        val message = "OTA接口运行正常，向设备发送的websocket地址是：$websocketUrl"
        val headers = HttpHeaders()
        addCorsHeaders(headers)
        return ResponseEntity.ok().headers(headers).body(message)
    }

    // -------- OTA POST --------
    @PostMapping
    suspend fun handlePost(@RequestHeader headers: Map<String, String>, @RequestBody body: String?): ResponseEntity<String> {
        val responseHeaders = HttpHeaders()
        addCorsHeaders(responseHeaders)
        val mapper = objectMapper
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = headers["device-id"] ?: throw Exception("OTA请求设备ID为空")
                val clientId = headers["client-id"] ?: throw Exception("OTA请求ClientID为空")
                refreshBinCacheIfNeeded()

                val bodyJson = body?.let { mapper.readTree(it) }

                // 模型
                val deviceModel = headers["device-model"] ?: bodyJson?.path("model")?.asText() ?: "default"
                val deviceVersion = headers["device-version"] ?: bodyJson?.path("application")?.path("version")?.asText() ?: "0.0.0"

                val returnJson = mutableMapOf<String, Any>(
                    "server_time" to mapOf(
                        "timestamp" to Instant.now().toEpochMilli(),
                        "timezone_offset" to appConfig.server.timezoneOffset * 60
                    ),
                    "firmware" to mutableMapOf(
                        "version" to deviceVersion,
                        "url" to ""
                    )
                )

                // 查找固件更新
                val candidates = binCache[deviceModel] ?: emptyList()
                for ((ver, fname) in candidates) {
                    if (isHigherVersion(ver, deviceVersion)) {
                        val visionUrl = getVisionUrl(appConfig)
                        (returnJson["firmware"] as MutableMap<String, Any>)["version"] = ver
                        (returnJson["firmware"] as MutableMap<String, Any>)["url"] =
                            visionUrl.replace("/mcp/vision/explain", "/xiaozhi/ota/download/$fname")
                        break
                    }
                }

                ResponseEntity.ok().headers(responseHeaders).body(mapper.writeValueAsString(returnJson))
            } catch (e: Exception) {
                logger.error("OTA POST处理异常: ${e.message}")
                val errJson = mapOf("success" to false, "message" to e.message)
                ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(responseHeaders).body(mapper.writeValueAsString(errJson))
            }
        }
    }

    // -------- OTA 下载 --------
    @GetMapping("/download/{filename}")
    suspend fun handleDownload(@PathVariable filename: String): ResponseEntity<FileSystemResource> {
        val headers = HttpHeaders()
        addCorsHeaders(headers)
        return try {
            val safeName = safeBasename(filename)
            if (!Regex("^[A-Za-z0-9._-]+\\.bin\$").matches(safeName)) throw Exception("invalid filename")
            val file = File(binDir, safeName)
            if (!file.exists() || !file.isFile) throw Exception("file not found")
            ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_OCTET_STREAM).body(FileSystemResource(file))
        } catch (e: Exception) {
            logger.error("固件下载异常: ${e.message}")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).build()
        }
    }
}

// ---------- VisionHandler ----------

@Component
@RestController
@RequestMapping("/xiaozhi/vision")
class VisionHandler(
    val appConfig: AppConfig,
    val objectMapper: JsonMapper
) : BaseHandler(appConfig) {

    // 假设AuthToken是你自己的工具类
        val auth = AuthToken(appConfig.server.authKey)
    val MAX_FILE_SIZE = 5 * 1024 * 1024

    private fun createErrorResponse(message: String) = mapOf("success" to false, "message" to message)

    private fun verifyAuthToken(authHeader: String?): Pair<Boolean, String?> {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false to null
        val token = authHeader.removePrefix("Bearer ")
        return auth.verifyToken(token)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun handlePost(@RequestHeader headers: Map<String, String>, @RequestParam("file") fileBytes: ByteArray, @RequestParam("question") question: String): ResponseEntity<String> {
        val responseHeaders = HttpHeaders()
        addCorsHeaders(responseHeaders)
        val mapper = objectMapper
        return try {
            val (isValid, tokenDeviceId) = verifyAuthToken(headers["Authorization"])
            if (!isValid) throw Exception("无效的认证token或token已过期")

            val deviceId = headers["Device-Id"] ?: throw Exception("缺少设备ID")
            val clientId = headers["Client-Id"] ?: throw Exception("缺少客户端ID")
            if (deviceId != tokenDeviceId) throw Exception("设备ID与token不匹配")

            if (fileBytes.size > MAX_FILE_SIZE) throw Exception("图片大小超过限制")
            val imageBase64 = Base64.getEncoder().encodeToString(fileBytes)

            // 模拟调用vllm分析
            val result = "分析结果" // TODO: 替换为 vllm.response(question, imageBase64)

            val returnJson = mapOf("success" to true, "action" to "RESPONSE", "response" to result)
            ResponseEntity.ok().headers(responseHeaders).body(mapper.writeValueAsString(returnJson))
        } catch (e: Exception) {
            logger.error("MCP Vision POST异常: ${e.message}")
            val errJson = createErrorResponse(e.message ?: "处理请求时发生错误")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(responseHeaders).body(mapper.writeValueAsString(errJson))
        }
    }

    @GetMapping
    suspend fun handleGet(): ResponseEntity<String> {
        val headers = HttpHeaders()
        addCorsHeaders(headers)
        return try {
            val visionUrl = getVisionUrl(appConfig)
            val message = if (visionUrl.isNotBlank() && visionUrl != "null") {
                "MCP Vision 接口运行正常，视觉解释接口地址是：$visionUrl"
            } else {
                "MCP Vision 接口运行不正常，请检查配置"
            }
            ResponseEntity.ok().headers(headers).body(message)
        } catch (e: Exception) {
            logger.error("MCP Vision GET异常: ${e.message}")
            val errJson = createErrorResponse("服务器内部错误")
            val mapper = jacksonObjectMapper()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(headers).body(mapper.writeValueAsString(errJson))
        }
    }
}

// ---------- 工具函数示例 ----------
fun getVisionUrl(config: AppConfig): String = config.server.websocket // TODO: 替换为真实逻辑

