# reference/短视频解析

短视频去水印解析参考（来源：`短视频解析.zip` 插件，作者「李贺无敌大王」）。
原插件支持 抖音 / 快手 / B站 / TikTok；本项目**只采用抖音 / 快手 / TikTok 部分**，B站仍走已有的 B站解析。

## 解析源（依次回退）

1. `GET http://47.99.158.118/video-crack/v2/parse?content=<url>`
   - `code == "0"` → `data.url`（视频直链）或 `data.imageUrl`（图集）
2. `GET http://api.guiguiya.com/api/video_qsy/juhe?url=<url>`
   - `code == "200"` → `data.url`
3. `GET https://api.tangdouz.com/dy.php?lj=<url>`
   - 纯文本：直接是视频直链，或 `img=...±img=...`（图集）
4. `GET https://douyin.wtf/api/hybrid/video_data?url=<url>&minimal=true`
   - `code == "200"` → `data.nwm_video_url_HQ` / `nwm_video_url` / `wm_video_url*`

## App 端实现

- `app/src/main/java/com/ncmdump/app/ShortVideoApi.kt` —— 多源回退解析
- `app/src/main/java/com/ncmdump/app/ShortVideoFragment.kt` —— 解析界面（播放/下载/复制直链）

> 以上均为第三方公共服务，可能随时失效或变更，仅供学习研究使用。
