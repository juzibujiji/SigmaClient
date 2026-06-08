package com.mentalfrostbyte.jello.util.client.render;

import org.newdawn.slick.Color;
import org.newdawn.slick.TrueTypeFont;

/**
 * Hybrid font renderer that uses Jello fonts for ASCII characters (English and numbers)
 * and Microsoft YaHei for non-ASCII characters (Chinese, etc.)
 *
 * This ensures consistent visual style while supporting full Unicode display.
 */
public class HybridFontRenderer {

    /**
     * Check if a character is ASCII (English letters, numbers, basic punctuation)
     * These will be rendered with Jello font.
     */
    private static boolean isAsciiChar(char c) {
        return c >= 0x0020 && c <= 0x007E; // Standard ASCII printable characters
    }

    /**
     * Check if a character is a CJK character or non-ASCII
     * These will be rendered with Microsoft YaHei.
     */
    private static boolean isNonAsciiChar(char c) {
        return c > 0x007E; // Beyond ASCII range
    }

    /**
     * Draw a string using hybrid font rendering.
     * English and numbers use Jello font, Chinese and other non-ASCII use Microsoft YaHei.
     *
     * @param jelloFont The Jello font to use for ASCII characters
     * @param fallbackFont The fallback font (Microsoft YaHei) for non-ASCII characters
     * @param x X position
     * @param y Y position
     * @param text Text to draw
     * @param color Color in Slick Color format
     */
    public static void drawString(TrueTypeFont jelloFont, TrueTypeFont fallbackFont,
                                   float x, float y, String text, Color color) {
        if (text == null || text.isEmpty()) {
            return;
        }

        float currentX = x;
        StringBuilder asciiBuffer = new StringBuilder();
        StringBuilder nonAsciiBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isAsciiChar(c)) {
                // Flush non-ASCII buffer first
                if (nonAsciiBuffer.length() > 0) {
                    String nonAsciiText = nonAsciiBuffer.toString();
                    fallbackFont.drawString(currentX, y, nonAsciiText, color);
                    currentX += fallbackFont.getWidth(nonAsciiText);
                    nonAsciiBuffer.setLength(0);
                }
                // Accumulate ASCII characters
                asciiBuffer.append(c);
            } else {
                // Flush ASCII buffer first
                if (asciiBuffer.length() > 0) {
                    String asciiText = asciiBuffer.toString();
                    jelloFont.drawString(currentX, y, asciiText, color);
                    currentX += jelloFont.getWidth(asciiText);
                    asciiBuffer.setLength(0);
                }
                // Accumulate non-ASCII characters
                nonAsciiBuffer.append(c);
            }
        }

        // Flush remaining buffers
        if (asciiBuffer.length() > 0) {
            jelloFont.drawString(currentX, y, asciiBuffer.toString(), color);
        }
        if (nonAsciiBuffer.length() > 0) {
            fallbackFont.drawString(currentX, y, nonAsciiBuffer.toString(), color);
        }
    }

    /**
     * Get the width of text when rendered with hybrid fonts.
     *
     * @param jelloFont The Jello font for ASCII characters
     * @param fallbackFont The fallback font for non-ASCII characters
     * @param text Text to measure
     * @return Total width in pixels
     */
    public static int getWidth(TrueTypeFont jelloFont, TrueTypeFont fallbackFont, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int totalWidth = 0;
        StringBuilder asciiBuffer = new StringBuilder();
        StringBuilder nonAsciiBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isAsciiChar(c)) {
                // Flush non-ASCII buffer
                if (nonAsciiBuffer.length() > 0) {
                    totalWidth += fallbackFont.getWidth(nonAsciiBuffer.toString());
                    nonAsciiBuffer.setLength(0);
                }
                asciiBuffer.append(c);
            } else {
                // Flush ASCII buffer
                if (asciiBuffer.length() > 0) {
                    totalWidth += jelloFont.getWidth(asciiBuffer.toString());
                    asciiBuffer.setLength(0);
                }
                nonAsciiBuffer.append(c);
            }
        }

        // Add remaining buffer widths
        if (asciiBuffer.length() > 0) {
            totalWidth += jelloFont.getWidth(asciiBuffer.toString());
        }
        if (nonAsciiBuffer.length() > 0) {
            totalWidth += fallbackFont.getWidth(nonAsciiBuffer.toString());
        }

        return totalWidth;
    }

    /**
     * Get the height of text (uses the maximum height of both fonts).
     *
     * @param jelloFont The Jello font for ASCII characters
     * @param fallbackFont The fallback font for non-ASCII characters
     * @param text Text to measure
     * @return Height in pixels
     */
    public static int getHeight(TrueTypeFont jelloFont, TrueTypeFont fallbackFont, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // Return the maximum height to ensure proper alignment
        return Math.max(jelloFont.getHeight(text), fallbackFont.getHeight(text));
    }
}
