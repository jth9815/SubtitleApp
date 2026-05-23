package com.subtitleapp.translation

import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * ML Kit 온디바이스 번역기.
 * 영어 → 한국어 (모델 최초 다운로드 후 완전 오프라인 동작).
 *
 * 모델 크기: 약 30MB/언어쌍
 * 최초 실행 시 자동 다운로드 (인터넷 필요).
 */
class MlKitTranslator {

    companion object {
        private const val TAG = "MlKitTranslator"
    }

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.KOREAN)
        .build()

    private val translator = Translation.getClient(options)
    private var isReady = false

    fun init() {
        val downloadConditions = com.google.mlkit.common.model.DownloadConditions.Builder()
            .build() // 조건 없음: WiFi/셀룰러 모두 허용

        translator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                isReady = true
                Log.i(TAG, "ML Kit 번역 모델 준비 완료")
            }
            .addOnFailureListener { e ->
                // 오프라인 상태 등으로 다운로드 실패 → 이미 다운로드된 경우엔 그냥 동작
                Log.w(TAG, "모델 다운로드 실패 (이미 설치됐으면 무시): ${e.message}")
                isReady = true  // 이미 설치된 경우 동작 가능
            }
    }

    /**
     * 텍스트 번역. 결과는 callback으로 반환.
     * ML Kit 내부적으로 백그라운드 처리.
     */
    fun translate(text: String, callback: (String) -> Unit) {
        if (!isReady) {
            callback(text) // 번역 불가 시 원문 반환
            return
        }

        translator.translate(text)
            .addOnSuccessListener { translated ->
                Log.d(TAG, "번역: $text → $translated")
                callback(translated)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "번역 실패: ${e.message}")
                callback(text) // 실패 시 원문 반환
            }
    }

    fun release() {
        translator.close()
    }
}
