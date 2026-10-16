package com.ncmdump.app

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * 应用入口：启用 Material You 动态取色（Android 12+），
 * 使界面跟随系统主题色，呈现 KernelSU 同款观感。
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
