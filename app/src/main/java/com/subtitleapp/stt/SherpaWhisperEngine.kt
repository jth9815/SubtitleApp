package com.subtitleapp.stt

import android.content.Context
import android.util.Log

// Sherpa-onnx 라이브러리 미포함 — 추후 추가 예정
// 현재는 Vosk(빠름 모드)만 사용
class SherpaWhisperEngine(
    private val context: Context,
    private val modelSize: String
) : SttEngine {

    private val voskFallback = VoskSttEngine(context)

    override fun init(modelPath: String, onReady: () -> Unit, onError: (Throwable) -> Unit) {
        // Vosk로 대체
        val voskPath = ModelPaths.VOSK_EN
        voskFallback.init(voskPath, onReady, onError)
    }

    override fun feedAudio(pcmData: ShortArray) {
        voskFallback.feedAudio(pcmData)
    }

    override fun setResultCallback(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        voskFallback.setResultCallback(onPartial, onFinal)
    }

    override fun release() {
        voskFallback.release()
    }
}
