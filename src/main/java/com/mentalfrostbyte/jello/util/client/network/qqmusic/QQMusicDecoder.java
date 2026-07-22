package com.mentalfrostbyte.jello.util.client.network.qqmusic;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Inflater;

/**
 * QQ 音乐 QRC 逐词歌词解密器。
 *
 * <p>QQ 音乐的 QRC 歌词以十六进制字符串下发，加密方案为
 * <b>三重「魔改 DES」+ zlib 压缩</b>。解密流程：
 * <pre>
 * hex 解码 → Ddes(KEY1) → des(KEY2) → Ddes(KEY3) → zlib inflate → QRC 明文
 * </pre>
 * 其中 {@code des} 为「加密」方向的密钥编排、{@code Ddes} 为「解密」方向，
 * 两者共用同一套分组运算，仅子密钥顺序相反（与标准 DES 一致）。
 *
 * <p><b>重要：这不是标准 DES。</b>QQ 音乐 {@code QQMusicCommon.dll} 里的 DES
 * 实现带有非标准的 S 盒 bug（{@code sbox2} 中出现 15 而非标准的 14、
 * {@code sbox4} 第 4 行出现 10,10 而非 10,1）。这些 bug 必须<strong>原样保留</strong>，
 * 否则解出的将是乱码。因此本类逐位移植了
 * <a href="https://github.com/wangqr/QQMusicDES">wangqr/QQMusicDES</a> 的
 * {@code des.c}（基于 Brad Conte 的 DES，被 QQ 改坏），而不能用
 * {@code javax.crypto} 的标准 DES 替代。
 *
 * <p>三把密钥（各取前 8 字节参与 DES）：
 * <ul>
 *   <li>KEY1 {@code "!@#)(NHLiuy*$%^&"} → Ddes</li>
 *   <li>KEY2 {@code "123ZXC!@#)(*$%^&"} → des</li>
 *   <li>KEY3 {@code "!@#)(*$%^&abcDEF"} → Ddes</li>
 * </ul>
 */
public final class QQMusicDecoder {

    private QQMusicDecoder() {
    }

    // 三把密钥，各取前 8 字节（des/Ddes 的 key_setup 仅读 key[0..7]）
    private static final byte[] KEY1 = "!@#)(NHL".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEY2 = "123ZXC!@".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEY3 = "!@#)(*$%".getBytes(StandardCharsets.US_ASCII);

    // ==================== 公开 API ====================

