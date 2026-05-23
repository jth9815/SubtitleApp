package com.subtitleapp.stt

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.json.JSONObject

/**
 * Vosk 기반 온디바이스 STT (빠름 모드).
 * - 모델: vosk-model-small-en-us (~50MB)
 * - 다운로드: https://alphacephei.com/vosk/models
 * - 설치 경로: context.filesDir/models/vosk-small-en/
 */
class VoskSttEngine(private val context: Context) : SttEngine {

    companion object {
        private const val TAG = "VoskSttEngine"
        private const val SAMPLE_RATE = 16000.0f
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null

    override fun init(modelPath: String, onReady: () -> Unit, onError: (Throwable) -> Unit) {
        Thread({
            try {
                val fullPath = "${context.filesDir}/$modelPath"
                Log.i(TAG, "모델 로딩: $fullPath")
                model = Model(fullPath)
                recognizer = Recognizer(model, SAMPLE_RATE)
                Log.i(TAG, "Vosk 초기화 완료")
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Vosk 초기화 실패: ${e.message}")
                onError(e)
            }
        }, "VoskInitThread").start()
    }

    override fun feedAudio(pcmData: ShortArray) {
        val rec = recognizer ?: return

        // ShortArray → ByteArray 변환 (little-endian PCM16)
        val bytes = ByteArray(pcmData.size * 2)
        for (i in pcmData.indices) {
            bytes[i * 2]     = (pcmData[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (pcmData[i].toInt() shr 8 and 0xFF).toByte()
        }

        val isFinal = rec.acceptWaveForm(bytes, bytes.size)
        if (isFinal) {
            val text = parseVoskResult(rec.result)
            if (text.isNotBlank()) onFinal?.invoke(text)
        } else {
            val text = parseVoskPartial(rec.partialResult)
            if (text.isNotBlank()) onPartial?.invoke(text)
        }
    }

    override fun setResultCallback(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        this.onPartial = onPartial
        this.onFinal = onFinal
    }

    override fun release() {
        recognizer?.close(); recognizer = null
        model?.close(); model = null
        Log.i(TAG, "Vosk 해제")
    }

    private fun parseVoskResult(json: String): String = try {
        JSONObject(json).optString("text", "")
    } catch (e: Exception) { "" }

    private fun parseVoskPartial(json: String): String = try {
        JSONObject(json).optString("partial", "")
    } catch (e: Exception) { "" }
}
