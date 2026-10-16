import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * HTTP API工具类
 * 提供HTTP请求相关的功能，包括GET请求、POST请求和Unicode解码
 */
/**
     * 发送GET请求
     * @param url 请求的URL地址
     * @return 服务器响应的字符串内容，如果发生错误则返回错误信息
     */
    public String get(String url) {
        try {
            StringBuffer buffer = new StringBuffer();
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            connection.setConnectTimeout(30000); // 连接超时时间为30秒
            connection.setReadTimeout(35000);   // 读取超时时间为35秒
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            //返回200 OK，处理响应
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }
            reader.close();
        } else {
            //处理非200响应
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }
            reader.close();
        }

        return buffer.toString();
    } catch (Exception e) {

        e.printStackTrace();
        return "发生错误: " + e.getMessage();
    }
}
    /**
     * 发送POST请求（带Cookie）
     * @param urlPath 请求的URL地址
     * @param cookie Cookie字符串
     * @param data 要发送的JSON数据
     * @return 服务器响应的字符串内容
     */
    public String httppost1(String urlPath, String cookie,String data) {
        StringBuffer buffer = new StringBuffer();
        InputStreamReader isr = null;
        try {
            URL url = new URL(urlPath);
            uc = (HttpURLConnection) url.openConnection();
            uc.setDoInput(true);
            uc.setDoOutput(true);
            uc.setConnectTimeout(2000000);
            uc.setReadTimeout(2000000);
            uc.setRequestMethod("POST");
            uc.setRequestProperty("Content-Type", "application/json");
            uc.setRequestProperty("Cookie",cookie);
            uc.getOutputStream().write(data.getBytes("UTF-8"));
            uc.getOutputStream().flush();
            uc.getOutputStream().close();
        isr = new InputStreamReader(uc.getInputStream(), "utf-8");
        BufferedReader reader = new BufferedReader(isr);
        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line + "\n");
        }
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        try {
            if (null != isr) {
                isr.close();
            }
        } catch (IOException e) {
            Toast("错误:\n"+e);
        }
    }
    if(buffer.length()==0)return buffer.toString();
    buffer.delete(buffer.length()-1,buffer.length());
    return buffer.toString();
}

    /**
     * Unicode解码方法
     * 将Unicode编码的字符串转换为中文字符串
     * @param unicode 包含Unicode编码的字符串（如"\\u4e2d\\u6587"）
     * @return 解码后的中文字符串
     */
    public static String u解(String unicode) {
        StringBuffer string = new StringBuffer();
        String[] hex = unicode.split("\\\\u");
        for (int i = 0; i < hex.length; i++) {

            try {
                if(hex[i].length()>=4) {
                    String chinese = hex[i].substring(0, 4);
                    try {
                        int chr = Integer.parseInt(chinese, 16);
                        boolean isChinese = isChinese((char) chr);
                        string.append((char) chr);
                        String behindString = hex[i].substring(4);
                        string.append(behindString);
                    } catch (NumberFormatException e1) {
                        string.append(hex[i]);
                    }

                } else {
                    string.append(hex[i]);
                }
            } catch (NumberFormatException e) {
                string.append(hex[i]);
            }
        }
        return string.toString();
    }