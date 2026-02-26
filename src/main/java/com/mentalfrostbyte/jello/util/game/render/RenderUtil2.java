package com.mentalfrostbyte.jello.util.game.render;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;

import java.awt.*;

public class RenderUtil2 implements MinecraftUtil {
    public static final ResourceLocation BLUR_SHADER = new ResourceLocation("shaders/post/blur.json");

    public static int applyAlpha(int color, float alpha) {
        return (int) (alpha * 255.0F) << 24 | color & 16777215;
    }

    /**
     * Adjusts the RGB components of a color towards black by a specified factor.
     * The alpha component remains unchanged.
     *
     * @param color The original color represented as an integer, where the highest byte is the alpha component,
     *              followed by red, green, and blue components.
     * @param shift The factor by which to adjust the color towards black. A value of 0.0 will leave the color unchanged,
     *              while a value of 1.0 will result in a completely black color.
     * @return The adjusted color as an integer, with the same alpha component as the original color and RGB components
     * adjusted towards black by the specified factor.
     */
    public static int shiftTowardsBlack(int color, float shift) {
        int a = color >> 24 & 0xFF;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        int shiftedR = (int) ((float) r * (1.0F - shift));
        int shiftedG = (int) ((float) g * (1.0F - shift));
        int shiftedB = (int) ((float) b * (1.0F - shift));
        return a << 24 | (shiftedR & 0xFF) << 16 | (shiftedG & 0xFF) << 8 | shiftedB & 0xFF;
    }

    /**
     * Adjusts the RGB components of 2 colors towards black by 1 specified factor.
     * The alpha component remains unchanged.
     *
     * @param color  The 1st original color represented as an integer, where the highest byte is the alpha component,
     *               followed by red, green, and blue components.
     * @param color2 The 2nd original color represented as an integer, where the highest byte is the alpha component,
     *               followed by red, green, and blue components.
     * @param shift  The factor by which to adjust the color towards black. A value of 0.0 will leave the color unchanged,
     *               while a value of 1.0 will result in the same color as the other.
     * @return The adjusted color as an integer, with the same alpha component as the original color and RGB components
     * adjusted towards each-other, I think...
     */
    public static int shiftTowardsOther(int color, int color2, float shift) {
        int a1 = color >> 24 & 0xFF;
        int r1 = color >> 16 & 0xFF;
        int g1 = color >> 8 & 0xFF;
        int b1 = color & 0xFF;
        int a2 = color2 >> 24 & 0xFF;
        int r2 = color2 >> 16 & 0xFF;
        int g2 = color2 >> 8 & 0xFF;
        int b2 = color2 & 0xFF;
        float factor = 1.0F - shift;
        float shiftedA = (float) a1 * shift + (float) a2 * factor;
        float shiftedR = (float) r1 * shift + (float) r2 * factor;
        float shiftedG = (float) g1 * shift + (float) g2 * factor;
        float shiftedB = (float) b1 * shift + (float) b2 * factor;
        return (int) shiftedA << 24 | ((int) shiftedR & 0xFF) << 16 | ((int) shiftedG & 0xFF) << 8 | (int) shiftedB & 0xFF;
    }

    public static float getAlpha(int color) {
        return (float) (color >> 24 & 0xFF) / 255.0F;
    }

    public static float[] calculateAspectRatioFit(float sourceWidth, float sourceHeight, float targetWidth, float targetHeight) {
        float sourceAspect = sourceWidth / sourceHeight;
        float targetAspect = targetWidth / targetHeight;
        float fittedWidth;
        float fittedHeight;

        if (targetAspect > sourceAspect) {
            fittedWidth = targetWidth;
            fittedHeight = sourceHeight * targetWidth / sourceWidth;
        } else {
            fittedWidth = sourceWidth * targetHeight / sourceHeight;
            fittedHeight = targetHeight;
        }

        float offsetX = (targetWidth - fittedWidth) / 2.0F;
        float offsetY = (targetHeight - fittedHeight) / 2.0F;

        return new float[]{offsetX, offsetY, fittedWidth, fittedHeight};
    }

    public static void blur() {
        if (mc.getRenderViewEntity() instanceof PlayerEntity && Client.getInstance().guiManager.getGuiBlur()) {
            if (mc.gameRenderer.shaderGroup != null) {
                mc.gameRenderer.shaderGroup.close();
            }

            mc.gameRenderer.loadShader(BLUR_SHADER);
        }

        setShaderParams(20);
    }

