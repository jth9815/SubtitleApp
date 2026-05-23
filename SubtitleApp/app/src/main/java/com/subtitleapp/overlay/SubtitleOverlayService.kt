package com.subtitleapp.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * 다른 앱 위에 자막을 표시하는 Foreground Service.
 * SYSTEM_ALERT_WINDOW 권한 필요.
 *
 * 기능:
 *  - 화면 하단 반투명 자막 뷰
 *  - 드래그로 위치 이동
 *  - partial(미확정): 흐릿한 텍스트
 *  - final(확정): 선명한 텍스트 → 5초 후 자동 사라짐
 */
class SubtitleOverlayService : Service() {

    companion object {
        private const val TAG = "SubtitleOverlay"
        private const val CHANNEL_ID = "subtitle_overlay"
        private const val NOTIFICATION_ID = 1002
        private const val AUTO_HIDE_MS = 5000L

        // static 접근용 (SttCoordinator → 오버레이)
        private var instance: SubtitleOverlayService? = null

        fun showPartial(text: String) {
            instance?.updateSubtitle(text, isPartial = true)
        }

        fun showFinal(text: String) {
            instance?.updateSubtitle(text, isPartial = false)
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SubtitleOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SubtitleOverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var subtitleContainer: FrameLayout
    private lateinit var subtitleText: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideSubtitle() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
        Log.i(TAG, "오버레이 서비스 시작")
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(hideRunnable)
        runCatching { windowManager.removeView(subtitleContainer) }
        super.onDestroy()
        Log.i(TAG, "오버레이 서비스 종료")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 오버레이 UI 설정 ──────────────────────────────────────────────

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 자막 텍스트
        subtitleText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            setPadding(24, 12, 24, 12)
            gravity = Gravity.CENTER
        }

        // 반투명 배경 컨테이너 (드래그 지원)
        subtitleContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(160, 0, 0, 0)) // 반투명 검정
            addView(subtitleText, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            visibility = View.GONE
            setupDrag()
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120  // 하단에서 120px 위
        }

        windowManager.addView(subtitleContainer, layoutParams)
    }

    /** 드래그로 자막 위치 이동 */
    private fun View.setupDrag() {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x; initialY = layoutParams.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchX).toInt()
                    layoutParams.y = initialY - (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(subtitleContainer, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    // ── 자막 업데이트 ─────────────────────────────────────────────────

    fun updateSubtitle(text: String, isPartial: Boolean) {
        handler.post {
            if (text.isBlank()) return@post

            subtitleText.text = text
            subtitleText.alpha = if (isPartial) 0.55f else 1.0f  // partial은 흐릿하게
            subtitleContainer.visibility = View.VISIBLE

            // 확정 텍스트만 자동 숨김 타이머 리셋
            if (!isPartial) {
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, AUTO_HIDE_MS)
            }
        }
    }

    private fun hideSubtitle() {
        subtitleContainer.visibility = View.GONE
    }

    // ── 알림 ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "자막 오버레이", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("자막 오버레이 활성")
        .setSmallIcon(android.R.drawable.ic_menu_myplaces)
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
}
