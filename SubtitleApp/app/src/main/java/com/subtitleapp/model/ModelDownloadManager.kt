package com.subtitleapp.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * STT 모델을 앱 내부 저장소에 다운로드/압축해제하는 매니저.
 *
 * 호출 흐름:
 *   ModelDownloadManager.download(context, mode,
 *       onProgress = { pct -> ... },
 *       onComplete = { ... },
 *       onError = { e -> ... }
 *   )
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownloadManager"

    // 모델별 다운로드 URL + 설치 경로
    data class ModelSpec(
        val url: String,
        val destDir: String,       // context.filesDir 기준 상대경로
        val archiveType: String    // "zip" | "tar.bz2"
    )

    private val SPECS = mapOf(
        "vosk-small-en" to ModelSpec(
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            destDir = "models/vosk-small-en",
            archiveType = "zip"
        ),
        "sherpa-whisper-tiny" to ModelSpec(
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2",
            destDir = "models/sherpa-whisper-tiny",
            archiveType = "tar.bz2"
        ),
        "sherpa-whisper-base" to ModelSpec(
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.en.tar.bz2",
            destDir = "models/sherpa-whisper-base",
            archiveType = "tar.bz2"
        )
    )

    /**
     * 모델이 이미 설치되어 있는지 확인.
     */
    fun isInstalled(context: Context, modelKey: String): Boolean {
        val spec = SPECS[modelKey] ?: return false
        val dir = File(context.filesDir, spec.destDir)
        return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    /**
     * 모델 다운로드 + 압축해제.
     * IO Dispatcher에서 실행. 콜백은 Main에서 호출.
     */
    fun download(
        context: Context,
        modelKey: String,
        onProgress: (percent: Int) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val spec = SPECS[modelKey] ?: run {
            onError(IllegalArgumentException("알 수 없는 모델: $modelKey"))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tmpFile = File(context.cacheDir, "$modelKey.tmp")
                val destDir = File(context.filesDir, spec.destDir)

                // ── 다운로드 ─────────────────────────────────────
                Log.i(TAG, "다운로드 시작: ${spec.url}")
                downloadFile(spec.url, tmpFile) { pct ->
                    CoroutineScope(Dispatchers.Main).launch { onProgress(pct) }
                }

                // ── 압축 해제 ────────────────────────────────────
                Log.i(TAG, "압축 해제: ${spec.archiveType}")
                withContext(Dispatchers.Main) { onProgress(95) }

                destDir.mkdirs()
                when (spec.archiveType) {
                    "zip"     -> extractZip(tmpFile, destDir)
                    "tar.bz2" -> extractTarBz2(tmpFile, destDir)
                }

                tmpFile.delete()
                Log.i(TAG, "설치 완료: ${destDir.absolutePath}")

                withContext(Dispatchers.Main) {
                    onProgress(100)
                    onComplete()
                }

            } catch (e: Exception) {
                Log.e(TAG, "다운로드 실패: ${e.message}")
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    // ── 파일 다운로드 ─────────────────────────────────────────────

    private fun downloadFile(
        urlStr: String,
        dest: File,
        onProgress: (Int) -> Unit
    ) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            connect()
        }

        val total = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) {
                        // 0~90% 구간을 다운로드에 할당 (나머지 10%는 압축해제)
                        onProgress((downloaded * 90 / total).toInt())
                    }
                }
            }
        }
        conn.disconnect()
    }

    // ── ZIP 압축 해제 ─────────────────────────────────────────────

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // 최상위 폴더 제거 (vosk-model-small-en-us-0.15/xxx → xxx)
                val relativePath = entry.name.substringAfter("/")
                if (relativePath.isBlank()) { zis.closeEntry(); entry = zis.nextEntry; continue }

                val file = File(destDir, relativePath)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // ── TAR.BZ2 압축 해제 ────────────────────────────────────────

    private fun extractTarBz2(archiveFile: File, destDir: File) {
        TarArchiveInputStream(
            BZip2CompressorInputStream(archiveFile.inputStream().buffered())
        ).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                // 최상위 폴더 제거
                val relativePath = entry.name.substringAfter("/")
                if (relativePath.isBlank()) { entry = tar.nextTarEntry; continue }

                val file = File(destDir, relativePath)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { tar.copyTo(it) }
                }
                entry = tar.nextTarEntry
            }
        }
    }
}
