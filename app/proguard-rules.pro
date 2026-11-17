# JNI 入口
-keep class com.ncmdump.app.NcmCrypt { *; }

# jaudiotagger 全程反射
-keep class org.jaudiotagger.** { *; }
-keep class net.jthink.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn net.jthink.**

# ffmpeg-kit 走 JNI
-keep class com.arthenica.** { *; }
-dontwarn com.arthenica.**

# Media3 / exoplayer 反射加载解码器
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 底栏玻璃库（RenderEffect / shader）
-keep class top.yukonga.** { *; }
-dontwarn top.yukonga.**

# Coil 的 AsyncImage 用反射取 model
-keep class coil.** { *; }
