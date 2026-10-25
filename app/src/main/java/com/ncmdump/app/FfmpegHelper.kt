package com.ncmdump.app

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import java.io.File

object FfmpegHelper {

    val audioFormats = listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "opus", "wma")
    private val losslessFormats = setOf("wav", "flac")

    private fun getOutputDir(context: Context): File = OutputDir.get(context)

    fun convertAudio(
        context: Context,
        inputUri: Uri,
        outputFormat: String,
        bitrate: String = "192k",
        callback: (Boolean, String?) -> Unit
    ) {
        val inputPath = FileUtils.getPathFromUri(context, inputUri) ?: run {
            callback(false, "无法获取文件路径")
            return
        }
        val inputFile = File(inputPath)
        val outputName = inputFile.nameWithoutExtension + ".$outputFormat"
        val outputFile = File(getOutputDir(context), outputName)

        val cmd = buildString {
            append("-y -i ").append(quotePath(inputPath))
            if (outputFormat !in losslessFormats) {
                append(" -b:a ").append(bitrate)
            }
            append(" ").append(quotePath(outputFile.absolutePath))
        }

        FFmpegKit.executeAsync(cmd) { session ->
            if (session.returnCode.isValueSuccess) {
                callback(true, outputFile.absolutePath)
            } else {
                callback(false, session.output ?: "未知错误")
            }
        }
    }

    fun trimAudio(
        context: Context,
        inputUri: Uri,
        startTime: String,
        duration: String,
        outputFormat: String = "mp3",
        callback: (Boolean, String?) -> Unit
    ) {
        val inputPath = FileUtils.getPathFromUri(context, inputUri) ?: run {
            callback(false, "无法获取文件路径")
            return
        }
        val inputFile = File(inputPath)
        val outputName = "${inputFile.nameWithoutExtension}_trim.$outputFormat"
        val outputFile = File(getOutputDir(context), outputName)

        val cmd = "-y -ss $startTime -t $duration -i ${quotePath(inputPath)} ${quotePath(outputFile.absolutePath)}"

        FFmpegKit.executeAsync(cmd) { session ->
            if (session.returnCode.isValueSuccess) {
                callback(true, outputFile.absolutePath)
            } else {
                callback(false, session.output ?: "未知错误")
            }
        }
    }

    fun mergeAudio(
        context: Context,
        inputUris: List<Uri>,
        outputFormat: String = "mp3",
        callback: (Boolean, String?) -> Unit
    ) {
        if (inputUris.size < 2) {
            callback(false, "至少需要两个音频文件")
            return
        }

        val listFile = File(context.cacheDir, "concat_list.txt")
        val sb = StringBuilder()
        val paths = mutableListOf<String>()
        for (uri in inputUris) {
            val path = FileUtils.getPathFromUri(context, uri)
            if (path != null) {
                paths.add(path)
                sb.append("file '").append(path.replace("'", "'\\''")).append("'\n")
            }
        }
        listFile.writeText(sb.toString())

        if (paths.size < 2) {
            callback(false, "无法获取文件路径")
            return
        }

        val outputName = "merged_${System.currentTimeMillis()}.$outputFormat"
        val outputFile = File(getOutputDir(context), outputName)

        val cmd = "-y -f concat -safe 0 -i ${quotePath(listFile.absolutePath)} -c copy ${quotePath(outputFile.absolutePath)}"

        FFmpegKit.executeAsync(cmd) { session ->
            if (session.returnCode.isValueSuccess) {
                listFile.delete()
                callback(true, outputFile.absolutePath)
            } else {
                // copy失败时重新编码
                val cmd2 = "-y -f concat -safe 0 -i ${quotePath(listFile.absolutePath)} ${quotePath(outputFile.absolutePath)}"
                FFmpegKit.executeAsync(cmd2) { session2 ->
                    listFile.delete()
                    if (session2.returnCode.isValueSuccess) {
                        callback(true, outputFile.absolutePath)
                    } else {
                        callback(false, session2.output ?: "未知错误")
                    }
                }
            }
        }
    }

    private fun quotePath(path: String): String {
        return if (path.contains(" ")) "\"$path\"" else path
    }

    fun cancelAll() {
        FFmpegKit.cancel()
    }
}
