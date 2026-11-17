package com.ncmdump.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ncmdump.app.DownloadHelper
import com.ncmdump.app.PlayerActivity
import com.ncmdump.app.ShortVideoPlayerActivity
import com.ncmdump.app.UpdateChecker
import com.ncmdump.app.ui.component.DownloadBanner
import com.ncmdump.app.ui.component.FloatingBottomBar
import com.ncmdump.app.ui.component.FloatingBottomBarItem
import com.ncmdump.app.ui.component.MiniPlayerBar
import com.ncmdump.app.ui.component.MuLingBackground
import com.ncmdump.app.ui.component.NavItem
import com.ncmdump.app.ui.component.NotePill
import com.ncmdump.app.ui.screen.ConvertScreen
import com.ncmdump.app.ui.screen.DownloadsScreen
import com.ncmdump.app.ui.screen.HomeScreen
import com.ncmdump.app.ui.screen.MusicScreen
import com.ncmdump.app.ui.screen.SettingsScreen
import com.ncmdump.app.ui.screen.ThemeScreen
import com.ncmdump.app.ui.screen.VideoScreen
import com.ncmdump.app.ui.theme.MuLingTheme
import com.ncmdump.app.ui.util.LocalBlurState
import com.ncmdump.app.ui.util.toast
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.launch

private enum class SubPage { Convert, Downloads, Theme }

private sealed interface Route {
    data class Tab(val index: Int) : Route
    data class Sub(val page: SubPage) : Route

    /** 用于判断切换方向（子页面总在标签页之后） */
    val order: Int
        get() = when (this) {
            is Tab -> index
            is Sub -> 10 + page.ordinal
        }
}

private val bottomTabs = listOf(
    NavItem("音频", Icons.TwoTone.GraphicEq),
    NavItem("视频", Icons.TwoTone.VideoLibrary),
    NavItem("音乐", Icons.TwoTone.MusicNote),
    NavItem("设置", Icons.TwoTone.Settings)
)

