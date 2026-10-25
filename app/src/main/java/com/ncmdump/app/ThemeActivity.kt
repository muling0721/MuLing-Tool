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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

class ThemeActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var btnThemeLight: Button
    private lateinit var btnThemeDark: Button
    private lateinit var btnThemeSystem: Button
    private lateinit var seekCardOpacity: SeekBar
    private lateinit var tvCardOpacity: TextView
    private lateinit var switchBlur: MaterialSwitch
    private lateinit var seekBlur: SeekBar
    private lateinit var tvBlurStrength: TextView
    private lateinit var switchCustomBg: MaterialSwitch
    private lateinit var switchDynamicColor: MaterialSwitch
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
            } catch (_: Exception) {
            }
            prefs.edit().putString("custom_background", it.toString()).apply()
            switchCustomBg.isChecked = true
            applyCustomBackground()
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
        seekBlur = findViewById(R.id.seekBlur)
        tvBlurStrength = findViewById(R.id.tvBlurStrength)
        switchCustomBg = findViewById(R.id.switchCustomBg)
        switchDynamicColor = findViewById(R.id.switchDynamicColor)
        btnSelectBg = findViewById(R.id.btnSelectBg)
        btnResetBg = findViewById(R.id.btnResetBg)
        themeModeGroup = findViewById(R.id.themeModeGroup)

        loadSettings()

        btnThemeLight.setOnClickListener { setThemeMode("light") }
        btnThemeDark.setOnClickListener { setThemeMode("dark") }
        btnThemeSystem.setOnClickListener { setThemeMode("system") }

        findViewById<View>(R.id.colorPurple).setOnClickListener { setThemeColor("purple") }
        findViewById<View>(R.id.colorBlue).setOnClickListener { setThemeColor("blue") }
        findViewById<View>(R.id.colorGreen).setOnClickListener { setThemeColor("green") }
        findViewById<View>(R.id.colorPink).setOnClickListener { setThemeColor("pink") }
        findViewById<View>(R.id.colorOrange).setOnClickListener { setThemeColor("orange") }

        switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            ThemeHelper.setDynamicColor(this, isChecked)
            recreate()
        }

        seekCardOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvCardOpacity.text = "$progress%"
                prefs.edit().putInt("card_opacity", progress).apply()
                ThemeHelper.applyCardOpacity(this@ThemeActivity)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchBlur.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_blur", isChecked).apply()
            seekBlur.isEnabled = isChecked
            applyCustomBackground()
        }

        seekBlur.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBlurStrength.text = "$progress%"
                prefs.edit().putInt("blur_strength", progress).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                applyCustomBackground()
            }
        })

        switchCustomBg.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                prefs.edit().remove("custom_background").apply()
                applyCustomBackground()
            }
        }

        btnSelectBg.setOnClickListener {
            try {
                pickBackground.launch(arrayOf("image/*"))
            } catch (_: Exception) {
                Toast.makeText(this, "打开选择器失败", Toast.LENGTH_SHORT).show()
            }
        }

        btnResetBg.setOnClickListener {
            prefs.edit().remove("custom_background").apply()
            switchCustomBg.isChecked = false
            applyCustomBackground()
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

        val blurEnabled = prefs.getBoolean("enable_blur", false)
        switchBlur.isChecked = blurEnabled
        val blurStrength = prefs.getInt("blur_strength", 40)
        seekBlur.progress = blurStrength
        seekBlur.isEnabled = blurEnabled
        tvBlurStrength.text = "$blurStrength%"
        switchCustomBg.isChecked = hasBg

        val dynamic = prefs.getBoolean("use_dynamic_color", false)
        switchDynamicColor.isChecked = dynamic
        val seedName = ThemeHelper.seedName(prefs.getInt("theme_seed_color", ThemeHelper.DEFAULT_SEED))
        val colors = listOf(
            "purple" to R.id.colorPurple,
            "blue" to R.id.colorBlue,
            "green" to R.id.colorGreen,
            "pink" to R.id.colorPink,
            "orange" to R.id.colorOrange
        )
        colors.forEach { (name, id) ->
            findViewById<View>(id).alpha = if (!dynamic && name == seedName) 1f else 0.4f
        }
    }

    private fun setThemeMode(mode: String) {
        ThemeHelper.setThemeMode(this, mode)
        when (mode) {
            "light" -> highlightThemeButton(btnThemeLight)
            "dark" -> highlightThemeButton(btnThemeDark)
            else -> highlightThemeButton(btnThemeSystem)
        }
    }

    private fun setThemeColor(color: String) {
        ThemeHelper.setSeedColor(this, ThemeHelper.seedFor(color))
        Toast.makeText(this, "已应用主题颜色", Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun highlightThemeButton(active: Button) {
        if (themeModeGroup.checkedButtonId != active.id) {
            themeModeGroup.check(active.id)
        }
    }

    override fun onResume() {
        super.onResume()
        applyCustomBackground()
        ThemeHelper.applyCardOpacity(this)
    }

    private fun applyCustomBackground() {
        val root = findViewById<View>(android.R.id.content)
        val bgUriStr = prefs.getString("custom_background", "")
        val uri = if (bgUriStr.isNullOrEmpty()) null else Uri.parse(bgUriStr)
        BackgroundHelper.apply(root, uri, ThemeHelper.blurStrength(this))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
