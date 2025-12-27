package com.xiaozhi.server.core.rate

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


sealed class QueueItem {
    data class Audio(val opusPacket: ByteArray) : QueueItem()
    data class Message(val callback: suspend () -> Unit) : QueueItem()
}

class AudioRateController(
    private val frameDurationMs: Long = 60L
) {
    private val TAG = "AudioRateController"
    private val queue = mutableListOf<QueueItem>()
    private val mutex = Mutex()
    private var playPositionMs: Long = 0
    private var startTimestamp: Double? = null
    private var sendJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val queueHasData = Channel<Unit>(Channel.CONFLATED)

    fun reset() {
        sendJob?.cancel()
        scope.launch {
            mutex.withLock {
                queue.clear()
                playPositionMs = 0
                startTimestamp = null
                queueHasData.trySend(Unit)
            }
        }
    }

    suspend fun addAudio(opusPacket: ByteArray) {
        mutex.withLock {
            queue.add(QueueItem.Audio(opusPacket))
            queueHasData.trySend(Unit)
        }
    }

    suspend fun addMessage(callback: suspend () -> Unit) {
        mutex.withLock {
            queue.add(QueueItem.Message(callback))
            queueHasData.trySend(Unit)
        }
    }

    private fun elapsedMs(): Long {
        val start = startTimestamp ?: return 0
        return ((System.nanoTime() / 1_000_000.0) - start).toLong()
    }

    private suspend fun checkQueue(sendAudioCallback: suspend (ByteArray) -> Unit) {
        while (true) {
            val item = mutex.withLock {
                if (queue.isEmpty()) return
                queue.first()
            }

            when (item) {
                is QueueItem.Message -> {
                    mutex.withLock { queue.removeAt(0) }
                    try {
                        item.callback()
                    } catch (e: Exception) {
                        println("$TAG - 发送消息失败: ${e.message}")
                    }
                }
                is QueueItem.Audio -> {
                    if (startTimestamp == null) {
                        startTimestamp = System.nanoTime() / 1_000_000.0
                    }

                    while (true) {
                        val elapsed = elapsedMs()
                        val output = playPositionMs
                        if (elapsed < output) {
                            val waitMs = output - elapsed
                            try {
                                delay(waitMs)
                            } catch (e: CancellationException) {
                                println("$TAG - 音频发送任务被取消")
                                throw e
                            }
                        } else {
                            break
                        }
                    }

                    mutex.withLock { queue.removeAt(0) }
                    playPositionMs += frameDurationMs
                    try {
                        sendAudioCallback(item.opusPacket)
                    } catch (e: Exception) {
                        println("$TAG - 发送音频失败: ${e.message}")
                    }
                }
            }
        }
    }

    fun startSending(sendAudioCallback: suspend (ByteArray) -> Unit) {
        sendJob = scope.launch {
            try {
                while (isActive) {
                    queueHasData.receive()
                    checkQueue(sendAudioCallback)
                }
            } catch (e: CancellationException) {
                println("$TAG - 音频发送循环已停止")
            } catch (e: Exception) {
                println("$TAG - 音频发送循环异常: ${e.message}")
            }
        }
    }

    fun stopSending() {
        sendJob?.cancel()
        println("$TAG - 已取消音频发送任务")
    }
}
