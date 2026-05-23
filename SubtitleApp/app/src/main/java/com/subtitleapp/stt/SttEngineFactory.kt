package com.subtitleapp.stt

import android.content.Context

object SttEngineFactory {
    fun create(context: Context, mode: SttMode): SttEngine = when (mode) {
        SttMode.FAST     -> VoskSttEngine(context)
        SttMode.BALANCED -> SherpaWhisperEngine(context, "tiny")
        SttMode.ACCURATE -> SherpaWhisperEngine(context, "base")
    }

    fun isModelReady(context: Context, mode: SttMode): Boolean {
        val dir = context.filesDir.resolve(ModelPaths.forMode(mode))
        return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }
}
