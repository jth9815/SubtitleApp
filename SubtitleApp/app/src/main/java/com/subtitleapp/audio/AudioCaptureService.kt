package com.subtitleapp.audio

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.subtitleapp.stt.SttCoordinator
import com.subtitleapp.ui.MainActivity

@RequiresApi(Build.VERSION_CODES.Q)
class AudioCaptureService : Service() {

    companion object {
        private const val TAG = "AudioCaptureService"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "subtitle_capture"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"

        // 전역 버퍼: SttCoordinator에서 접근
        val chunkBuffer = AudioChunkBuffer(chunkSizeMs = 200, sampleRate = SAMPLE_RATE)

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioCaptureService::class.java))
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var isCapturing = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "권한 없음. 종료.")
            stopSelf(); return START_NOT_STICKY
        }

        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        SttCoordinator.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 캡처 ─────────────────────────────────────────────────────────

    private fun startCapture(resultCode: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data).also { mp ->
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, null)
        }

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize * 4)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 초기화 실패")
            stopSelf(); return
        }

        isCapturing = true
        audioRecord?.startRecording()

        // STT 처리 시작
        SttCoordinator.start(applicationContext, chunkBuffer)

        captureThread = Thread({
            val buf = ShortArray(bufSize / 2)
            while (isCapturing) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: break
                if (n > 0) chunkBuffer.feed(buf, n)
                else if (n < 0) { Log.e(TAG, "read error $n"); break }
            }
        }, "AudioCaptureThread").apply { start() }

        Log.i(TAG, "캡처 시작")
    }

    private fun stopCapture() {
        isCapturing = false
        captureThread?.interrupt(); captureThread = null
        audioRecord?.run { stop(); release() }; audioRecord = null
        mediaProjection?.stop(); mediaProjection = null
        chunkBuffer.clear()
        Log.i(TAG, "캡처 종료")
    }

    // ── 알림 ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "실시간 자막", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("실시간 자막 실행 중")
        .setContentText("탭하여 설정 열기")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
}
