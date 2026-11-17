package com.ncmdump.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MuLing 主题：Material 3 + HyperOS 风格。
 *
 * - 浅色 / 深色 / 跟随系统
 * - Android 12+ 可选动态取色
 * - 预设种子色（湖蓝 / 暮紫 / 森绿 / 樱粉 / 暖橙）
 */
enum class SeedColor(val id: String, val label: String, val color: Color) {
    Blue("blue", "湖蓝", Color(0xFF3563E9)),
    Purple("purple", "暮紫", Color(0xFF6C4EB8)),
    Green("green", "森绿", Color(0xFF2E7D57)),
    Pink("pink", "樱粉", Color(0xFFB04A76)),
    Orange("orange", "暖橙", Color(0xFFC0602A));

    companion object {
        fun fromId(id: String?): SeedColor =
            entries.firstOrNull { it.id == id } ?: Blue
    }
}

/** 卡片不透明度（0.3 ~ 1.0），由设置页控制 */
val LocalCardAlpha = staticCompositionLocalOf { 1f }

/** 是否处于深色模式，供组件微调 */
val LocalIsDark = staticCompositionLocalOf { true }

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

private fun lightScheme(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = White,
    primaryContainer = lerp(seed, White, 0.84f),
    onPrimaryContainer = lerp(seed, Black, 0.55f),
    inversePrimary = lerp(seed, White, 0.35f),
    secondary = lerp(seed, Color(0xFF6B7280), 0.62f),
    onSecondary = White,
    secondaryContainer = lerp(seed, White, 0.88f),
    onSecondaryContainer = lerp(seed, Black, 0.52f),
    tertiary = lerp(seed, Color(0xFF8B5CF6), 0.48f),
    onTertiary = White,
    tertiaryContainer = lerp(seed, White, 0.9f),
    onTertiaryContainer = lerp(seed, Black, 0.5f),
    error = Color(0xFFB3261E),
    onError = White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF14151A),
    surface = Color(0xFFF6F6FA),
    onSurface = Color(0xFF14151A),
    surfaceVariant = Color(0xFFE6E7EE),
    onSurfaceVariant = Color(0xFF565A65),
    surfaceTint = seed,
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFFCFBFF),
    surfaceContainer = Color(0xFFF0F0F6),
    surfaceContainerHigh = Color(0xFFEAEAF1),
    surfaceContainerHighest = Color(0xFFE4E4ED),
    outline = Color(0xFF8A8D97),
    outlineVariant = Color(0xFFDCDEE6),
    inverseSurface = Color(0xFF2F3138),
    inverseOnSurface = Color(0xFFF2F2F7),
    scrim = Black
)

private fun darkScheme(seed: Color) = darkColorScheme(
    primary = lerp(seed, White, 0.4f),
    onPrimary = lerp(seed, Black, 0.62f),
    primaryContainer = lerp(seed, Black, 0.52f),
    onPrimaryContainer = lerp(seed, White, 0.84f),
    inversePrimary = lerp(seed, White, 0.18f),
    secondary = lerp(seed, White, 0.56f),
    onSecondary = lerp(seed, Black, 0.6f),
    secondaryContainer = lerp(seed, Black, 0.62f),
    onSecondaryContainer = lerp(seed, White, 0.86f),
    tertiary = lerp(lerp(seed, Color(0xFF8B5CF6), 0.5f), White, 0.45f),
    onTertiary = lerp(seed, Black, 0.6f),
    tertiaryContainer = lerp(seed, Black, 0.6f),
    onTertiaryContainer = lerp(seed, White, 0.86f),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF0D0E12),
    onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF2A2B33),
    onSurfaceVariant = Color(0xFFB2B4BF),
    surfaceTint = lerp(seed, White, 0.4f),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF131419),
    surfaceContainer = Color(0xFF191A20),
    surfaceContainerHigh = Color(0xFF212227),
    surfaceContainerHighest = Color(0xFF2A2B31),
    outline = Color(0xFF6E7078),
    outlineVariant = Color(0xFF33343C),
    inverseSurface = Color(0xFFE8E8EE),
    inverseOnSurface = Color(0xFF2F3138),
    scrim = Black
)

private val MuLingTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp
    )
)

private val MuLingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MuLingTheme(
    dark: Boolean,
    seed: SeedColor,
    dynamicColor: Boolean,
    cardAlpha: Float,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme(seed.color)
        else -> lightScheme(seed.color)
    }

    CompositionLocalProvider(
        LocalCardAlpha provides cardAlpha,
        LocalIsDark provides dark
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MuLingTypography,
            shapes = MuLingShapes,
            content = content
        )
    }
}
