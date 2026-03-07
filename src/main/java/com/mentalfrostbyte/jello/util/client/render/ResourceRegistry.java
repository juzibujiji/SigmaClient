package com.mentalfrostbyte.jello.util.client.render;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.ClientMode;
import org.newdawn.slick.TrueTypeFont;

import java.awt.*;
import java.io.InputStream;

public class ResourceRegistry {
        public static final TrueTypeFont JelloLightFont12 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 12.0F);
        public static final TrueTypeFont JelloLightFont14 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 14.0F);
        public static final TrueTypeFont JelloLightFont18 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 18.0F);
        public static final TrueTypeFont JelloLightFont20 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 20.0F);
        public static final TrueTypeFont JelloLightFont25 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 25.0F);
        public static final TrueTypeFont JelloLightFont40 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 40.0F);
        public static final TrueTypeFont JelloLightFont50 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 50.0F);
        public static final TrueTypeFont JelloLightFont28 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 28.0F);
        public static final TrueTypeFont JelloLightFont24 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 24.0F);
        public static final TrueTypeFont JelloLightFont36 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 36.0F);
        public static final TrueTypeFont RegularFont20 = getFont("com/mentalfrostbyte/gui/resources/font/regular.ttf",
                        0,
                        20.0F);
        public static final TrueTypeFont RegularFont40 = getFont("com/mentalfrostbyte/gui/resources/font/regular.ttf",
                        0,
                        40.0F);
        public static final TrueTypeFont JelloMediumFont20 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 20.0F);
        public static final TrueTypeFont JelloMediumFont25 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 25.0F);
        public static final TrueTypeFont JelloMediumFont40 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 40.0F);
        public static final TrueTypeFont JelloMediumFont50 = getFont(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 50.0F);
        public static final com.mentalfrostbyte.jello.util.client.render.DefaultClientFont DefaultClientFont = new DefaultClientFont(
                        2);
        public static final TrueTypeFont LyricsFont = createLyricsFont();
        public static final TrueTypeFont JelloLightFont18_1 = getFont2(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf", 0, 18.0F);
        public static final TrueTypeFont JelloMediumFont20_1 = getFont2(
                        "com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf", 0, 20.0F);

        public static TrueTypeFont getFont2(String fontPath, int style, float size) {
                try {
                        InputStream fontFile = Resources.readInputStream(fontPath);
                        Font font = Font.createFont(0, fontFile);
                        font = font.deriveFont(style, size);
                        return new TrueTypeFont(font, (int) size);
                } catch (Exception ex) {
                        return new TrueTypeFont(new Font("Arial", Font.PLAIN, (int) size), true);
                }
        }

        public static TrueTypeFont getFont(String fontPath, int style, float size) {
                try {
                        InputStream fontFile = Resources.readInputStream(fontPath);
                        Font font = Font.createFont(0, fontFile);
                        font = font.deriveFont(style, size);
                        return new TrueTypeFont(font, true);
                } catch (Exception ex) {
                        return new TrueTypeFont(new Font("Arial", Font.PLAIN, (int) size),
                                        Client.getInstance().clientMode != ClientMode.CLASSIC);
                }
        }

        private static TrueTypeFont createLyricsFont() {
                // Generate CJK characters for Chinese lyrics support (U+4E00 to U+9FFF is CJK
                // Unified Ideographs)
                StringBuilder sb = new StringBuilder();
                // Common CJK punctuation using Unicode escapes
                sb.append("\u300A\u300B\u300C\u300D\u300E\u300F\u3010\u3011\u3014\u3015\u3008\u3009");
                sb.append("\u201C\u201D\u2018\u2019\u2026\u2014\uFF5E\u00B7\u3001\u3002\uFF01\uFF1F\uFF1B\uFF1A");
                // CJK Unified Ideographs (U+4E00 to U+9FFF)
                for (char c = 0x4E00; c <= 0x9FFF; c++) {
                        sb.append(c);
                }
                char[] additionalChars = sb.toString().toCharArray();
                return new TrueTypeFont(
                                new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 18),
                                true,
                                additionalChars);
        }
}
