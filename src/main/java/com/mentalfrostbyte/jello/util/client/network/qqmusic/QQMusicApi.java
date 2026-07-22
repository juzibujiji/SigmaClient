package com.mentalfrostbyte.jello.util.client.network.qqmusic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * QQ 音乐 API：搜索歌曲 + 获取 QRC 逐词歌词。
 *
 * <p>用于在网易云缺失 yrc 逐词歌词时，从 QQ 音乐补充逐词歌词，
 * 让歌词高亮能逐字对准。整体流程：
 * <pre>
 * search(歌名, 歌手) → 候选列表(含 songmid / 时长 / 专辑)
 *   → QQMusicMatcher 综合打分选最佳 → fetchQrc(songmid)
 *   → QQMusicDecoder 解密(3DES + zlib) → QRC 文本
 *   → YrcParser.parseQrc() → 逐词行
 * </pre>
 *
 * <p>接口均为 QQ 音乐 Web 端公开接口，走 fcg 端点，无需登录 Cookie
 * （歌词接口对匿名请求可用）。若后续接口收紧，可在此补充签名/Cookie。
 */
public class QQMusicApi {

    private static final String SEARCH_URL =
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    // 逐词 QRC 接口（老版 PC 客户端接口）：返回 XML，内含 hex 编码的 3DES+zlib 加密逐词歌词。
    // 注意此接口只认数字 musicid，不认 songmid。
    private static final String LYRIC_URL =
            "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg";

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "https://y.qq.com/";
    // 逐词歌词接口需要 player.html 作为 Referer，否则返回空
    private static final String LYRIC_REFERER = "https://y.qq.com/portal/player.html";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;

    // ==================== 数据类 ====================

    /** QQ 音乐搜索结果中的一首歌。 */
    public static class QQTrack {
        public final long songId;       // 歌曲数字 id（取逐词 QRC 用，lyric_download.fcg 只认它）
        public final String songMid;    // 歌曲 mid（备用）
        public final String name;       // 歌名
        public final String artist;     // 歌手（多位以 / 连接）
        public final String album;      // 专辑名
        public final long durationMs;   // 时长（毫秒）

        public QQTrack(long songId, String songMid, String name, String artist,
                       String album, long durationMs) {
            this.songId = songId;
            this.songMid = songMid;
            this.name = name;
            this.artist = artist;
            this.album = album;
            this.durationMs = durationMs;
        }

        @Override
        public String toString() {
            return "QQTrack{id=" + songId + ", mid=" + songMid + ", name='" + name
                    + "', artist='" + artist + "', dur=" + durationMs + "}";
        }
    }

    // ==================== 公开 API ====================

    /**
     * 搜索歌曲。
     *
     * @param keyword 搜索关键词（通常传 "歌名 歌手"）
     * @param limit   返回数量上限
     * @return 候选歌曲列表，失败返回空列表
     */
    public static List<QQTrack> search(String keyword, int limit) {
        List<QQTrack> tracks = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return tracks;
        }

