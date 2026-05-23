package com.subtitleapp.audio

import java.util.concurrent.ArrayBlockingQueue

/**
 * AudioRecord PCM 데이터를 청크 단위로 관리하는 버퍼.
 * STT 스레드가 take()로 소비, AudioCaptureService가 feed()로 생산.
 */
class AudioChunkBuffer(
    val chunkSizeMs: Int = 200,
    val sampleRate: Int = 16000,
    private val maxQueueSize: Int = 20
) {
    val chunkSizeSamples: Int = sampleRate * chunkSizeMs / 1000

    private val queue = ArrayBlockingQueue<ShortArray>(maxQueueSize)
    private val pendingBuffer = ShortArray(chunkSizeSamples)
    private var pendingOffset = 0

    fun feed(data: ShortArray, length: Int) {
        var inputOffset = 0
        while (inputOffset < length) {
            val copyLen = minOf(chunkSizeSamples - pendingOffset, length - inputOffset)
            System.arraycopy(data, inputOffset, pendingBuffer, pendingOffset, copyLen)
            pendingOffset += copyLen
            inputOffset += copyLen

            if (pendingOffset >= chunkSizeSamples) {
                val chunk = pendingBuffer.copyOf()
                if (!queue.offer(chunk)) {
                    queue.poll() // 오래된 것 드롭
                    queue.offer(chunk)
                }
                pendingOffset = 0
            }
        }
    }

    /** STT 스레드에서 호출. 청크가 생길 때까지 블로킹. */
    @Throws(InterruptedException::class)
    fun take(): ShortArray = queue.take()

    fun clear() {
        queue.clear()
        pendingOffset = 0
    }
}
