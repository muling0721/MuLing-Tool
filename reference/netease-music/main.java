loadJava("HttpApi.java");
    /*MusicItem 为歌曲信息对象,脚本一般存放在hashmap中
    String name
    String id
    String word
    HashMap<String, Object> extra
    int page
    boolean isDir*/
public class MusicModel {
    // 与 MuLing-Tool 的 NeteaseApi.kt 保持一致：
    // 搜索：https://music.163.com/api/cloudsearch/pc
    // 解析：https://api.yutangxiaowu.cn/api/music/Song_V1

    /**
     * URL 编码（手动实现，避免不同运行环境对 URLEncoder 的支持差异）
     */
    public static String encode(String text) {
        if (text == null) {
            return "";
        }
        try {
            byte[] bytes = text.getBytes("UTF-8");
            StringBuffer buffer = new StringBuffer();
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xff;
                if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                        || (b >= '0' && b <= '9') || b == '-' || b == '_'
                        || b == '.' || b == '~') {
                    buffer.append((char) b);
                } else {
                    buffer.append('%');
                    String hex = Integer.toHexString(b).toUpperCase();
                    if (hex.length() < 2) {
                        buffer.append('0');
                    }
                    buffer.append(hex);
                }
            }
            return buffer.toString();
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 安全读取 JSON 字段，兼容字符串、数字和 null
     */
    public static String str(JSONObject object, String key) {
        try {
            Object value = object.get(key);
            if (value == null) {
                return "";
            }
            String text = value.toString();
            return "null".equals(text) ? "" : text;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 拼接歌手列表，例如：周杰伦 / 李硕 / 张鑫
     */
    public static String joinArtists(JSONArray array) {
        if (array == null) {
            return "";
        }
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject artist = array.getJSONObject(i);
                String name = str(artist, "name");
                if (name.length() > 0) {
                    if (buffer.length() > 0) {
                        buffer.append(" / ");
                    }
                    buffer.append(name);
                }
            } catch (Exception e) {
                // 忽略单个异常歌手
            }
        }
        return buffer.toString();
    }

    /**
     * 网易云封面地址统一为 https，并加缩略图参数
     */
    public static String normalizeCover(String raw) {
        if (raw == null || raw.equals("")) {
            return "";
        }
        String cover = raw;
        if (cover.startsWith("http://")) {
            cover = "https://" + cover.substring(7);
        }
        if (cover.indexOf("music.126.net") >= 0 && cover.indexOf("param=") < 0) {
            cover += (cover.indexOf("?") >= 0 ? "&" : "?") + "param=120y120";
        }
        return cover;
    }

    /**
     * 毫秒转 mm:ss
     */
    public static String formatDuration(long ms) {
        long total = ms / 1000;
        if (total < 0) {
            total = 0;
        }
        long minute = total / 60;
        long second = total % 60;
        return minute + ":" + (second < 10 ? "0" : "") + second;
    }

