package com.ncmdump.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyNightMode(this)
        ThemeHelper.applyColors(this)
        super.onCreate(savedInstanceState)
    }
}
