package com.xiaozhi.server.core.api

import com.xiaozhi.server.core.AuthManager
import getVisionUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Paths
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ---------- OTAHandler ----------

data class BinCacheInfo(
    var updated_at: Long, // timestamp
    var ttl: Long, // seconds
    val files_by_model: MutableMap<String, List<Pair<String, String>>> = mutableMapOf() // { model: [(version, filename), ...] }
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
        files_by_model = mutableMapOf()
    )
    val auth =  AuthManager(appConfig.server.authKey)

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
        if (now - binCache.updated_at < binCache.ttl && binCache.files_by_model.isEmpty()) {
            binCache.updated_at = now
            return
        }

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
        binCache.files_by_model.clear()
        binCache.files_by_model.putAll(filesByModel)
        binCache.updated_at = now
        logger.info("Firmware cache refreshed: ${binCache.files_by_model.size} models")
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

    private fun getLocalIp(): String {
        return try {
            val socket = Socket().apply {
                connect(InetSocketAddress("8.8.8.8", 80))
            }
            val localIp = socket.localAddress.hostAddress
            socket.close()
            localIp
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    private fun getWebsocketUrl(localIp: String, port: Int): String =
        if (appConfig.server.websocket.contains("你的")) "ws://$localIp:$port/xiaozhi/v1/"
        else appConfig.server.websocket

    // -------- OTA GET --------
    @GetMapping
    suspend fun handleGet(): ResponseEntity<String> {
        val serverConfig = appConfig.server
        val localIp = getLocalIp()
        val websocketPort = serverConfig.port
        val websocketUrl = getWebsocketUrl(localIp, websocketPort)
        val message = "OTA接口运行正常，向设备发送的websocket地址是：$websocketUrl"
        val headers = HttpHeaders()
        addCorsHeaders(headers)
        return ResponseEntity.ok().headers(headers).body(message)
    }

    // -------- OTA POST --------
    @PostMapping
    suspend fun handlePost(@RequestHeader headers: Map<String, String>, @RequestBody(required = false) body: String?): ResponseEntity<String> {
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
                var deviceModel = ""
                // header candidates
                for (h in listOf("device-model", "device_model", "model")) {
                    if (headers.containsKey(h)) {
                        deviceModel = headers[h]?.trim() ?: ""
                        break
                    }
                }
                // body fallback
                if (deviceModel.isEmpty()) {
                    try {
                        if (bodyJson != null && bodyJson.has("board") && bodyJson.get("board").isObject) {
                            deviceModel = bodyJson.get("board").get("type").asText("")
                        } else if (bodyJson != null && bodyJson.has("model")) {
                            deviceModel = bodyJson.get("model").asText("")
                        }
                    } catch (e: Exception) {
                        deviceModel = ""
                    }
                }
                if (deviceModel.isEmpty()) {
                    deviceModel = "default"
                }

                // 版本
                var deviceVersion = ""
                for (h in listOf("device-version", "device_version", "firmware-version", "app-version", "application-version")) {
                    if (headers.containsKey(h)) {
                        deviceVersion = headers[h]?.trim() ?: ""
                        break
                    }
                }
                if (deviceVersion.isEmpty()) {
                    try {
                        deviceVersion = bodyJson?.get("application")?.get("version")?.asText() ?: ""
                    } catch (e: Exception) {
                        deviceVersion = ""
                    }
                }
                if (deviceVersion.isEmpty()) {
                    deviceVersion = "0.0.0"
                }

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

                // MQTT/WebSocket逻辑
                val mqttGatewayEndpoint = appConfig.server.mqttGateway
                if (mqttGatewayEndpoint.isNotEmpty()) {
                    var groupId = try {
                        "GID_$deviceModel".replace(":", "_").replace(" ", "_")
                    } catch (e: Exception) {
                        logger.error("获取设备型号失败: ${e.message}")
                        "GID_default"
                    }

                    val macAddressSafe = deviceId.replace(":", "_")
                    val mqttClientId = "$groupId@@@$macAddressSafe@@@$macAddressSafe"

                    // 构建用户数据
                    var username = ""
                    try {
                        val userData = mapOf("ip" to "unknown")
                        val userDataJson = mapper.writeValueAsString(userData)
                        username = Base64.getEncoder().encodeToString(userDataJson.toByteArray(Charsets.UTF_8))
                    } catch (e: Exception) {
                        logger.error("生成用户名失败: ${e.message}")
                        username = ""
                    }

                    // 生成密码
                    var password = ""
                    val signatureKey = appConfig.server.mqttSignatureKey
                    if (signatureKey.isNotEmpty()) {
                        password = generatePasswordSignature("$mqttClientId|$username", signatureKey)
                        if (password.isEmpty()) {
                            password = ""
                        }
                    } else {
                        logger.warn("缺少MQTT签名密钥，密码留空")
                    }

                    returnJson["mqtt"] = mapOf(
                        "endpoint" to mqttGatewayEndpoint,
                        "client_id" to mqttClientId,
                        "username" to username,
                        "password" to password,
                        "publish_topic" to "device-server",
                        "subscribe_topic" to "devices/p2p/$macAddressSafe"
                    )
                    logger.info("为设备 $deviceId 下发MQTT网关配置")
                } else {
                    // WebSocket配置
                    var token = ""
                    val authEnable = appConfig.server.auth.enabled
                    val allowedDevices = appConfig.server.auth.allowedDevices
                    if (authEnable) {
                        if (allowedDevices.isNotEmpty()) {
                            if (deviceId !in allowedDevices) {
                                token = auth.generateToken(clientId, deviceId)
                            }
                        } else {
                            token = auth.generateToken(clientId, deviceId)
                        }
                    }
                    val localIp = getLocalIp()
                    val websocketPort = appConfig.server.port
                    returnJson["websocket"] = mapOf(
                        "url" to getWebsocketUrl(localIp, websocketPort),
                        "token" to token
                    )
                    logger.info("未配置MQTT网关，为设备 $deviceId 下发WebSocket配置")
                }

                // 检查固件更新
                try {
                    refreshBinCacheIfNeeded()
                    val candidates = binCache.files_by_model[deviceModel] ?: emptyList()

                    logger.info("查找型号 $deviceModel 的固件，找到 ${candidates.size} 个候选")

                    var chosenUrl = ""
                    var chosenVersion = deviceVersion

                    for ((ver, fname) in candidates) {
                        if (isHigherVersion(ver, deviceVersion)) {
                            val visionUrl = getVisionUrl(appConfig)
                            chosenVersion = ver
                            chosenUrl = visionUrl.replace("/mcp/vision/explain", "/xiaozhi/ota/download/$fname")
                            break
                        }
                    }

                    if (chosenUrl.isNotEmpty()) {
                        (returnJson["firmware"] as MutableMap<String, Any>)["version"] = chosenVersion
                        (returnJson["firmware"] as MutableMap<String, Any>)["url"] = chosenUrl
                        logger.info("为设备 $deviceId 下发固件 $chosenVersion [如果地址前缀有误，请检查配置文件中的server.vision_explain]-> $chosenUrl ")
                    } else {
                        logger.info("设备 $deviceId 固件已是最新: $deviceVersion")
                    }
                } catch (e: Exception) {
                    logger.error("检查固件版本时出错: ${e.message}")
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
            if (!Regex("^[A-Za-z0-9._-]+\\.bin$").matches(safeName)) throw Exception("invalid filename")
            val file = File(binDir, safeName)
            if (!file.exists() || !file.isFile) throw Exception("file not found")
            ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_OCTET_STREAM).body(FileSystemResource(file))
        } catch (e: Exception) {
            logger.error("固件下载异常: ${e.message}")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).build()
        }
    }
}
