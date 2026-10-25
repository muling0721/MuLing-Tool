package com.ncmdump.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlin.concurrent.thread

class AudioParseFragment : Fragment() {

    private lateinit var btnSelect: Button
    private lateinit var btnDecrypt: Button
    private lateinit var btnGrantPermission: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvFileList: TextView
    private lateinit var tvPermissionHint: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutControls: View
    private lateinit var cardPermission: View

    private val selectedFiles = mutableListOf<Uri>()

    private val requestLegacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPermissionGranted()
        } else {
            Toast.makeText(requireContext(), "存储权限被拒绝，无法使用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio_parse, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelect = view.findViewById(R.id.btnSelect)
        btnDecrypt = view.findViewById(R.id.btnDecrypt)
        btnGrantPermission = view.findViewById(R.id.btnGrantPermission)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvFileList = view.findViewById(R.id.tvFileList)
        tvPermissionHint = view.findViewById(R.id.tvPermissionHint)
        progressBar = view.findViewById(R.id.progressBar)
        layoutControls = view.findViewById(R.id.layoutControls)
        cardPermission = view.findViewById(R.id.cardPermission)

        btnGrantPermission.setOnClickListener { requestAllFilesPermission() }

        btnSelect.setOnClickListener {
            if (!hasStoragePermission()) { showPermissionRequired(); return@setOnClickListener }
            try { (requireActivity() as MainActivity).openFilePicker() }
            catch (e: Exception) { Toast.makeText(requireContext(), "打开文件选择器失败: ${e.message}", Toast.LENGTH_SHORT).show() }
        }

        btnDecrypt.setOnClickListener {
            if (!hasStoragePermission()) { showPermissionRequired(); return@setOnClickListener }
            if (selectedFiles.isEmpty()) {
                Toast.makeText(requireContext(), "请先选择 ncm 文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startDecrypt()
        }

        checkPermissionAndInit()
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) onPermissionGranted()
        // 恢复时刷新已选择文件的显示
        if (selectedFiles.isNotEmpty() && view != null) {
            updateFileList()
            tvStatus.text = "已选择 ${selectedFiles.size} 个文件，点击开始解密"
        }
    }

    // 宿主Activity回调：文件选择结果
    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            selectedFiles.clear()
            selectedFiles.addAll(uris)
            if (view != null) {
                updateFileList()
                tvStatus.text = "已选择 ${uris.size} 个文件，点击开始解密"
            }
        }
    }

    private fun checkPermissionAndInit() {
        if (hasStoragePermission()) onPermissionGranted() else showPermissionRequired()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                catch (_: Exception) { Toast.makeText(requireContext(), "请在设置中手动开启所有文件访问权限", Toast.LENGTH_LONG).show() }
            }
        } else {
            requestLegacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionRequired() {
        cardPermission.visibility = View.VISIBLE
        layoutControls.visibility = View.GONE
        tvPermissionHint.text = "需要「所有文件访问权限」才能读取和解密 ncm 文件\n\n请点击下方按钮，在设置中允许访问所有文件"
    }

    private fun onPermissionGranted() {
        cardPermission.visibility = View.GONE
        layoutControls.visibility = View.VISIBLE
        if (tvStatus.text.isNullOrEmpty() || tvStatus.text == "等待选择文件...") {
            tvStatus.text = "权限已授予，请选择 ncm 文件"
        }
    }

    private fun updateFileList() {
        val sb = StringBuilder()
        sb.append("已选择 ${selectedFiles.size} 个文件:\n\n")
        selectedFiles.forEachIndexed { index, uri ->
            sb.append("${index + 1}. ${getDocumentFileName(uri)}\n")
        }
        tvFileList.text = sb.toString()
    }

    private fun getDocumentFileName(uri: Uri): String {
        return try {
            var name = ""
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) name = cursor.getString(nameIndex)
            }
            name.ifEmpty { uri.lastPathSegment ?: "unknown" }
        } catch (e: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }

    private fun copyUriToCache(uri: Uri): String? {
        val fileName = getDocumentFileName(uri)
        val cacheFile = java.io.File(requireContext().cacheDir, fileName)
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null
        } catch (e: Exception) { null }
    }

    private fun getOutputDirPath(): String = OutputDir.get(requireContext()).absolutePath

    private fun startDecrypt() {
        btnDecrypt.isEnabled = false
        progressBar.progress = 0
        progressBar.max = selectedFiles.size
        tvStatus.text = "正在解密..."

        thread {
            val outputDir = getOutputDirPath()
            var success = 0
            var failed = 0

            selectedFiles.forEachIndexed { index, uri ->
                val cachePath = copyUriToCache(uri)
                if (cachePath != null) {
                    try {
                        val metadataJson = NcmCrypt.getMetadata(cachePath)
                        val coverBytes = NcmCrypt.getCoverImage(cachePath)
                        val coverMime = NcmCrypt.getCoverMime(cachePath)

                        val result = NcmCrypt.decrypt(cachePath, outputDir)
                        if (result.isNotEmpty()) {
                            try {
                                val json = org.json.JSONObject(metadataJson)
                                val title = json.optString("name", "")
                                val artist = json.optString("artist", "")
                                val album = json.optString("album", "")
                                val safeCover = if (coverBytes.size > 10 * 1024 * 1024) null else coverBytes

                                try { MetadataWriter.write(result, title, artist, album, safeCover, coverMime, null) } catch (_: Throwable) {}

                                try {
                                    if (title.isNotEmpty()) {
                                        val lyrics = LyricFetcher.fetchLyrics(title, artist)
                                        if (!lyrics.isNullOrEmpty()) {
                                            val audioFile = java.io.File(result)
                                            val lrcName = audioFile.nameWithoutExtension + ".lrc"
                                            val lrcFile = java.io.File(audioFile.parentFile, lrcName)
                                            lrcFile.writeText(lyrics, Charsets.UTF_8)
                                        }
                                    }
                                } catch (_: Throwable) {}
                            } catch (_: Throwable) {}
                            success++
                        } else { failed++ }
                    } catch (e: UnsatisfiedLinkError) {
                        failed++
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "原生库加载失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Throwable) { failed++ }
                    finally {
                        try { java.io.File(cachePath).delete() } catch (_: Exception) {}
                    }
                } else { failed++ }

                requireActivity().runOnUiThread {
                    progressBar.progress = index + 1
                    tvStatus.text = "解密中... ${index + 1}/${selectedFiles.size}"
                }
            }

            requireActivity().runOnUiThread {
                btnDecrypt.isEnabled = true
                tvStatus.text = "完成: 成功 $success, 失败 $failed\n输出到: $outputDir"
                Toast.makeText(requireContext(), "解密完成", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
