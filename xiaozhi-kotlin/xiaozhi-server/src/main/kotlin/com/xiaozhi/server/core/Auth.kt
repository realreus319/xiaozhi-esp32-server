package com.xiaozhi.server.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class AuthenticationError(message: String) : Exception(message)

/**
 * 统一授权认证管理器
 * 生成与验证 client_id device_id token（HMAC-SHA256）认证三元组
 * token 中不含明文 client_id/device_id，只携带签名 + 时间戳; client_id/device_id在连接时传递
 * 在 MQTT 中 client_id: client_id, username: device_id, password: token
 * 在 Websocket 中，header:{Device-ID: device_id, Client-ID: client_id, Authorization: Bearer token, ......}
 */
class AuthManager(
    private val secretKey: String,
    expireSeconds: Long = 60 * 60 * 24 * 30L // 默认30天
) {
    private val expireSeconds: Long = if (expireSeconds <= 0) 60 * 60 * 24 * 30L else expireSeconds

    /**
     * HMAC-SHA256签名并Base64编码
     */
    private fun _sign(content: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        val secretKeySpec = SecretKeySpec(secretKey.encodeToByteArray(), algorithm)
        mac.init(secretKeySpec)
        val signatureBytes = mac.doFinal(content.encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
    }

    /**
     * 生成 token
     * @param clientId 设备连接ID
     * @param username 设备用户名（通常为deviceId）
     * @return token字符串
     */
    fun generateToken(clientId: String, username: String): String {
        val ts = System.currentTimeMillis() / 1000
        val content = "$clientId|$username|$ts"
        val signature = _sign(content)
        // token仅包含签名与时间戳，不包含明文信息
        return "$signature.$ts"
    }

    /**
     * 验证token有效性
     * @param token 客户端传入的token
     * @param clientId 连接使用的client_id
     * @param username 连接使用的username
     * @return 验证结果
     */
    fun verifyToken(token: String, clientId: String, username: String): Boolean {
        return try {
            val parts = token.split(".", limit = 2)
            if (parts.size != 2) return false
            
            val sigPart = parts[0]
            val tsStr = parts[1]
            val ts = tsStr.toLongOrNull() ?: return false
            
            // 检查是否过期
            if (System.currentTimeMillis() / 1000 - ts > expireSeconds) {
                return false
            }

            val expectedSig = _sign("$clientId|$username|$ts")
            expectedSig == sigPart
        } catch (e: Exception) {
            false
        }
    }
}
