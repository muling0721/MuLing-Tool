package com.ncmdump.app

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

class ThemeActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var btnThemeLight: Button
    private lateinit var btnThemeDark: Button
    private lateinit var btnThemeSystem: Button
    private lateinit var seekCardOpacity: SeekBar
    private lateinit var tvCardOpacity: TextView
    private lateinit var switchBlur: MaterialSwitch
    private lateinit var switchCustomBg: MaterialSwitch
    private lateinit var btnSelectBg: Button
    private lateinit var btnResetBg: Button
    private lateinit var themeModeGroup: MaterialButtonToggleGroup

    private val pickBackground = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            prefs.edit().putString("custom_background", it.toString()).apply()
            switchCustomBg.isChecked = true
            Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)

        setSupportActionBar(findViewById(R.id.topAppBar))
        supportActionBar?.title = "主题设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)

        btnThemeLight = findViewById(R.id.btnThemeLight)
        btnThemeDark = findViewById(R.id.btnThemeDark)
        btnThemeSystem = findViewById(R.id.btnThemeSystem)
        seekCardOpacity = findViewById(R.id.seekCardOpacity)
        tvCardOpacity = findViewById(R.id.tvCardOpacity)
        switchBlur = findViewById(R.id.switchBlur)
        switchCustomBg = findViewById(R.id.switchCustomBg)
        btnSelectBg = findViewById(R.id.btnSelectBg)
        btnResetBg = findViewById(R.id.btnResetBg)
        themeModeGroup = findViewById(R.id.themeModeGroup)

        loadSettings()
        applyCustomBackground()

        btnThemeLight.setOnClickListener { setThemeMode("light") }
        btnThemeDark.setOnClickListener { setThemeMode("dark") }
        btnThemeSystem.setOnClickListener { setThemeMode("system") }

        findViewById<View>(R.id.colorPurple).setOnClickListener { setThemeColor("purple") }
        findViewById<View>(R.id.colorBlue).setOnClickListener { setThemeColor("blue") }
        findViewById<View>(R.id.colorGreen).setOnClickListener { setThemeColor("green") }
        findViewById<View>(R.id.colorPink).setOnClickListener { setThemeColor("pink") }
        findViewById<View>(R.id.colorOrange).setOnClickListener { setThemeColor("orange") }

        seekCardOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvCardOpacity.text = "$progress%"
                prefs.edit().putInt("card_opacity", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchBlur.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_blur", isChecked).apply()
        }

        switchCustomBg.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                prefs.edit().remove("custom_background").apply()
            }
        }

        btnSelectBg.setOnClickListener {
            try { pickBackground.launch(arrayOf("image/*")) }
            catch (e: Exception) { Toast.makeText(this, "打开选择器失败", Toast.LENGTH_SHORT).show() }
        }

        btnResetBg.setOnClickListener {
            prefs.edit().remove("custom_background").apply()
            switchCustomBg.isChecked = false
            Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        when (prefs.getString("theme_mode", "system")) {
            "light" -> highlightThemeButton(btnThemeLight)
            "dark" -> highlightThemeButton(btnThemeDark)
            else -> highlightThemeButton(btnThemeSystem)
        }

        val hasBg = prefs.getString("custom_background", "").isNullOrEmpty().not()
        val opacity = prefs.getInt("card_opacity", if (hasBg) 72 else 100)
        seekCardOpacity.progress = opacity
        tvCardOpacity.text = "$opacity%"

        switchBlur.isChecked = prefs.getBoolean("enable_blur", false)

        val hasBg = prefs.getString("custom_background", "").isNullOrEmpty().not()
        switchCustomBg.isChecked = hasBg
    }

    private fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        when (mode) {
            "light" -> highlightThemeButton(btnThemeLight)
            "dark" -> highlightThemeButton(btnThemeDark)
            else -> highlightThemeButton(btnThemeSystem)
        }
    }

    private fun highlightThemeButton(active: Button) {
        if (themeModeGroup.checkedButtonId != active.id) {
            themeModeGroup.check(active.id)
        }
    }

    private fun setThemeColor(color: String) {
        prefs.edit().putString("theme_color", color).apply()
    }

    override fun onResume() {
        super.onResume()
        applyCustomBackground()
    }

    private fun applyCustomBackground() {
        val bgUriStr = prefs.getString("custom_background", "")
        val rootView = findViewById<View>(android.R.id.content)

        if (!bgUriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(bgUriStr)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val bitmapDrawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                    bitmapDrawable.gravity = android.view.Gravity.FILL
                    val surface = com.google.android.material.color.MaterialColors.getColor(
                        rootView, com.google.android.material.R.attr.colorSurface
                    )
                    val scrim = android.graphics.drawable.ColorDrawable(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 0x5A)
                    )
                    rootView.background = android.graphics.drawable.LayerDrawable(
                        arrayOf(bitmapDrawable, scrim)
                    )
                    rootView.foreground = null
                    return
                }
            } catch (_: Exception) {
                // 回退到默认背景
            }
        }
        rootView.setBackgroundResource(R.color.bg_main)
        rootView.foreground = null
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
