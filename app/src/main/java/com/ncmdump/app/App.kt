package com.ncmdump.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        val prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)
        if (BuildConfig.DEBUG && !prefs.getBoolean("debug_auto_update_defaulted", false)) {
            prefs.edit()
                .putBoolean("auto_update", false)
                .putBoolean("debug_auto_update_defaulted", true)
                .apply()
        }
    }
}
