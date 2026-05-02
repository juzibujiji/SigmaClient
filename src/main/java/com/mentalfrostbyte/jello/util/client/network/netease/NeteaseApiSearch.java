package com.mentalfrostbyte.jello.util.client.network.netease;



import com.google.gson.*;



import java.io.IOException;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.List;



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

        return urls.isEmpty() ? null : urls.get(0).url;

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

            for (long id : songIds) {

                JsonObject obj = new JsonObject();

                obj.addProperty("id", id);

                c.add(obj);

            }



            JsonObject data = new JsonObject();

            data.add("c", c);

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

