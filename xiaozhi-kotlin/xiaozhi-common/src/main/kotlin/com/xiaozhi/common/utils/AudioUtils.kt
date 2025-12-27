package com.xiaozhi.common.utils

import java.util.Base64

object AudioUtils {
    fun base64ToByteArray(base64: String): ByteArray {
        return Base64.getDecoder().decode(base64)
    }

    fun byteArrayToBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun calculateAudioDuration(
        bytes: ByteArray,
        sampleRate: Int = 16000,
        bitsPerSample: Int = 16,
        channels: Int = 1
    ): Long {
        val bytesPerSample = bitsPerSample / 8
        val bytesPerSecond = sampleRate * channels * bytesPerSample
        return (bytes.size * 1000L) / bytesPerSecond
    }

    fun opusBytesToSamples(bytes: ByteArray, frameDuration: Int = 60): Int {
        val samplesPerFrame = (frameDuration * 16000) / 1000
        val frameSize = 2 + (samplesPerFrame / 8) * 2
        return (bytes.size / frameSize) * samplesPerFrame
    }
}
