package com.ncmdump.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 下载到统一输出目录（替代 DownloadManager，避免自定义目录写不进去）。
 */
object DownloadHelper {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun download(
        context: Context,
        url: String,
        file: File,
        referer: String? = null
    ) {
        val app = context.applicationContext
        thread {
            var ok = false
            var message = ""
            try {
                file.parentFile?.mkdirs()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", UA)
                if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)

                val code = conn.responseCode
                if (code in 200..299) {
                    conn.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output, 64 * 1024)
                        }
                    }
                    ok = file.length() > 0
                    message = if (ok) file.absolutePath else "文件为空"
                } else {
                    message = "HTTP $code"
                }
                conn.disconnect()
            } catch (e: Exception) {
                message = e.message ?: "下载失败"
            }

            val resultOk = ok
            val resultMsg = message
            mainHandler.post {
                Toast.makeText(
                    app,
                    if (resultOk) "已保存：${file.name}" else "下载失败：$resultMsg",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
