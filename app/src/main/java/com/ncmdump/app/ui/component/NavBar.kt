package com.ncmdump.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ncmdump.app.ui.theme.LocalCardAlpha

class NavItem(
    val label: String,
    val icon: ImageVector
)

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
