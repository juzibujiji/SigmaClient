package com.mentalfrostbyte.jello.util.client.network.netease;



import com.google.gson.*;



import java.io.IOException;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.List;

import java.util.Map;



/**

 * 网易云音乐搜索 & 歌曲 URL 获取。

 * 全部通过 weapi 加密调用官方接口。

 */

public class NeteaseApiSearch {



    private static final String BASE_URL = "https://music.163.com";



    // ==================== 搜索结果数据类 ====================



    /**

     * 搜索结果中的一首歌。

     */

    public static class NeteaseTrack {

        public long id;

        public String name;

        public String artist;

        public String album;

        public long duration; // 毫秒

        public String coverUrl;



        public NeteaseTrack(long id, String name, String artist, String album, long duration, String coverUrl) {

            this.id = id;

            this.name = name;

            this.artist = artist;

            this.album = album;

            this.duration = duration;

            this.coverUrl = coverUrl;

        }



        /**

         * 返回 "歌手 - 歌名" 格式的显示标题。

         */

        public String getDisplayTitle() {

            return artist + " - " + name;

        }



        @Override

        public String toString() {

            return "NeteaseTrack{id=" + id + ", name='" + name + "', artist='" + artist + "'}";

        }

    }



    /**

     * 歌曲播放 URL 信息。

     */

    public static class NeteaseSongUrl {

        public long id;

        public String url;

        public int br; // 码率

        public String type; // mp3 / flac 等



        public NeteaseSongUrl(long id, String url, int br, String type) {

            this.id = id;

            this.url = url;

            this.br = br;

            this.type = type;

        }

    }



    // ==================== 公开 API ====================



    /**

     * 搜索歌曲。

     *

     * @param keyword 搜索关键词

     * @param limit   返回数量（建议 20-30）

     * @param offset  偏移（分页用）

     * @return 搜索结果列表

     */

    public static List<NeteaseTrack> search(String keyword, int limit, int offset) {

        List<NeteaseTrack> tracks = new ArrayList<>();

        try {

            JsonObject data = new JsonObject();

            data.addProperty("s", keyword);

            data.addProperty("type", 1); // 1=单曲

            data.addProperty("limit", limit);

            data.addProperty("offset", offset);

            data.addProperty("total", true);

            data.addProperty("csrf_token", "");



            String[] encrypted = NeteaseApiEncrypt.encrypt(data.toString());

            String body = "params=" + urlEncode(encrypted[0])

                    + "&encSecKey=" + urlEncode(encrypted[1]);



            String cookie = NeteaseApiLogin.isLoggedIn()

                    ? NeteaseApiLogin.getLoginCookie() : "";

            String response = NeteaseApiLogin.postRequest(

                    BASE_URL + "/weapi/search/get", body, cookie);



            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.get("code").getAsInt() != 200) {

                System.err.println("[NeteaseSearch] Bad response code: " + json.get("code"));

                return tracks;

            }



            JsonObject result = json.getAsJsonObject("result");

            if (result == null || !result.has("songs")) {

                return tracks;

            }



            JsonArray songs = result.getAsJsonArray("songs");

            for (JsonElement elem : songs) {

                JsonObject song = elem.getAsJsonObject();

                long id = song.get("id").getAsLong();

                String name = song.get("name").getAsString();



                // 提取第一个歌手名

                String artist = "Unknown";

                if (song.has("artists") && song.getAsJsonArray("artists").size() > 0) {

                    artist = song.getAsJsonArray("artists")

                            .get(0).getAsJsonObject().get("name").getAsString();

                } else if (song.has("ar") && song.getAsJsonArray("ar").size() > 0) {

                    artist = song.getAsJsonArray("ar")

                            .get(0).getAsJsonObject().get("name").getAsString();

                }



                // 提取专辑名

                String album = "";

                if (song.has("album") && !song.get("album").isJsonNull()) {

                    album = song.getAsJsonObject("album").get("name").getAsString();

                } else if (song.has("al") && !song.get("al").isJsonNull()) {

                    album = song.getAsJsonObject("al").get("name").getAsString();

                }



                // 时长

                long duration = song.has("duration") ? song.get("duration").getAsLong() : 0;

                if (duration == 0 && song.has("dt")) {

                    duration = song.get("dt").getAsLong();

                }



                // 封面图片 URL

                String coverUrl = "";

                if (song.has("al") && !song.get("al").isJsonNull()

                        && song.getAsJsonObject("al").has("picUrl")) {

                    coverUrl = song.getAsJsonObject("al").get("picUrl").getAsString();

                } else if (song.has("album") && !song.get("album").isJsonNull()

                        && song.getAsJsonObject("album").has("picUrl")) {

                    coverUrl = song.getAsJsonObject("album").get("picUrl").getAsString();

                }



                tracks.add(new NeteaseTrack(id, name, artist, album, duration, coverUrl));

            }

        } catch (Exception e) {

            System.err.println("[NeteaseSearch] search failed: " + e.getMessage());

            e.printStackTrace();

        }

