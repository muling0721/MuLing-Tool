import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;

long MAX_VIDEO_SIZE = 100 * 1024 * 1024;
int PAGE_SIZE = 5;
List adminList = new ArrayList();
List groupListCache = new ArrayList();
Set enabledGroups = new HashSet();
boolean pluginEnabled = true;
boolean groupParseEnabled = true;
boolean wifiOnlyParse = false;

Pattern urlPattern = Pattern.compile("https?://(?:(?:www\\.)?douyin\\.com|v\\.douyin\\.com|iesdouyin\\.com|(?:www\\.)?kuaishou\\.com|v\\.kuaishou\\.com|www\\.kwai\\.com|b23\\.tv|(?:www\\.)?bilibili\\.com|(?:www\\.)?xiaohongshu\\.com|xhslink\\.com|(?:www\\.)?weishi\\.qq\\.com|(?:www\\.)?shipinhao\\.weixin\\.qq\\.com|(?:www\\.)?tiktok\\.com|vm\\.tiktok\\.com|vt\\.tiktok\\.com)[^\\s]*");

void loadConfig() {
    try {
        File f = new File(pluginPath + "/config/config.json");
        if (!f.exists()) f = new File(pluginPath + "/config.json");
        if (f.exists()) {
            String json = readFile(f);
            MAX_VIDEO_SIZE = parseLong(json, "maxFileSizeMB", 100) * 1024 * 1024;
            PAGE_SIZE = (int)parseLong(json, "pageSize", 5);
        }

        long kvSize = getLong("config", "maxFileSizeMB", 0);
        if (kvSize > 0) MAX_VIDEO_SIZE = kvSize * 1024 * 1024;
        int kvPage = getInt("config", "pageSize", 0);
        if (kvPage > 0) PAGE_SIZE = kvPage;

        pluginEnabled     = getBoolean("config", "pluginEnabled",     true);
        groupParseEnabled = getBoolean("config", "groupParseEnabled", true);
        wifiOnlyParse     = getBoolean("config", "wifiOnlyParse",     false);

        enabledGroups.clear();
        String eg = getString("config", "enabledGroups", "");
        if (!eg.isEmpty()) {
            for (String g : eg.split(",")) {
                g = g.trim();
                if (!g.isEmpty()) enabledGroups.add(g);
            }
        }

        adminList.clear();
        String admins = getString("config", "adminList", "");
        if (!admins.isEmpty()) {
            for (String u : admins.split(",")) {
                u = u.trim();
                if (!u.isEmpty()) adminList.add(u);
            }
        }

        log("plugin_log.txt", "[配置] 加载完成 pluginEnabled=" + pluginEnabled + " groupParse=" + groupParseEnabled + " wifiOnly=" + wifiOnlyParse + " 已选群=" + enabledGroups.size());
    } catch (Exception e) {
        log("plugin_log.txt", "[配置] 加载失败: " + e);
    }
}

void saveConfig() {
    try {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"maxFileSizeMB\":").append(MAX_VIDEO_SIZE / 1024 / 1024).append(",");
        sb.append("\"pageSize\":").append(PAGE_SIZE).append(",");
        sb.append("\"pluginEnabled\":").append(pluginEnabled).append(",");
        sb.append("\"groupParseEnabled\":").append(groupParseEnabled).append(",");
        sb.append("\"onlySelf\":").append(getBoolean("config", "onlySelf", false)).append(",");
        
        sb.append("\"adminList\":[");
        for (int i = 0; i < adminList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(adminList.get(i)).append("\"");
        }
        sb.append("]");
        sb.append("}");
        
        File f = new File(pluginPath + "/config/config.json");
        f.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(f);
        fw.write(sb.toString());
        fw.close();
    } catch (Exception e) {
        log("plugin_log.txt", "[配置] 保存失败: " + e);
    }
}

void saveEnabledGroups() {
    StringBuilder sb = new StringBuilder();
    for (Object g : enabledGroups) {
        if (sb.length() > 0) sb.append(",");
        sb.append(g);
    }
    putString("config", "enabledGroups", sb.toString());
}

void applyLimitSettings(long newSizeMB, int newPage) {
    putLong("config", "maxFileSizeMB", newSizeMB);
    putInt("config", "pageSize", newPage);
    loadConfig();
}

void applyParseSettings(boolean onlyAdmin, String adminInput) {
    putBoolean("config", "onlySelf", onlyAdmin);
    adminList.clear();
    if (onlyAdmin && !adminInput.isEmpty()) {
        for (String u : adminInput.split("\n")) {
            u = u.trim();
            if (!u.isEmpty()) adminList.add(u);
        }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < adminList.size(); i++) {
        if (i > 0) sb.append(",");
        sb.append(adminList.get(i));
    }
    putString("config", "adminList", sb.toString());
    loadConfig();
}

void applyGroupSettings(boolean newParseEnabled, boolean newWifiOnly, Set newGroups) {
    putBoolean("config", "groupParseEnabled", newParseEnabled);
    putBoolean("config", "wifiOnlyParse",     newWifiOnly);

    StringBuilder sb = new StringBuilder();
    for (Object g : newGroups) {
        if (sb.length() > 0) sb.append(",");
        sb.append(g);
    }
    putString("config", "enabledGroups", sb.toString());

    loadConfig();
    log("plugin_log.txt", "[配置] 应用完成 groupParse=" + groupParseEnabled + " wifiOnly=" + wifiOnlyParse + " 已选群=" + enabledGroups.size());
}

boolean isWifiConnected() {
    try {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) return false;
        return ni.getType() == android.net.ConnectivityManager.TYPE_WIFI;
    } catch (Exception e) {
        return true;
    }
}

boolean isAdmin(String uin) {
    if (uin.equals(myUin)) return true;
    for (int i = 0; i < adminList.size(); i++) 
        if (adminList.get(i).equals(uin)) return true;
    return false;
}

