package com.mentalfrostbyte.jello.util.client.network.netease;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 网易云音乐加密工具类。
 * <p>
 * 支持两种加密模式：
 * <ul>
 *   <li><b>weapi</b>：两轮 AES-CBC-128 + RSA 加密随机密钥（Web端接口）</li>
 *   <li><b>eapi</b>：AES-ECB-128 加密（客户端接口，码率更高）</li>
 * </ul>
 */
public class NeteaseApiEncrypt {

    // ---- weapi 固定常量（来自网易云 Web 端 core.js）----
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";
    private static final String PUBLIC_KEY =
            "010001";
    private static final String MODULUS =
            "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7"
                    + "b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280"
                    + "104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932"
                    + "575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b"
                    + "3ece0462db0a22b8e7";
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // ---- eapi 固定常量（与 NeteaseConstants 保持一致）----
    private static final String EAPI_KEY = NeteaseConstants.EAPI_KEY;
    private static final String LINUX_API_KEY = NeteaseConstants.LINUX_API_KEY;

    /**
     * 执行 weapi 加密，返回 params 和 encSecKey。
     *
     * @param plainText 明文 JSON 字符串
     * @return String[2]：[0]=params(Base64), [1]=encSecKey(Hex)
     */
    public static String[] encrypt(String plainText) {
        try {
            // 1. 生成 16 位随机密钥
            String secretKey = generateRandomKey(16);

            // 2. 第一轮 AES：用固定 presetKey 加密
            String firstEncrypted = aesEncrypt(plainText, PRESET_KEY);

            // 3. 第二轮 AES：用随机 secretKey 加密
            String params = aesEncrypt(firstEncrypted, secretKey);

            // 4. RSA 加密随机密钥（反转后做 RSA）
            String encSecKey = rsaEncrypt(secretKey);

            return new String[]{params, encSecKey};
        } catch (Exception e) {
            throw new RuntimeException("Netease weapi encrypt failed", e);
        }
    }

    /**
     * AES-128-CBC-PKCS5Padding 加密，返回 Base64 字符串。
     */
    public static String aesEncrypt(String plainText, String key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes("UTF-8"));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * RSA 加密随机密钥（网易云自定义的简易 RSA，不使用 OAEP）。
     * 将密钥反转后，按字节转为大整数，然后做 modPow(e, n)，输出十六进制。
     */
    public static String rsaEncrypt(String text) {
        // 反转字符串
        String reversed = new StringBuilder(text).reverse().toString();

        // 转为大整数（按 hex 编码文本的字节）
        StringBuilder hexText = new StringBuilder();
        for (byte b : reversed.getBytes()) {
            hexText.append(String.format("%02x", b));
        }

        BigInteger biText = new BigInteger(hexText.toString(), 16);
        BigInteger biEx = new BigInteger(PUBLIC_KEY, 16);
        BigInteger biMod = new BigInteger(MODULUS, 16);

        BigInteger biResult = biText.modPow(biEx, biMod);

        // 左填充到 256 个 hex 字符
        String result = biResult.toString(16);
        while (result.length() < 256) {
            result = "0" + result;
        }
        return result;
    }

    /**
     * 生成指定长度的随机字符串（a-z A-Z 0-9）。
     */
    public static String generateRandomKey(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    // ==================== EAPI 加密 ====================

    /**
     * 执行 eapi 加密。
     * <p>
     * eapi 用于网易云客户端接口（如 /eapi/song/enhance/player/url），
     * 可获得更高码率的音频。
     *
     * @param url      API 路径（如 /api/song/enhance/player/url）
     * @param jsonData 明文 JSON 字符串
     * @return 大写 Hex 编码的密文，失败返回 null
     */
    public static String eapiEncrypt(String url, String jsonData) {
        try {
            String message = "nobody" + url + "use" + jsonData + "md5forencrypt";
            String digest = md5Hex(message);
            String plainText = url + NeteaseConstants.EAPI_SEPARATOR + jsonData
                    + NeteaseConstants.EAPI_SEPARATOR + digest;
            return bytesToHexUpper(aesEcbEncrypt(plainText.getBytes("UTF-8"), EAPI_KEY.getBytes("UTF-8")));
        } catch (Exception e) {
            System.err.println("[NeteaseEncrypt] eapiEncrypt failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Linux API 加密（AES-ECB，大写 Hex）。
     *
     * @param jsonData 明文 JSON 字符串
     * @return 大写 Hex 编码的密文
     */
    public static String linuxApiEncrypt(String jsonData) {
        try {
            return bytesToHexUpper(aesEcbEncrypt(jsonData.getBytes("UTF-8"), LINUX_API_KEY.getBytes("UTF-8")));
        } catch (Exception e) {
            System.err.println("[NeteaseEncrypt] linuxApiEncrypt failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * eapi 解密（用于解密 eapi 接口的响应体）。
     *
     * @param cipherHex Hex 编码的密文
     * @return 解密后的明文字符串
     */
    public static String eapiDecrypt(String cipherHex) {
        try {
            byte[] cipherBytes = hexToBytes(cipherHex);
            byte[] decrypted = aesEcbDecrypt(cipherBytes, EAPI_KEY.getBytes("UTF-8"));
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            System.err.println("[NeteaseEncrypt] eapiDecrypt failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * AES-128-ECB-PKCS5Padding 加密。
     */
    private static byte[] aesEcbEncrypt(byte[] data, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    /**
     * AES-128-ECB-PKCS5Padding 解密。
     */
    private static byte[] aesEcbDecrypt(byte[] data, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    /**
     * 计算 MD5 并返回小写 Hex 字符串。
     */
    private static String md5Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes("UTF-8"));
        return bytesToHex(digest);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 字节数组转大写 Hex 字符串（EAPI / LinuxAPI 要求大写）。
     */
    private static String bytesToHexUpper(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
