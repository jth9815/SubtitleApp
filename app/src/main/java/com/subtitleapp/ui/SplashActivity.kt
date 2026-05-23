package com.subtitleapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.subtitleapp.model.ModelDownloadManager
import com.subtitleapp.model.ModelSetupActivity
import com.subtitleapp.stt.SttMode

/**
 * 런처 Activity.
 * 모델 설치 여부에 따라:
 *   - 미설치 → ModelSetupActivity (다운로드 화면)
 *   - 설치됨 → MainActivity (바로 실행)
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 스플래시 테마가 있으면 보여주고, 없으면 바로 분기
        route()
    }

    private fun route() {
        val anyModelInstalled = SttMode.values().any { mode ->
            val key = when (mode) {
                SttMode.FAST     -> "vosk-small-en"
                SttMode.BALANCED -> "sherpa-whisper-tiny"
                SttMode.ACCURATE -> "sherpa-whisper-base"
            }
            ModelDownloadManager.isInstalled(this, key)
        }

        val dest = if (anyModelInstalled) MainActivity::class.java
                   else ModelSetupActivity::class.java

        startActivity(Intent(this, dest))
        finish()
    }
}
