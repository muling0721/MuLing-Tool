# MuLingTool

> 一站式 Android 多媒体工具箱

![Android CI](https://github.com/muling0721/MuLing-Tool/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/muling0721/MuLing-Tool?label=release&color=blue)
![Platform](https://img.shields.io/badge/platform-Android%206.0%2B-3ddc84?logo=android&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285f4)

<p align="center">
  <img src="docs/icon-source.jpg" width="120" alt="MuLingTool" />
</p>

MuLingTool 是一款面向 Android 平台的多功能媒体工具，把音乐搜索、视频解析、音频处理和文件解密整合进同一个应用，
省去在多个工具之间来回切换的麻烦，让音乐、视频与音频的处理尽量一步到位。

## ✨ 功能

### 🎵 音乐
- 网易云音乐歌曲、歌手搜索
- 多档音质可选，支持在线播放与试听
- 歌曲下载，歌词同步保存（原文 + 翻译合并）
- 全局迷你播放器，切到其他页面也能控制播放

### 🎬 视频
- 哔哩哔哩视频解析与播放，支持分 P 与播放地址获取
- 抖音、快手短视频解析，可获取无水印视频
- 图集解析与批量下载

### 🎧 音频处理
- MP3、WAV、AAC、FLAC、OGG、M4A、OPUS、WMA 等格式互转
- 支持自定义输出码率

### 📦 文件解密
- NCM 文件单个或批量解密
- 自动识别输出音频格式
- 自动写入歌曲信息与专辑封面
- 自动获取并保存对应歌词

### 📥 下载管理
- 统一下载历史记录
- 支持查看文件、复制路径与删除记录
- 自动识别下载文件类型

## 🎨 界面

- Jetpack Compose + Material 3，悬浮胶囊底栏（可拖动、边缘折射，Android 13+ 真实背景采样）
- 深色 / 浅色 / 跟随系统，Android 12+ 支持动态取色，另附 5 组预设主题色
- 自定义背景与卡片不透明度，页面切换带动画

## 💙 项目理念

日常用到的多媒体工具就那么几样，却分散在不同应用里。MuLingTool 希望把它们收进一个应用：
打开就能用，少记几个入口，也不把任何内容上传到服务器。

**MuLingTool —— 让多媒体工具触手可及。**

## 🚀 构建

需要 JDK 21、Android SDK Platform 37、Build-Tools 36.1.0、NDK 30.0.16248370、CMake 3.22.1。

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包，需要仓库根目录的 release.jks
```

APK 输出在 `app/build/outputs/apk/debug/`、`app/build/outputs/apk/release/`。

- `Android CI`：push 到 `master` 时构建 Debug 包并上传 Artifact，不发 Release
- `Release`：推送 `v*` 标签时构建签名 Release 并发布，签名信息由 Secrets 注入

## 📄 更新日志

见 [CHANGELOG.md](CHANGELOG.md) 与 [Releases](https://github.com/muling0721/MuLing-Tool/releases)。

## ⚠️ 声明

请合理使用本工具，遵守各平台服务条款及著作权法律法规，尊重原创内容与创作者权益。
本工具仅供个人已购买 / 下载内容的本地备份使用，请勿传播或商用解密后的音频、视频文件。

by.muling