boolean canParse(String uin) {
    return !getBoolean("config", "onlySelf", false) || isAdmin(uin);
}

boolean canRecall(String operatorUin, String senderUin, String groupUin) {
    if (operatorUin.equals(senderUin)) return true;
    
    try {
        Object opInfo = getMemberInfo(groupUin, operatorUin);
        Object senderInfo = getMemberInfo(groupUin, senderUin);
        
        String opRole = null;
        String senderRole = null;
        if (opInfo != null) opRole = opInfo.role;
        if (senderInfo != null) senderRole = senderInfo.role;
        
        if ("OWNER".equals(opRole)) return true;
        if ("ADMIN".equals(opRole) && "MEMBER".equals(senderRole)) return true;
    } catch (Exception e) {}
    
    return false;
}

void onMsg(Object d) {
    if (!pluginEnabled) return;
    if (d.type != 1 && d.type != 2) return;
    
    if (wifiOnlyParse && !isWifiConnected()) return;
    
    if (d.type == 2) {
        if (!groupParseEnabled) return;
        if (enabledGroups.isEmpty()) return;
        if (!enabledGroups.contains(d.peerUin)) return;
    }
    
    String msg = d.msg != null ? d.msg : "";
    boolean admin = isAdmin(d.userUin);

    if (d.type == 2 && admin && d.atList != null && d.atList.size() > 0 && msg.contains(" tx")) {
        String title = msg.substring(msg.indexOf(" tx") + 3).trim();
        for (int i = 0; i < d.atList.size(); i++) 
            setGroupMemberTitle(d.peerUin, (String)d.atList.get(i), title);
        sleepRecall(admin, d.type, d.peerUin, d.msgId);
        return;
    }

    if (msg.trim().equals("继续") && canParse(d.userUin)) {
        String qv = getString("queue", "q_" + d.peerUin, "");
        if (!qv.isEmpty()) {
            new Thread(() -> sendNextPage(d.peerUin, d.type, "q_" + d.peerUin, qv, d.msgId, admin)).start();
            return;
        }
    }

    Matcher m = urlPattern.matcher(msg);
    if (!m.find() || !canParse(d.userUin)) return;
    
    String url = m.group();
    new Thread(() -> parseAndSend(url, d.peerUin, d.type, d.msgId, admin, d.userUin)).start();
}

void parseAndSend(String url, String peer, int type, long mid, boolean admin, String senderUin) {
    try {
        mkDirs();
        String enc = URLEncoder.encode(url, "UTF-8");
        
        List cv = findCache(url, "video");
        if (cv != null) { 
            sendVideo(peer, (String)cv.get(0), type); 
            tryRecall(admin, type, peer, mid, senderUin); 
            return; 
        }
        
        List ci = findCache(url, "image");
        if (ci != null) { 
            sendImageList(peer, type, ci, mid, admin, senderUin); 
            return; 
        }

        String videoUrl = null;
        List imgs = null;

        if (url.contains("tiktok.com")) {
            videoUrl = parseTikTok(url);
        } else {
            String j1 = httpGet("http://47.99.158.118/video-crack/v2/parse?content=" + enc);
            if (j1 != null && "0".equals(val(j1, "code"))) {
                String u = val(extractData(j1), "url");
                if (u != null && !u.isEmpty()) videoUrl = u;
                else imgs = arr(j1, "imageUrl");
            }

            if (videoUrl == null && imgs == null) {
                String j2 = httpGet("http://api.guiguiya.com/api/video_qsy/juhe?url=" + enc);
                if (j2 != null && "200".equals(val(j2, "code"))) {
                    String u = val(extractData(j2), "url");
                    if (u != null && !u.isEmpty() && !u.contains(".mp3")) videoUrl = u;
                }
            }

            if (videoUrl == null && imgs == null) {
                String raw = httpGet("https://api.tangdouz.com/dy.php?lj=" + enc);
                if (raw != null) {
                    raw = raw.trim();
                    if (raw.startsWith("http") && !raw.contains("±")) videoUrl = raw;
                    else if (raw.contains("±img=")) imgs = parseTangdouz(raw);
                }
            }
        }

        if (videoUrl != null) {
            String sp = pluginPath + "/cache/video/video_" + System.currentTimeMillis() + ".mp4";
            if (downloadFile(videoUrl, sp)) {
                log("plugin_log.txt", "[缓存] video|" + url + "|" + sp);
                sendVideo(peer, sp, type);
                tryRecall(admin, type, peer, mid, senderUin);
            }
        } else if (imgs != null) {
            List saved = downloadImages(imgs);
            if (saved.size() > 0) {
                log("plugin_log.txt", "[缓存] image|" + url + "|" + join(saved, ","));
                sendImageList(peer, type, saved, mid, admin, senderUin);
            }
        }
    } catch (Exception e) { 
        log("plugin_log.txt", "[异常] " + e); 
    }
}

List downloadImages(List urls) {
    List saved = new ArrayList();
    for (int i = 0; i < urls.size(); i++) {
        String iu = (String)urls.get(i);
        if (iu == null || iu.isEmpty()) continue;
        String ip = pluginPath + "/cache/image/img_" + System.currentTimeMillis() + "_" + i + ".jpg";
        if (downloadFile(iu, ip)) saved.add(ip);
    }
    return saved;
}

void sendImageList(String peer, int type, List paths, long mid, boolean admin, String senderUin) {
    int total = paths.size();
    if (total == 0) return;
    
    tryRecall(admin, type, peer, mid, senderUin);
    
    if (total <= PAGE_SIZE) {
        sendPage(peer, type, paths, 0, total, total);
        putString("queue", "q_" + peer, "");
    } else {
        putString("queue", "q_" + peer, buildQ(0, total, paths));
        sendPage(peer, type, paths, 0, PAGE_SIZE, total);
        sendReplyMsg(peer, mid, "📷 图集共" + total + "张，已展示前" + PAGE_SIZE + "张\n回复「继续」查看剩余" + (total - PAGE_SIZE) + "张", type);
    }
}