    /**
     * 解密 QQ 音乐 QRC 歌词。
     *
     * @param encryptedHex 加密的 QRC 十六进制字符串
     * @return 解密后的 QRC 明文（可能含 XML 外壳）；若输入本就是明文则原样返回；失败返回 null
     */
    public static String decryptLyrics(String encryptedHex) {
        if (encryptedHex == null) {
            return null;
        }
        String hex = encryptedHex.replaceAll("\\s", "");
        if (hex.isEmpty()) {
            return null;
        }

        // 部分歌曲直接返回明文 LRC/QRC（非 hex），此时原样返回交由上层判断
        if (!isHex(hex)) {
            return encryptedHex;
        }
        if (hex.length() % 2 != 0) {
            return null;
        }

        try {
            byte[] data = hexToBytes(hex);
            if (data.length < 8) {
                return null;
            }

            // 三重魔改 DES：解密 → 加密 → 解密
            desBuffer(data, KEY1, /*decrypt=*/true);
            desBuffer(data, KEY2, /*decrypt=*/false);
            desBuffer(data, KEY3, /*decrypt=*/true);

            // zlib 解压
            byte[] inflated = zlibInflate(data);
            if (inflated == null || inflated.length == 0) {
                return null;
            }
            return new String(inflated, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[QQMusicDecoder] decrypt failed: " + e.getMessage());
            return null;
        }
    }

    // ==================== 魔改 DES 移植 ====================
    // 以下 S 盒与置换逻辑逐位移植自 wangqr/QQMusicDES 的 des.c，
    // 含 QQ 特有的非标准 bug，请勿"修正"。

    private static final int[] SBOX1 = {
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13
    };
    private static final int[] SBOX2 = {
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5, // 注意: 第 8 个为 15（QQ bug，标准应为 14）
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9
    };
    private static final int[] SBOX3 = {
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12
    };
    private static final int[] SBOX4 = {
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14 // 注意: 第 5,6 个为 10,10（QQ bug，标准应为 10,1）
    };
    private static final int[] SBOX5 = {
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3
    };
    private static final int[] SBOX6 = {
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13
    };
    private static final int[] SBOX7 = {
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12
    };
    private static final int[] SBOX8 = {
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11
    };

    // BITNUM: 从字节数组取第 b 位（从左数，MSB 优先），左移到第 c 位
    private static int bit(byte[] a, int off, int b, int c) {
        int idx = off + b / 32 * 4 + 3 - (b % 32) / 8;
        return (((a[idx] & 0xff) >> (7 - (b % 8))) & 0x01) << c;
    }

    // BITNUMINTR: 从 32 位字取第 b 位（从左/MSB 数），移到第 c 位
    private static int bitR(int a, int b, int c) {
        return ((a >>> (31 - b)) & 0x00000001) << c;
    }

    // BITNUMINTL: 左移 b 位后取最高位，逻辑右移到第 c 位
    private static int bitL(int a, int b, int c) {
        return ((a << b) & 0x80000000) >>> c;
    }

    // SBOXBIT: 6 位块行号从"首末位"重排为"前两位"
    private static int sboxbit(int a) {
        return (a & 0x20) | ((a & 0x1f) >> 1) | ((a & 0x01) << 4);
    }

    // 初始置换 IP：填充 state[0]、state[1]
    private static void ip(int[] state, byte[] in, int off) {
        state[0] = bit(in, off, 57, 31) | bit(in, off, 49, 30) | bit(in, off, 41, 29) | bit(in, off, 33, 28) |
                bit(in, off, 25, 27) | bit(in, off, 17, 26) | bit(in, off, 9, 25) | bit(in, off, 1, 24) |
                bit(in, off, 59, 23) | bit(in, off, 51, 22) | bit(in, off, 43, 21) | bit(in, off, 35, 20) |
                bit(in, off, 27, 19) | bit(in, off, 19, 18) | bit(in, off, 11, 17) | bit(in, off, 3, 16) |
                bit(in, off, 61, 15) | bit(in, off, 53, 14) | bit(in, off, 45, 13) | bit(in, off, 37, 12) |
                bit(in, off, 29, 11) | bit(in, off, 21, 10) | bit(in, off, 13, 9) | bit(in, off, 5, 8) |
                bit(in, off, 63, 7) | bit(in, off, 55, 6) | bit(in, off, 47, 5) | bit(in, off, 39, 4) |
                bit(in, off, 31, 3) | bit(in, off, 23, 2) | bit(in, off, 15, 1) | bit(in, off, 7, 0);

        state[1] = bit(in, off, 56, 31) | bit(in, off, 48, 30) | bit(in, off, 40, 29) | bit(in, off, 32, 28) |
                bit(in, off, 24, 27) | bit(in, off, 16, 26) | bit(in, off, 8, 25) | bit(in, off, 0, 24) |
                bit(in, off, 58, 23) | bit(in, off, 50, 22) | bit(in, off, 42, 21) | bit(in, off, 34, 20) |
                bit(in, off, 26, 19) | bit(in, off, 18, 18) | bit(in, off, 10, 17) | bit(in, off, 2, 16) |
                bit(in, off, 60, 15) | bit(in, off, 52, 14) | bit(in, off, 44, 13) | bit(in, off, 36, 12) |
                bit(in, off, 28, 11) | bit(in, off, 20, 10) | bit(in, off, 12, 9) | bit(in, off, 4, 8) |
                bit(in, off, 62, 7) | bit(in, off, 54, 6) | bit(in, off, 46, 5) | bit(in, off, 38, 4) |
                bit(in, off, 30, 3) | bit(in, off, 22, 2) | bit(in, off, 14, 1) | bit(in, off, 6, 0);
    }

    // 逆初始置换 InvIP：写回 out 的 8 个字节
    private static void invIp(int[] state, byte[] out, int off) {
        int s0 = state[0], s1 = state[1];
        out[off + 3] = (byte) (bitR(s1, 7, 7) | bitR(s0, 7, 6) | bitR(s1, 15, 5) | bitR(s0, 15, 4)
                | bitR(s1, 23, 3) | bitR(s0, 23, 2) | bitR(s1, 31, 1) | bitR(s0, 31, 0));
        out[off + 2] = (byte) (bitR(s1, 6, 7) | bitR(s0, 6, 6) | bitR(s1, 14, 5) | bitR(s0, 14, 4)
                | bitR(s1, 22, 3) | bitR(s0, 22, 2) | bitR(s1, 30, 1) | bitR(s0, 30, 0));
        out[off + 1] = (byte) (bitR(s1, 5, 7) | bitR(s0, 5, 6) | bitR(s1, 13, 5) | bitR(s0, 13, 4)
                | bitR(s1, 21, 3) | bitR(s0, 21, 2) | bitR(s1, 29, 1) | bitR(s0, 29, 0));
        out[off + 0] = (byte) (bitR(s1, 4, 7) | bitR(s0, 4, 6) | bitR(s1, 12, 5) | bitR(s0, 12, 4)
                | bitR(s1, 20, 3) | bitR(s0, 20, 2) | bitR(s1, 28, 1) | bitR(s0, 28, 0));
        out[off + 7] = (byte) (bitR(s1, 3, 7) | bitR(s0, 3, 6) | bitR(s1, 11, 5) | bitR(s0, 11, 4)
                | bitR(s1, 19, 3) | bitR(s0, 19, 2) | bitR(s1, 27, 1) | bitR(s0, 27, 0));
        out[off + 6] = (byte) (bitR(s1, 2, 7) | bitR(s0, 2, 6) | bitR(s1, 10, 5) | bitR(s0, 10, 4)
                | bitR(s1, 18, 3) | bitR(s0, 18, 2) | bitR(s1, 26, 1) | bitR(s0, 26, 0));
        out[off + 5] = (byte) (bitR(s1, 1, 7) | bitR(s0, 1, 6) | bitR(s1, 9, 5) | bitR(s0, 9, 4)
                | bitR(s1, 17, 3) | bitR(s0, 17, 2) | bitR(s1, 25, 1) | bitR(s0, 25, 0));
        out[off + 4] = (byte) (bitR(s1, 0, 7) | bitR(s0, 0, 6) | bitR(s1, 8, 5) | bitR(s0, 8, 4)
                | bitR(s1, 16, 3) | bitR(s0, 16, 2) | bitR(s1, 24, 1) | bitR(s0, 24, 0));
    }

    // Feistel 轮函数 f
    private static int f(int state, byte[] key) {
        int t1 = bitL(state, 31, 0) | ((state & 0xf0000000) >>> 1) | bitL(state, 4, 5) |
                bitL(state, 3, 6) | ((state & 0x0f000000) >>> 3) | bitL(state, 8, 11) |
                bitL(state, 7, 12) | ((state & 0x00f00000) >>> 5) | bitL(state, 12, 17) |
                bitL(state, 11, 18) | ((state & 0x000f0000) >>> 7) | bitL(state, 16, 23);

        int t2 = bitL(state, 15, 0) | ((state & 0x0000f000) << 15) | bitL(state, 20, 5) |
                bitL(state, 19, 6) | ((state & 0x00000f00) << 13) | bitL(state, 24, 11) |
                bitL(state, 23, 12) | ((state & 0x000000f0) << 11) | bitL(state, 28, 17) |
                bitL(state, 27, 18) | ((state & 0x0000000f) << 9) | bitL(state, 0, 23);

        int[] lrg = new int[6];
        lrg[0] = (t1 >>> 24) & 0xff;
        lrg[1] = (t1 >>> 16) & 0xff;
        lrg[2] = (t1 >>> 8) & 0xff;
        lrg[3] = (t2 >>> 24) & 0xff;
        lrg[4] = (t2 >>> 16) & 0xff;
        lrg[5] = (t2 >>> 8) & 0xff;

        lrg[0] ^= key[0] & 0xff;
        lrg[1] ^= key[1] & 0xff;
        lrg[2] ^= key[2] & 0xff;
        lrg[3] ^= key[3] & 0xff;
        lrg[4] ^= key[4] & 0xff;
        lrg[5] ^= key[5] & 0xff;

        state = (SBOX1[sboxbit(lrg[0] >>> 2)] << 28) |
                (SBOX2[sboxbit(((lrg[0] & 0x03) << 4) | (lrg[1] >>> 4))] << 24) |
                (SBOX3[sboxbit(((lrg[1] & 0x0f) << 2) | (lrg[2] >>> 6))] << 20) |
                (SBOX4[sboxbit(lrg[2] & 0x3f)] << 16) |
                (SBOX5[sboxbit(lrg[3] >>> 2)] << 12) |
                (SBOX6[sboxbit(((lrg[3] & 0x03) << 4) | (lrg[4] >>> 4))] << 8) |
                (SBOX7[sboxbit(((lrg[4] & 0x0f) << 2) | (lrg[5] >>> 6))] << 4) |
                SBOX8[sboxbit(lrg[5] & 0x3f)];

        // P 置换
        state = bitL(state, 15, 0) | bitL(state, 6, 1) | bitL(state, 19, 2) |
                bitL(state, 20, 3) | bitL(state, 28, 4) | bitL(state, 11, 5) |
                bitL(state, 27, 6) | bitL(state, 16, 7) | bitL(state, 0, 8) |
                bitL(state, 14, 9) | bitL(state, 22, 10) | bitL(state, 25, 11) |
                bitL(state, 4, 12) | bitL(state, 17, 13) | bitL(state, 30, 14) |
                bitL(state, 9, 15) | bitL(state, 1, 16) | bitL(state, 7, 17) |
                bitL(state, 23, 18) | bitL(state, 13, 19) | bitL(state, 31, 20) |
                bitL(state, 26, 21) | bitL(state, 2, 22) | bitL(state, 8, 23) |
                bitL(state, 18, 24) | bitL(state, 12, 25) | bitL(state, 29, 26) |
                bitL(state, 5, 27) | bitL(state, 21, 28) | bitL(state, 10, 29) |
                bitL(state, 3, 30) | bitL(state, 24, 31);

        return state;
    }

    // 密钥编排：生成 16 个 6 字节子密钥。decrypt=true 时子密钥逆序（对应 Ddes）。
    private static byte[][] keySetup(byte[] key, boolean decrypt) {
        byte[][] schedule = new byte[16][6];
        final int[] keyRndShift = {1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1};
        final int[] keyPermC = {56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17,
                9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35};
        final int[] keyPermD = {62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21,
                13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3};
        final int[] keyCompression = {13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9,
                22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
                40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47,
                43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31};

        int c = 0, d = 0;
        for (int i = 0, j = 31; i < 28; ++i, --j) {
            c |= bit(key, 0, keyPermC[i], j);
        }
        for (int i = 0, j = 31; i < 28; ++i, --j) {
            d |= bit(key, 0, keyPermD[i], j);
        }

        for (int i = 0; i < 16; ++i) {
            int s = keyRndShift[i];
            c = ((c << s) | (c >>> (28 - s))) & 0xfffffff0;
            d = ((d << s) | (d >>> (28 - s))) & 0xfffffff0;

            int toGen = decrypt ? 15 - i : i;
            for (int j = 0; j < 6; ++j) {
                schedule[toGen][j] = 0;
            }
            int j = 0;
            for (; j < 24; ++j) {
                schedule[toGen][j / 8] |= (byte) bitR(c, keyCompression[j], 7 - (j % 8));
            }
            for (; j < 48; ++j) {
                schedule[toGen][j / 8] |= (byte) bitR(d, keyCompression[j] - 27, 7 - (j % 8));
            }
        }
        return schedule;
    }

    // 单块 DES 运算（16 轮 Feistel），in/out 可为同一数组
    private static void desCrypt(byte[] in, int inOff, byte[] out, int outOff, byte[][] schedule) {
        int[] state = new int[2];
        ip(state, in, inOff);

        for (int idx = 0; idx < 15; ++idx) {
            int t = state[1];
            state[1] = f(state[1], schedule[idx]) ^ state[0];
            state[0] = t;
        }
        // 最后一轮不换边
        state[0] = f(state[1], schedule[15]) ^ state[0];

        invIp(state, out, outOff);
    }

    /**
     * 对整个缓冲区按 8 字节分组做 DES（原地）。对应 QQMusicCommon 的 des/Ddes：
     * 仅取密钥前 8 字节，按块循环，尾部不足 8 字节的部分保持不变。
     *
     * @param buf     数据缓冲（原地修改）
     * @param key8    8 字节密钥
     * @param decrypt true=解密方向(Ddes)，false=加密方向(des)
     */
    private static void desBuffer(byte[] buf, byte[] key8, boolean decrypt) {
        byte[][] schedule = keySetup(key8, decrypt);
        int n = buf.length - (buf.length % 8);
        for (int i = 0; i < n; i += 8) {
            desCrypt(buf, i, buf, i, schedule);
        }
    }

    // ==================== 工具 ====================

    private static byte[] zlibInflate(byte[] data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length * 4));
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.finished() || inflater.needsDictionary()) {
                        break;
                    }
                    if (inflater.needsInput()) {
                        break; // 输入耗尽仍未结束：数据不完整
                    }
                }
                out.write(buffer, 0, count);
            }
        } catch (Exception e) {
            System.err.println("[QQMusicDecoder] zlib inflate failed: " + e.getMessage());
            return null;
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
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
