package com.ncmdump.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyNightMode(this)
        ThemeHelper.applyDynamic(this)
        super.onCreate(savedInstanceState)
        ThemeHelper.applyPalette(this)
    }
}
