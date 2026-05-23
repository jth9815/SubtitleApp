package com.subtitleapp.stt

interface SttEngine {
    fun init(modelPath: String, onReady: () -> Unit, onError: (Throwable) -> Unit)
    fun feedAudio(pcmData: ShortArray)
    fun setResultCallback(onPartial: (String) -> Unit, onFinal: (String) -> Unit)
    fun release()
}

enum class SttMode(val displayName: String, val description: String) {
    FAST    ("빠름",  "Vosk — 저지연, 저사양 기기 적합 (~50MB)"),
    BALANCED("균형",  "Whisper tiny — 속도·정확도 균형 (~150MB)"),
    ACCURATE("정확",  "Whisper base — 높은 정확도, 고사양 권장 (~300MB)")
}

object ModelPaths {
    const val VOSK_EN      = "models/vosk-small-en"
    const val WHISPER_TINY = "models/sherpa-whisper-tiny"
    const val WHISPER_BASE = "models/sherpa-whisper-base"

    fun forMode(mode: SttMode) = when (mode) {
        SttMode.FAST     -> VOSK_EN
        SttMode.BALANCED -> WHISPER_TINY
        SttMode.ACCURATE -> WHISPER_BASE
    }
}
