package com.ncmdump.app.ui.util

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/** 是否有可用的背景采样层（决定底栏是否走玻璃分支） */
val LocalBlurState = compositionLocalOf<LayerBackdrop?> { null }
