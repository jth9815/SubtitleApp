package com.subtitleapp.model

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.subtitleapp.R
import com.subtitleapp.stt.ModelPaths
import com.subtitleapp.stt.SttMode
import com.subtitleapp.ui.MainActivity

/**
 * 앱 첫 실행 시 보여주는 모델 다운로드 화면.
 *
 * 흐름:
 *   1. 설치된 모델 현황 표시
 *   2. 사용자가 원하는 모드 선택
 *   3. 해당 모델 다운로드 (진행률 표시)
 *   4. 완료 → MainActivity로 이동
 */
class ModelSetupActivity : AppCompatActivity() {

    // 모드별 UI 상태
    private data class ModeRow(
        val mode: SttMode,
        val modelKey: String,
        val sizeMb: String
    )

    private val rows = listOf(
        ModeRow(SttMode.FAST,     "vosk-small-en",      "~50MB"),
        ModeRow(SttMode.BALANCED, "sherpa-whisper-tiny", "~150MB"),
        ModeRow(SttMode.ACCURATE, "sherpa-whisper-base", "~300MB")
    )

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnSkip: Button
    private lateinit var btnProceed: Button
    private lateinit var radioGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_setup)

        progressBar = findViewById(R.id.progress_bar)
        tvStatus    = findViewById(R.id.tv_status)
        btnSkip     = findViewById(R.id.btn_skip)
        btnProceed  = findViewById(R.id.btn_proceed)
        radioGroup  = findViewById(R.id.radio_group)

        setupRadioButtons()
        updateInstallStatus()

        btnProceed.setOnClickListener { startDownload() }
        btnSkip.setOnClickListener {
            // 이미 하나라도 설치된 경우 스킵 허용
            if (rows.any { ModelDownloadManager.isInstalled(this, it.modelKey) }) {
                goToMain()
            } else {
                Toast.makeText(this, "최소 하나의 모델이 필요해요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRadioButtons() {
        rows.forEach { row ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                tag = row
                val installed = ModelDownloadManager.isInstalled(this@ModelSetupActivity, row.modelKey)
                text = buildString {
                    append("${row.mode.displayName}  (${row.sizeMb})")
                    if (installed) append("  ✅ 설치됨")
                }
                textSize = 15f
                setPadding(0, 16, 0, 16)
            }
            radioGroup.addView(rb)

            // 이미 설치된 것 중 첫 번째를 기본 선택
            if (ModelDownloadManager.isInstalled(this, row.modelKey) &&
                radioGroup.checkedRadioButtonId == -1) {
                rb.isChecked = true
            }
        }
        // 아무것도 설치 안 된 경우 첫 번째(FAST) 기본 선택
        if (radioGroup.checkedRadioButtonId == -1) {
            (radioGroup.getChildAt(0) as? RadioButton)?.isChecked = true
        }
    }

    private fun updateInstallStatus() {
        val allInstalled = rows.all { ModelDownloadManager.isInstalled(this, it.modelKey) }
        btnSkip.text = if (rows.any { ModelDownloadManager.isInstalled(this, it.modelKey) })
            "건너뛰기 (이미 설치된 모델 사용)"
        else
            "건너뛰기"
    }

    private fun startDownload() {
        val checkedId = radioGroup.checkedRadioButtonId
        val rb = radioGroup.findViewById<RadioButton>(checkedId) ?: return
        val row = rb.tag as ModeRow

        // 이미 설치됨 → 바로 이동
        if (ModelDownloadManager.isInstalled(this, row.modelKey)) {
            goToMain()
            return
        }

        // UI 잠금
        setDownloading(true)
        tvStatus.text = "${row.mode.displayName} 모델 다운로드 중..."
        progressBar.progress = 0

        ModelDownloadManager.download(
            context = this,
            modelKey = row.modelKey,
            onProgress = { pct ->
                progressBar.progress = pct
                tvStatus.text = when {
                    pct < 90  -> "${row.mode.displayName} 다운로드 중... $pct%"
                    pct < 100 -> "압축 해제 중..."
                    else      -> "완료!"
                }
            },
            onComplete = {
                setDownloading(false)
                tvStatus.text = "✅ ${row.mode.displayName} 모델 설치 완료!"
                rb.text = rb.text.toString().replace("  ✅ 설치됨", "") + "  ✅ 설치됨"
                Toast.makeText(this, "설치 완료!", Toast.LENGTH_SHORT).show()
                goToMain()
            },
            onError = { e ->
                setDownloading(false)
                tvStatus.text = "❌ 다운로드 실패: ${e.message}"
                Toast.makeText(this, "다운로드 실패. 인터넷 연결을 확인해주세요.", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun setDownloading(downloading: Boolean) {
        progressBar.visibility = if (downloading) View.VISIBLE else View.GONE
        btnProceed.isEnabled = !downloading
        btnSkip.isEnabled = !downloading
        radioGroup.isEnabled = !downloading
        for (i in 0 until radioGroup.childCount) {
            radioGroup.getChildAt(i).isEnabled = !downloading
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
