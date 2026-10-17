#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
kxlove_adapter.py —— 把 music.kxlove.top 适配成 NeteaseCloudMusicApi 接口
只依赖 Python 标准库，无需 pip 安装。

启动：  python3 kxlove_adapter.py
默认监听 0.0.0.0:8000
AstrBot 插件 api_url 填： http://127.0.0.1:8000  （或 http://本机IP:8000）
"""
import json
import urllib.parse
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SEARCH = "https://music.kxlove.top/api/WYY/wyy_search.php?word="
PLAY = "https://music.kxlove.top/api/WYY/wyy.php?id="
UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
PORT = 8000

_last_songs = {}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_opener_follow = urllib.request.build_opener()
_opener_nofollow = urllib.request.build_opener(_NoRedirect)


def fetch(url, follow=True, range_probe=False):
    headers = {"User-Agent": UA}
    if range_probe:
        headers["Range"] = "bytes=0-0"
    req = urllib.request.Request(url, headers=headers)
    opener = _opener_follow if follow else _opener_nofollow
    try:
        r = opener.open(req, timeout=25)
        return r.geturl(), r.read()
    except urllib.error.HTTPError as e:
        return e.headers.get("Location"), None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _json(self, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(parsed.query)
        path = parsed.path.rstrip("/")

        if path == "/search":
            keyword = (q.get("keywords") or [""])[0]
            try:
                limit = int((q.get("limit") or ["5"])[0])
            except ValueError:
                limit = 5
            _, body = fetch(SEARCH + urllib.parse.quote(keyword))
            try:
                data = json.loads((body or b"{}").decode("utf-8", "ignore") or "{}")
            except Exception:
                data = {}
            songs = []
            for it in (data.get("data") or [])[:limit]:
                cover = it.get("picUrl") or ""
                if cover.startswith("http://"):
                    cover = "https://" + cover[7:]
                song = {
                    "id": int(it.get("id")),
                    "name": it.get("song", ""),
                    "artists": [{"name": it.get("singer", "")}],
                    "album": {"name": it.get("album", ""), "picUrl": cover},
                    "duration": int(it.get("duration") or 0),
                }
                _last_songs[song["id"]] = song
                songs.append(song)
            return self._json({"result": {"songs": songs}})

        if path == "/song/detail":
            ids = (q.get("ids") or [""])[0]
            out = []
            for x in ids.split(","):
                try:
                    sid = int(x)
                except ValueError:
                    continue
                s = _last_songs.get(sid)
                if s:
                    out.append({
                        "id": s["id"],
                        "name": s["name"],
                        "ar": s["artists"],
                        "al": s["album"],
                        "dt": s["duration"],
                    })
            return self._json({"songs": out})

        if path == "/song/url/v1":
            sid = (q.get("id") or [""])[0]
            loc, _ = fetch(PLAY + urllib.parse.quote(sid), follow=False, range_probe=True)
            return self._json({"data": [{
                "id": int(sid) if sid.isdigit() else sid,
                "url": loc or (PLAY + sid),
            }]})

        self.send_response(404)
        self.end_headers()


if __name__ == "__main__":
    print(f"kxlove adapter listening on http://0.0.0.0:{PORT}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