@Composable
fun MuLingApp() {
    val context = LocalContext.current
    val appState = remember { AppState(context) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (appState.darkMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity
        val window = activity?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    MuLingTheme(
        dark = dark,
        seed = appState.seed,
        dynamicColor = appState.dynamicColor,
        cardAlpha = appState.cardAlpha
    ) {
        val media = remember { MediaSessionState() }
        CompositionLocalProvider(
            LocalAppState provides appState,
            LocalMediaSession provides media
        ) {
            AppContent(appState = appState, media = media)
        }
    }
}

@Composable
private fun AppContent(appState: AppState, media: MediaSessionState) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<SubPage>() }
    var lastBack by remember { mutableLongStateOf(0L) }

    // 跨页面保留的状态
    val homeState = remember { HomeState() }
    val musicState = remember { MusicState() }

    val downloads = remember { mutableStateMapOf<Int, DownloadHelper.Progress>() }
    var downloadNote by remember { mutableStateOf<String?>(null) }

    var updateRelease by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }

    // 媒体会话：随生命周期连接 / 断开
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> media.attach(context)
                Lifecycle.Event.ON_STOP -> media.detach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            media.detach()
        }
    }

    // 下载进度监听
    DisposableEffect(Unit) {
        val listener = object : DownloadHelper.Listener {
            override fun onProgress(progress: DownloadHelper.Progress) {
                when (progress.state) {
                    DownloadHelper.State.RUNNING -> downloads[progress.id] = progress
                    DownloadHelper.State.DONE -> {
                        downloads.remove(progress.id)
                        downloadNote = "已保存：${progress.title}"
                    }

                    DownloadHelper.State.FAILED -> {
                        downloads.remove(progress.id)
                        downloadNote = "下载失败：${progress.title}"
                    }
                }
            }
        }
        DownloadHelper.addListener(listener)
        onDispose { DownloadHelper.removeListener(listener) }
    }

    LaunchedEffect(downloadNote) {
        if (downloadNote != null) {
            kotlinx.coroutines.delay(2800)
            downloadNote = null
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (appState.autoUpdate) {
            val release = UpdateChecker.fetchLatest()
            val current = UpdateChecker.currentVersion(context)
            if (release != null && UpdateChecker.isNewer(release.tagName, current)) {
                updateRelease = release
            }
        }
    }

    BackHandler(enabled = true) {
        if (stack.isNotEmpty()) {
            stack.removeAt(stack.lastIndex)
            return@BackHandler
        }
        if (tabIndex != 0) {
            tabIndex = 0
            return@BackHandler
        }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) {
            activity?.finish()
        } else {
            lastBack = now
            toast(context, "再按一次退出")
        }
    }

    val navInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val statusInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    val route: Route = stack.lastOrNull()?.let { Route.Sub(it) } ?: Route.Tab(tabIndex)
    val nowPlaying = media.info

    val tabBottomSpace = navInset + 64.dp + 24.dp +
        (if (nowPlaying != null) 66.dp else 0.dp) +
        (if (downloads.isNotEmpty()) 54.dp else 0.dp)
    val subBottomSpace = navInset + 24.dp

    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        runCatching { top.yukonga.miuix.kmp.shader.isRenderEffectSupported() }.getOrDefault(false)
    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalBlurState provides if (canBlur) backdrop else null) {
    // 只把背景层录进 backdrop，滚动内容不进采样层（和 BakaSU 的读取层次一致，也不会每帧重录列表）
    MuLingBackground(
        backgroundModifier = if (canBlur) Modifier.layerBackdrop(backdrop) else Modifier
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = targetState.order >= initialState.order
                val direction = if (forward) 1 else -1
                (
                    slideInHorizontally(animationSpec = tween(280)) { full ->
                        direction * full / 8
                    } + fadeIn(animationSpec = tween(200))
                    ).togetherWith(
                    slideOutHorizontally(animationSpec = tween(240)) { full ->
                        -direction * full / 10
                    } + fadeOut(animationSpec = tween(140))
                )
            },
            label = "screen"
        ) { current ->
            val bottom = if (current is Route.Tab) tabBottomSpace else subBottomSpace
            when (current) {
                is Route.Sub -> when (current.page) {
                    SubPage.Convert -> ConvertScreen(
                        topPadding = statusInset,
                        bottomPadding = bottom,
                        onBack = { stack.removeAt(stack.lastIndex) }
                    )

                    SubPage.Downloads -> DownloadsScreen(
                        topPadding = statusInset,
                        bottomPadding = bottom,
                        onBack = { stack.removeAt(stack.lastIndex) }
                    )

                    SubPage.Theme -> ThemeScreen(
                        topPadding = statusInset,
                        bottomPadding = bottom,
                        onBack = { stack.removeAt(stack.lastIndex) }
                    )
                }

                is Route.Tab -> when (current.index) {
                    0 -> HomeScreen(
                        state = homeState,
                        scope = scope,
                        topPadding = statusInset,
                        bottomPadding = bottom,
                        onOpenConvert = { stack.add(SubPage.Convert) },
                        onOpenDownloads = { stack.add(SubPage.Downloads) }
                    )

                    1 -> VideoScreen(
                        topPadding = statusInset,
                        bottomPadding = bottom,
                        onPlayBili = { bvid, cid, title ->
                            runCatching {
                                context.startActivity(
                                    Intent(context, PlayerActivity::class.java).apply {
                                        putExtra("bvid", bvid)
                                        putExtra("cid", cid)
                                        putExtra("video_title", title)
                                    }
                                )
                            }
                        },
                        onPlayShort = { url, referer, title ->
                            runCatching {
                                context.startActivity(
                                    Intent(context, ShortVideoPlayerActivity::class.java).apply {
                                        putExtra("video_url", url)
                                        putExtra("referer", referer)
                                        putExtra("video_title", title)
                                    }
                                )
                            }
                        }
                    )

                    2 -> MusicScreen(
                        topPadding = statusInset,
                        bottomPadding = bottom,
                        media = media,
                        state = musicState
                    )

                    else -> SettingsScreen(
                        topPadding = statusInset,
                        bottomPadding = bottom,
                        onOpenTheme = { stack.add(SubPage.Theme) },
                        onOpenDownloads = { stack.add(SubPage.Downloads) }
                    )
                }
            }
        }

        if (route is Route.Tab) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = navInset + 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = downloadNote != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    NotePill(text = downloadNote.orEmpty())
                }

                if (downloads.isNotEmpty()) {
                    val progress = downloads.values.map { it.percent }.average().toInt()
                    val label = if (downloads.size == 1) {
                        downloads.values.first().title
                    } else {
                        "共 ${downloads.size} 项"
                    }
                    DownloadBanner(
                        text = "下载中 $progress% · $label",
                        progress = progress / 100f
                    )
                }

                MiniPlayerBar(
                    info = nowPlaying,
                    onTogglePlay = { media.toggle() },
                    onDownload = { media.downloadCurrent(context, scope) },
                    onClose = { media.close() }
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingBottomBar(
                        isBlurEnabled = canBlur,
                        selectedIndex = tabIndex,
                        onSelected = { index ->
                            stack.clear()
                            tabIndex = index
                        },
                        tabsCount = bottomTabs.size
                    ) { activateTab ->
                        bottomTabs.forEachIndexed { index, tab ->
                            FloatingBottomBarItem(
                                selected = index == tabIndex,
                                onClick = { activateTab(index) },
                                modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                            ) {
                                val contentColor = LocalContentColor.current
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = contentColor
                                )
                                Text(
                                    text = tab.label,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    val release = updateRelease
    if (release != null) {
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text(text = "发现新版本", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        text = "v${UpdateChecker.currentVersion(context)}  →  ${release.tagName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = release.body.ifBlank { "有新版本可用，建议更新以获得最新修复与改进。" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.htmlUrl)))
                    }
                    updateRelease = null
                }) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { updateRelease = null }) { Text("稍后") }
            }
        )
    }
}
