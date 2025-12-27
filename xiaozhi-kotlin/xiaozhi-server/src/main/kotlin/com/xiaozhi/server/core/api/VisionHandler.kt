import com.xiaozhi.server.core.api.AppConfig
import com.xiaozhi.server.core.api.BaseHandler
import com.xiaozhi.server.core.api.createVllmInstance
import com.xiaozhi.server.core.utils.AuthToken
import com.xiaozhi.server.core.utils.TokenVerificationResult
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.InetAddress
import java.util.Base64
import java.util.Collections
import kotlin.text.isEmpty

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

    private fun verifyAuthToken(authHeader: String?): TokenVerificationResult? {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null
        val token = authHeader.removePrefix("Bearer ")
        return auth.verifyToken(token)
    }

    @PostMapping(
        path = ["/"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun handlePost(
        @RequestHeader headers: Map<String, String>,
        @RequestPart("question") question: String?,
        @RequestPart("file") file: FilePart
    ): ResponseEntity<String> {
        val responseHeaders = HttpHeaders()
        addCorsHeaders(responseHeaders)
        val mapper = objectMapper
        return try {
            val (isValid, tokenDeviceId) = verifyAuthToken(headers["Authorization"])?:throw Exception("无效的认证token或token已过期")
            if (!isValid) throw Exception("无效的认证token或token已过期")

            val deviceId = headers["Device-Id"] ?: throw Exception("缺少设备ID")
            val clientId = headers["Client-Id"] ?: throw Exception("缺少客户端ID")
            if (deviceId != tokenDeviceId) throw Exception("设备ID与token不匹配")

            // 获取question字段
            val question = question ?: throw Exception("缺少问题字段")
            logger.debug("Question: $question")

            // 读取文件内容
            val fileBytes = file.content()
                .reduce { buf1, buf2 -> buf1.write(buf2) }
                .map { it.asByteBuffer().array() }
                .awaitSingle()

            if (fileBytes.isEmpty()) throw Exception("图片数据为空")

            if (fileBytes.size > MAX_FILE_SIZE) throw Exception("图片大小超过限制，最大允许${MAX_FILE_SIZE/1024/1024}MB")

            // 检查文件格式
            if (!isValidImageFile(fileBytes)) throw Exception("不支持的文件格式，请上传有效的图片文件（支持JPEG、PNG、GIF、BMP、TIFF、WEBP格式）")

            val imageBase64 = Base64.getEncoder().encodeToString(fileBytes)

            // 如果开启了智控台，则从智控台获取模型配置
            var currentConfig = appConfig
            if (appConfig.readConfigFromApi) {
                currentConfig = getPrivateConfigFromApi(currentConfig, deviceId, clientId)
            }

            val selectVllmModule = currentConfig.selectedModule["VLLM"]
            if (selectVllmModule == null) throw Exception("您还未设置默认的视觉分析模块")

            val vllmType = if (currentConfig.vllm[selectVllmModule]?.containsKey("type") == true) {
                currentConfig.vllm[selectVllmModule]?.get("type") as? String ?: selectVllmModule
            } else {
                selectVllmModule
            }

            if (vllmType.isEmpty()) throw Exception("无法找到VLLM模块对应的供应器$vllmType")

            val vllmConfig = currentConfig.vllm[selectVllmModule] ?: Collections.emptyMap()
            val vllm = createVllmInstance(vllmType, vllmConfig)
            val result = vllm.response(question, imageBase64)

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
            val message = if (visionUrl.isNotEmpty() && visionUrl != "null" && visionUrl.length > 0) {
                "MCP Vision 接口运行正常，视觉解释接口地址是：$visionUrl"
            } else {
                "MCP Vision 接口运行不正常，请打开data目录下的.config.yaml文件，找到【server.vision_explain】，设置好地址"
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

// ---------- 工具函数 ----------

// ---------- 配置加载函数 ----------
suspend fun getPrivateConfigFromApi(currentConfig: AppConfig, deviceId: String, clientId: String): AppConfig {
    // 这里应该实现从API获取私有配置的逻辑
    // 为简化，我们返回当前配置，实际实现应从API获取私有配置
    return currentConfig
}

fun getVisionUrl(config: AppConfig): String {
    val serverConfig = config.server
    var visionExplain = serverConfig.visionExplain
    if ("你的" in visionExplain) {
        val localIp = InetAddress.getLocalHost().hostAddress
        val port = serverConfig.httpPort
        visionExplain = "http://$localIp:$port/mcp/vision/explain"
    }
    return visionExplain
}

fun isValidImageFile(fileData: ByteArray): Boolean {
    // 常见图片格式的魔数（文件头）
    val imageSignatures = listOf(
        byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) to "JPEG", // JPEG
        "89504e47".chunked(2).map { it.toInt(16).toByte() }.toByteArray() to "PNG", // PNG
        "474946383761".chunked(2).map { it.toInt(16).toByte() }.toByteArray() to "GIF", // GIF87a
        "474946383961".chunked(2).map { it.toInt(16).toByte() }.toByteArray() to "GIF", // GIF89a
        byteArrayOf(0x42, 0x4d) to "BMP", // BM
        byteArrayOf(0x49, 0x49, 0x2a, 0x00) to "TIFF", // II*\x00
        byteArrayOf(0x4d, 0x4d, 0x00, 0x2a) to "TIFF", // MM\x00*
        "52494646".chunked(2).map { it.toInt(16).toByte() }.toByteArray() to "WEBP" // RIFF
    )

    // 检查文件头是否匹配任何已知的图片格式
    for ((signature, _) in imageSignatures) {
        if (fileData.size >= signature.size && fileData.sliceArray(0 until signature.size).contentEquals(signature)) {
            return true
        }
    }

    return false
}