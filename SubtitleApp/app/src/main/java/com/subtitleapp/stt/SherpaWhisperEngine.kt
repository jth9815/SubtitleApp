package com.subtitleapp.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*

/**
 * Sherpa-onnx + Whisper 기반 온디바이스 STT (균형/정확 모드).
 *
 * 모델 다운로드:
 *   tiny  → https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
 *           sherpa-onnx-whisper-tiny.en.tar.bz2
 *   base  → sherpa-onnx-whisper-base.en.tar.bz2
 *
 * 설치 경로:
 *   context.filesDir/models/sherpa-whisper-tiny/
 *   context.filesDir/models/sherpa-whisper-base/
 *
 * 각 디렉터리 내부 구조:
 *   tiny-encoder.int8.onnx
 *   tiny-decoder.int8.onnx
 *   tiny-tokens.txt
 */
class SherpaWhisperEngine(
    private val context: Context,
    private val modelSize: String  // "tiny" 또는 "base"
) : SttEngine {

    companion object {
        private const val TAG = "SherpaWhisperEngine"
    }

    private var recognizer: OfflineRecognizer? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null

    // 청크 누적 버퍼 (Whisper는 긴 컨텍스트가 정확도에 유리)
    private val audioAccumulator = mutableListOf<Float>()
    private val accumulateSamples = 16000 * 5  // 5초 누적 후 인식

    override fun init(modelPath: String, onReady: () -> Unit, onError: (Throwable) -> Unit) {
        Thread({
            try {
                val dir = "${context.filesDir}/$modelPath"
                Log.i(TAG, "Sherpa-Whisper 모델 로딩: $dir")

                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = "$dir/${modelSize}-encoder.int8.onnx",
                            decoder = "$dir/${modelSize}-decoder.int8.onnx",
                            language = "en",   // 소스 언어 (영어 → 한국어 번역은 ML Kit)
                            task = "transcribe"
                        ),
                        tokens = "$dir/${modelSize}-tokens.txt",
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    )
                )

                recognizer = OfflineRecognizer(config)
                Log.i(TAG, "Sherpa-Whisper 초기화 완료")
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Sherpa-Whisper 초기화 실패: ${e.message}")
                onError(e)
            }
        }, "SherpaInitThread").start()
    }

    override fun feedAudio(pcmData: ShortArray) {
        // Short → Float 정규화 [-1.0, 1.0]
        val floats = FloatArray(pcmData.size) { pcmData[it] / 32768.0f }
        audioAccumulator.addAll(floats.toList())

        // partial: 누적 중임을 알림
        if (audioAccumulator.size < accumulateSamples) {
            onPartial?.invoke("...")
            return
        }

        // 누적된 오디오 인식
        val samples = audioAccumulator.toFloatArray()
        audioAccumulator.clear()

        val rec = recognizer ?: return
        val stream = rec.createStream()
        stream.acceptWaveform(samples, sampleRate = 16000)
        rec.decode(stream)
        val result = rec.getResult(stream).text.trim()
        stream.release()

        if (result.isNotBlank()) {
            Log.d(TAG, "인식 결과: $result")
            onFinal?.invoke(result)
        }
    }

    override fun setResultCallback(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        this.onPartial = onPartial
        this.onFinal = onFinal
    }

    override fun release() {
        recognizer?.release(); recognizer = null
        audioAccumulator.clear()
        Log.i(TAG, "Sherpa-Whisper 해제")
    }
}
