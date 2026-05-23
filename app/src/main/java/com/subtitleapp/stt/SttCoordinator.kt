package com.subtitleapp.stt

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.subtitleapp.audio.AudioChunkBuffer
import com.subtitleapp.overlay.SubtitleOverlayService
import com.subtitleapp.translation.MlKitTranslator

object SttCoordinator {

    private const val TAG = "SttCoordinator"
    const val PREF_STT_MODE = "stt_mode"

    private var engine: SttEngine? = null
    private var translator: MlKitTranslator? = null
    private var consumeThread: Thread? = null
    @Volatile private var running = false

    fun start(context: Context, buffer: AudioChunkBuffer) {
        if (running) return
        running = true

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val modeName = prefs.getString(PREF_STT_MODE, SttMode.FAST.name) ?: SttMode.FAST.name
        val mode = runCatching { SttMode.valueOf(modeName) }.getOrDefault(SttMode.FAST)

        // 모델 없으면 크래시 대신 안내
        if (!SttEngineFactory.isModelReady(context, mode)) {
            Log.w(TAG, "모델 없음: ${ModelPaths.forMode(mode)}")
            SubtitleOverlayService.showFinal("⚠️ STT 모델이 없어요\n앱을 재실행해서 모델을 다운로드하세요")
            running = false
            return
        }

        translator = MlKitTranslator().also { it.init() }

        engine = SttEngineFactory.create(context, mode).also { eng ->
            eng.setResultCallback(
                onPartial = { text -> SubtitleOverlayService.showPartial(text) },
                onFinal = { text ->
                    Log.d(TAG, "STT 확정: $text")
                    translator?.translate(text) { korean ->
                        SubtitleOverlayService.showFinal(korean)
                    }
                }
            )
            eng.init(
                modelPath = ModelPaths.forMode(mode),
                onReady = {
                    Log.i(TAG, "엔진 준비 완료")
                    startConsumeLoop(buffer)
                },
                onError = { e ->
                    Log.e(TAG, "엔진 초기화 실패: ${e.message}")
                    SubtitleOverlayService.showFinal("⚠️ STT 모델 로드 실패\n앱을 재실행하세요")
                    running = false
                }
            )
        }
    }

    fun stop() {
        running = false
        consumeThread?.interrupt(); consumeThread = null
        engine?.release(); engine = null
        translator?.release(); translator = null
    }

    fun switchMode(context: Context, buffer: AudioChunkBuffer, mode: SttMode) {
        stop()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(PREF_STT_MODE, mode.name).apply()
        start(context, buffer)
    }

    private fun startConsumeLoop(buffer: AudioChunkBuffer) {
        consumeThread = Thread({
            while (running) {
                try {
                    val chunk = buffer.take()
                    engine?.feedAudio(chunk)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "SttConsumeThread").apply { start() }
    }
}
