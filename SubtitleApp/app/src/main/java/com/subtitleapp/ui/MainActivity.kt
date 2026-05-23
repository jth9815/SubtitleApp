package com.subtitleapp.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.subtitleapp.R
import com.subtitleapp.audio.AudioCaptureService
import com.subtitleapp.databinding.ActivityMainBinding
import com.subtitleapp.overlay.SubtitleOverlayService
import com.subtitleapp.stt.SttCoordinator
import com.subtitleapp.stt.SttMode
import androidx.preference.PreferenceManager

@RequiresApi(Build.VERSION_CODES.Q)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false

    // ── 권한 런처 ────────────────────────────────────────────────────

    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkOverlayPermission()
        else showDialog("마이크 권한 필요", "오디오 캡처를 위해 마이크 권한이 필요해요.")
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) requestMediaProjection()
        else showDialog("권한 필요", "오버레이 권한이 없으면 자막을 표시할 수 없어요.")
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startServices(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "화면 캡처 권한을 허용해야 해요.", Toast.LENGTH_LONG).show()
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // 시작/종료 버튼
        binding.btnToggle.setOnClickListener {
            if (isServiceRunning) stopServices()
            else audioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }

        // 설정 버튼
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 현재 모드 표시
        updateModeDisplay()
    }

    override fun onResume() {
        super.onResume()
        updateModeDisplay()
    }

    private fun updateModeDisplay() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val modeName = prefs.getString(SttCoordinator.PREF_STT_MODE, SttMode.FAST.name)
            ?: SttMode.FAST.name
        val mode = runCatching { SttMode.valueOf(modeName) }.getOrDefault(SttMode.FAST)
        binding.tvCurrentMode.text = "현재 모드: ${mode.displayName} — ${mode.description}"
    }

    // ── 권한 흐름 ────────────────────────────────────────────────────

    private fun checkOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            requestMediaProjection()
        } else {
            AlertDialog.Builder(this)
                .setTitle("자막 오버레이 권한 필요")
                .setMessage("다른 앱 위에 자막을 표시하려면\n'다른 앱 위에 표시' 권한이 필요해요.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    overlaySettingsLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun requestMediaProjection() {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    // ── 서비스 제어 ──────────────────────────────────────────────────

    private fun startServices(resultCode: Int, data: Intent) {
        SubtitleOverlayService.start(this)
        AudioCaptureService.start(this, resultCode, data)
        isServiceRunning = true
        binding.btnToggle.text = "⏹ 자막 종료"
        binding.statusIndicator.text = "● 실행 중"
        Toast.makeText(this, "자막 서비스 시작 ✅", Toast.LENGTH_SHORT).show()
    }

    private fun stopServices() {
        AudioCaptureService.stop(this)
        SubtitleOverlayService.stop(this)
        isServiceRunning = false
        binding.btnToggle.text = "▶ 자막 시작"
        binding.statusIndicator.text = "○ 대기 중"
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title).setMessage(message)
            .setPositiveButton("확인", null).show()
    }
}
