package com.subtitleapp.stt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.subtitleapp.audio.AudioChunkBuffer
import com.subtitleapp.overlay.SubtitleOverlayService
import com.subtitleapp.translation.MlKitTranslator

/**
 * STT + 번역 + 오버레이를 연결하는 코디네이터.
 *
 * AudioCaptureService → SttCoordinator → MlKitTranslator → SubtitleOverlayService
 *
 * start()는 AudioCaptureService.onStartCommand에서 호출.
 * stop()는 AudioCaptureService.onDestroy에서 호출.
 */
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

        Log.i(TAG, "STT 모드: ${mode.displayName}")

        translator = MlKitTranslator().also { it.init() }

        engine = SttEngineFactory.create(context, mode).also { eng ->
            eng.setResultCallback(
                onPartial = { text ->
                    // 미확정 텍스트: 흐릿하게 표시
                    SubtitleOverlayService.showPartial(text)
                },
                onFinal = { text ->
                    Log.d(TAG, "STT 확정: $text")
                    // 번역 후 자막 표시
                    translator?.translate(text) { korean ->
                        SubtitleOverlayService.showFinal(korean)
                    }
                }
            )

            eng.init(
                modelPath = ModelPaths.forMode(mode),
                onReady = {
                    Log.i(TAG, "엔진 준비 완료, 소비 루프 시작")
                    startConsumeLoop(buffer)
                },
                onError = { e ->
                    Log.e(TAG, "엔진 초기화 실패: ${e.message}")
                    SubtitleOverlayService.showFinal("⚠️ STT 모델 로드 실패\n모델 파일을 확인하세요")
                }
            )
        }
    }

    fun stop() {
        running = false
        consumeThread?.interrupt(); consumeThread = null
        engine?.release(); engine = null
        translator?.release(); translator = null
        Log.i(TAG, "코디네이터 종료")
    }

    fun switchMode(context: Context, buffer: AudioChunkBuffer, mode: SttMode) {
        Log.i(TAG, "모드 전환 → ${mode.displayName}")
        stop()
        // prefs에 저장
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
