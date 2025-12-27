package com.xiaozhi.common

data class AudioFormat(
    val format: String = "opus",
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val frameDuration: Int = 60
) {
    companion object {
        val DEFAULT = AudioFormat()
        val OPUS_16K = AudioFormat("opus", 16000, 1, 60)
        val PCM_16K = AudioFormat("pcm", 16000, 1, 60)
    }
}