    /**
     * 网易云官方搜索接口，与 MuLing-Tool 的 NeteaseApi.search 一致
     */
    public static List<HashMap<String, Object>> searchSongs(String word) throws Exception {
        List<HashMap<String, Object>> musicList = new ArrayList<>();
        String searchApi = "https://music.163.com/api/cloudsearch/pc?s=";
        String parseApi = "https://api.yutangxiaowu.cn/api/music/Song_V1";
        String songPage = "https://music.163.com/song?id=";
        String level = "standard";
        String jsonData = get(searchApi + encode(word) + "&type=1&limit=30&offset=0");

        JSONObject jsonObject = new JSONObject(jsonData);
        int code = -1;
        try {
            code = jsonObject.getInt("code");
        } catch (Exception e) {
            code = -1;
        }
        if (code != 200) {
            String message = str(jsonObject, "message");
            if (message.equals("")) {
                message = str(jsonObject, "msg");
            }
            throw new Exception(message.equals("") ? "搜索接口返回异常" : message);
        }

        JSONObject result = null;
        try {
            result = jsonObject.getJSONObject("result");
        } catch (Exception e) {
            result = null;
        }
        if (result == null) {
            return musicList;
        }

        JSONArray songs = null;
        try {
            songs = result.getJSONArray("songs");
        } catch (Exception e) {
            songs = null;
        }
        if (songs == null || songs.length() == 0) {
            return musicList;
        }

        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.getJSONObject(i);
            String id = str(song, "id");
            if (id.equals("")) {
                continue;
            }

            String name = str(song, "name");

            JSONArray artistsArray = null;
            try {
                artistsArray = song.getJSONArray("ar");
            } catch (Exception e) {
                artistsArray = null;
            }
            if (artistsArray == null) {
                try {
                    artistsArray = song.getJSONArray("artists");
                } catch (Exception e) {
                    artistsArray = null;
                }
            }
            String artist = joinArtists(artistsArray);

            JSONObject albumObject = null;
            try {
                albumObject = song.getJSONObject("al");
            } catch (Exception e) {
                albumObject = null;
            }
            if (albumObject == null) {
                try {
                    albumObject = song.getJSONObject("album");
                } catch (Exception e) {
                    albumObject = null;
                }
            }
            String album = albumObject == null ? "" : str(albumObject, "name");
            String cover = albumObject == null ? "" : normalizeCover(str(albumObject, "picUrl"));

            long durationMs = 0;
            try {
                durationMs = song.getLong("dt");
            } catch (Exception e) {
                durationMs = 0;
            }
            if (durationMs == 0) {
                try {
                    durationMs = song.getLong("duration");
                } catch (Exception e) {
                    durationMs = 0;
                }
            }

            HashMap<String, Object> newItem = new HashMap<>();
            newItem.put("name", name + " - " + artist);
            newItem.put("id", id);
            newItem.put("artist", artist);
            newItem.put("album", album);
            newItem.put("pic", cover);
            newItem.put("duration", formatDuration(durationMs));
            // 保存解析接口地址，播放时由 fetchPlayUrl 获取真实音频直链
            newItem.put("url", parseApi + "?url=" + encode(songPage + id)
                    + "&level=" + level + "&type=json");

            musicList.add(newItem);
        }
        return musicList;
    }

    public static List<HashMap<String, Object>> fetchItems(HashMap<String,Object> curItem) {
        List<HashMap<String, Object>> musicList = new ArrayList<>();
        try {
            // 首页默认展示“晴天”的搜索结果
            musicList = searchSongs("晴天");
            if (musicList == null || musicList.isEmpty()) {
                HashMap<String, Object> newItem = new HashMap<>();
                newItem.put("name", "未获取到歌曲，请检查网络");
                musicList.add(newItem);
            }
            return musicList;
        } catch (Exception e) {
            HashMap<String, Object> newItem = new HashMap<>();
            newItem.put("name", "接口请求失败：" + e.getMessage());
            musicList.add(newItem);
            return musicList;
        }
    }

    public static Pair<String, HashMap<String, String>> fetchPlayUrl(HashMap<String, Object> musicItem){
        HashMap headers = new HashMap();
        headers.put("Referer", "https://music.163.com/");
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");

        Object urlObj = musicItem.get("url");
        String url = urlObj == null ? "" : urlObj.toString();

        // Song_V1 返回 JSON，需要取出其中的 url 字段
        if (url.indexOf("Song_V1") >= 0) {
            try {
                JSONObject jsonObject = new JSONObject(get(url));
                String playUrl = str(jsonObject, "url");
                if (playUrl.length() > 0) {
                    return new Pair<>(playUrl, headers);
                }
            } catch (Exception e) {
                // 解析失败时继续走下面的兜底逻辑
            }
        } else if (url.length() > 0) {
            // 已经是可直接播放的音频地址
            return new Pair<>(url, headers);
        }

        // 兜底：用歌曲 ID 走音频流接口
        Object idObj = musicItem.get("id");
        String mid = idObj == null ? "" : idObj.toString();
        if (!mid.equals("")) {
            return new Pair<>("https://api.yutangxiaowu.cn/api/mix/stream?id="
                    + encode(mid) + "&platform=163&quality=standard", headers);
        }
        return new Pair<>("", headers);
    }

    public static List<HashMap<String, Object>> searchItems(HashMap<String, Object> name) throws IOException {
        List<HashMap<String, Object>> musicList = new ArrayList<>();
        Object wordObj = name.get("word");
        String word = wordObj == null ? "" : wordObj.toString();

        try {
            musicList = searchSongs(word);
            if (musicList == null || musicList.isEmpty()) {
                HashMap<String, Object> newItem = new HashMap<>();
                newItem.put("name", "未搜索到");
                musicList.add(newItem);
            }
            return musicList;
        } catch (Exception e) {
            HashMap<String, Object> newItem = new HashMap<>();
            newItem.put("name", "搜索失败：" + e.getMessage());
            musicList.add(newItem);
            return musicList;
        }
    }
}