void sendNextPage(String peer, int type, String qkey, String qval, long mid, boolean admin) {
    try {
        int fc = qval.indexOf(","), sc = qval.indexOf(",", fc + 1);
        int offset = Integer.parseInt(qval.substring(0, fc));
        int total = Integer.parseInt(qval.substring(fc + 1, sc));
        List paths = new ArrayList(Arrays.asList(qval.substring(sc + 1).split(",")));
        int from = offset + PAGE_SIZE, to = Math.min(from + PAGE_SIZE, total), remaining = total - to;
        
        sendPage(peer, type, paths, from, to, total);
        
        if (remaining > 0) {
            putString("queue", qkey, buildQ(from, total, paths));
            sendReplyMsg(peer, mid, "📷 已展示第" + (from + 1) + "~" + to + "张，还剩" + remaining + "张\n回复「继续」继续查看", type);
        } else {
            putString("queue", qkey, "");
        }
    } catch (Exception e) { 
        log("plugin_log.txt", "[翻页异常] " + e); 
    }
}

void sendPage(String peer, int type, List paths, int from, int to, int total) {
    StringBuilder sb = new StringBuilder();
    sb.append(total > PAGE_SIZE ? "📷 图集第" + (from + 1) + "~" + to + "张（共" + total + "张）\n" : "📷 图集共" + total + "张\n");
    for (int i = from; i < to; i++) sb.append("[pic=").append(paths.get(i)).append("]");
    sendMsg(peer, sb.toString(), type);
    log("plugin_log.txt", "[图集] 发送第" + (from + 1) + "~" + to + "张");
}

void tryRecall(boolean doIt, int type, String peer, long mid, String senderUin) {
    if (!doIt) return;
    if (type == 2 && !canRecall(myUin, senderUin, peer)) return;
    
    try { 
        Thread.sleep(500); 
        recallMsg(type, peer, mid); 
    } catch (Exception e) {}
}

void sleepRecall(boolean doIt, int type, String peer, long mid) {
    if (!doIt) return;
    try { 
        Thread.sleep(500); 
        recallMsg(type, peer, mid); 
    } catch (Exception e) {}
}

List findCache(String url, String t) {
    try {
        File f = new File(pluginPath + "/plugin_log.txt");
        if (!f.exists()) return null;
        
        String marker = "[缓存] " + t + "|" + url + "|";
        String found = null;
        
        BufferedReader br = new BufferedReader(new FileReader(f));
        String ln;
        while ((ln = br.readLine()) != null) {
            if (ln.contains(marker)) found = ln.substring(ln.lastIndexOf("|") + 1).trim();
        }
        br.close();
        
        if (found == null) return null;
        
        if (t.equals("video")) {
            File vf = new File(found);
            if (vf.exists() && vf.length() > 1024) {
                List r = new ArrayList();
                r.add(found);
                return r;
            }
        } else {
            List ps = new ArrayList();
            for (String p : found.split(",")) {
                File pf = new File(p.trim());
                if (pf.exists() && pf.length() > 1024) ps.add(p.trim());
            }
            if (ps.size() > 0) return ps;
        }
    } catch (Exception e) { 
        log("plugin_log.txt", "[异常] findCache: " + e); 
    }
    return null;
}

void mkDirs() {
    new File(pluginPath + "/cache/video/").mkdirs();
    new File(pluginPath + "/cache/image/").mkdirs();
}

String readFile(File f) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(f));
    StringBuilder sb = new StringBuilder();
    String ln;
    while ((ln = br.readLine()) != null) sb.append(ln);
    br.close();
    return sb.toString();
}

long parseLong(String json, String key, long def) {
    try {
        int i = json.indexOf("\"" + key + "\"");
        if (i == -1) return def;
        int col = json.indexOf(":", i);
        int e2 = json.indexOf(",", col);
        if (e2 == -1) e2 = json.indexOf("}", col);
        return Long.parseLong(json.substring(col + 1, e2).trim());
    } catch (Exception e) { return def; }
}

boolean parseBool(String json, String key, boolean def) {
    try {
        int i = json.indexOf("\"" + key + "\"");
        if (i == -1) return def;
        int col = json.indexOf(":", i);
        int e2 = json.indexOf(",", col);
        if (e2 == -1) e2 = json.indexOf("}", col);
        return json.substring(col + 1, e2).trim().equals("true");
    } catch (Exception e) { return def; }
}

String parseArrStr(String json, String key) {
    int i = json.indexOf("\"" + key + "\"");
    if (i == -1) return null;
    int s = json.indexOf("[", i), e = json.indexOf("]", s);
    if (s == -1 || e == -1) return null;
    return json.substring(s + 1, e);
}

String parseTikTok(String url) {
    try {
        String enc = URLEncoder.encode(url, "UTF-8");
        String json = httpGet("https://douyin.wtf/api/hybrid/video_data?url=" + enc + "&minimal=true");
        if (json == null) return null;
        if (!"200".equals(val(json, "code"))) return null;
        String nwmHQ = val(json, "nwm_video_url_HQ");
        if (nwmHQ != null && !nwmHQ.isEmpty()) return nwmHQ;
        String nwm = val(json, "nwm_video_url");
        if (nwm != null && !nwm.isEmpty()) return nwm;
        String wmHQ = val(json, "wm_video_url_HQ");
        if (wmHQ != null && !wmHQ.isEmpty()) return wmHQ;
        return val(json, "wm_video_url");
    } catch (Exception e) {
        log("plugin_log.txt", "[异常] parseTikTok: " + e);
        return null;
    }
}