    public static void setShaderParams(int radius) {
        if (mc.gameRenderer.shaderGroup != null) {
            mc.gameRenderer.shaderGroup.listShaders.get(0).getShaderManager().getShaderUniform("Radius").set((float) radius);
            mc.gameRenderer.shaderGroup.listShaders.get(1).getShaderManager().getShaderUniform("Radius").set((float) radius);
        }
    }

    /**
     * Resets the current shader to its default state or loads a specific shader based on the current shader index.
     * If the shader index is equal to the total number of shaders, it sets the shader group to null, effectively
     * disabling any active shaders. Otherwise, it loads the shader corresponding to the current shader index.
     */
    public static void resetShaders() {
        if (mc.gameRenderer.shaderIndex == GameRenderer.SHADER_COUNT) {
            mc.gameRenderer.shaderGroup = null;
        } else {
            mc.gameRenderer.loadShader(GameRenderer.SHADERS_TEXTURES[mc.gameRenderer.shaderIndex]);
        }
    }

    public static float[] intColorToFloatArrayColor(int color) {
        float a = (float) (color >> 24 & 0xFF) / 255.0F;
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        return new float[]{r, g, b, a};
    }

    /**
     * Adjusts the RGB components of a color towards white by a specified factor.
     *
     * @param original The original color represented as an integer, where the highest byte is the alpha component,
     *                 followed by red, green, and blue components.
     * @param shift    The factor by which to adjust the color towards white. A value of 0.0 will leave the color unchanged,
     *                 while a value of 1.0 will result in a completely white color.
     * @return The adjusted color as an integer, with the same alpha component as the original color and RGB components
     * adjusted towards white by the specified factor.
     */
    public static int adjustColorTowardsWhite(int original, float shift) {
        int a = original >> 24 & 0xFF;
        int r = original >> 16 & 0xFF;
        int g = original >> 8 & 0xFF;
        int b = original & 0xFF;
        int var8 = (int) ((float) r + (float) (255 - r) * shift);
        int var9 = (int) ((float) g + (float) (255 - g) * shift);
        int var10 = (int) ((float) b + (float) (255 - b) * shift);
        return a << 24 | (var8 & 0xFF) << 16 | (var9 & 0xFF) << 8 | var10 & 0xFF;
    }

    public static Color calculateAverageColor(Color... colors) {
        if (colors == null || colors.length == 0) {
            return Color.WHITE;
        }

        float weight = 1.0F / colors.length;
        float totalRed = 0.0F;
        float totalGreen = 0.0F;
        float totalBlue = 0.0F;
        float totalAlpha = 0.0F;

        for (Color color : colors) {
            if (color == null) {
                color = Color.BLACK;
            }

            totalRed += color.getRed() * weight;
            totalGreen += color.getGreen() * weight;
            totalBlue += color.getBlue() * weight;
            totalAlpha += color.getAlpha() * weight;
        }

        return new Color(totalRed / 255.0F, totalGreen / 255.0F, totalBlue / 255.0F, totalAlpha / 255.0F);
    }

    /**
     * Blends two colors by a specified factor, resulting in a color that is a mix of the two.
     * The blending is done by interpolating each RGB component separately.
     *
     * @param first  The first color to blend, represented as a {@link Color} object.
     * @param second The second color to blend, represented as a {@link Color} object.
     * @param factor The blending factor, where 0.0 results in the second color, and 1.0 results in the first color.
     *               Values between 0.0 and 1.0 will result in a mix of the two colors.
     * @return A new {@link Color} object representing the blended color.
     */
    public static Color blendColor(Color first, Color second, float factor) {
        float newFactor = 1.0F - factor;
        float blendedR = (float) first.getRed() * factor + (float) second.getRed() * newFactor;
        float blendedG = (float) first.getGreen() * factor + (float) second.getGreen() * newFactor;
        float blendedB = (float) first.getBlue() * factor + (float) second.getBlue() * newFactor;
        return new Color(blendedR / 255.0F, blendedG / 255.0F, blendedB / 255.0F);
    }

    public static void setShaderParamsRounded(float radius) {
        setShaderParams(Math.round(radius * 20.0F));
    }
}
