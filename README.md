# MuLing Tool

> Android 多媒体工具箱 · NCM 解密 · 音频处理 · 视频解析

![Android CI](https://github.com/muling0721/MuLing-Tool/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/muling0721/MuLing-Tool?label=release&color=blue)
![Platform](https://img.shields.io/badge/platform-Android%206.0%2B-3ddc84?logo=android&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285f4)
![Version](https://img.shields.io/badge/version-2.0-ff8fc7)

基于 ncmdump C++ 核心的 Android 图形化解密工具，附带音频处理、哔哩哔哩 / 短视频解析与网易云音乐搜索下载。
界面使用 **Jetpack Compose** 全量重写：大圆角分组卡片、折叠大标题、深浅色与自定义背景；
底栏为 BakaSU 的悬浮胶囊（可拖动、边缘折射、重力感应高光），Android 13+ 走真实背景采样。

## 功能

### NCM 解密
- 选择单个或多个 .ncm 文件批量解密，支持全选/移除
- 自定义输出目录（全应用统一，自动记忆）
- 解密后自动写入专辑封面 + ID3 元数据（歌曲名/艺术家/专辑）
- 自动爬取网易云歌词，输出同名 .lrc 文件
- 批量解密进度显示，自动识别输出格式（mp3 / flac）
- 支持 armeabi-v7a / arm64-v8a / x86 / x86_64 全架构

### 音频处理（FFmpeg）
- 格式转换：mp3 / wav / aac / flac / ogg / m4a / opus / wma，可选码率

### 下载记录
- 自动记录最近 200 条下载（标题 / 路径 / 时间 / 类型）
- 支持打开文件、复制完整路径、单条删除与清空
- 下载时按服务端 Content-Type 自动纠正扩展名（mp4 / webm / mp3 …）

### 短视频解析（抖音 / 快手）
- 粘贴分享链接自动识别平台
- 聚合解析接口获取无水印视频直链
- 内置播放、下载无水印视频、复制直链
- 支持图集：一键下载全部图片

### 哔哩哔哩视频解析
- 输入 BV 号 / av 号 / b23.tv 短链 / 视频链接
- 显示封面、标题、UP主、时长、简介、分P列表（点击直接播放）
- 获取 DASH 播放地址（视频流 + 音频流），支持复制
- 内置播放器（官方内嵌播放器 / 直链播放）

### 网易云音乐
- 在线搜索歌曲 / 歌手，支持标准 / 无损 / Hi-Res 等音质
- 点击结果直接试听，播放/暂停可在系统通知栏与锁屏控制（Media3 媒体会话）
- 全局迷你播放器：任意页面悬浮显示，支持下载当前歌曲
- 长按结果：播放 / 查看并下载歌词 / 复制直链 / 下载音频
- 下载音频时自动保存同名 .lrc 歌词（原文 + 翻译合并）

### 界面与主题
- Jetpack Compose + Material 3，悬浮胶囊底部导航与折叠式大标题
- 深色 / 浅色 / 跟随系统三种模式
- Android 12+ 动态取色，另提供 5 组预设主题色
- 自定义背景图片：可选模糊强度，卡片半透明叠加，任意明暗背景都保持可读
- 卡片不透明度可调
- 应用内下载进度条 + 通知栏百分比进度
- 页面切换滑动 + 淡入淡出动画，标签间状态保留
- 更新检测使用 Compose 对话框，展示版本对比与更新说明

## 项目结构

```
MuLingTool/
├── app/
│   ├── build.gradle                      # Compose / Media3 / FFmpeg 依赖
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ncmdump/app/
│       │   ├── MainActivity.kt           # 单 Activity 入口，托管 Compose
│       │   ├── PlayerActivity.kt         # B站播放页（Compose 播放器）
│       │   ├── ShortVideoPlayerActivity.kt
│       │   ├── NcmCrypt.kt               # JNI 绑定类
│       │   ├── MetadataWriter.kt         # ID3 元数据/封面写入
│       │   ├── LyricFetcher.kt           # 网易云歌词爬取
│       │   ├── BiliParser.kt             # B站 API 解析
│       │   ├── ShortVideoApi.kt          # 抖音/快手解析
│       │   ├── NeteaseApi.kt             # 网易云搜索/直链/歌词
│       │   ├── DownloadHelper.kt         # 下载 + 通知栏进度
│       │   ├── FfmpegHelper.kt           # 转码 / 剪辑 / 合并
│       │   ├── PlaybackService.kt        # Media3 播放会话
│       │   ├── UpdateChecker.kt          # 版本检测
│       │   └── ui/
│       │       ├── MuLingApp.kt          # 应用外壳：导航 / 迷你播放器 / 下载提示
│       │       ├── AppState.kt           # 主题与外观偏好
│       │       ├── MediaSessionState.kt  # Media3 会话状态
│       │       ├── theme/Theme.kt        # Material 3 主题、色板、字体、形状
│       │       ├── component/            # 卡片、导航、输入、滑杆等通用组件
│       │       ├── screen/               # 首页 / 格式转换 / 下载记录 / 视频 / 音乐 / 设置 / 播放器
│       │       └── util/
│       ├── jni/                          # ncm 解密 C++ 核心（AES + cJSON + JNI）
│       └── res/                          # 仅保留图标与主题资源（无 XML 布局）
├── .github/workflows/
│   ├── build.yml                         # push 构建 Debug 测试包
│   └── release.yml                       # v* 标签构建签名 Release
├── reference/                            # 接口参考
└── tools/
```

## 解密原理

ncm 文件采用两层加密：
1. **密钥层**：文件头内置经异或混淆的核心密钥，用固定 `sCoreKey` 经 AES-128-ECB 解密后构建 RC4 风格的 keybox
2. **音频层**：原始音频流用 keybox 逐字节异或解密，输出标准 mp3 或 flac
3. **元数据层**：歌曲名/艺术家/专辑/封面经 `sModifyKey` AES 解密 + Base64 解码后解析

## 构建

### 环境要求
- JDK 17
- Android SDK Platform 34
- Android NDK 25.2.9519653
- CMake 3.22.1
- Gradle 8.2

### 命令行构建
```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

gradle assembleDebug      # 调试包，无需签名
gradle assembleRelease    # 发布包，需要仓库根目录的 release.jks
```

APK 输出路径：`app/build/outputs/apk/debug/`、`app/build/outputs/apk/release/`

### GitHub Actions
- **Android CI**（`.github/workflows/build.yml`）：push 到 `master` 时构建 Debug APK 并上传 Artifact（不发布 Release）
- **Release**（`.github/workflows/release.yml`）：推送 `v*` 标签时构建**签名** Release 并发布 GitHub Release
  - 签名信息通过仓库 Secrets 注入：`SIGNING_KEYSTORE_BASE64` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`

## 使用

1. 安装 APK 后打开应用
2. 首次使用需授予「所有文件访问权限」
3. 首页点击「选择 ncm 文件」，选择 .ncm 文件（可多选）
4. 在设置页可选择统一的输出目录（自动记忆）
5. 点击「开始解密」，等待进度完成
6. 解密后的 mp3/flac + .lrc 歌词保存在输出目录
7. 底部导航切换「视频」「音乐」，使用解析与试听功能

## 技术要点

### Compose 单 Activity 架构
- 所有页面由 `MuLingApp` 内部的路由栈管理，`BackHandler` 处理返回与「再按一次退出」
- 主题状态集中在 `AppState`，修改后即时生效，不需要 `recreate()`
- 通用视觉组件（分组卡片、设置行、胶囊标签、悬浮导航、迷你播放器）集中在 `ui/component`

### SAF 路径处理
Android 10+ 采用 Scoped Storage，通过 SAF 选择的文件只有 `content://` Uri。解决方案：
- 先将 Uri 对应的文件流拷贝到应用私有缓存目录
- 获取缓存文件的绝对路径传给 native 层解密
- 解密完成后删除缓存文件

### 输出目录记忆
- 同时持久化 SAF Tree Uri 和解析后的真实路径
- 支持内置存储（primary:）和 SD 卡（xxxx-xxxx:）路径映射

### 大文件保护
- `android:largeHeap="true"` 增加内存配额
- 后处理流程全部使用 `Throwable` 捕获，防止 OOM 导致闪退
- 封面超过 10MB 时自动跳过写入

### jaudiotagger 元数据写入
- 使用 `net.jthink:jaudiotagger:3.0.1` 纯 Java 库
- 支持 MP3 ID3v2 和 FLAC 格式
- 封面写入：`deleteArtworkField()` + `addField(artwork)` 避免冲突

## 更新日志

每个版本的改动记录在 [CHANGELOG.md](CHANGELOG.md)，详见 [Releases](https://github.com/muling0721/MuLing-Tool/releases)。

## 版权声明

本工具仅供个人已购买/下载歌曲的本地备份使用。未经授权传播、商用解密后的音频文件，会侵犯音乐作品著作权。B站 / 短视频解析功能仅供学习研究使用。

by.muling
