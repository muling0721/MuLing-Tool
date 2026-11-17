package com.ncmdump.app

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // 测试版默认关闭自动更新（避免测试包反复提示升级）
        val prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)
        if (BuildConfig.DEBUG && !prefs.getBoolean("debug_auto_update_defaulted", false)) {
            prefs.edit()
                .putBoolean("auto_update", false)
                .putBoolean("debug_auto_update_defaulted", true)
                .apply()
        }
    }
}