List parseTangdouz(String raw) {
    List l = new ArrayList();
    for (String p : raw.split("±")) {
        p = p.trim();
        if (p.startsWith("img=")) {
            String u = p.substring(4).trim();
            if (!u.isEmpty()) l.add(u);
        }
    }
    return l;
}

String extractData(String json) {
    int i = json.indexOf("\"data\"");
    if (i == -1) return json;
    int b = json.indexOf("{", i);
    if (b == -1) return json;
    int d = 0, e = b;
    while (e < json.length()) {
        char c = json.charAt(e);
        if (c == '{') d++;
        else if (c == '}') {
            if (--d == 0) break;
        }
        e++;
    }
    return json.substring(b, e + 1);
}

List arr(String json, String key) {
    List l = new ArrayList();
    int ki = json.indexOf("\"" + key + "\"");
    if (ki == -1) return l;
    int as = json.indexOf("[", ki), ae = json.indexOf("]", as);
    if (as == -1 || ae == -1) return l;
    String c = json.substring(as + 1, ae);
    int pos = 0;
    while (pos < c.length()) {
        int q1 = c.indexOf("\"", pos);
        if (q1 == -1) break;
        int q2 = q1 + 1;
        while (q2 < c.length()) {
            if (c.charAt(q2) == '"' && c.charAt(q2 - 1) != '\\') break;
            q2++;
        }
        l.add(c.substring(q1 + 1, q2));
        pos = q2 + 1;
    }
    return l;
}

String httpGet(String u) {
    try {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0.4472.120 Mobile Safari/537.36");
        if (c.getResponseCode() != 200) return null;
        
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String ln;
        while ((ln = r.readLine()) != null) sb.append(ln);
        r.close();
        return sb.toString();
    } catch (Exception e) { 
        log("plugin_log.txt", "[异常] httpGet: " + e); 
        return null; 
    }
}

boolean downloadFile(String url, String path) {
    try {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/91.0.4472.120 Mobile Safari/537.36");
        c.setRequestProperty("Referer", "https://www.douyin.com/");
        
        if (c.getResponseCode() != 200) {
            log("plugin_log.txt", "[下载失败] HTTP " + c.getResponseCode());
            return false;
        }
        
        long cl = c.getContentLengthLong();
        if (cl > MAX_VIDEO_SIZE) {
            log("plugin_log.txt", "[跳过] 超过限制");
            return false;
        }
        
        InputStream in = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(path);
        byte[] buf = new byte[8192];
        int len;
        long total = 0;
        
        while ((len = in.read(buf)) != -1) {
            total += len;
            if (total > MAX_VIDEO_SIZE) {
                fo.close();
                in.close();
                new File(path).delete();
                return false;
            }
            fo.write(buf, 0, len);
        }
        fo.close();
        in.close();
        
        long sz = new File(path).length();
        log("plugin_log.txt", "[下载] " + (Math.round(sz * 10.0 / 1024 / 1024) / 10.0) + "M");
        return sz > 1024;
    } catch (Exception e) { 
        log("plugin_log.txt", "[异常] download: " + e); 
        return false; 
    }
}

String val(String json, String key) {
    try {
        int ki = json.indexOf("\"" + key + "\"");
        if (ki == -1) return null;
        int ci = json.indexOf(":", ki + key.length() + 2);
        if (ci == -1) return null;
        int s = ci + 1;
        while (s < json.length() && json.charAt(s) == ' ') s++;
        char f = json.charAt(s);
        
        if (f == '"') {
            int e = s + 1;
            while (e < json.length()) {
                if (json.charAt(e) == '"' && json.charAt(e - 1) != '\\') break;
                e++;
            }
            return json.substring(s + 1, e);
        }
        if (f == 'n') return null;
        
        int e = s;
        while (e < json.length() && ",}\n".indexOf(json.charAt(e)) == -1) e++;
        return json.substring(s, e).trim();
    } catch (Exception e) { return null; }
}

String buildQ(int offset, int total, List paths) { 
    return offset + "," + total + "," + join(paths, ","); 
}

String join(List l, String sep) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < l.size(); i++) {
        if (i > 0) sb.append(sep);
        sb.append(l.get(i));
    }
    return sb.toString();
}

void loadGroupList() {
    try {
        groupListCache = getGroupList();
        log("plugin_log.txt", "[群列表] 预加载 " + groupListCache.size() + " 个群");
    } catch (Exception e) {
        log("plugin_log.txt", "[群列表] 预加载失败: " + e);
    }
}

Drawable createRoundRect(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
}

LinearLayout createRadioRow(Activity act, String text, boolean checked) {
    LinearLayout row = new LinearLayout(act);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(20, 24, 20, 24);
    
    TextView label = new TextView(act);
    label.setText(text);
    label.setTextSize(15);
    label.setTextColor(Color.BLACK);
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
    label.setLayoutParams(labelParams);
    
    TextView check = new TextView(act);
    check.setText(checked ? "●" : "○");
    check.setTextSize(24);
    check.setTextColor(checked ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
    
    row.addView(label);
    row.addView(check);
    
    View line = new View(act);
    line.setBackgroundColor(Color.parseColor("#EEEEEE"));
    LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, 1);
    line.setLayoutParams(lineParams);
    
    LinearLayout wrap = new LinearLayout(act);
    wrap.setOrientation(LinearLayout.VERTICAL);
    wrap.addView(row);
    wrap.addView(line);
    
    return wrap;
}

Button createSaveButton(Activity act, Runnable onClick) {
    Button btn = new Button(act);
    btn.setText("保存");
    btn.setBackground(createRoundRect(Color.parseColor("#4CAF50"), 25));
    btn.setTextColor(Color.WHITE);
    btn.setPadding(40, 20, 40, 20);
    btn.setOnClickListener(v -> onClick.run());
    return btn;
}

