package com.mentalfrostbyte.jello.gui.base.elements.impl.button;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.NanoVGFontRenderer;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.newdawn.slick.TrueTypeFont;

/**
 * 使用 NanoVG 渲染文字的按钮，支持中文/CJK字符。
 * 用于音乐播放器的选项卡按钮等需要显示中文的场景。
 * 当 NanoVG 未初始化时自动回退到原始字体渲染。
 */
public class NanoVGButton extends Button {

    public NanoVGButton(CustomGuiScreen screen, String iconName, int x, int y, int width, int height,
                        ColorHelper colorHelper, String text, TrueTypeFont fallbackFont) {
        super(screen, iconName, x, y, width, height, colorHelper, text, fallbackFont);
    }

    @Override
    public void draw(float partialTicks) {
        // 绘制背景（与 Button 完全相同的逻辑）
        float var4 = !this.isHovered() ? 0.3F : (!this.method13216() ? (!this.method13212() ? Math.max(partialTicks * this.field20584, 0.0F) : 1.5F) : 0.0F);
        int color = RenderUtil2.applyAlpha(
                RenderUtil2.shiftTowardsOther(this.textColor.getPrimaryColor(), this.textColor.getSecondaryColor(), 1.0F - var4),
                (float) (this.textColor.getPrimaryColor() >> 24 & 0xFF) / 255.0F * partialTicks
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
                    (float) this.getXA(), (float) this.getYA(), (float) this.getWidthA(), (float) this.getHeightA(), (float) this.field20586, color
            );
        }

        // 使用 NanoVG 渲染文字（支持CJK字符）
        if (this.getText() != null) {
            if (NanoVGFontRenderer.isInitialized()) {
                int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
                int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();
                float fontSize = 14.0f;

                // 计算绝对屏幕坐标（NanoVG忽略GL矩阵）
                int absX = this.getXA();
                int absY = this.getYA();
                CustomGuiScreen p = this.getParent();
                while (p != null) {
                    absX += p.getXA();
                    absY += p.getYA();
                    p = p.getParent();
                }

                float textWidth = NanoVGFontRenderer.getTextWidth(this.getText(), fontSize);

                // 根据对齐设置计算文字位置
                float textX;
                float textY;
                if (this.textColor.method19411() == FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2) {
                    textX = absX + (this.getWidthA() - textWidth) / 2.0f;
                } else {
                    textX = absX;
                }
                if (this.textColor.method19413() == FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2) {
                    textY = absY + (this.getHeightA() - fontSize) / 2.0f;
                } else {
                    textY = absY;
                }

                int textColorVal = RenderUtil2.applyAlpha(this.textColor.getTextColor(), partialTicks);

                NanoVGFontRenderer.beginFrame(sw, sh);
                NanoVGFontRenderer.drawText(this.getText(), textX, textY, fontSize, textColorVal);
                NanoVGFontRenderer.endFrame();
            } else {
                // 回退到原始字体渲染
                int var10 = this.getXA()
                        + (this.textColor.method19411() != FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
                        ? 0 : (this.textColor.method19411() != FontSizeAdjust.WIDTH_NEGATE ? this.getWidthA() / 2 : this.getWidthA()));
                int var11 = this.getYA()
                        + (this.textColor.method19413() != FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
                        ? 0 : (this.textColor.method19413() != FontSizeAdjust.HEIGHT_NEGATE ? this.getHeightA() / 2 : this.getHeightA()));
                RenderUtil.drawString(
                        this.getFont(),
                        (float) var10,
                        (float) var11,
                        this.getText(),
                        RenderUtil2.applyAlpha(this.textColor.getTextColor(), partialTicks),
                        this.textColor.method19411(),
                        this.textColor.method19413()
                );
            }
        }

        // 绘制子元素（跳过 Button 的 super.draw 中的文字渲染）
        this.drawChildren(partialTicks);
    }
}
