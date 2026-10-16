# reference/netease-music

网易云歌曲解析接口参考（来源：`网易云music.zip` 点歌插件）。

## 接口

- **搜索**：`GET https://music.kxlove.top/api/WYY/wyy_search.php?word=<关键词>`
  ```json
  { "code": 200, "message": "请求成功",
    "data": [ { "id": 2652820720, "song": "晴天(深情版)", "singer": "Lucky小爱",
                "album": "晴天(深情版)", "picUrl": "http://...jpg", "duration": 278961 } ] }
  ```
- **播放**：`GET https://music.kxlove.top/api/WYY/wyy.php?id=<id>`
  - 返回 `302`，`Location` 即真实音频直链（mp3）。

## App 端实现

- `app/src/main/java/com/ncmdump/app/NeteaseApi.kt` —— 搜索 / 解析直链
- `app/src/main/java/com/ncmdump/app/NeteaseFragment.kt` —— 搜索界面、试听、复制直链、下载

> 接口为第三方公共服务，可能失效或变更，仅供学习研究使用。
