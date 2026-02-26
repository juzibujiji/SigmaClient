package com.mentalfrostbyte.jello.gui.base.elements.impl.button.types;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.newdawn.slick.TrueTypeFont;

public class TextButton extends Element {
    public Animation lineAnim;

    public TextButton(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, ColorHelper var7, String var8, TrueTypeFont var9) {
        super(var1, var2, var3, var4, var5, var6, var7, var8, var9, false);
        int var12 = (int) (210.0 * Math.sqrt((float) var5 / 242.0F));
        this.lineAnim = new Animation(var12, var12);
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
        this.lineAnim.changeDirection(!this.method13298() ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
    }

    @Override
    public void draw(float partialTicks) {
        if (this.getText() != null) {
            int var4 = this.textColor.getPrimaryColor();
            int var5 = this.getXA()
                    + (
                    this.textColor.method19411() != FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
                            ? 0
                            : (this.textColor.method19411() != FontSizeAdjust.WIDTH_NEGATE ? this.getWidthA() / 2 : this.getWidthA())
            );
            int var6 = this.getYA()
                    + (
                    this.textColor.method19413() != FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
                            ? 0
                            : (this.textColor.method19413() != FontSizeAdjust.HEIGHT_NEGATE ? this.getHeightA() / 2 : this.getHeightA())
            );
            int var7 = this.getFont().getWidth(this.getText());
            float var8 = 18;
            float var9 = (float) Math.pow(this.lineAnim.calcPercent(), 3.0);
            RenderUtil.drawString(
                    this.getFont(),
                    (float) var5,
                    (float) var6,
                    this.getText(),
                    RenderUtil2.applyAlpha(var4, partialTicks * RenderUtil2.getAlpha(var4)),
                    this.textColor.method19411(),
                    this.textColor.method19413()
            );
            RenderUtil.drawRoundedRect(
                    (float) var5 - (float) (var7 / 2) * var9,
                    var6 + var8,
                    (float) var5 + (float) (var7 / 2) * var9,
                    var6 + var8 + 2,
                    RenderUtil2.applyAlpha(var4, partialTicks * RenderUtil2.getAlpha(var4))
            );
            super.draw(partialTicks);
        }
    }
}