void showParseSettings(int type, String peer, String name) {
    Activity act = getNowActivity();
    if (act == null) return;
    
    act.runOnUiThread(() -> {
        try {
            Dialog dialog = new Dialog(act);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(createRoundRect(Color.WHITE, 25));
            root.setPadding(40, 40, 40, 40);
            
            TextView title = new TextView(act);
            title.setText("解析权限");
            title.setTextSize(18);
            title.setTextColor(Color.BLACK);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
            titleParams.setMargins(0, 0, 0, 30);
            root.addView(title, titleParams);
            
            boolean onlyAdmin = getBoolean("config", "onlySelf", false);
            
            LinearLayout allRow = createRadioRow(act, "所有人", !onlyAdmin);
            LinearLayout adminRow = createRadioRow(act, "仅管理员", onlyAdmin);
            
            root.addView(allRow);
            root.addView(adminRow);
            
            EditText adminInput = new EditText(act);
            adminInput.setHint("输入管理员QQ号，多个用换行分隔");
            adminInput.setBackground(createRoundRect(Color.parseColor("#F5F5F5"), 12));
            adminInput.setPadding(30, 20, 30, 20);
            adminInput.setTextSize(14);
            adminInput.setMinLines(3);
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < adminList.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(adminList.get(i));
            }
            adminInput.setText(sb.toString());
            
            if (!onlyAdmin) adminInput.setVisibility(View.GONE);
            root.addView(adminInput);
            
            LinearLayout allWrap = (LinearLayout)allRow.getChildAt(0);
            LinearLayout adminWrap = (LinearLayout)adminRow.getChildAt(0);
            TextView allCheck = (TextView)allWrap.getChildAt(1);
            TextView adminCheck = (TextView)adminWrap.getChildAt(1);
            
            allRow.setOnClickListener(v -> {
                allCheck.setText("●");
                allCheck.setTextColor(Color.parseColor("#4CAF50"));
                adminCheck.setText("○");
                adminCheck.setTextColor(Color.parseColor("#CCCCCC"));
                adminInput.setVisibility(View.GONE);
            });
            
            adminRow.setOnClickListener(v -> {
                adminCheck.setText("●");
                adminCheck.setTextColor(Color.parseColor("#4CAF50"));
                allCheck.setText("○");
                allCheck.setTextColor(Color.parseColor("#CCCCCC"));
                adminInput.setVisibility(View.VISIBLE);
            });
            
            Button saveBtn = createSaveButton(act, () -> {
                boolean isOnlyAdmin = adminCheck.getText().equals("●");
                String inputText = isOnlyAdmin ? adminInput.getText().toString().trim() : "";
                applyParseSettings(isOnlyAdmin, inputText);
                qqToast(2, "设置已保存");
                dialog.dismiss();
            });
            
            LinearLayout btnLayout = new LinearLayout(act);
            btnLayout.setGravity(Gravity.RIGHT);
            btnLayout.setPadding(0, 30, 0, 0);
            btnLayout.addView(saveBtn);
            root.addView(btnLayout);
            
            dialog.setContentView(root);
            dialog.show();
            dialog.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.9), -2);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
        } catch (Exception e) {
            log("plugin_log.txt", "[UI错误] 解析权限: " + e);
        }
    });
}