        try {
            String url = SEARCH_URL
                    + "?format=json&p=1&n=" + Math.max(1, limit)
                    + "&w=" + urlEncode(keyword.trim());

            String response = getRequest(url);
            if (response == null || response.isEmpty()) {
                return tracks;
            }

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (!json.has("data")) {
                return tracks;
            }
            JsonObject data = json.getAsJsonObject("data");
            if (data == null || !data.has("song")) {
                return tracks;
            }
            JsonObject song = data.getAsJsonObject("song");
            if (song == null || !song.has("list")) {
                return tracks;
            }

            JsonArray list = song.getAsJsonArray("list");
            for (JsonElement elem : list) {
                QQTrack track = parseSongObject(elem.getAsJsonObject());
                if (track != null) {
                    tracks.add(track);
                }
            }
        } catch (Exception e) {
            System.err.println("[QQMusicApi] search failed: " + e.getMessage());
        }
        return tracks;
    }

    /**
     * 获取指定歌曲的 QRC 逐词歌词（已解密的 QRC 文本）。
     *
     * <p>走老版 PC 客户端接口 {@code lyric_download.fcg}，返回一段 XML，
     * 主歌词放在 {@code <content ...><![CDATA[ <hex> ]]></content>} 里，
     * {@code <hex>} 为 3DES+zlib 加密的逐词 QRC（大写十六进制），
     * 交由 {@link QQMusicDecoder#decryptLyrics(String)} 解密。该接口
     * <b>只认数字 songId，不认 songmid</b>。若该曲无逐词 QRC，解密结果
     * 不含逐词时间戳，调用方应据此回退。
     *
     * @param songId 歌曲数字 id
     * @return 解密后的 QRC 文本（含 XML 外壳），失败返回 null
     */
    public static String fetchQrc(long songId) {
        if (songId <= 0L) {
            return null;
        }

        try {
            String url = LYRIC_URL
                    + "?version=15&miniversion=82&lrctype=4&musicid=" + songId;

            String response = getRequest(url, LYRIC_REFERER);
            if (response == null || response.isEmpty()) {
                return null;
            }

            // 提取第一个 <![CDATA[ ... ]]>（主歌词的加密 hex）
            String hex = extractFirstCdata(response);
            if (hex == null || hex.isEmpty()) {
                return null;
            }

            String decrypted = QQMusicDecoder.decryptLyrics(hex);
            if (decrypted != null && !decrypted.isEmpty()) {
                return decrypted;
            }
        } catch (Exception e) {
            System.err.println("[QQMusicApi] fetchQrc failed: " + e.getMessage());
        }
        return null;
    }

    /** 提取响应中第一段 {@code <![CDATA[...]]>} 内容（去空白）。 */
    private static String extractFirstCdata(String xml) {
        int start = xml.indexOf("<![CDATA[");
        if (start < 0) {
            return null;
        }
        int end = xml.indexOf("]]>", start);
        if (end < 0) {
            return null;
        }
        return xml.substring(start + 9, end).trim();
    }

    // ==================== 内部工具 ====================

    private static QQTrack parseSongObject(JsonObject song) {
        try {
            // 数字 id：搜索结果里为 songid（有时为 id）
            long songId = 0L;
            if (song.has("songid")) {
                songId = song.get("songid").getAsLong();
            } else if (song.has("id")) {
                songId = song.get("id").getAsLong();
            }
            if (songId <= 0L) {
                return null;
            }

            String mid = song.has("songmid") ? song.get("songmid").getAsString()
                    : (song.has("mid") ? song.get("mid").getAsString() : "");

            String name = song.has("songname") ? song.get("songname").getAsString()
                    : (song.has("name") ? song.get("name").getAsString() : "");

            // 歌手：singer 数组
            StringBuilder artist = new StringBuilder();
            if (song.has("singer") && song.get("singer").isJsonArray()) {
                JsonArray singers = song.getAsJsonArray("singer");
                for (int i = 0; i < singers.size(); i++) {
                    JsonObject s = singers.get(i).getAsJsonObject();
                    if (s.has("name")) {
                        if (artist.length() > 0) artist.append("/");
                        artist.append(s.get("name").getAsString());
                    }
                }
            }

            String album = "";
            if (song.has("albumname")) {
                album = song.get("albumname").getAsString();
            } else if (song.has("album") && song.get("album").isJsonObject()) {
                JsonObject al = song.getAsJsonObject("album");
                album = al.has("name") ? al.get("name").getAsString() : "";
            }

            // 时长：interval 字段单位为秒
            long durationMs = 0L;
            if (song.has("interval")) {
                durationMs = song.get("interval").getAsLong() * 1000L;
            }

            return new QQTrack(songId, mid, name, artist.toString(), album, durationMs);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getRequest(String urlStr) throws IOException {
        return getRequest(urlStr, REFERER);
    }

    private static String getRequest(String urlStr, String referer) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", referer);

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 400)
                    ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