        return tracks;

    }



    /**

     * 搜索歌曲（简化版，默认返回 30 条，从第 0 条开始）。

     */

    public static List<NeteaseTrack> search(String keyword) {

        return search(keyword, 30, 0);

    }



    /**

     * 获取歌曲播放 URL。

     *

     * @param songIds 一个或多个歌曲 ID

     * @return URL 列表

     */

    public static List<NeteaseSongUrl> getSongUrls(long... songIds) {

        List<NeteaseSongUrl> urls = new ArrayList<>();

        try {

            JsonArray ids = new JsonArray();

            for (long id : songIds) {

                ids.add(id);

            }



            JsonObject data = new JsonObject();

            data.add("ids", ids);

            data.addProperty("level", "standard");

            data.addProperty("encodeType", "mp3");

            data.addProperty("csrf_token", "");



            String[] encrypted = NeteaseApiEncrypt.encrypt(data.toString());

            String body = "params=" + urlEncode(encrypted[0])

                    + "&encSecKey=" + urlEncode(encrypted[1]);



            String cookie = NeteaseApiLogin.isLoggedIn()

                    ? NeteaseApiLogin.getLoginCookie() : "";

            String response = NeteaseApiLogin.postRequest(

                    BASE_URL + "/weapi/song/enhance/player/url/v1", body, cookie);



            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.get("code").getAsInt() != 200) {

                System.err.println("[NeteaseSearch] getSongUrls bad code: " + json.get("code"));

                return urls;

            }



            JsonArray dataArr = json.getAsJsonArray("data");

            for (JsonElement elem : dataArr) {

                JsonObject item = elem.getAsJsonObject();

                long id = item.get("id").getAsLong();

                String songUrl = item.has("url") && !item.get("url").isJsonNull()

                        ? item.get("url").getAsString() : null;

                int br = item.has("br") ? item.get("br").getAsInt() : 0;

                String type = item.has("type") && !item.get("type").isJsonNull()

                        ? item.get("type").getAsString() : "mp3";



                if (songUrl != null && !songUrl.isEmpty()) {

                    urls.add(new NeteaseSongUrl(id, songUrl, br, type));

                } else {

                    int fee = item.has("fee") && !item.get("fee").isJsonNull()
                            ? item.get("fee").getAsInt() : -1;
                    int itemCode = item.has("code") ? item.get("code").getAsInt() : -1;
                    System.err.println("[NeteaseSearch] weapi url=null for song " + id
                            + " fee=" + fee + " itemCode=" + itemCode
                            + " loggedIn=" + NeteaseApiLogin.isLoggedIn());

                }

            }

        } catch (Exception e) {

            System.err.println("[NeteaseSearch] getSongUrls failed: " + e.getMessage());

            e.printStackTrace();

        }

        return urls;

    }



    /**

     * 获取单首歌曲的播放 URL，若无则返回 null。

     */

    public static String getSongUrl(long songId) {

        List<NeteaseSongUrl> urls = getSongUrls(songId);

        if (!urls.isEmpty()) {

            return urls.get(0).url;

        }

        // Fallback 1: EAPI（PC 客户端官方接口）。登录后能拿到 VIP 歌曲的
        // 会员/免费试听播放地址，比 weapi 更可靠。依次尝试多个音质等级降级。
        String[] levels = {"exhigh", "higher", "standard"};
        for (String level : levels) {
            String eapiUrl = NeteaseApiLogin.getSongUrlEapi(songId, level);
            if (eapiUrl != null && !eapiUrl.isEmpty()) {
                System.out.println("[NeteaseSearch] Resolved song " + songId
                        + " via EAPI (level=" + level + "): " + eapiUrl);
                return eapiUrl;
            }
        }

        // Fallback 2: outer/url 外链端点，手动跟随 302 重定向
        // （Java HttpURLConnection 不跟随 https→http 跨协议重定向）。
        String resolved = resolveOuterUrl(songId);
        if (resolved != null) {
            System.out.println("[NeteaseSearch] Resolved song " + songId
                    + " via outer/url: " + resolved);
            return resolved;
        }

        System.err.println("[NeteaseSearch] Failed to resolve song URL for ID: " + songId
                + " (weapi/eapi returned null and outer/url fallback failed)");
        return null;

    }

    /**
     * 通过 outer/url 外链端点解析歌曲播放 URL。
     * 该端点是网易云 web 播放器的公开接口，支持 VIP 歌曲免费试听。
     * 手动跟随 302 重定向以处理 https→http 跨协议跳转。
     *
     * @param songId 网易云歌曲 ID
     * @return 实际音频 URL，失败返回 null
     */
    private static String resolveOuterUrl(long songId) {
        String requestUrl = "https://music.163.com/song/media/outer/url?id=" + songId + ".mp3";
        String cookie = NeteaseApiLogin.isLoggedIn()
                ? NeteaseApiLogin.getLoginCookie() : "";

        try {
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(requestUrl).openConnection();
            conn.setInstanceFollowRedirects(false); // 手动跟随，处理跨协议
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://music.163.com/");
            if (!cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            int maxRedirects = 5;
            for (int i = 0; i < maxRedirects; i++) {
                int status = conn.getResponseCode();
                if (status == java.net.HttpURLConnection.HTTP_OK) {
                    // 拿到了实际音频 URL
                    return conn.getURL().toString();
                }
                if (status == java.net.HttpURLConnection.HTTP_MOVED_PERM
                        || status == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                        || status == 307) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null || location.isEmpty()) break;
                    conn = (java.net.HttpURLConnection) new java.net.URL(location).openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    continue;
                }
                // 403/404 等——歌曲不可用
                conn.disconnect();
                break;
            }
        } catch (Exception e) {
            System.err.println("[NeteaseSearch] resolveOuterUrl failed: " + e.getMessage());
        }
        return null;
    }

    public static Map<Long, NeteaseSongUrl> getSongUrlMap(long... songIds) {

        Map<Long, NeteaseSongUrl> urlMap = new HashMap<>();
        if (songIds == null || songIds.length == 0) {
            return urlMap;
        }

        for (NeteaseSongUrl songUrl : getSongUrls(songIds)) {

            urlMap.put(songUrl.id, songUrl);

        }

        return urlMap;

    }



    /**

     * 获取歌曲详情（用于获取封面等信息）。

     *

     * @param songIds 歌曲 ID 列表

     * @return NeteaseTrack 列表

     */

    public static List<NeteaseTrack> getSongDetail(long... songIds) {

        List<NeteaseTrack> tracks = new ArrayList<>();

        try {

            JsonArray c = new JsonArray();
            JsonArray ids = new JsonArray();

            for (long id : songIds) {

                JsonObject obj = new JsonObject();

                obj.addProperty("id", id);

                c.add(obj);
                ids.add(id);

            }



            JsonObject data = new JsonObject();

            data.addProperty("c", c.toString());
            data.addProperty("ids", ids.toString());

            data.addProperty("csrf_token", "");



            String[] encrypted = NeteaseApiEncrypt.encrypt(data.toString());

            String body = "params=" + urlEncode(encrypted[0])

                    + "&encSecKey=" + urlEncode(encrypted[1]);



            String cookie = NeteaseApiLogin.isLoggedIn()

                    ? NeteaseApiLogin.getLoginCookie() : "";

            String response = NeteaseApiLogin.postRequest(

                    BASE_URL + "/weapi/v3/song/detail", body, cookie);



            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.has("songs")) {

                JsonArray songs = json.getAsJsonArray("songs");

                for (JsonElement elem : songs) {

                    JsonObject song = elem.getAsJsonObject();

                    long id = song.get("id").getAsLong();

                    String name = song.get("name").getAsString();



                    String artist = "Unknown";

                    if (song.has("ar") && song.getAsJsonArray("ar").size() > 0) {

                        artist = song.getAsJsonArray("ar")

                                .get(0).getAsJsonObject().get("name").getAsString();

                    }



                    String album = "";

                    String coverUrl = "";

                    if (song.has("al") && !song.get("al").isJsonNull()) {

                        JsonObject al = song.getAsJsonObject("al");

                        album = al.has("name") ? al.get("name").getAsString() : "";

                        coverUrl = al.has("picUrl") ? al.get("picUrl").getAsString() : "";

                    }



                    long duration = song.has("dt") ? song.get("dt").getAsLong() : 0;



                    tracks.add(new NeteaseTrack(id, name, artist, album, duration, coverUrl));

                }

            }

        } catch (Exception e) {

            System.err.println("[NeteaseSearch] getSongDetail failed: " + e.getMessage());

            e.printStackTrace();

        }

        return tracks;

    }



    /**

     * 获取歌词。

     *

     * @param songId 歌曲 ID

     * @return 歌词文本（LRC 格式），失败返回 null

     */

    public static String getLyrics(long songId) {

        try {

            JsonObject data = new JsonObject();

            data.addProperty("id", songId);

            data.addProperty("lv", -1);

            data.addProperty("tv", -1);

            data.addProperty("csrf_token", "");



            String[] encrypted = NeteaseApiEncrypt.encrypt(data.toString());

            String body = "params=" + urlEncode(encrypted[0])

                    + "&encSecKey=" + urlEncode(encrypted[1]);



            String cookie = NeteaseApiLogin.isLoggedIn()

                    ? NeteaseApiLogin.getLoginCookie() : "";

            String response = NeteaseApiLogin.postRequest(

                    BASE_URL + "/weapi/song/lyric", body, cookie);



            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            // 优先返回 YRC 逐词歌词（如果可用），否则回退到普通 LRC
            if (json.has("yrc") && !json.get("yrc").isJsonNull()) {

                JsonObject yrc = json.getAsJsonObject("yrc");

                if (yrc.has("lyric") && !yrc.get("lyric").isJsonNull()) {

                    String yrcText = yrc.get("lyric").getAsString();

                    if (yrcText != null && !yrcText.isEmpty()) {

                        return yrcText;

                    }

                }

            }

            if (json.has("lrc") && !json.get("lrc").isJsonNull()) {

                JsonObject lrc = json.getAsJsonObject("lrc");

                if (lrc.has("lyric") && !lrc.get("lyric").isJsonNull()) {

                    return lrc.get("lyric").getAsString();

                }

            }

        } catch (Exception e) {

            System.err.println("[NeteaseSearch] getLyrics failed: " + e.getMessage());

            e.printStackTrace();

        }

        return null;

    }



    private static String urlEncode(String s) {

        return URLEncoder.encode(s, StandardCharsets.UTF_8);

    }

}

