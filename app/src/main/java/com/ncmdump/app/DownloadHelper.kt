package com.ncmdump.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

object DownloadHelper {

    private const val CHANNEL_ID = "downloads"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val mainHandler = Handler(Looper.getMainLooper())

    enum class State { RUNNING, DONE, FAILED }
    data class Progress(val id: Int, val title: String, val percent: Int, val state: State)
    interface Listener { fun onProgress(progress: Progress) }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun emit(id: Int, title: String, percent: Int, state: State) {
        val progress = Progress(id, title, percent, state)
        listeners.forEach { runCatching { it.onProgress(progress) } }
    }

    fun download(
        context: Context,
        url: String,
        file: File,
        referer: String? = null,
        title: String = file.name
    ) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val id = file.absolutePath.hashCode()
        notifyProgress(app, manager, id, title, 0, 0)
        emit(id, title, 0, State.RUNNING)

        thread {
            var ok = false
            var message = ""
            var saved: File = file
            try {
                file.parentFile?.mkdirs()
                var current = URL(url)
                var redirects = 0

                while (true) {
                    val conn = current.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("User-Agent", UA)
                    if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)

                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank() || redirects++ > 8) {
                            throw IOException("重定向异常 HTTP $code")
                        }
                        current = URL(current, location)
                        continue
                    }
                    if (code != 200 && code != 206) {
                        conn.disconnect()
                        throw IOException("HTTP $code")
                    }

                    // 服务端返回真实容器格式时纠正扩展名，避免出现「后缀是 mp4、内容却是 webm」的情况
                    saved = resolveTargetFile(file, conn.contentType, current.toString())

                    val total = conn.contentLengthLong
                    var written = 0L
                    var lastPercent = -1
                    var lastTime = 0L
                    conn.inputStream.use { input ->
                        saved.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val percent = (written * 100 / total).toInt()
                                    val now = System.currentTimeMillis()
                                    if (percent != lastPercent && now - lastTime > 300) {
                                        lastPercent = percent
                                        lastTime = now
                                        notifyProgress(app, manager, id, title, percent, 100)
                                        emit(id, title, percent, State.RUNNING)
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    break
                }

                if (saved.length() <= 0) throw IOException("文件为空")
                ok = true
                message = saved.absolutePath
                DownloadHistory.add(app, title, saved.absolutePath)
                notifyDone(app, manager, id, title)
                emit(id, title, 100, State.DONE)
            } catch (e: Exception) {
                runCatching { if (saved.length() <= 0) saved.delete() }
                message = e.message ?: "下载失败"
                notifyFailed(app, manager, id, title, message)
                emit(id, title, 0, State.FAILED)
            }

            val finalOk = ok
            val finalMessage = message
            val finalFile = saved
            mainHandler.post {
                Toast.makeText(
                    app,
                    if (finalOk) "已保存：${finalFile.name}" else "下载失败：$finalMessage",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "flv", "avi")
    private val audioExtensions = setOf("mp3", "flac", "aac", "m4a", "ogg", "wav", "opus")

    /**
     * 根据 Content-Type / URL 纠正输出文件扩展名。
     * 流媒体播放列表（m3u8）无法直接保存为文件，直接报错。
     */
    private fun resolveTargetFile(original: File, contentType: String?, url: String): File {
        val ct = contentType?.lowercase().orEmpty()
        val fromContentType = when {
            ct.contains("mpegurl") -> "m3u8"
            ct.contains("webm") -> "webm"
            ct.contains("matroska") -> "mkv"
            ct.contains("mp4") -> "mp4"
            ct.contains("quicktime") -> "mov"
            ct.contains("mpeg") -> "mp3"
            ct.contains("flac") -> "flac"
            ct.contains("aac") -> "aac"
            ct.contains("m4a") -> "m4a"
            ct.contains("opus") -> "opus"
            ct.contains("ogg") -> "ogg"
            ct.contains("wav") -> "wav"
            else -> null
        }
        val fromUrl = url.substringBefore('?').substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in videoExtensions || it in audioExtensions }

        val wanted = fromContentType ?: fromUrl
        if (wanted == "m3u8") {
            throw IOException("该资源为流媒体（m3u8），无法直接保存为文件")
        }
        if (wanted == null) {
            // 视频默认 mp4，其余保持原名
            return if (original.extension.lowercase() in audioExtensions) original else {
                if (original.name.isEmpty()) File(original.parentFile, "video.mp4") else original
            }
        }
        if (original.extension.equals(wanted, ignoreCase = true)) return original
        return File(original.parentFile, "${original.nameWithoutExtension}.$wanted")
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "下载",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun notifyProgress(
        context: Context,
        manager: NotificationManager,
        id: Int,
        title: String,
        progress: Int,
        max: Int
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(if (max > 0) "$progress%" else "准备下载…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (max > 0) builder.setProgress(100, progress, false) else builder.setProgress(0, 0, true)
        manager.notify(id, builder.build())
    }

    private fun notifyDone(context: Context, manager: NotificationManager, id: Int, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText("下载完成")
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        manager.notify(id, notification)
    }

    private fun notifyFailed(
        context: Context,
        manager: NotificationManager,
        id: Int,
        title: String,
        message: String
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText("下载失败：$message")
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        manager.notify(id, notification)
    }
}
