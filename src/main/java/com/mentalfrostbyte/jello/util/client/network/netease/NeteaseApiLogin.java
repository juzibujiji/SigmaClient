package com.mentalfrostbyte.jello.util.client.network.netease;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 网易云音乐扫码登录流程（PC_EAPI 加密）。
 * <p>
 * 修复 8821 安全环境校验失败：
 * - QR 登录接口改用 PC_EAPI（/eapi/ 端点 + EAPI 加密）
 * - 请求体内嵌包含 clientSign 的 header 对象
 * - Cookie 包含 os、appver、osver、WEVNSM、channel、ntes_kaola_ad 等字段
 * - type 参数改为 3
 * <p>
 * 流程：
 * 1. 调用 {@link #getUniKey()} 获取唯一标识 unikey
 * 2. 调用 {@link #generateQRCode(String)} 根据 unikey 生成二维码 BufferedImage
 * 3. 轮询 {@link #checkQrLogin(String)} 检查扫码状态
 *    - 801=等待扫码  802=已扫码待确认  803=登录成功  8821=安全环境校验失败
 * 4. 登录成功后用 cookie 调用 {@link #getLoginStatus(String)} 获取用户信息
 */
public class NeteaseApiLogin {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // ==================== Cookie 存储 ====================

    /** 持久化的 cookie 键值对（从 Set-Cookie 响应头中收集） */
    private static final Map<String, String> cookieJar = new LinkedHashMap<>();

    private static String nickname = "";
    private static long userId = 0;

    // ==================== 公开 API ====================

    /**
     * 第一步：获取 unikey（二维码唯一标识）。
     * 使用 PC_EAPI 加密，发送到 /eapi/login/qrcode/unikey。
     *
     * @return unikey 字符串，失败返回 null
     */
    public static String getUniKey() {
        return retryOnce(() -> {
            try {
                String uri = "/api/login/qrcode/unikey";

                JsonObject params = new JsonObject();
                params.addProperty("type", 3);

                Map<String, String> result = doEapiRequest(uri, params, CryptoType.PC_EAPI);
                String response = result.get("body");
                System.out.println("[NeteaseLogin] getUniKey response: " + response);

                collectResponseCookies(result.get("cookies"), uri);

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                if (json.get("code").getAsInt() == 200) {
                    return json.get("unikey").getAsString();
                } else {
                    System.err.println("[NeteaseLogin] getUniKey bad code: " + json.get("code"));
                }
            } catch (Exception e) {
                System.err.println("[NeteaseLogin] getUniKey failed: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * 第二步：根据 unikey 生成二维码图片。
     * 用户用网易云音乐 App 扫描此二维码。
     *
     * @param uniKey 从 {@link #getUniKey()} 获取的值
     * @return 200x200 的二维码 BufferedImage，失败返回 null
     */
    public static BufferedImage generateQRCode(String uniKey) {
        try {
            String qrUrl = NeteaseConstants.QR_LOGIN_URL_PREFIX + uniKey;
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(qrUrl, BarcodeFormat.QR_CODE, 200, 200);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return image;
        } catch (Exception e) {
            System.err.println("[NeteaseLogin] generateQRCode failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 第三步：轮询检查扫码登录状态。
     * 使用 PC_EAPI 加密，发送到 /eapi/login/qrcode/client/login。
     *
     * @param uniKey 二维码 unikey
     * @return 状态码：800=过期 801=等待 802=已扫描待确认 803=登录成功
     */
    public static int checkQrLogin(String uniKey) {
        return retryOnce(() -> {
            try {
                String uri = "/api/login/qrcode/client/login";

                JsonObject params = new JsonObject();
                params.addProperty("key", uniKey);
                params.addProperty("type", 3);

                Map<String, String> result = doEapiRequest(uri, params, CryptoType.PC_EAPI);
                String response = result.get("body");
                System.out.println("[NeteaseLogin] checkQrLogin raw response: " + response);
                System.out.println("[NeteaseLogin] checkQrLogin raw cookies: " + result.get("cookies"));

                collectResponseCookies(result.get("cookies"), uri);

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                int code = json.get("code").getAsInt();
                System.out.println("[NeteaseLogin] checkQrLogin status: " + code);

                if (code == 803) {
                    // 登录成功：从 JSON body 的 cookie 字段提取
                    if (json.has("cookie") && !json.get("cookie").isJsonNull()) {
                        String bodyCookie = json.get("cookie").getAsString();
                        if (bodyCookie != null && !bodyCookie.isEmpty()) {
                            parseCookieString(bodyCookie);
                            System.out.println("[NeteaseLogin] Got cookies from JSON body");
                        }
                    }
                    saveCookieJar();
                    System.out.println("[NeteaseLogin] Login successful! isLoggedIn=" + isLoggedIn());
                    return 803;
                }

                return code;
            } catch (Exception e) {
                System.err.println("[NeteaseLogin] checkQrLogin failed: " + e.getMessage());
                e.printStackTrace();
            }
            return -1;
        });
    }

    /**
     * 第四步：获取已登录用户信息。
     * 使用 WEAPI 加密（与原实现兼容）。
     *
     * @param cookie 登录后的 cookie（可传 null 使用内部缓存）
     * @return JsonObject 包含 profile 等信息，失败返回 null
     */
    public static JsonObject getLoginStatus(String cookie) {
        try {
            if (cookie == null || cookie.isEmpty()) {
                cookie = buildWeapiCookieString();
            }

            JsonObject data = new JsonObject();
            String[] encrypted = NeteaseApiEncrypt.encrypt(data.toString());
            String body = "params=" + urlEncode(encrypted[0])
                    + "&encSecKey=" + urlEncode(encrypted[1]);

            String response = postRequest(
                    NeteaseConstants.WEB_DOMAIN + "/weapi/w/nuser/account/get", body, cookie);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            if (json.has("profile") && !json.get("profile").isJsonNull()) {
                JsonObject profile = json.getAsJsonObject("profile");
                nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
                userId = profile.has("userId") ? profile.get("userId").getAsLong() : 0;
                System.out.println("[NeteaseLogin] Logged in as: " + nickname + " (id=" + userId + ")");
            }

            return json;
        } catch (Exception e) {
            System.err.println("[NeteaseLogin] getLoginStatus failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ==================== Getters ====================

    public static boolean isLoggedIn() {
        return cookieJar.containsKey("MUSIC_U") && !cookieJar.get("MUSIC_U").isEmpty();
    }

    /**
     * 返回完整的 cookie 字符串（用于 WEAPI 请求等外部调用）。
     */
    public static String getLoginCookie() {
        return buildWeapiCookieString();
    }

    public static void setLoginCookie(String cookie) {
        if (cookie != null && !cookie.isEmpty()) {
            parseCookieString(cookie);
        }
    }

    public static String getNmtidCookie() {
        String nmtid = cookieJar.get("NMTID");
        return nmtid != null ? "NMTID=" + nmtid : "";
    }

    /**
     * 通过 EAPI（PC 客户端接口）解析歌曲播放 URL。
     * <p>
     * EAPI 是网易云 PC 客户端使用的官方接口，携带登录 MUSIC_U 后
     * 能获取 VIP 歌曲的免费试听/会员播放地址，比 weapi 更可靠。
     * 响应为明文 JSON（doEapiRequest 设置 e_r=false）。
     *
     * @param songId 歌曲 ID
     * @param level  音质等级：standard / higher / exhigh / lossless / hires
     * @return 播放 URL，失败或不可用返回 null
     */
    public static String getSongUrlEapi(long songId, String level) {
        try {
            com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
            ids.add(songId);

            JsonObject params = new JsonObject();
            params.addProperty("ids", ids.toString());
            params.addProperty("level", level == null ? "exhigh" : level);
            params.addProperty("encodeType", "flac");
            if ("sky".equals(level)) {
                params.addProperty("immerseType", "c51");
            }

            Map<String, String> result = doEapiRequest(
                    "/api/song/enhance/player/url/v1", params, CryptoType.PC_EAPI);
            String response = result.get("body");
            if (response == null || response.isEmpty()) {
                System.err.println("[NeteaseLogin] getSongUrlEapi empty response for " + songId);
                return null;
            }

            // e_r=false 时响应为明文 JSON；若不是（返回加密 hex），尝试 EAPI 解密
            String trimmed = response.trim();
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                String decrypted = NeteaseApiEncrypt.eapiDecrypt(trimmed);
                if (decrypted != null && !decrypted.isEmpty()) {
                    response = decrypted;
                }
            }

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (!json.has("code") || json.get("code").getAsInt() != 200) {
                System.err.println("[NeteaseLogin] getSongUrlEapi bad code: "
                        + (json.has("code") ? json.get("code") : "?")
                        + " resp=" + trimForLog(response));
                return null;
            }

            com.google.gson.JsonArray dataArr = json.getAsJsonArray("data");
            if (dataArr == null || dataArr.size() == 0) return null;

            JsonObject item = dataArr.get(0).getAsJsonObject();
            String url = item.has("url") && !item.get("url").isJsonNull()
                    ? item.get("url").getAsString() : null;
            int fee = item.has("fee") && !item.get("fee").isJsonNull()
                    ? item.get("fee").getAsInt() : 0;
            int code = item.has("code") ? item.get("code").getAsInt() : 0;

            if (url == null || url.isEmpty()) {
                System.err.println("[NeteaseLogin] getSongUrlEapi url=null for " + songId
                        + " fee=" + fee + " itemCode=" + code
                        + " (可能需要会员或该音质不可用)");
                return null;
            }
            // EAPI 有时返回 http:// 地址，MP3 解码器可正常处理
            return url;
        } catch (Exception e) {
            System.err.println("[NeteaseLogin] getSongUrlEapi failed: " + e.getMessage());
            return null;
        }
    }

    private static String trimForLog(String s) {
        if (s == null) return "null";
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    public static String getNickname() {
        return nickname;
    }

    public static long getUserId() {
        return userId;
    }

    public static void logout() {
        cookieJar.clear();
        nickname = "";
        userId = 0;
        try {
            Path cookieFile = getCookieFilePath();
            if (cookieFile != null) {
                Files.deleteIfExists(cookieFile);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== EAPI 请求核心 ====================

    private enum CryptoType {
        PC_EAPI, ANDROID_EAPI
    }

    /**
     * 执行 EAPI 加密请求（PC 或 Android）。
     * <p>
     * 构造流程：
     * 1. 构造包含 clientSign 等字段的 header JsonObject
     * 2. 将原始参数、header、e_r 组装到 data JsonObject
     * 3. EAPI 加密 data
     * 4. POST 请求到 /eapi/... 端点
     */
    private static Map<String, String> doEapiRequest(String apiUri, JsonObject params, CryptoType type)
            throws IOException {
        NeteaseDeviceData device = NeteaseDeviceData.getInstance();

        // ---- 1. 构造 header ----
        JsonObject header = new JsonObject();
        if (type == CryptoType.PC_EAPI) {
            header.addProperty("clientSign", device.getClientSign());
            header.addProperty("os", "pc");
            header.addProperty("appver", NeteaseConstants.PC_APP_VER);
            header.addProperty("deviceId", device.getDeviceId());
            header.addProperty("requestId", 0);
            header.addProperty("osver", NeteaseConstants.PC_OS_VER);
        }
        // ANDROID_EAPI: header 为空 JsonObject

        // ---- 2. 组装 data ----
        JsonObject data = new JsonObject();
        // 合并原始参数
        for (String key : params.keySet()) {
            data.add(key, params.get(key));
        }
        data.add("header", header);
        data.addProperty("e_r", false);

        // ---- 3. EAPI 加密 ----
        String jsonText = new Gson().toJson(data);
        String encryptedParams = NeteaseApiEncrypt.eapiEncrypt(apiUri, jsonText);
        if (encryptedParams == null) {
            throw new IOException("EAPI encryption failed for " + apiUri);
        }
        String body = "params=" + encryptedParams;

        // ---- 4. 确定 URL ----
        String eapiPath = apiUri.replace("/api/", "/eapi/");
        String domain;
        String userAgent;
        if (type == CryptoType.ANDROID_EAPI) {
            domain = NeteaseConstants.ANDROID_EAPI_DOMAIN;
            userAgent = NeteaseConstants.UA_ANDROID;
        } else {
            domain = NeteaseConstants.PC_EAPI_DOMAIN;
            userAgent = NeteaseConstants.UA_PC_DESKTOP;
        }
        String fullUrl = domain + eapiPath;

        // ---- 5. 构造 Cookie ----
        String cookieStr = buildEapiCookieString(apiUri, type);

        // ---- 6. 发送请求 ----
        System.out.println("[NeteaseLogin] EAPI request: " + fullUrl);
        return postRequestWithHeaders(fullUrl, body, cookieStr, userAgent);
    }

    // ==================== Cookie 构造 ====================

    /**
     * 构造 EAPI 请求的 cookie 字符串。
     * 包含 os、appver、osver、WEVNSM、channel、ntes_kaola_ad 等必需字段。
     */
    private static String buildEapiCookieString(String uri, CryptoType type) {
        Map<String, String> cookies = new LinkedHashMap<>();

        // 基础随机字段
        cookies.put("_ntes_nuid", randomString(16));
        if (!uri.contains("login")) {
            cookies.put("NMTID", randomString(16));
        }

        // 已有的持久 cookie
        String csrf = cookieJar.get("__csrf");
        if (csrf != null && !csrf.isEmpty()) cookies.put("__csrf", csrf);
        String musicU = cookieJar.get("MUSIC_U");
        if (musicU != null && !musicU.isEmpty()) cookies.put("MUSIC_U", musicU);
        String nmtid = cookieJar.get("NMTID");
        if (nmtid != null && !nmtid.isEmpty()) cookies.put("NMTID", nmtid);

        // EAPI 专用字段
        if (type == CryptoType.PC_EAPI) {
            cookies.put("os", "pc");
            cookies.put("appver", NeteaseConstants.PC_APP_VER);
            cookies.put("osver", NeteaseConstants.PC_OS_VER);
            cookies.put("WEVNSM", "1.0.0");
        } else {
            cookies.put("os", "android");
            cookies.put("appver", NeteaseConstants.ANDROID_APP_VER);
            cookies.put("osver", NeteaseConstants.ANDROID_OS_VER);
            cookies.put("EVNSM", "1.0.0");
            cookies.put("buildver", NeteaseConstants.ANDROID_BUILD_VER);
            cookies.put("versioncode", NeteaseConstants.ANDROID_VERSION_CODE);
            cookies.put("mobilename", NeteaseConstants.ANDROID_MOBILE_NAME);
            cookies.put("resolution", "2316x1080");
        }
        cookies.put("ntes_kaola_ad", "1");
        cookies.put("channel", "netease");

        return serializeCookies(cookies);
    }

    /**
     * 构造 WEAPI 请求的 cookie 字符串。
     */
    private static String buildWeapiCookieString() {
        Map<String, String> cookies = new LinkedHashMap<>();

        cookies.put("_ntes_nuid", randomString(16));
        cookies.put("__remember_me", "true");

        // 已有的持久 cookie
        String csrf = cookieJar.get("__csrf");
        if (csrf != null && !csrf.isEmpty()) cookies.put("__csrf", csrf);
        String musicU = cookieJar.get("MUSIC_U");
        if (musicU != null && !musicU.isEmpty()) cookies.put("MUSIC_U", musicU);
        String nmtid = cookieJar.get("NMTID");
        if (nmtid != null && !nmtid.isEmpty()) cookies.put("NMTID", nmtid);

        // 如果没有 MUSIC_U 也没有 MUSIC_A，可选加匿名 token
        // （暂不实现匿名 token）

        return serializeCookies(cookies);
    }

    private static String serializeCookies(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 从 Set-Cookie 响应头收集 cookie 到 cookieJar。
     */
    private static void collectResponseCookies(String setCookieStr, String uri) {
        if (setCookieStr == null || setCookieStr.isEmpty()) return;
        String[] pairs = setCookieStr.split(";\\s*");
        for (String pair : pairs) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String name = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                // 跳过 cookie 属性
                if (!name.equalsIgnoreCase("path") && !name.equalsIgnoreCase("domain")
                        && !name.equalsIgnoreCase("expires") && !name.equalsIgnoreCase("max-age")
                        && !name.equalsIgnoreCase("httponly") && !name.equalsIgnoreCase("secure")
                        && !name.equalsIgnoreCase("samesite")) {
                    cookieJar.put(name, value);
                }
            }
        }
    }

    /**
     * 解析 cookie 字符串（"key=value; key=value" 或 "key=value;; key=value"）到 cookieJar。
     */
    private static void parseCookieString(String cookieStr) {
        if (cookieStr == null || cookieStr.isEmpty()) return;
        // 处理 ;; 分隔符
        cookieStr = cookieStr.replace(";;", ";");
        String[] pairs = cookieStr.split(";\\s*");
        for (String pair : pairs) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String name = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                // 跳过 cookie 属性
                if (!name.equalsIgnoreCase("path") && !name.equalsIgnoreCase("domain")
                        && !name.equalsIgnoreCase("expires") && !name.equalsIgnoreCase("max-age")
                        && !name.equalsIgnoreCase("httponly") && !name.equalsIgnoreCase("secure")
                        && !name.equalsIgnoreCase("samesite")) {
                    cookieJar.put(name, value);
                }
            }
        }
    }

    // ==================== Cookie 持久化 ====================

    /**
     * 保存当前 cookie 到磁盘（sigma5/netease_cookie.dat）。
     */
    public static void savePersistentCookie() {
        saveCookieJar();
    }

    private static void saveCookieJar() {
        try {
            Path cookieFile = getCookieFilePath();
            if (cookieFile == null) return;
            JsonObject json = new JsonObject();
            JsonObject cookiesJson = new JsonObject();
            for (Map.Entry<String, String> entry : cookieJar.entrySet()) {
                cookiesJson.addProperty(entry.getKey(), entry.getValue());
            }
            json.add("cookies", cookiesJson);
            json.addProperty("nickname", nickname);
            json.addProperty("userId", userId);
            Files.writeString(cookieFile, json.toString(), StandardCharsets.UTF_8);
            System.out.println("[NeteaseLogin] Cookie jar saved to: " + cookieFile);
        } catch (Exception e) {
            System.err.println("[NeteaseLogin] saveCookieJar failed: " + e.getMessage());
        }
    }

    /**
     * 从磁盘加载持久化的 cookie 并验证登录状态。
     *
     * @return true 如果成功恢复了有效登录状态
     */
    public static boolean loadPersistentCookie() {
        try {
            Path cookieFile = getCookieFilePath();
            if (cookieFile == null || !Files.exists(cookieFile)) return false;
            String content = Files.readString(cookieFile, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            // 尝试新格式
            if (json.has("cookies") && json.get("cookies").isJsonObject()) {
                JsonObject cookiesJson = json.getAsJsonObject("cookies");
                for (String key : cookiesJson.keySet()) {
                    cookieJar.put(key, cookiesJson.get(key).getAsString());
                }
            } else if (json.has("cookie")) {
                // 兼容旧格式：单个 cookie 字符串
                String savedCookie = json.get("cookie").getAsString();
                if (savedCookie != null && !savedCookie.isEmpty()) {
                    parseCookieString(savedCookie);
                }
            }

            nickname = json.has("nickname") ? json.get("nickname").getAsString() : "";
            userId = json.has("userId") ? json.get("userId").getAsLong() : 0;

            if (!isLoggedIn()) return false;

            // 异步验证 cookie 是否仍然有效
            new Thread(() -> {
                try {
                    JsonObject status = getLoginStatus(null);
                    if (status != null && status.has("profile") && !status.get("profile").isJsonNull()) {
                        System.out.println("[NeteaseLogin] Cookie restored, logged in as: " + nickname);
                    } else {
                        System.out.println("[NeteaseLogin] Saved cookie expired, clearing...");
                        cookieJar.clear();
                        nickname = "";
                        userId = 0;
                        Files.deleteIfExists(cookieFile);
                    }
                } catch (Exception e) {
                    System.err.println("[NeteaseLogin] Cookie validation failed: " + e.getMessage());
                }
            }, "NeteaseLogin-CookieValidation").start();

            return true;
        } catch (Exception e) {
            System.err.println("[NeteaseLogin] loadPersistentCookie failed: " + e.getMessage());
        }
        return false;
    }

    private static Path getCookieFilePath() {
        try {
            File sigma5Dir = new File("sigma5");
            if (!sigma5Dir.exists()) {
                sigma5Dir.mkdirs();
            }
            return sigma5Dir.toPath().resolve("netease_cookie.dat");
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 重试机制 ====================

    @FunctionalInterface
    private interface RetrySupplier<T> {
        T get() throws Exception;
    }

    private static <T> T retryOnce(RetrySupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            try {
                Thread.sleep(500 + ThreadLocalRandom.current().nextInt(1000));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            try {
                return supplier.get();
            } catch (Exception e2) {
                System.err.println("[NeteaseLogin] Retry also failed: " + e2.getMessage());
                return null;
            }
        }
    }

    // ==================== HTTP 工具方法 ====================

    /**
     * 发送 POST 请求并返回响应体字符串（使用默认 WEAPI UA）。
     */
    public static String postRequest(String urlStr, String body, String cookie) throws IOException {
        Map<String, String> result = postRequestWithHeaders(
                urlStr, body, cookie, NeteaseConstants.UA_PC_BROWSER);
        return result.get("body");
    }

    /**
     * 发送 POST 请求，返回 body 和 cookies。
     */
    public static Map<String, String> postRequestWithHeaders(
            String urlStr, String body, String cookie, String userAgent) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Referer", "https://music.163.com/");
        conn.setRequestProperty("Origin", "https://music.163.com");

        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        // 读取响应
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 400)
                ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        // 收集 Set-Cookie
        StringBuilder cookieBuilder = new StringBuilder();
        Map<String, List<String>> headers = conn.getHeaderFields();
        List<String> setCookies = null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                setCookies = entry.getValue();
                break;
            }
        }
        if (setCookies != null) {
            for (String sc : setCookies) {
                String kv = sc.split(";")[0];
                if (cookieBuilder.length() > 0) {
                    cookieBuilder.append("; ");
                }
                cookieBuilder.append(kv);
            }
        }

        Map<String, String> result = new HashMap<>();
        result.put("body", sb.toString());
        result.put("cookies", cookieBuilder.toString());
        conn.disconnect();

        return result;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * 生成指定长度的随机字符串（a-z A-Z 0-9）。
     */
    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }
}
