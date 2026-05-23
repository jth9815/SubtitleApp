package com.subtitleapp.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class SubtitleOverlayService : Service() {

    companion object {
        private const val TAG = "SubtitleOverlay"
        private const val CHANNEL_ID = "subtitle_overlay"
        private const val NOTIFICATION_ID = 1002
        private const val AUTO_HIDE_MS = 5000L

        private var instance: SubtitleOverlayService? = null

        fun showPartial(text: String) { instance?.updateSubtitle(text, isPartial = true) }
        fun showFinal(text: String) { instance?.updateSubtitle(text, isPartial = false) }
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
    private lateinit var wlp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { subtitleContainer.visibility = View.GONE }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(hideRunnable)
        runCatching { windowManager.removeView(subtitleContainer) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        subtitleText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            setPadding(24, 12, 24, 12)
            gravity = Gravity.CENTER
        }

        wlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        subtitleContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            addView(subtitleText, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            visibility = View.GONE
            setupDrag()
        }

        windowManager.addView(subtitleContainer, wlp)
    }

    private fun View.setupDrag() {
        var initX = 0; var initY = 0
        var touchX = 0f; var touchY = 0f

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = wlp.x; initY = wlp.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    wlp.x = initX + (event.rawX - touchX).toInt()
                    wlp.y = initY - (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(subtitleContainer, wlp)
                    true
                }
                else -> false
            }
        }
    }

    fun updateSubtitle(text: String, isPartial: Boolean) {
        handler.post {
            if (text.isBlank()) return@post
            subtitleText.text = text
            subtitleText.alpha = if (isPartial) 0.55f else 1.0f
            subtitleContainer.visibility = View.VISIBLE
            if (!isPartial) {
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, AUTO_HIDE_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "자막 오버레이", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("자막 오버레이 활성")
        .setSmallIcon(android.R.drawable.ic_menu_myplaces)
        .setOngoing(true).setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
}
