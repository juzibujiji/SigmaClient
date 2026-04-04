package com.mentalfrostbyte.jello.util.client.network.netease;

import com.google.gson.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 网易云音乐统一请求 API（仿 Gensh1n RequestApi 模式）。
 * <p>
 * 提供歌单详情、排行榜、推荐歌单、每日推荐、用户歌单等核心接口，
 * 统一走 weapi 加密 + cookie 鉴权。
 */
public class NeteaseRequestApi {

    private static final String BASE = "https://music.163.com";

    // ==================== 排行榜 / 歌单 ====================

    /** 网易云官方热歌榜 ID */
    public static final long PLAYLIST_HOT_SONGS = 3778678L;
    /** 网易云官方新歌榜 ID */
    public static final long PLAYLIST_NEW_SONGS = 3779629L;
    /** 网易云官方飙升榜 ID */
    public static final long PLAYLIST_SURGE = 19723756L;
    /** 网易云官方原创榜 ID */
    public static final long PLAYLIST_ORIGINAL = 2884035L;

    /**
     * 获取歌单详情（包含歌曲列表）。
     *
     * @param playlistId 歌单 ID
     * @param limit      每页数量（最大 1000）
     * @param offset     偏移
     * @return NeteaseTrack 列表
     */
    public static List<NeteaseApiSearch.NeteaseTrack> getPlaylistDetail(long playlistId, int limit, int offset) {
        List<NeteaseApiSearch.NeteaseTrack> tracks = new ArrayList<>();
        try {
            JsonObject data = new JsonObject();
            data.addProperty("id", playlistId);
            data.addProperty("n", limit);
            data.addProperty("s", 0); // 不需要收藏者信息
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String cookie = getCookie();
            String resp = NeteaseApiLogin.postRequest(BASE + "/weapi/v6/playlist/detail", body, cookie);

            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.get("code").getAsInt() != 200) {
                System.err.println("[NeteaseRequestApi] getPlaylistDetail bad code: " + json.get("code"));
                return tracks;
            }

            JsonObject playlist = json.getAsJsonObject("playlist");
            if (playlist == null) {
                return tracks;
            }

            JsonArray tracksArr = playlist.has("tracks") && !playlist.get("tracks").isJsonNull()
                    ? playlist.getAsJsonArray("tracks")
                    : null;
            if (tracksArr == null || tracksArr.size() == 0) {
                JsonArray trackIds = playlist.has("trackIds") && !playlist.get("trackIds").isJsonNull()
                        ? playlist.getAsJsonArray("trackIds")
                        : null;
                if (trackIds == null || trackIds.size() == 0) {
                    return tracks;
                }

                List<Long> ids = new ArrayList<>();
                int start = Math.min(offset, trackIds.size());
                int end = Math.min(start + limit, trackIds.size());
                for (int i = start; i < end; i++) {
                    ids.add(trackIds.get(i).getAsJsonObject().get("id").getAsLong());
                }
                if (!ids.isEmpty()) {
                    return NeteaseApiSearch.getSongDetail(ids.stream().mapToLong(Long::longValue).toArray());
                }
                return tracks;
            }

            int start = Math.min(offset, tracksArr.size());
            int end = Math.min(start + limit, tracksArr.size());
            for (int i = start; i < end; i++) {
                JsonObject song = tracksArr.get(i).getAsJsonObject();
                tracks.add(parseSongObject(song));
            }
        } catch (Exception e) {
            System.err.println("[NeteaseRequestApi] getPlaylistDetail failed: " + e.getMessage());
            e.printStackTrace();
        }
        return tracks;
    }

    /**
     * 获取歌单详情（默认前 50 首）。
     */
    public static List<NeteaseApiSearch.NeteaseTrack> getPlaylistDetail(long playlistId) {
        return getPlaylistDetail(playlistId, 50, 0);
    }

    /**
     * 获取所有排行榜列表。
     *
     * @return 每个排行榜的 { id, name, coverUrl, updateTime } 信息列表
     */
    public static List<ToplistInfo> getToplist() {
        List<ToplistInfo> list = new ArrayList<>();
        try {
            JsonObject data = new JsonObject();
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String resp = NeteaseApiLogin.postRequest(BASE + "/weapi/toplist", body, getCookie());
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.get("code").getAsInt() != 200) return list;

            JsonArray toplistArr = json.getAsJsonArray("list");
            if (toplistArr == null) return list;

            for (JsonElement elem : toplistArr) {
                JsonObject item = elem.getAsJsonObject();
                long id = item.get("id").getAsLong();
                String name = item.get("name").getAsString();
                String coverUrl = item.has("coverImgUrl") ? item.get("coverImgUrl").getAsString() : "";
                String updateTime = item.has("updateFrequency") ? item.get("updateFrequency").getAsString() : "";
                list.add(new ToplistInfo(id, name, coverUrl, updateTime));
            }
        } catch (Exception e) {
            System.err.println("[NeteaseRequestApi] getToplist failed: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 获取推荐歌单（需要登录）。
     *
     * @param limit 数量
     * @return 推荐歌单信息列表
     */
    public static List<ToplistInfo> getRecommendPlaylists(int limit) {
        List<ToplistInfo> list = new ArrayList<>();
        try {
            JsonObject data = new JsonObject();
            data.addProperty("limit", limit);
            data.addProperty("offset", 0);
            data.addProperty("total", true);
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String resp = NeteaseApiLogin.postRequest(BASE + "/weapi/v1/discovery/recommend/resource", body, getCookie());
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.get("code").getAsInt() != 200) return list;

            JsonArray recommend = json.has("recommend") ? json.getAsJsonArray("recommend") : null;
            if (recommend == null) return list;

            for (JsonElement elem : recommend) {
                JsonObject item = elem.getAsJsonObject();
                long id = item.get("id").getAsLong();
                String name = item.get("name").getAsString();
                String coverUrl = item.has("picUrl") ? item.get("picUrl").getAsString() : "";
                list.add(new ToplistInfo(id, name, coverUrl, ""));
            }
        } catch (Exception e) {
            System.err.println("[NeteaseRequestApi] getRecommendPlaylists failed: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 获取每日推荐歌曲（需要登录）。
     *
     * @return 推荐歌曲列表
     */
    public static List<NeteaseApiSearch.NeteaseTrack> getDailyRecommendSongs() {
        List<NeteaseApiSearch.NeteaseTrack> tracks = new ArrayList<>();
        try {
            JsonObject data = new JsonObject();
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String resp = NeteaseApiLogin.postRequest(
                    BASE + "/weapi/v3/discovery/recommend/songs", body, getCookie());
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.get("code").getAsInt() != 200) return tracks;

            JsonObject dataObj = json.has("data") ? json.getAsJsonObject("data") : null;
            JsonArray dailySongs = dataObj != null && dataObj.has("dailySongs")
                    ? dataObj.getAsJsonArray("dailySongs") : null;
            if (dailySongs == null) return tracks;

            for (JsonElement elem : dailySongs) {
                tracks.add(parseSongObject(elem.getAsJsonObject()));
            }
        } catch (Exception e) {
            System.err.println("[NeteaseRequestApi] getDailyRecommendSongs failed: " + e.getMessage());
            e.printStackTrace();
        }
        return tracks;
    }

    /**
     * 获取用户歌单列表（需要登录）。
     *
     * @param uid 用户ID（0 表示当前登录用户）
     * @return 用户歌单信息列表
     */
    public static List<ToplistInfo> getUserPlaylists(long uid) {
        List<ToplistInfo> list = new ArrayList<>();
        try {
            if (uid == 0) {
                uid = NeteaseApiLogin.getUserId();
                if (uid == 0) return list;
            }

            JsonObject data = new JsonObject();
            data.addProperty("uid", uid);
            data.addProperty("limit", 50);
            data.addProperty("offset", 0);
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String resp = NeteaseApiLogin.postRequest(BASE + "/weapi/user/playlist", body, getCookie());
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.get("code").getAsInt() != 200) return list;

            JsonArray playlists = json.has("playlist") ? json.getAsJsonArray("playlist") : null;
            if (playlists == null) return list;

            for (JsonElement elem : playlists) {
                JsonObject item = elem.getAsJsonObject();
                long id = item.get("id").getAsLong();
                String name = item.get("name").getAsString();
                String coverUrl = item.has("coverImgUrl") ? item.get("coverImgUrl").getAsString() : "";
                list.add(new ToplistInfo(id, name, coverUrl, ""));
            }
        } catch (Exception e) {
            System.err.println("[NeteaseRequestApi] getUserPlaylists failed: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // ==================== 歌曲操作 ====================

    /**
     * 检查歌曲是否可用。
     *
     * @param songId 歌曲 ID
     * @return true 表示可播放
     */
    public static boolean checkSongAvailability(long songId) {
        try {
            JsonObject data = new JsonObject();
            JsonArray ids = new JsonArray();
            ids.add(songId);
            data.add("ids", ids);
            data.addProperty("br", 999000);
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String resp = NeteaseApiLogin.postRequest(
                    BASE + "/weapi/song/enhance/player/url", body, getCookie());
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.get("code").getAsInt() != 200) return false;

            JsonArray dataArr = json.getAsJsonArray("data");
            if (dataArr != null && dataArr.size() > 0) {
                JsonObject item = dataArr.get(0).getAsJsonObject();
                return item.has("url") && !item.get("url").isJsonNull()
                        && !item.get("url").getAsString().isEmpty();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 喜欢/取消喜欢歌曲（需要登录）。
     *
     * @param songId 歌曲 ID
     * @param like   true=喜欢, false=取消
     * @return 是否操作成功
     */
    public static boolean likeSong(long songId, boolean like) {
        try {
            JsonObject data = new JsonObject();
            data.addProperty("trackId", songId);
            data.addProperty("like", like);
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String resp = NeteaseApiLogin.postRequest(BASE + "/weapi/radio/like", body, getCookie());
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            return json.get("code").getAsInt() == 200;
        } catch (Exception e) {
            System.err.println("[NeteaseRequestApi] likeSong failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * 获取相似歌曲推荐。
     *
     * @param songId 歌曲 ID
     * @return 相似歌曲列表
     */
    public static List<NeteaseApiSearch.NeteaseTrack> getSimiSongs(long songId) {
        List<NeteaseApiSearch.NeteaseTrack> tracks = new ArrayList<>();
        try {
            JsonObject data = new JsonObject();
            data.addProperty("songid", songId);
            data.addProperty("limit", 10);
            data.addProperty("offset", 0);
            data.addProperty("csrf_token", "");

            String[] enc = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + enc(enc[0]) + "&encSecKey=" + enc(enc[1]);

            String resp = NeteaseApiLogin.postRequest(BASE + "/weapi/v1/discovery/simiSong", body, getCookie());
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.get("code").getAsInt() != 200) return tracks;

            JsonArray songs = json.has("songs") ? json.getAsJsonArray("songs") : null;
            if (songs == null) return tracks;

            for (JsonElement elem : songs) {
                tracks.add(parseSongObject(elem.getAsJsonObject()));
            }
        } catch (Exception e) {
            System.err.println("[NeteaseRequestApi] getSimiSongs failed: " + e.getMessage());
            e.printStackTrace();
        }
        return tracks;
    }

    // ==================== 数据类 ====================

    /**
     * 排行榜/歌单信息。
     */
    public static class ToplistInfo {
        public long id;
        public String name;
        public String coverUrl;
        public String updateFrequency;

        public ToplistInfo(long id, String name, String coverUrl, String updateFrequency) {
            this.id = id;
            this.name = name;
            this.coverUrl = coverUrl;
            this.updateFrequency = updateFrequency;
        }

        @Override
        public String toString() {
            return "ToplistInfo{id=" + id + ", name='" + name + "'}";
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 解析歌曲 JSON 对象为 NeteaseTrack。
     * 兼容搜索接口（artists/album）和详情接口（ar/al）两种格式。
     */
    static NeteaseApiSearch.NeteaseTrack parseSongObject(JsonObject song) {
        long id = song.get("id").getAsLong();
        String name = song.get("name").getAsString();

        String artist = "Unknown";
        if (song.has("ar") && song.getAsJsonArray("ar").size() > 0) {
            artist = song.getAsJsonArray("ar").get(0).getAsJsonObject().get("name").getAsString();
        } else if (song.has("artists") && song.getAsJsonArray("artists").size() > 0) {
            artist = song.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
        }

        String album = "";
        String coverUrl = "";
        if (song.has("al") && !song.get("al").isJsonNull()) {
            JsonObject al = song.getAsJsonObject("al");
            album = al.has("name") ? al.get("name").getAsString() : "";
            coverUrl = al.has("picUrl") ? al.get("picUrl").getAsString() : "";
        } else if (song.has("album") && !song.get("album").isJsonNull()) {
            JsonObject alb = song.getAsJsonObject("album");
            album = alb.has("name") ? alb.get("name").getAsString() : "";
            coverUrl = alb.has("picUrl") ? alb.get("picUrl").getAsString() : "";
        }

        long duration = 0;
        if (song.has("dt")) duration = song.get("dt").getAsLong();
        else if (song.has("duration")) duration = song.get("duration").getAsLong();

        return new NeteaseApiSearch.NeteaseTrack(id, name, artist, album, duration, coverUrl);
    }

    private static String getCookie() {
        return NeteaseApiLogin.isLoggedIn() ? NeteaseApiLogin.getLoginCookie() : "";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
