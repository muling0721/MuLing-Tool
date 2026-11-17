package com.ncmdump.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ncmdump.app.ui.theme.LocalCardAlpha
import com.ncmdump.app.ui.theme.LocalIsDark
import kotlin.math.roundToInt

class NavItem(
    val label: String,
    val icon: ImageVector
)

/**
 * iOS 风格的悬浮胶囊底栏：整条是圆角药丸，选中项是一块滑过去的高亮。
 * 纯 Compose 绘制，不做背景模糊，滚动时不掉帧。
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(50)

    var barSize by remember { mutableStateOf(IntSize.Zero) }

    val sidePadPx = with(density) { 6.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val itemWidthPx = if (barSize.width > 0 && items.isNotEmpty()) {
        ((barSize.width - sidePadPx * 2 - gapPx * (items.size - 1)) / items.size).coerceAtLeast(0f)
    } else {
        0f
    }
    val indicatorX by animateFloatAsState(
        targetValue = sidePadPx + selectedIndex * (itemWidthPx + gapPx),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "indicatorX"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .onSizeChanged { barSize = it }
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.30f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerHigh.copy(alpha = 0.97f),
                        scheme.surfaceContainer.copy(alpha = 0.97f)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                color = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f),
                shape = shape
            )
    ) {
        if (itemWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = indicatorX.roundToInt(),
                            y = with(density) { 7.dp.toPx() }.roundToInt()
                        )
                    }
                    .size(width = with(density) { itemWidthPx.toDp() }, height = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary.copy(alpha = if (dark) 0.22f else 0.14f))
            )
        }

        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
                    label = "iconScale"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(index) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp * iconScale)
                    )
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NowPlayingInfo(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String,
    val isPlaying: Boolean
)

/** 全局迷你播放器：悬浮在底部导航之上 */
@Composable
fun MiniPlayerBar(
    info: NowPlayingInfo?,
    onTogglePlay: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = info != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        val scheme = MaterialTheme.colorScheme
        val data = info ?: NowPlayingInfo("", "", "", "", false)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(scheme.surfaceContainerHigh.copy(alpha = LocalCardAlpha.current))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(
                url = data.cover,
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = data.artist.ifBlank { "MuLing Tool" },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (data.isPlaying) Icons.TwoTone.Pause else Icons.TwoTone.PlayArrow,
                    contentDescription = "播放/暂停",
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.TwoTone.Download,
                    contentDescription = "下载",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = "关闭",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 下载进度条（应用内） */
@Composable
fun DownloadBanner(
    text: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHigh.copy(alpha = LocalCardAlpha.current))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.TwoTone.Download,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50)),
            color = scheme.primary,
            trackColor = scheme.surfaceContainerHighest
        )
    }
}

/** 占位，避免未使用参数告警 */
@Composable
internal fun BoxPlaceholder() {
    Box(modifier = Modifier.size(0.dp))
}

/** 轻提示胶囊（应用内提示，替代 Toast 的部分场景） */
@Composable
fun NotePill(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.inverseSurface.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
