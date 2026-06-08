package com.mentalfrostbyte.jello.util.client.render;

import com.mentalfrostbyte.jello.util.game.render.RenderUtil;

/**
 * Example usage of Hybrid Font Rendering.
 * This demonstrates how to use Jello fonts for ASCII and Microsoft YaHei for non-ASCII characters.
 */
public class HybridFontRenderingExample {

    /**
     * Example 1: Simple text rendering
     */
    public static void example1_BasicRendering(float x, float y) {
        // This will render "Hello" in Jello font and "你好" in Microsoft YaHei
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont20,
            x, y,
            "Hello 你好",
            0xFFFFFFFF  // White color
        );
    }

    /**
     * Example 2: Mixed English and Chinese with numbers
     */
    public static void example2_MixedContent(float x, float y) {
        // English and numbers use Jello, Chinese uses YaHei
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloMediumFont20,
            x, y,
            "Score: 1000 分数",
            0xFF00FF00  // Green color
        );
    }

    /**
     * Example 3: Centered text with shadow
     */
    public static void example3_CenteredWithShadow(float screenWidth, float y) {
        String text = "Player 玩家: Level 等级 5";

        // Center the text
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont18,
            screenWidth / 2.0f,
            y,
            text,
            0xFFFFFF00,  // Yellow
            FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2,  // Center horizontally
            FontSizeAdjust.field14488,               // No vertical adjustment
            true                                     // Draw shadow
        );
    }

    /**
     * Example 4: Right-aligned text
     */
    public static void example4_RightAligned(float screenWidth, float y) {
        String text = "HP 生命值: 100/100";

        // Get the width to calculate right alignment
        int textWidth = RenderUtil.getHybridStringWidth(
            ResourceRegistry.JelloLightFont20,
            text
        );

        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont20,
            screenWidth - textWidth - 10,  // 10px padding from right edge
            y,
            text,
            0xFFFF0000  // Red color
        );
    }

    /**
     * Example 5: HUD display with multiple lines
     */
    public static void example5_HUDDisplay(float x, float y) {
        String[] lines = {
            "FPS: 60",
            "玩家: Steve",
            "坐标 X: 100",
            "Dimension 维度: Overworld"
        };

        float currentY = y;
        for (String line : lines) {
            RenderUtil.drawHybridString(
                ResourceRegistry.JelloLightFont14,
                x, currentY,
                line,
                0xFFFFFFFF,
                FontSizeAdjust.field14488,
                FontSizeAdjust.field14488,
                true  // Shadow for better readability
            );
            currentY += 15;  // Line spacing
        }
    }

    /**
     * Example 6: Only English (no overhead, uses only Jello font)
     */
    public static void example6_EnglishOnly(float x, float y) {
        // This will only use Jello font since there are no non-ASCII characters
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloMediumFont25,
            x, y,
            "Welcome to Sigma Client",
            0xFF00FFFF  // Cyan
        );
    }

    /**
     * Example 7: Only Chinese (uses only Microsoft YaHei)
     */
    public static void example7_ChineseOnly(float x, float y) {
        // This will only use Microsoft YaHei
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont20,
            x, y,
            "欢迎使用",
            0xFFFF00FF  // Magenta
        );
    }

    /**
     * Example 8: Direct HybridFontRenderer usage (advanced)
     */
    public static void example8_DirectUsage(float x, float y) {
        // For more control, use HybridFontRenderer directly
        HybridFontRenderer.drawString(
            ResourceRegistry.JelloLightFont20,    // ASCII font
            ResourceRegistry.LyricsFont,           // Non-ASCII font
            x, y,
            "Advanced 高级: Level 等级 10",
            new org.newdawn.slick.Color(255, 255, 0, 255)  // Yellow with full alpha
        );
    }

    /**
     * Example 9: Dynamic text with measurement
     */
    public static void example9_DynamicLayout(float x, float y, String playerName, int score) {
        // First line
        String line1 = "Player 玩家: " + playerName;
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont18,
            x, y,
            line1,
            0xFFFFFFFF
        );

        // Second line, indented based on first line's width
        int indent = RenderUtil.getHybridStringWidth(
            ResourceRegistry.JelloLightFont18,
            "Player 玩家: "
        );

        String line2 = "Score 分数: " + score;
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont18,
            x + indent,
            y + 15,
            line2,
            0xFF00FF00
        );
    }

    /**
     * Example 10: Chat message with timestamp
     */
    public static void example10_ChatMessage(float x, float y, String sender, String message, String time) {
        // Timestamp
        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont12,
            x, y,
            "[" + time + "]",
            0xFF888888  // Gray
        );

        // Sender name
        float timeWidth = RenderUtil.getHybridStringWidth(
            ResourceRegistry.JelloLightFont12,
            "[" + time + "] "
        );

        RenderUtil.drawHybridString(
            ResourceRegistry.JelloMediumFont14,
            x + timeWidth, y,
            sender + ":",
            0xFF00AAFF  // Light blue
        );

        // Message
        float senderWidth = RenderUtil.getHybridStringWidth(
            ResourceRegistry.JelloMediumFont14,
            sender + ": "
        );

        RenderUtil.drawHybridString(
            ResourceRegistry.JelloLightFont14,
            x + timeWidth + senderWidth, y,
            message,
            0xFFFFFFFF  // White
        );
    }
}
