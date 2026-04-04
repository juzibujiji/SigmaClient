package com.mentalfrostbyte.jello.util.client.network.netease;

/**
 * 网易云音乐 API 常量。
 * 版本号、UA、域名等，与官方客户端保持同步以通过风控检测。
 */
public class NeteaseConstants {

    // ==================== 版本号 ====================

    public static final String PC_APP_VER = "3.1.28.205001";
    public static final String ANDROID_APP_VER = "9.4.80";
    public static final String ANDROID_OS_VER = "14";
    public static final String ANDROID_BUILD_VER = "250320154029";
    public static final String ANDROID_VERSION_CODE = "9004080";
    public static final String ANDROID_MOBILE_NAME = "194RCA8";
    public static final String PC_OS_VER = "Microsoft-Windows-10-Professional-build-22631-64bit";

    // ==================== API 域名 ====================

    public static final String API_DOMAIN = "https://interface.music.163.com";
    public static final String PC_EAPI_DOMAIN = "https://interface.music.163.com";
    public static final String ANDROID_EAPI_DOMAIN = "https://interface3.music.163.com";
    public static final String WEB_DOMAIN = "https://music.163.com";

    // ==================== User-Agent ====================

    /** PC 浏览器 UA（用于 WEAPI） */
    public static final String UA_PC_BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 Edg/127.0.0.0";

    /** PC 桌面客户端 UA（用于 PC_EAPI） */
    public static final String UA_PC_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 "
                    + "NeteaseMusicDesktop/" + PC_APP_VER;

    /** Android APP UA（用于 ANDROID_EAPI） */
    public static final String UA_ANDROID =
            "NeteaseMusic/" + ANDROID_APP_VER + "." + ANDROID_BUILD_VER
                    + "(" + ANDROID_VERSION_CODE + ");Dalvik/2.1.0 (Linux; U; Android "
                    + ANDROID_OS_VER + "; " + ANDROID_MOBILE_NAME + " Build/UP1A.231005.007)";

    /** Linux UA */
    public static final String UA_LINUX =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 Edg/127.0.2651.90";

    /** 移动端 UA */
    public static final String UA_MOBILE =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36 EdgA/127.0.0.0";

    // ==================== 加密常量 ====================

    public static final String EAPI_KEY = "e82ckenh8dichen8";
    public static final String EAPI_SEPARATOR = "-36cd479b6b5-";
    public static final String LINUX_API_KEY = "rFgB&h#%2?^eDg:Q";

    // ==================== QR 登录 ====================

    public static final String QR_LOGIN_URL_PREFIX = "https://music.163.com/login?codekey=";

    private NeteaseConstants() {}
}
