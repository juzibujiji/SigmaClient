package com.mentalfrostbyte.jello.util.client.network.netease;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * 网易云音乐设备指纹数据。
 * <p>
 * 首次生成后持久化到 sigma5/netease_device.json，后续启动直接加载。
 * 字段规格严格按照参考实现（Gensh1n）：
 * <ul>
 *   <li>deviceId: "00" + 50位随机hex大写 = 52位</li>
 *   <li>macId: "XX:XX:XX:XX:XX:XX" 格式，6段2位hex大写，冒号分隔 = 17位</li>
 *   <li>scrw: "00" + 13位随机hex大写 = 15位</li>
 *   <li>scrw1: "00" + 60位随机hex大写 = 62位</li>
 * </ul>
 */
public class NeteaseDeviceData {

    private static final String HEX_CHARS = "0123456789abcdef";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEVICE_FILE = "netease_device.json";

    private String deviceId;
    private String macId;
    private String scrw;
    private String scrw1;

    // 单例
    private static NeteaseDeviceData instance;

    private NeteaseDeviceData() {}

    /**
     * 获取单例实例。首次调用时从文件加载或生成新数据。
     */
    public static synchronized NeteaseDeviceData getInstance() {
        if (instance == null) {
            instance = loadOrCreate();
        }
        return instance;
    }

    /**
     * 强制重新生成设备指纹（删除旧文件并重新创建）。
     */
    public static synchronized void regenerate() {
        try {
            Path file = getDeviceFilePath();
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (Exception ignored) {}
        instance = null;
        getInstance(); // 触发重新生成
    }

    // ==================== Getters ====================

    public String getDeviceId() { return deviceId; }
    public String getMacId() { return macId; }
    public String getScrw() { return scrw; }
    public String getScrw1() { return scrw1; }

    /**
     * 构造 clientSign 字段。
     * 格式: "{macId}@@@SCRW{scrw}@@@@@@7{scrw1}"
     */
    public String getClientSign() {
        return macId + "@@@SCRW" + scrw + "@@@@@@7" + scrw1;
    }

    // ==================== 持久化 ====================

    private static NeteaseDeviceData loadOrCreate() {
        NeteaseDeviceData data = tryLoad();
        if (data != null) {
            System.out.println("[NeteaseDevice] Loaded device data from file");
            System.out.println("[NeteaseDevice]   deviceId length=" + data.deviceId.length()
                    + ", macId length=" + data.macId.length()
                    + ", scrw length=" + data.scrw.length()
                    + ", scrw1 length=" + data.scrw1.length());
            return data;
        }
        data = generate();
        data.save();
        System.out.println("[NeteaseDevice] Generated and saved new device data");
        return data;
    }

    private static NeteaseDeviceData tryLoad() {
        try {
            Path file = getDeviceFilePath();
            if (file == null || !Files.exists(file)) return null;

            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            NeteaseDeviceData data = new NeteaseDeviceData();
            data.deviceId = json.get("deviceId").getAsString();
            data.macId = json.get("macId").getAsString();
            data.scrw = json.get("scrw").getAsString();
            data.scrw1 = json.get("scrw1").getAsString();

            // 校验长度
            if (data.deviceId.length() != 52
                    || data.macId.length() != 17
                    || data.scrw.length() != 15
                    || data.scrw1.length() != 62) {
                System.out.println("[NeteaseDevice] Device data validation failed, regenerating...");
                return null;
            }

            return data;
        } catch (Exception e) {
            System.err.println("[NeteaseDevice] Failed to load device data: " + e.getMessage());
            return null;
        }
    }

    private void save() {
        try {
            Path file = getDeviceFilePath();
            if (file == null) return;
            JsonObject json = new JsonObject();
            json.addProperty("deviceId", deviceId);
            json.addProperty("macId", macId);
            json.addProperty("scrw", scrw);
            json.addProperty("scrw1", scrw1);
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
            System.out.println("[NeteaseDevice] Saved to: " + file);
        } catch (Exception e) {
            System.err.println("[NeteaseDevice] Failed to save device data: " + e.getMessage());
        }
    }

    // ==================== 生成 ====================

    private static NeteaseDeviceData generate() {
        NeteaseDeviceData data = new NeteaseDeviceData();
        data.deviceId = generateDeviceId();
        data.macId = generateMacId();
        data.scrw = generateScrw();
        data.scrw1 = generateScrw1();
        return data;
    }

    /**
     * deviceId: "00" + 50位随机hex大写 = 52位
     */
    private static String generateDeviceId() {
        return "00" + randomHexUpper(50);
    }

    /**
     * macId: "XX:XX:XX:XX:XX:XX"，6段2位hex大写，冒号分隔 = 17位
     */
    private static String generateMacId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(':');
            sb.append(randomHexUpper(2));
        }
        return sb.toString();
    }

    /**
     * scrw: "00" + 13位随机hex大写 = 15位
     */
    private static String generateScrw() {
        return "00" + randomHexUpper(13);
    }

    /**
     * scrw1: "00" + 60位随机hex大写 = 62位
     */
    private static String generateScrw1() {
        return "00" + randomHexUpper(60);
    }

    /**
     * 生成指定长度的随机十六进制大写字符串。
     */
    private static String randomHexUpper(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(HEX_CHARS.charAt(RANDOM.nextInt(HEX_CHARS.length())));
        }
        return sb.toString().toUpperCase();
    }

    private static Path getDeviceFilePath() {
        try {
            File sigma5Dir = new File("sigma5");
            if (!sigma5Dir.exists()) {
                sigma5Dir.mkdirs();
            }
            return sigma5Dir.toPath().resolve(DEVICE_FILE);
        } catch (Exception e) {
            return null;
        }
    }
}
