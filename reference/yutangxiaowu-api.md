# 羽棠小屋 API —— 短视频聚合解析

文档：https://doc.yutangxiaowu.cn/421976742e0
App 端实现：`app/src/main/java/com/ncmdump/app/ShortVideoApi.kt`

## 聚合解析（抖音 / 快手）

```
GET https://api.yutangxiaowu.cn/api/v1/mix/video?url=<经过 URL 编码的分享链接>
```

该接口对抖音、快手返回不同结构，App 已同时兼容：

### 抖音
```json
{
  "title": "...",
  "author": { "name": "...", "avatar": ["..."] },
  "image": ["封面1", "封面2"],
  "video": [
    { "definition": "normal_1080_0", "file_size": 9747912,
      "file_url": ["https://...mp4", "..."] }
  ]
}
```
- 视频直链：`video[]` 中 `file_size` 最大的一档的 `file_url[0]`
- 封面：`image[0]`
- 图集：`video` 为空时使用 `image[]`

### 快手（统一结构）
```json
{
  "code": 0, "msg": "解析成功!",
  "data": {
    "video": { "title": "...", "url": "https://...mp4", "fm": "封面", "duration": "30秒" },
    "owner": { "name": "..." },
    "images": 0
  }
}
```
- 视频直链：`data.video.url`
- 封面：`data.video.fm`
- 图集：`data.image[]` / `data.images[]`

## 其他可用接口（未使用）

- 抖音：`POST /api/v1/douyin/parse`，JSON `{ "url": "..." }`
- 快手：`GET /api/v1/kuaishou?url=...`
- B站：本项目仍使用已有的 B站解析，不走这里
