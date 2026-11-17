# reference/netease-music

FunBox 网易云点歌脚本。接口与 MuLing-Tool 的 `NeteaseApi.kt` 保持一致。

## 接口

### 1. 搜索（网易云官方接口）

```text
GET https://music.163.com/api/cloudsearch/pc?s=<关键词>&type=1&limit=30&offset=0
```

请求头：

```text
User-Agent: Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36
Referer: https://music.163.com/
```

响应字段：

- `result.songs[]`：歌曲列表
- `id`：歌曲 ID
- `name`：歌名
- `ar[]`：歌手列表
- `al`：专辑信息（`name`、`picUrl`）
- `dt`：时长（毫秒）

### 2. 播放直链解析（羽棠小屋）

```text
GET https://api.yutangxiaowu.cn/api/music/Song_V1?url=<歌曲页面URL>&level=standard&type=json
```

歌曲页面 URL：

```text
https://music.163.com/song?id=<歌曲ID>
```

请求头同样使用 `Referer: https://music.163.com/`。

响应 `status=200` 且包含 `url` 时，`url` 即音频直链。脚本在 `fetchPlayUrl()` 中解析该字段。

### 3. 兜底播放

如果 `Song_V1` 解析失败，脚本会回退到：

```text
GET https://api.yutangxiaowu.cn/api/mix/stream?id=<歌曲ID>&platform=163&quality=standard
```

该接口直接返回 `audio/mpeg` 音频流。

## 文件

- `main.java`：FunBox 点歌脚本（搜索 / 播放直链 / 兜底）
- `HttpApi.java`：HTTP 请求封装，已增加网易云所需的 UA / Referer 请求头
- `info.json`：脚本信息

## 说明

- 搜索走网易云官方接口，不再依赖 `music.kxlove.top`。
- 播放解析与 MuLing-Tool App 使用同一套接口。
- 默认音质为 `standard`，可修改 `main.java` 中的 `LEVEL` 常量。
