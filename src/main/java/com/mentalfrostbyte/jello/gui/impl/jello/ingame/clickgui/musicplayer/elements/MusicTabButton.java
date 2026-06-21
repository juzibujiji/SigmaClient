package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.musicplayer.elements;

import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.SkijaFontRenderer;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.newdawn.slick.TrueTypeFont;

/**
 * Local MusicPlayer button variant that keeps the original Button rendering path
 * while forcing opaque background colors so tabs do not wash out to white.
 * Uses Skija for font rendering.
 */
public class MusicTabButton extends Button {

    public MusicTabButton(CustomGuiScreen screen, String iconName, int x, int y, int width, int height,
                          ColorHelper colorHelper, String text, TrueTypeFont font) {
        super(screen, iconName, x, y, width, height, colorHelper, text, font);
    }

    @Override
    public void draw(float partialTicks) {
        int primaryColor = normalizeOpaqueColor(this.textColor.getPrimaryColor());
        int secondaryColor = normalizeOpaqueColor(this.textColor.getSecondaryColor());
        float var4 = !this.isHovered() ? 0.3F : (!this.method13216()
                ? (!this.method13212() ? Math.max(partialTicks * this.field20584, 0.0F) : 1.5F)
                : 0.0F);
        int color = RenderUtil2.applyAlpha(
                RenderUtil2.shiftTowardsOther(primaryColor, secondaryColor, 1.0F - var4),
                (float) (primaryColor >> 24 & 0xFF) / 255.0F * partialTicks
        );
        if (this.field20586 <= 0) {
            RenderUtil.drawRoundedRect(
                    (float) this.getXA(),
                    (float) this.getYA(),
                    (float) (this.getXA() + this.getWidthA()),
                    (float) (this.getYA() + this.getHeightA()),
                    color
            );
        } else {
            RenderUtil.drawRoundedButton(
                    (float) this.getXA(),
                    (float) this.getYA(),
                    (float) this.getWidthA(),
                    (float) this.getHeightA(),
                    (float) this.field20586,
                    color
            );
        }

        int var10 = this.getXA()
                + (this.textColor.method19411() != FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
                ? 0
                : (this.textColor.method19411() != FontSizeAdjust.WIDTH_NEGATE ? this.getWidthA() / 2 : this.getWidthA()));
        int var11 = this.getYA()
                + (this.textColor.method19413() != FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
                ? 0
                : (this.textColor.method19413() != FontSizeAdjust.HEIGHT_NEGATE ? this.getHeightA() / 2 : this.getHeightA()));
        if (this.getText() != null) {
            if (SkijaFontRenderer.isInitialized()) {
                int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
                int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();

                float textX = (float) (this.method13035() + var10);
                float textY = (float) var11;
                int textColor = RenderUtil2.applyAlpha(this.textColor.getTextColor(), partialTicks);

                // Apply alignment adjustments
                float textWidth = SkijaFontRenderer.getTextWidth(this.getText(), 14f);
                if (this.textColor.method19411() == FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2) {
                    textX -= textWidth / 2.0f;
                } else if (this.textColor.method19411() == FontSizeAdjust.WIDTH_NEGATE) {
                    textX -= textWidth;
                }

                SkijaFontRenderer.beginFrame(sw, sh);
                SkijaFontRenderer.drawText(this.getText(), textX, textY, 14f, textColor);
                SkijaFontRenderer.endFrame();
            } else {
                RenderUtil.drawString(
                        this.getFont(),
                        (float) (this.method13035() + var10),
                        (float) var11,
                        this.getText(),
                        RenderUtil2.applyAlpha(this.textColor.getTextColor(), partialTicks),
                        this.textColor.method19411(),
                        this.textColor.method19413()
                );
            }
        }

        this.drawChildren(partialTicks);
    }

    private static int normalizeOpaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }
}
