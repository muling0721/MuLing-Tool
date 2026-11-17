package com.ncmdump.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ncmdump.app.ui.LocalAppState
import com.ncmdump.app.ui.theme.LocalIsDark
import androidx.compose.material3.MaterialTheme

/**
 * 应用背景：主题渐变 + 顶部主色光晕，可选自定义背景图（含模糊与遮罩）。
 * 对应旧版 BackgroundHelper 的功能。
 */
@Composable
fun MuLingBackground(
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = LocalAppState.current
    val scheme = MaterialTheme.colorScheme
    val dark = LocalIsDark.current

    Box(modifier = modifier.fillMaxSize()) {
    // 背景层单独一层，玻璃采样只取这一层（和 BakaSU 一样读背景，不读滚动内容）
    Box(modifier = Modifier.fillMaxSize().then(backgroundModifier)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.surfaceContainerLow,
                            scheme.surface,
                            scheme.surfaceContainerLowest
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = if (dark) 0.22f else 0.14f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )

        if (state.background.isNotBlank()) {
            val wallpaper = rememberWallpaper(
                uri = state.background,
                blurEnabled = state.blurEnabled,
                blurStrength = state.blurStrength
            )
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        scheme.surface.copy(alpha = if (dark) 0.62f else 0.55f)
                    )
            )
        }
    }

    content()
    }
}
