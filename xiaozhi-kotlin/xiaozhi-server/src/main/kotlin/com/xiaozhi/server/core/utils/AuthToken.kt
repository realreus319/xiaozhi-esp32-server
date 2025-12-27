package com.xiaozhi.server.core.utils

import com.xiaozhi.server.config.JsonConfig
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import tools.jackson.module.kotlin.readValue
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class TokenVerificationResult(val isValid: Boolean, val deviceId: String?)

class AuthToken(secretKey: String) {
    private val encryptionKey: ByteArray
    private val objectMapper = JsonConfig.objectMapper
    private val jwtSigningKey = Keys.hmacShaKeyFor(secretKey.toByteArray())

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AES_KEY_LENGTH = 32 // 256 bits
        private const val PBKDF2_ITERATIONS = 100000
    }

    init {
        encryptionKey = deriveKey(secretKey.toByteArray(), AES_KEY_LENGTH)
    }

    /**
     * """派生固定长度的密钥"""
     */
    private fun deriveKey(password: ByteArray, length: Int): ByteArray {
        // 在实际生产中应使用随机盐
        val salt = "fixed_salt_placeholder".toByteArray()

        // 使用PBKDF2进行密钥派生（这里简化实现，实际应使用Java的PBKDF2实现）
        // 注意：Java标准库没有内置PBKDF2，需要使用Bouncy Castle或其他库
        // 为了简化，这里使用简单的哈希方式，实际生产环境需要正确实现
        return simpleKeyDerivation(password, salt, length)
    }

    // 简化的密钥派生方法（实际生产环境应使用标准PBKDF2实现）
    private fun simpleKeyDerivation(password: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val combined = password + salt
        val result = ByteArray(length)
        for (i in 0 until length) {
            result[i] = combined[i % combined.size]
        }
        return result
    }
    /** 使用AES-GCM加密整个payload **/
    private fun encryptPayload(payload: Map<String, Any>): String {
        val payloadJson = objectMapper.writeValueAsString(payload)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encryptionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(payloadJson.toByteArray())

        // 组合 IV + 密文 + 标签
        val encryptedData = iv + ciphertext
        return Base64.getUrlEncoder().encodeToString(encryptedData)
    }
    /** 解密AES-GCM加密的payload **/
    private fun decryptPayload(encryptedData: String): Map<String, Any> {
        val data = Base64.getUrlDecoder().decode(encryptedData)
        val iv = data.sliceArray(0 until GCM_IV_LENGTH)
        val ciphertext = data.sliceArray(GCM_IV_LENGTH until data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encryptionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val plaintext = cipher.doFinal(ciphertext)

        return objectMapper.readValue(String(plaintext))
    }

    /**
     *   生成JWT token
     *   :param device_id: 设备ID
     *   :return: JWT token字符串
     */
    fun generateToken(deviceId: String): String {
        val expireTime = Date(System.currentTimeMillis() + 3600000) // 1小时后

        val payload = mapOf(
            "device_id" to deviceId,
            "exp" to expireTime.time / 1000 // 时间戳格式
        )

        val encryptedPayload = encryptPayload(payload)
        val outerPayload = mapOf("data" to encryptedPayload)

        return Jwts.builder()
            .claims(outerPayload as Map<String, Any>)
            .expiration(expireTime)
            .signWith(jwtSigningKey) // 使用新方法，自动选择算法
            .compact()
    }
    /**
     *   验证JWT token
     *   :param token: JWT token字符串
     *   :return: (是否有效, 设备ID)
     */
    fun verifyToken(token: String): TokenVerificationResult {
        return try {
            val claims = Jwts.parser()
                .verifyWith(jwtSigningKey)
                .build()
                .parseSignedClaims(token)
                .payload

            val innerPayload = decryptPayload(claims["data"] as String)

            // 再次检查过期时间
            val exp = (innerPayload["exp"] as Number).toLong()
            if (exp < System.currentTimeMillis() / 1000) {
                return TokenVerificationResult(false, null)
            }

            TokenVerificationResult(true, innerPayload["device_id"] as String)
        } catch (e: Exception) {
            println("Token verification failed: ${e.message}")
            TokenVerificationResult(false, null)
        }
    }
}
