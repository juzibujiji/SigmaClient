package com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker.impl;

import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;

public class ColorPickerBubble extends Element {
    public int field21365;

    public ColorPickerBubble(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, int var7) {
        super(var1, var2, var3, var4, var5, var6, false);
        this.field21365 = var7;
    }

    @Override
    public void draw(float partialTicks) {
        RenderUtil.drawCircle(
                (float) this.xA + (float) this.widthA / 2.0F,
                (float) this.yA + (float) this.widthA / 2.0F,
                (float) this.widthA,
                RenderUtil2.applyAlpha(RenderUtil2.shiftTowardsOther(this.field21365, ClientColors.DEEP_TEAL.getColor(), 0.8F), partialTicks)
        );
        RenderUtil.drawCircle(
                (float) this.xA + (float) this.widthA / 2.0F,
                (float) this.yA + (float) this.widthA / 2.0F,
                (float) (this.widthA - 2),
                RenderUtil2.applyAlpha(this.field21365, partialTicks)
        );
        if (this.method13212()) {
            RenderUtil.drawCircle(
                    (float) this.xA + (float) this.widthA / 2.0F,
                    (float) this.yA + (float) this.widthA / 2.0F,
                    (float) (this.widthA - 2),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), partialTicks * 0.2F)
            );
        }

        super.draw(partialTicks);
    }
}
