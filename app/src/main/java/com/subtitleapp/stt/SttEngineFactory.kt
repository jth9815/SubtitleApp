package com.subtitleapp.stt

import android.content.Context

object SttEngineFactory {
    fun create(context: Context, mode: SttMode): SttEngine = when (mode) {
        SttMode.FAST,
        SttMode.BALANCED,
        SttMode.ACCURATE -> VoskSttEngine(context)  // 일단 전부 Vosk로
    }

    fun isModelReady(context: Context, mode: SttMode): Boolean {
        val dir = context.filesDir.resolve(ModelPaths.forMode(mode))
        return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }
}
