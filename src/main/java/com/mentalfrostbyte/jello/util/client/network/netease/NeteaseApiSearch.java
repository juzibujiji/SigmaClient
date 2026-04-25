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

                // 封面图片 URL：依次尝试 al.picUrl / album.picUrl / album.blurPicUrl，再做统一 normalize。
                // 搜索接口的 picUrl 经常为空字符串，空值时返回 "" 让上层调用 getSongDetail 兜底补齐。
                String coverUrl = extractPicUrl(song);
                tracks.add(new NeteaseTrack(id, name, artist, album, duration, normalizeCoverUrl(coverUrl)));
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
     * <p>
     * 修正 weapi/v3/song/detail 请求体兼容性：
     * <ul>
     *   <li>c 与 ids 均按字符串化 JSON 数组传入（网易云 core.js 约定）</li>
     *   <li>响应必须 code==200 才解析 songs</li>
     *   <li>songIds 超过 50 时按 50 分批调用后合并</li>
     * </ul>
     *
     * @param songIds 歌曲 ID 列表
     * @return NeteaseTrack 列表（保序：未返回的 ID 会被跳过，不会抛异常）
     */
    public static List<NeteaseTrack> getSongDetail(long... songIds) {
        List<NeteaseTrack> tracks = new ArrayList<>();
        if (songIds == null || songIds.length == 0) {
            return tracks;
        }
        final int batchSize = 50;
        for (int start = 0; start < songIds.length; start += batchSize) {
            int end = Math.min(start + batchSize, songIds.length);
            long[] batch = new long[end - start];
            System.arraycopy(songIds, start, batch, 0, batch.length);
            tracks.addAll(getSongDetailBatch(batch));
        }
        return tracks;
    }

    /**
     * 单批（<=50）的 /weapi/v3/song/detail 请求。单批失败不影响其它批次。
     */
    private static List<NeteaseTrack> getSongDetailBatch(long[] songIds) {
        List<NeteaseTrack> tracks = new ArrayList<>();
        try {
            // weapi/v3/song/detail 要求 c 与 ids 为"字符串化的 JSON 数组"，
            // 直接传 JsonArray 对象会导致服务器端返回空 songs。
            StringBuilder cStr = new StringBuilder("[");
            StringBuilder idsStr = new StringBuilder("[");
            for (int i = 0; i < songIds.length; i++) {
                if (i > 0) {
                    cStr.append(',');
                    idsStr.append(',');
                }
                cStr.append("{\"id\":").append(songIds[i]).append('}');
                idsStr.append(songIds[i]);
            }
            cStr.append(']');
            idsStr.append(']');

            JsonObject data = new JsonObject();
            data.addProperty("c", cStr.toString());
            data.addProperty("ids", idsStr.toString());
            data.addProperty("csrf_token", "");

            String[] encrypted = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + urlEncode(encrypted[0])
                    + "&encSecKey=" + urlEncode(encrypted[1]);

            String cookie = NeteaseApiLogin.isLoggedIn()
                    ? NeteaseApiLogin.getLoginCookie() : "";
            String response = NeteaseApiLogin.postRequest(
                    BASE_URL + "/weapi/v3/song/detail", body, cookie);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (!json.has("code") || json.get("code").getAsInt() != 200) {
                System.err.println("[NeteaseSearch] getSongDetail bad code: "
                        + (json.has("code") ? json.get("code") : "<missing>"));
                return tracks;
            }
            if (!json.has("songs") || json.get("songs").isJsonNull()) {
                return tracks;
            }

            JsonArray songs = json.getAsJsonArray("songs");
            for (JsonElement elem : songs) {
                JsonObject song = elem.getAsJsonObject();
                long id = song.get("id").getAsLong();
                String name = song.has("name") && !song.get("name").isJsonNull()
                        ? song.get("name").getAsString() : "";

                String artist = "Unknown";
                if (song.has("ar") && song.getAsJsonArray("ar").size() > 0) {
                    artist = song.getAsJsonArray("ar")
                            .get(0).getAsJsonObject().get("name").getAsString();
                } else if (song.has("artists") && song.getAsJsonArray("artists").size() > 0) {
                    artist = song.getAsJsonArray("artists")
                            .get(0).getAsJsonObject().get("name").getAsString();
                }

                String album = "";
                String coverUrl = "";
                if (song.has("al") && !song.get("al").isJsonNull()) {
                    JsonObject al = song.getAsJsonObject("al");
                    album = al.has("name") && !al.get("name").isJsonNull()
                            ? al.get("name").getAsString() : "";
                    coverUrl = al.has("picUrl") && !al.get("picUrl").isJsonNull()
                            ? al.get("picUrl").getAsString() : "";
                } else if (song.has("album") && !song.get("album").isJsonNull()) {
                    JsonObject alb = song.getAsJsonObject("album");
                    album = alb.has("name") && !alb.get("name").isJsonNull()
                            ? alb.get("name").getAsString() : "";
                    coverUrl = alb.has("picUrl") && !alb.get("picUrl").isJsonNull()
                            ? alb.get("picUrl").getAsString() : "";
                }

                long duration = song.has("dt") ? song.get("dt").getAsLong() : 0;
                if (duration == 0 && song.has("duration")) {
                    duration = song.get("duration").getAsLong();
                }

                tracks.add(new NeteaseTrack(id, name, artist, album, duration, normalizeCoverUrl(coverUrl)));
            }
        } catch (Exception e) {
            System.err.println("[NeteaseSearch] getSongDetail batch failed: " + e.getMessage());
            e.printStackTrace();
        }
        return tracks;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 从搜索/详情接口返回的 song 对象中提取封面 URL。
     * 兼容 {@code al.picUrl}（详情接口）、{@code album.picUrl} / {@code album.blurPicUrl}（搜索接口）。
     */
    private static String extractPicUrl(JsonObject song) {
        if (song.has("al") && !song.get("al").isJsonNull()) {
            JsonObject al = song.getAsJsonObject("al");
            if (al.has("picUrl") && !al.get("picUrl").isJsonNull()) {
                String u = al.get("picUrl").getAsString();
                if (u != null && !u.trim().isEmpty()) return u;
            }
        }
        if (song.has("album") && !song.get("album").isJsonNull()) {
            JsonObject alb = song.getAsJsonObject("album");
            if (alb.has("picUrl") && !alb.get("picUrl").isJsonNull()) {
                String u = alb.get("picUrl").getAsString();
                if (u != null && !u.trim().isEmpty()) return u;
            }
            if (alb.has("blurPicUrl") && !alb.get("blurPicUrl").isJsonNull()) {
                String u = alb.get("blurPicUrl").getAsString();
                if (u != null && !u.trim().isEmpty()) return u;
            }
        }
        return "";
    }

    /**
     * 统一封面 URL normalize：
     * <ul>
     *   <li>trim、空字符串 → ""</li>
     *   <li>{@code //host/...} 补 https</li>
     *   <li>仅保留 http/https，其它协议返回 ""</li>
     *   <li>未带 {@code param=} 时追加 {@code param=300y300}（网易云 CDN 缩图约定）</li>
     * </ul>
     */
    private static String normalizeCoverUrl(String coverUrl) {
        if (coverUrl == null) {
            return "";
        }
        String s = coverUrl.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.startsWith("//")) {
            s = "https:" + s;
        }
        if (!(s.startsWith("http://") || s.startsWith("https://"))) {
            return "";
        }
        if (!s.contains("param=")) {
            s += (s.contains("?") ? "&" : "?") + "param=300y300";
        }
        return s;
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
