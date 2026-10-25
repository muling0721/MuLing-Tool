package com.ncmdump.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    private lateinit var tvVersion: TextView
    private lateinit var tvOutputDir: TextView
    private lateinit var switchAutoUpdate: MaterialSwitch
    private lateinit var prefs: SharedPreferences

    private val pickDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val ctx = context ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val path = resolveTreeUriToPath(uri)
        if (path.isBlank()) {
            Toast.makeText(ctx, "无法识别该目录，请选择内部存储中的目录", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        OutputDir.set(ctx, path)
        refreshOutputDir()
        Toast.makeText(ctx, "输出目录：$path", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("ncmdump", android.content.Context.MODE_PRIVATE)

        tvVersion = view.findViewById(R.id.tvVersion)
        tvOutputDir = view.findViewById(R.id.tvOutputDir)
        switchAutoUpdate = view.findViewById(R.id.switchAutoUpdate)

        try {
            val pkgInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            tvVersion.text = "版本 ${pkgInfo.versionName}"
        } catch (_: Exception) {
            tvVersion.text = "版本 1.5.1"
        }

        switchAutoUpdate.isChecked = prefs.getBoolean("auto_update", true)
        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_update", isChecked).apply()
            Toast.makeText(requireContext(), if (isChecked) "已开启自动检测更新" else "已关闭自动检测更新", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnCheckUpdate).setOnClickListener {
            UpdateChecker.checkUpdate(requireContext(), showNoUpdate = true)
        }

        view.findViewById<View>(R.id.cardThemeSettings).setOnClickListener {
            startActivity(Intent(requireContext(), ThemeActivity::class.java))
        }

        view.findViewById<View>(R.id.cardGithub).setOnClickListener {
            val url = "https://github.com/muling0721/MuLing-Tool"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        view.findViewById<View>(R.id.cardOutputDir).setOnClickListener {
            chooseOutputDir()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOutputDir()
    }

    private fun refreshOutputDir() {
        tvOutputDir.text = OutputDir.display(requireContext())
    }

    private fun chooseOutputDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(requireContext(), "请先授予「所有文件访问权限」", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${requireContext().packageName}"))
                )
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
            return
        }
        pickDir.launch(null)
    }

    private fun resolveTreeUriToPath(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val colon = docId.indexOf(':')
            if (colon < 0) return ""
            val volume = docId.substring(0, colon)
            val rel = docId.substring(colon + 1)
            val base = when {
                volume == "primary" -> Environment.getExternalStorageDirectory().absolutePath
                else -> "/storage/$volume"
            }
            if (rel.isBlank()) base else "$base/$rel"
        } catch (_: Exception) {
            ""
        }
    }
}
