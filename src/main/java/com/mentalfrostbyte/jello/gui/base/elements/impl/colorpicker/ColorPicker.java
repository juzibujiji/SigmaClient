package com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker;

import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker.impl.ColorPickerBlock;
import com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker.impl.ColorPickerBubble;
import com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker.impl.ColorPickerSlider;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;

import java.awt.*;

public class ColorPicker extends Element {
    public int field20618;
    public boolean field20619;
    public ColorPickerBlock field20620;
    public ColorPickerSlider field20621;
    public ColorPickerBubble field20622;

    public ColorPicker(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, int var7, boolean var8) {
        super(var1, var2, var3, var4, var5, var6, false);
        this.field20618 = var7;
        Color var11 = new Color(var7);
        float[] var12 = Color.RGBtoHSB(var11.getRed(), var11.getGreen(), var11.getBlue(), null);
        this.addToList(this.field20620 = new ColorPickerBlock(this, "block", 10, 10, var5 - 20, var6 - 50, var12[0], var12[1], var12[2]));
        this.addToList(this.field20621 = new ColorPickerSlider(this, "slider", 14, var6 - 25, var5 - 65, 8, var12[0]));
        this.addToList(this.field20622 = new ColorPickerBubble(this, "bubble", var5 - 40, var6 - 32, 25, 25, var11.getRGB()));
        this.field20620.onPress(var1x -> this.method13050());
        this.field20621.onPress(var1x -> this.method13050());
        this.field20622.onClick((var1x, var2x) -> this.method13045(!this.method13047()));
        this.field20619 = var8;
    }

    public void method13045(boolean var1) {
        this.method13046(var1);
        this.callUIHandlers();
    }

    public void method13046(boolean var1) {
        this.field20619 = var1;
    }

    public boolean method13047() {
        return this.field20619;
    }

    public void method13048(int var1) {
        if (var1 != this.field20618) {
            Color var4 = new Color(var1);
            float[] var5 = Color.RGBtoHSB(var4.getRed(), var4.getGreen(), var4.getBlue(), null);
            this.field20620.method13678(var5[0]);
            this.field20620.method13681(var5[1], false);
            this.field20620.method13684(var5[2], false);
            this.field20621.method13098(var5[0], false);
            this.field20622.field21365 = var1;
        }
    }

    public int method13049() {
        return this.field20618;
    }

    private void method13050() {
        this.method13051();
        this.callUIHandlers();
    }

    private void method13051() {
        float var3 = this.field20621.method13096();
        this.field20620.method13678(var3);
        this.field20618 = this.field20620.method13685();
        this.field20622.field21365 = this.field20618;
    }

    public static void method13052(int var0, int var1, int var2, float var3) {
        RenderUtil.drawCircle((float) var0, (float) var1, (float) 14, RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.1F * var3));
        RenderUtil.drawCircle((float) var0, (float) var1, (float) (14 - 1), RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.14F * var3));
        RenderUtil.drawCircle((float) var0, (float) var1, (float) (14 - 2), RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var3));
        RenderUtil.drawCircle(
                (float) var0, (float) var1, (float) (14 - 6), RenderUtil2.applyAlpha(RenderUtil2.shiftTowardsOther(var2, ClientColors.DEEP_TEAL.getColor(), 0.7F), var3)
        );
        RenderUtil.drawCircle((float) var0, (float) var1, (float) (14 - 7), RenderUtil2.applyAlpha(var2, var3));
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
    }

    @Override
    public void draw(float partialTicks) {
        if (this.field20619) {
            this.field20621.method13098((float) (System.currentTimeMillis() % 4000L) / 4000.0F, false);
            this.method13051();
        }

        super.draw(partialTicks);
    }
}
