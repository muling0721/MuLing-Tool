package com.ncmdump.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var tvVersion: TextView
    private lateinit var tvOutputDir: TextView
    private lateinit var switchAutoUpdate: MaterialSwitch
    private lateinit var prefs: SharedPreferences

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
            tvVersion.text = "版本 1.4"
        }

        // 自动检测更新开关
        switchAutoUpdate.isChecked = prefs.getBoolean("auto_update", true)
        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_update", isChecked).apply()
            Toast.makeText(requireContext(), if (isChecked) "已开启自动检测更新" else "已关闭自动检测更新", Toast.LENGTH_SHORT).show()
        }

        // 手动检查更新
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
    }

    override fun onResume() {
        super.onResume()
        val lastDir = prefs.getString("last_output_dir", "")
        tvOutputDir.text = if (lastDir.isNullOrEmpty()) "默认（应用目录）" else lastDir
    }
}
