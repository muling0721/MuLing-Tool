loadJava("HttpApi.java");
    /*MusicItem 为歌曲信息对象,脚本一般存放在hashmap中
    String name
    String id
    String word
    HashMap<String, Object> extra
    int page
    boolean isDir*/
public class MusicModel {
    public static List<HashMap<String, Object>> fetchItems(HashMap<String,Object> curItem) {
        List<HashMap<String, Object>> musicList=new ArrayList<>();
        // 使用新的API接口获取热门歌曲
        String jsonData=get("https://music.kxlove.top/api/WYY/wyy_search.php?word=晴天");
         try {
            JSONObject jsonObject=new JSONObject(jsonData);
            if(jsonObject.getInt("code")==200) {
                JSONArray dataArray=jsonObject.getJSONArray("data");
                if(dataArray==null||dataArray.length()==0) {
                    HashMap<String, Object> newItem=new HashMap<>();
                    newItem.put("name","官方群736037693");
                    musicList.add(newItem);
                    return musicList;
                }
                for(int i=0; i<dataArray.length(); i++) {
                    HashMap<String, Object> newItem=new HashMap<>();
                    JSONObject data=dataArray.getJSONObject(i);
                    
                    // 解析新格式的字段
                    String id=data.getString("id");
                    String song=data.getString("song");
                    String singer=data.getString("singer");
                    
                    // 设置歌曲信息
                    newItem.put("name",song+" - "+singer);
                    newItem.put("id", id);
                    newItem.put("artist", singer);
                    
                    // 设置播放URL（使用新的播放API）
                    String playUrl="https://music.kxlove.top/api/WYY/wyy.php?id="+id;
                    newItem.put("url", playUrl);
                    
                    musicList.add(newItem);
                }
                return musicList;
            } else {
                HashMap<String, Object> newItem=new HashMap<>();
                newItem.put("name","官方群736037693");
                musicList.add(newItem);
                return musicList;
            }
        } catch(Exception e) {
            HashMap<String, Object> newItem=new HashMap<>();
            newItem.put("name","官方群736037693");
            musicList.add(newItem);
            return musicList;
        }
      
    }
    public static Pair<String, HashMap<String, String>> fetchPlayUrl(HashMap<String, Object> musicItem){
        HashMap headers=new HashMap();
        String url=musicItem.get("url");
        if(url==null||url.equals("")) {
            // 如果url字段不存在，使用新的播放API
            String mid=musicItem.get("id");
            if(mid.equals("")) return new Pair<>("",headers);
            return new Pair<>("https://music.kxlove.top/api/WYY/wyy.php?id="+musicItem.get("id"),headers);
        }
        return new Pair<>(url,headers);
    }
    public static List<HashMap<String, Object>> searchItems(HashMap<String, Object> name) throws IOException {
        List<HashMap<String, Object>> musicList = new ArrayList<>();
        // 使用新的搜索API接口
        String jsonData=get("https://music.kxlove.top/api/WYY/wyy_search.php?word="+name.get("word"));
        try {
            JSONObject jsonObject=new JSONObject(jsonData);
            if(jsonObject.getInt("code")==200) {
                JSONArray dataArray=jsonObject.getJSONArray("data");
                if(dataArray==null||dataArray.length()==0) {
                    HashMap<String, Object> newItem = new HashMap<>();
                    newItem.put("name","未搜索到");
                    musicList.add(newItem);
                    return musicList;
                }
                for(int i=0; i<dataArray.length(); i++) {
                    HashMap<String, Object> newItem = new HashMap<>();
                    JSONObject data=dataArray.getJSONObject(i);
                    
                    // 解析新格式的字段
                    String id=data.getString("id");
                    String song=data.getString("song");
                    String singer=data.getString("singer");
                    
                    // 设置歌曲信息
                    newItem.put("name",song+" - "+singer);
                    newItem.put("id", id);
                    newItem.put("artist", singer);
                    
                    // 设置播放URL（使用新的播放API）
                    String playUrl="https://music.kxlove.top/api/WYY/wyy.php?id="+id;
                    newItem.put("url", playUrl);
                    
                    musicList.add(newItem);
                }
                return musicList;
            } else {
                HashMap<String, Object> newItem = new HashMap<>();
                newItem.put("name","搜索失败："+jsonObject.getString("message"));
                musicList.add(newItem);
                return musicList;
            }
        } catch(Exception e) {
            HashMap<String, Object> newItem=new HashMap<>();
            newItem.put("name","错误，请稍后再试："+e.getMessage());
            musicList.add(newItem);
            return musicList;
        }
    }
}