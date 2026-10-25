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

    private lateinit var btnLight: Button
    private lateinit var btnDark: Button
    private lateinit var btnSystem: Button
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var switchDynamic: MaterialSwitch
    private lateinit var switchBackground: MaterialSwitch
    private lateinit var switchBlur: MaterialSwitch
    private lateinit var seekBlur: SeekBar
    private lateinit var tvBlur: TextView
    private lateinit var seekOpacity: SeekBar
    private lateinit var tvOpacity: TextView

    private val pickBackground = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val ctx = applicationContext
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        prefs.edit().putString("custom_background", uri.toString()).apply()
        switchBackground.isChecked = true
        applyBackground()
        Toast.makeText(ctx, "背景已设置", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)

        setSupportActionBar(findViewById(R.id.topAppBar))
        supportActionBar?.title = "主题设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)

        btnLight = findViewById(R.id.btnThemeLight)
        btnDark = findViewById(R.id.btnThemeDark)
        btnSystem = findViewById(R.id.btnThemeSystem)
        modeGroup = findViewById(R.id.themeModeGroup)
        switchDynamic = findViewById(R.id.switchDynamicColor)
        switchBackground = findViewById(R.id.switchCustomBg)
        switchBlur = findViewById(R.id.switchBlur)
        seekBlur = findViewById(R.id.seekBlur)
        tvBlur = findViewById(R.id.tvBlurStrength)
        seekOpacity = findViewById(R.id.seekCardOpacity)
        tvOpacity = findViewById(R.id.tvCardOpacity)

        load()

        btnLight.setOnClickListener { setMode("light") }
        btnDark.setOnClickListener { setMode("dark") }
        btnSystem.setOnClickListener { setMode("system") }

        switchDynamic.setOnCheckedChangeListener { _, checked ->
            markChanged()
            ThemeHelper.setDynamicColor(this, checked)
            recreate()
        }

        findViewById<View>(R.id.colorPurple).setOnClickListener { setColor("purple") }
        findViewById<View>(R.id.colorBlue).setOnClickListener { setColor("blue") }
        findViewById<View>(R.id.colorGreen).setOnClickListener { setColor("green") }
        findViewById<View>(R.id.colorPink).setOnClickListener { setColor("pink") }
        findViewById<View>(R.id.colorOrange).setOnClickListener { setColor("orange") }

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacity.text = "$progress%"
                prefs.edit().putInt("card_opacity", progress).apply()
                ThemeHelper.applyCardOpacity(this@ThemeActivity)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        switchBlur.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("enable_blur", checked).apply()
            seekBlur.isEnabled = checked
            applyBackground()
        }

        seekBlur.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvBlur.text = "$progress%"
                prefs.edit().putInt("blur_strength", progress).apply()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {
                applyBackground()
            }
        })

        switchBackground.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                prefs.edit().remove("custom_background").apply()
                applyBackground()
            }
        }

        findViewById<View>(R.id.btnSelectBg).setOnClickListener {
            runCatching { pickBackground.launch(arrayOf("image/*")) }
        }

        findViewById<View>(R.id.btnResetBg).setOnClickListener {
            prefs.edit().remove("custom_background").apply()
            switchBackground.isChecked = false
            applyBackground()
        }
    }

    private fun load() {
        when (prefs.getString("theme_mode", "system")) {
            "light" -> highlight(btnLight)
            "dark" -> highlight(btnDark)
            else -> highlight(btnSystem)
        }

        val dynamic = prefs.getBoolean("use_dynamic_color", false)
        switchDynamic.isChecked = dynamic

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

        val hasBg = !prefs.getString("custom_background", "").isNullOrEmpty()
        switchBackground.isChecked = hasBg

        val blur = prefs.getBoolean("enable_blur", false)
        switchBlur.isChecked = blur
        val strength = prefs.getInt("blur_strength", 40)
        seekBlur.progress = strength
        seekBlur.isEnabled = blur
        tvBlur.text = "$strength%"

        val opacity = ThemeHelper.cardOpacity(this)
        seekOpacity.progress = opacity
        tvOpacity.text = "$opacity%"
    }

    private fun setMode(mode: String) {
        markChanged()
        ThemeHelper.setThemeMode(this, mode)
        when (mode) {
            "light" -> highlight(btnLight)
            "dark" -> highlight(btnDark)
            else -> highlight(btnSystem)
        }
    }

    private fun setColor(name: String) {
        markChanged()
        ThemeHelper.setSeedColor(this, ThemeHelper.seedFor(name))
        recreate()
    }

    private fun highlight(button: Button) {
        if (modeGroup.checkedButtonId != button.id) modeGroup.check(button.id)
    }

    private fun markChanged() {
        prefs.edit().putBoolean("theme_changed", true).apply()
    }

    private fun applyBackground() {
        val root = findViewById<View>(R.id.themeRoot)
        val value = prefs.getString("custom_background", "")
        val uri = if (value.isNullOrEmpty()) null else Uri.parse(value)
        BackgroundHelper.apply(root, uri, ThemeHelper.blurStrength(this))
    }

    override fun onResume() {
        super.onResume()
        applyBackground()
        ThemeHelper.applyCardOpacity(this)
    }

    override fun finish() {
        if (::prefs.isInitialized) {
            setResult(if (prefs.getBoolean("theme_changed", false)) RESULT_OK else RESULT_CANCELED)
        }
        super.finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