void showGroupSettings(int type, String peer, String name) {
    Activity act = getNowActivity();
    if (act == null) return;
    
    new Thread(() -> {
        try {
            List fresh = getGroupList();
            if (fresh != null && !fresh.isEmpty()) {
                groupListCache.clear();
                groupListCache.addAll(fresh);
            }
        } catch (Exception e) {
            log("plugin_log.txt", "[刷新群列表] " + e);
        }
        
        act.runOnUiThread(() -> {
            try {
                Dialog dialog = new Dialog(act);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                
                LinearLayout root = new LinearLayout(act);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackground(createRoundRect(Color.WHITE, 25));
                root.setPadding(40, 40, 40, 40);
                
                TextView title = new TextView(act);
                title.setText("生效范围");
                title.setTextSize(18);
                title.setTextColor(Color.BLACK);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
                titleParams.setMargins(0, 0, 0, 20);
                root.addView(title, titleParams);
                
                final boolean[] currentParseState = {getBoolean("config", "groupParseEnabled", true)};
                TextView switchCheck = new TextView(act);
                switchCheck.setText(currentParseState[0] ? "●" : "○");
                switchCheck.setTextSize(24);
                switchCheck.setTextColor(currentParseState[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
                
                LinearLayout switchWrap = buildSwitchRow(act, "群聊解析", switchCheck);
                root.addView(switchWrap);
                
                final boolean[] currentWifiState = {getBoolean("config", "wifiOnlyParse", false)};
                TextView wifiCheck = new TextView(act);
                wifiCheck.setText(currentWifiState[0] ? "●" : "○");
                wifiCheck.setTextSize(24);
                wifiCheck.setTextColor(currentWifiState[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
                
                LinearLayout wifiWrap = buildSwitchRow(act, "仅WIFI下解析", wifiCheck);
                root.addView(wifiWrap);
                
                final TextView stateTip = new TextView(act);
                stateTip.setText(currentParseState[0] ? "✓ 群聊解析已开启，请勾选需要生效的群" : "✗ 群聊解析已关闭");
                stateTip.setTextSize(12);
                stateTip.setTextColor(currentParseState[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#999999"));
                LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(-2, -2);
                tipParams.setMargins(20, 5, 0, 10);
                stateTip.setLayoutParams(tipParams);
                root.addView(stateTip);
                
                LinearLayout topBar = new LinearLayout(act);
                topBar.setOrientation(LinearLayout.HORIZONTAL);
                topBar.setGravity(Gravity.CENTER_VERTICAL);
                topBar.setPadding(0, 20, 0, 20);
                
                EditText search = new EditText(act);
                search.setHint("输入群号或群名过滤...");
                search.setBackground(createRoundRect(Color.parseColor("#F5F5F5"), 12));
                search.setPadding(30, 20, 30, 20);
                search.setTextSize(14);
                LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
                search.setLayoutParams(searchParams);
                
                Button selectAllBtn = new Button(act);
                selectAllBtn.setText("全选");
                selectAllBtn.setBackground(createRoundRect(Color.parseColor("#FF9800"), 16));
                selectAllBtn.setTextColor(Color.WHITE);
                selectAllBtn.setPadding(20, 12, 20, 12);
                LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(-2, -2);
                selectParams.setMargins(20, 0, 0, 0);
                selectAllBtn.setLayoutParams(selectParams);
                
                topBar.addView(search);
                topBar.addView(selectAllBtn);
                root.addView(topBar);
                
                ScrollView scroll = new ScrollView(act);
                scroll.setPadding(0, 20, 0, 0);
                LinearLayout listContainer = new LinearLayout(act);
                listContainer.setOrientation(LinearLayout.VERTICAL);
                scroll.addView(listContainer);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 800);
                root.addView(scroll, scrollParams);
                
                LinearLayout btnBar = new LinearLayout(act);
                btnBar.setOrientation(LinearLayout.HORIZONTAL);
                btnBar.setPadding(0, 20, 0, 0);
                btnBar.setGravity(Gravity.RIGHT);
                
                Button saveBtn = new Button(act);
                saveBtn.setText("保存");
                saveBtn.setBackground(createRoundRect(Color.parseColor("#4CAF50"), 20));
                saveBtn.setTextColor(Color.WHITE);
                saveBtn.setPadding(30, 16, 30, 16);
                btnBar.addView(saveBtn);
                root.addView(btnBar);
                
                TextView tip = new TextView(act);
                tip.setText("私聊解析始终开启");
                tip.setTextSize(12);
                tip.setTextColor(Color.GRAY);
                LinearLayout.LayoutParams tipParams2 = new LinearLayout.LayoutParams(-2, -2);
                tipParams2.setMargins(0, 10, 0, 0);
                root.addView(tip, tipParams2);
                
                dialog.setContentView(root);
                
                final boolean[] allSelected = {false};
                final List groupChecks = new ArrayList();
                final List groupIds = new ArrayList();
                final Set[] pendingGroups = {new HashSet()};
                pendingGroups[0].addAll(enabledGroups);
                
                switchWrap.setOnClickListener(v -> {
                    currentParseState[0] = !currentParseState[0];
                    switchCheck.setText(currentParseState[0] ? "●" : "○");
                    switchCheck.setTextColor(currentParseState[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
                    stateTip.setText(currentParseState[0] ? "✓ 群聊解析已开启，请勾选需要生效的群" : "✗ 群聊解析已关闭（群选择已保留）");
                    stateTip.setTextColor(currentParseState[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#999999"));
                    updateGroupListClickable(listContainer, currentParseState[0]);
                    if (!currentParseState[0]) {
                        allSelected[0] = false;
                        selectAllBtn.setText("全选");
                    }
                });
                
                wifiWrap.setOnClickListener(v -> {
                    currentWifiState[0] = !currentWifiState[0];
                    wifiCheck.setText(currentWifiState[0] ? "●" : "○");
                    wifiCheck.setTextColor(currentWifiState[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
                });
                
                selectAllBtn.setOnClickListener(v -> {
                    if (!currentParseState[0]) {
                        qqToast(1, "请先开启群聊解析");
                        return;
                    }
                    allSelected[0] = !allSelected[0];
                    selectAllBtn.setText(allSelected[0] ? "反选" : "全选");
                    for (int i = 0; i < groupChecks.size(); i++) {
                        TextView check = (TextView) groupChecks.get(i);
                        String gid = (String) groupIds.get(i);
                        check.setText(allSelected[0] ? "●" : "○");
                        check.setTextColor(allSelected[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
                        if (allSelected[0]) pendingGroups[0].add(gid);
                        else pendingGroups[0].remove(gid);
                    }
                });
                
                search.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        updateGroupListWithSelect(listContainer, s.toString(), act, groupChecks, groupIds, currentParseState[0], pendingGroups[0]);
                    }
                    public void afterTextChanged(Editable s) {}
                });
                
                saveBtn.setOnClickListener(v -> {
                    applyGroupSettings(currentParseState[0], currentWifiState[0], pendingGroups[0]);
                    qqToast(2, "设置已保存");
                    dialog.dismiss();
                });
                
                updateGroupListWithSelect(listContainer, "", act, groupChecks, groupIds, currentParseState[0], pendingGroups[0]);
                
                dialog.show();
                dialog.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.9), -2);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                
            } catch (Exception e) {
                log("plugin_log.txt", "[UI错误] 生效范围: " + e);
            }
        });
    }).start();
}

void updateGroupListWithSelect(LinearLayout container, String filter, Activity act, List checks, List ids, boolean parseEnabled, Set groupSet) {
    container.removeAllViews();
    checks.clear();
    ids.clear();
    String f = filter.toLowerCase();
    
    for (int i = 0; i < groupListCache.size(); i++) {
        Object g = groupListCache.get(i);
        String gid = null;
        String gname = null;
        
        if (g instanceof Map) {
            Map m = (Map) g;
            gid = (String) m.get("group");
            gname = (String) m.get("groupName");
        } else {
            try {
                gid = (String) g.getClass().getField("group").get(g);
                gname = (String) g.getClass().getField("groupName").get(g);
            } catch (Exception e) {
                continue;
            }
        }
        
        if (gid == null) continue;
        if (!f.isEmpty() && !gid.contains(f) && !(gname != null && gname.toLowerCase().contains(f))) continue;
        
        LinearLayout item = createGroupItemWithSelect(act, gid, gname != null ? gname : "未命名", checks, ids, parseEnabled, groupSet);
        container.addView(item);
    }
    
    if (container.getChildCount() == 0) {
        TextView empty = new TextView(act);
        empty.setText("无匹配群聊");
        empty.setTextColor(Color.GRAY);
        empty.setPadding(20, 40, 20, 40);
        empty.setGravity(Gravity.CENTER);
        container.addView(empty);
    }
}

void updateGroupListClickable(LinearLayout container, boolean clickable) {
    for (int i = 0; i < container.getChildCount(); i++) {
        View child = container.getChildAt(i);
        if (child instanceof LinearLayout) {
            LinearLayout wrap = (LinearLayout) child;
            if (wrap.getChildCount() > 0 && wrap.getChildAt(0) instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) wrap.getChildAt(0);
                row.setEnabled(clickable);
                row.setAlpha(clickable ? 1.0f : 0.5f);
            }
        }
    }
}

LinearLayout createGroupItemWithSelect(Activity act, String gid, String gname, List checks, List ids, boolean parseEnabled, Set groupSet) {
    LinearLayout row = new LinearLayout(act);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(24, 20, 24, 20);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setEnabled(parseEnabled);
    row.setAlpha(parseEnabled ? 1.0f : 0.5f);
    
    LinearLayout info = new LinearLayout(act);
    info.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
    info.setLayoutParams(infoParams);
    
    TextView nameView = new TextView(act);
    nameView.setText(gname);
    nameView.setTextSize(15);
    nameView.setTextColor(Color.BLACK);
    
    TextView idView = new TextView(act);
    idView.setText(gid);
    idView.setTextSize(12);
    idView.setTextColor(Color.GRAY);
    
    info.addView(nameView);
    info.addView(idView);
    
    boolean checked = groupSet.contains(gid);
    TextView check = new TextView(act);
    check.setText(checked ? "●" : "○");
    check.setTextSize(28);
    check.setTextColor(checked ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
    LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(-2, -2);
    checkParams.setMargins(20, 0, 0, 0);
    check.setLayoutParams(checkParams);
    
    checks.add(check);
    ids.add(gid);
    
    row.addView(info);
    row.addView(check);
    
    row.setOnClickListener(v -> {
        if (!parseEnabled) {
            qqToast(1, "请先开启群聊解析");
            return;
        }
        boolean newChecked = check.getText().equals("○");
        check.setText(newChecked ? "●" : "○");
        check.setTextColor(newChecked ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
        if (newChecked) groupSet.add(gid);
        else groupSet.remove(gid);
    });
    
    View line = new View(act);
    line.setBackgroundColor(Color.parseColor("#F0F0F0"));
    LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, 1);
    line.setLayoutParams(lineParams);
    
    LinearLayout wrap = new LinearLayout(act);
    wrap.setOrientation(LinearLayout.VERTICAL);
    wrap.addView(row);
    wrap.addView(line);
    
    return wrap;
}

void showLimitSettings(int type, String peer, String name) {
    Activity act = getNowActivity();
    if (act == null) return;
    
    act.runOnUiThread(() -> {
        try {
            Dialog dialog = new Dialog(act);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(createRoundRect(Color.WHITE, 25));
            root.setPadding(40, 40, 40, 40);
            
            TextView title = new TextView(act);
            title.setText("限制设置");
            title.setTextSize(18);
            title.setTextColor(Color.BLACK);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
            titleParams.setMargins(0, 0, 0, 30);
            root.addView(title, titleParams);
            
            TextView videoLabel = new TextView(act);
            videoLabel.setText("视频最大大小 (MB)");
            videoLabel.setTextSize(14);
            videoLabel.setTextColor(Color.GRAY);
            root.addView(videoLabel);
            
            EditText videoInput = new EditText(act);
            long curSizeMB = getLong("config", "maxFileSizeMB", 0);
            if (curSizeMB <= 0) curSizeMB = MAX_VIDEO_SIZE / 1024 / 1024;
            videoInput.setText(String.valueOf(curSizeMB));
            videoInput.setBackground(createRoundRect(Color.parseColor("#F5F5F5"), 12));
            videoInput.setPadding(30, 20, 30, 20);
            videoInput.setTextSize(14);
            videoInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            root.addView(videoInput);
            
            TextView pageLabel = new TextView(act);
            pageLabel.setText("每页图集数量");
            pageLabel.setTextSize(14);
            pageLabel.setTextColor(Color.GRAY);
            LinearLayout.LayoutParams pageLabelParams = new LinearLayout.LayoutParams(-2, -2);
            pageLabelParams.setMargins(0, 20, 0, 0);
            root.addView(pageLabel, pageLabelParams);
            
            EditText pageInput = new EditText(act);
            int curPage = getInt("config", "pageSize", 0);
            if (curPage <= 0) curPage = PAGE_SIZE;
            pageInput.setText(String.valueOf(curPage));
            pageInput.setBackground(createRoundRect(Color.parseColor("#F5F5F5"), 12));
            pageInput.setPadding(30, 20, 30, 20);
            pageInput.setTextSize(14);
            pageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            root.addView(pageInput);
            
            Button saveBtn = createSaveButton(act, () -> {
                try {
                    long newSize = Long.parseLong(videoInput.getText().toString().trim());
                    int newPage = Integer.parseInt(pageInput.getText().toString().trim());
                    applyLimitSettings(newSize, newPage);
                    qqToast(2, "设置已保存");
                    dialog.dismiss();
                } catch (Exception e) {
                    qqToast(1, "输入无效");
                }
            });
            
            LinearLayout btnLayout = new LinearLayout(act);
            btnLayout.setGravity(Gravity.RIGHT);
            btnLayout.setPadding(0, 30, 0, 0);
            btnLayout.addView(saveBtn);
            root.addView(btnLayout);
            
            dialog.setContentView(root);
            dialog.show();
            dialog.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.9), -2);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
        } catch (Exception e) {
            log("plugin_log.txt", "[UI错误] 限制设置: " + e);
        }
    });
}

void showClearSettings(int type, String peer, String name) {
    Activity act = getNowActivity();
    if (act == null) return;
    
    act.runOnUiThread(() -> {
        try {
            Dialog dialog = new Dialog(act);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(createRoundRect(Color.WHITE, 25));
            root.setPadding(40, 40, 40, 40);
            
            TextView title = new TextView(act);
            title.setText("清理缓存");
            title.setTextSize(18);
            title.setTextColor(Color.BLACK);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
            titleParams.setMargins(0, 0, 0, 30);
            root.addView(title, titleParams);
            
            final boolean[] clearVideo = {false};
            final boolean[] clearImage = {false};
            final boolean[] clearLogFile = {false};
            
            LinearLayout videoRow = createCheckRow(act, "清除视频缓存", clearVideo[0]);
            LinearLayout imageRow = createCheckRow(act, "清除图集缓存", clearImage[0]);
            LinearLayout logRow = createCheckRow(act, "清除日志", clearLogFile[0]);
            
            root.addView(videoRow);
            root.addView(imageRow);
            root.addView(logRow);
            
            TextView videoCheck = (TextView)((LinearLayout)videoRow.getChildAt(0)).getChildAt(1);
            TextView imageCheck = (TextView)((LinearLayout)imageRow.getChildAt(0)).getChildAt(1);
            TextView logCheck = (TextView)((LinearLayout)logRow.getChildAt(0)).getChildAt(1);
            
            videoRow.setOnClickListener(v -> {
                clearVideo[0] = !clearVideo[0];
                videoCheck.setText(clearVideo[0] ? "●" : "○");
                videoCheck.setTextColor(clearVideo[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
            });
            
            imageRow.setOnClickListener(v -> {
                clearImage[0] = !clearImage[0];
                imageCheck.setText(clearImage[0] ? "●" : "○");
                imageCheck.setTextColor(clearImage[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
            });
            
            logRow.setOnClickListener(v -> {
                clearLogFile[0] = !clearLogFile[0];
                logCheck.setText(clearLogFile[0] ? "●" : "○");
                logCheck.setTextColor(clearLogFile[0] ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
            });
            
            Button saveBtn = createSaveButton(act, () -> {
                int c = 0;
                if (clearVideo[0]) c += clearDir(pluginPath + "/cache/video/");
                if (clearImage[0]) c += clearDir(pluginPath + "/cache/image/");
                if (clearLogFile[0]) {
                    File f = new File(pluginPath + "/plugin_log.txt");
                    if (f.exists()) f.delete();
                }
                
                String msg = "";
                if (clearVideo[0]) msg += "视频 ";
                if (clearImage[0]) msg += "图集 ";
                if (clearLogFile[0]) msg += "日志 ";
                
                qqToast(2, "已清理: " + (msg.isEmpty() ? "无" : msg));
                dialog.dismiss();
            });
            
            LinearLayout btnLayout = new LinearLayout(act);
            btnLayout.setGravity(Gravity.RIGHT);
            btnLayout.setPadding(0, 30, 0, 0);
            btnLayout.addView(saveBtn);
            root.addView(btnLayout);
            
            dialog.setContentView(root);
            dialog.show();
            dialog.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.9), -2);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
        } catch (Exception e) {
            log("plugin_log.txt", "[UI错误] 清理缓存: " + e);
        }
    });
}

LinearLayout buildSwitchRow(Activity act, String text, TextView checkView) {
    LinearLayout row = new LinearLayout(act);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(20, 24, 20, 24);
    
    TextView label = new TextView(act);
    label.setText(text);
    label.setTextSize(15);
    label.setTextColor(Color.BLACK);
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
    label.setLayoutParams(labelParams);
    
    row.addView(label);
    row.addView(checkView);
    
    View line = new View(act);
    line.setBackgroundColor(Color.parseColor("#EEEEEE"));
    LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, 1);
    line.setLayoutParams(lineParams);
    
    LinearLayout wrap = new LinearLayout(act);
    wrap.setOrientation(LinearLayout.VERTICAL);
    wrap.addView(row);
    wrap.addView(line);
    
    return wrap;
}

LinearLayout createCheckRow(Activity act, String text, boolean checked) {
    LinearLayout row = new LinearLayout(act);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(20, 24, 20, 24);
    
    TextView label = new TextView(act);
    label.setText(text);
    label.setTextSize(15);
    label.setTextColor(Color.BLACK);
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
    label.setLayoutParams(labelParams);
    
    TextView check = new TextView(act);
    check.setText(checked ? "●" : "○");
    check.setTextSize(24);
    check.setTextColor(checked ? Color.parseColor("#4CAF50") : Color.parseColor("#CCCCCC"));
    
    row.addView(label);
    row.addView(check);
    
    View line = new View(act);
    line.setBackgroundColor(Color.parseColor("#EEEEEE"));
    LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, 1);
    line.setLayoutParams(lineParams);
    
    LinearLayout wrap = new LinearLayout(act);
    wrap.setOrientation(LinearLayout.VERTICAL);
    wrap.addView(row);
    wrap.addView(line);
    
    return wrap;
}

void togglePlugin(int t, String p, String n) {
    putBoolean("config", "pluginEnabled", !pluginEnabled);
    loadConfig();
    qqToast(2, pluginEnabled ? "插件已开启" : "插件已关闭");
}

int clearDir(String path) {
    File d = new File(path);
    int c = 0;
    if (d.exists()) {
        File[] fs = d.listFiles();
        if (fs != null) {
            for (int i = 0; i < fs.length; i++) {
                if (fs[i].isFile() && fs[i].delete()) c++;
            }
        }
    }
    return c;
}

loadConfig();
loadGroupList();

addItem("插件开关", "togglePlugin");
addItem("解析权限", "showParseSettings");
addItem("生效范围", "showGroupSettings");
addItem("限制设置", "showLimitSettings");
addItem("清理缓存", "showClearSettings");