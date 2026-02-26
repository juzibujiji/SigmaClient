package com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker.impl;

import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker.ColorPicker;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;

import java.awt.*;

public class ColorPickerSlider extends Element {
    private static String[] field20602;
    private float field20679;
    public boolean field20680 = false;

    public ColorPickerSlider(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, float var7) {
        super(var1, var2, var3, var4, var5, var6, false);
        this.field20679 = var7;
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        int var5 = this.getHeightO() - this.method13271();
        if (this.field20680) {
            this.method13097((float) var5 / (float) this.getWidthA());
        }

        super.updatePanelDimensions(newHeight, newWidth);
    }

    @Override
    public void draw(float partialTicks) {
        for (int var4 = 0; var4 < this.widthA; var4++) {
            float var5 = (float) var4 / (float) this.widthA;
            RenderUtil.drawRoundedRect2(
                    (float) (this.xA + var4),
                    (float) this.yA,
                    1.0F,
                    (float) this.heightA,
                    RenderUtil2.applyAlpha(Color.HSBtoRGB(var5, 1.0F, 1.0F), partialTicks)
            );
        }

        RenderUtil.method11428(
                (float) this.getXA(),
                (float) this.getYA(),
                (float) (this.getXA() + this.getWidthA()),
                (float) (this.getYA() + this.getHeightA()),
                RenderUtil2.applyAlpha(ClientColors.MID_GREY.getColor(), 0.5F * partialTicks)
        );
        ColorPicker.method13052(
                this.xA + Math.round((float) this.widthA * this.field20679) + 1, this.yA + 4, Color.HSBtoRGB(this.field20679, 1.0F, 1.0F), partialTicks
        );
        super.draw(partialTicks);
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int mouseButton) {
        this.field20680 = true;
        return super.onClick(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onClick2(int mouseX, int mouseY, int mouseButton) {
        this.field20680 = false;
    }

    public float method13096() {
        return this.field20679;
    }

    public void method13097(float var1) {
        this.method13098(var1, true);
    }

    public void method13098(float var1, boolean var2) {
        var1 = Math.min(Math.max(var1, 0.0F), 1.0F);
        float var5 = this.field20679;
        this.field20679 = var1;
        if (var2 && var5 != var1) {
            this.callUIHandlers();
        }
    }
}
