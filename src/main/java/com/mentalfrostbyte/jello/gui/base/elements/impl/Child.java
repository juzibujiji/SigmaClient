package com.mentalfrostbyte.jello.gui.base.elements.impl;

import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.Class6984;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.newdawn.slick.TrueTypeFont;

public class Child extends Element {
    public final int field20690;
    private float field20691;
    private boolean field20692 = false;
    private boolean field20693 = false;

    public Child(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, String var7, int var8) {
        super(var1, var2, var3, var4, var5, var6, ColorHelper.field27961, var7, false);
        this.field20690 = var8;
        this.method13102();
    }

    public void method13102() {
        for (Class6984 var4 : KeyboardScreen.method13328()) {
            int var5 = var4.method21599();
            if (var5 == this.field20690) {
                this.field20693 = true;
                return;
            }
        }

        this.field20693 = false;
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
        this.field20691 = Math.max(0.0F, Math.min(1.0F, this.field20691 + 0.2F * (float) (!this.method13212() && !this.field20692 ? -1 : 1)));
    }

    @Override
    public void draw(float partialTicks) {
        RenderUtil.drawRoundedButton(
                (float) this.xA,
                (float) (this.yA + 5),
                (float) this.widthA,
                (float) this.heightA,
                8.0F,
                RenderUtil2.shiftTowardsOther(-3092272, -2171170, this.field20691)
        );
        RenderUtil.drawRoundedButton(
                (float) this.xA, (float) this.yA + 3.0F * this.field20691, (float) this.widthA, (float) this.heightA, 8.0F, -986896
        );
        TrueTypeFont var4 = ResourceRegistry.JelloLightFont20;
        if (this.text.contains("Lock")) {
            RenderUtil.drawCircle(
                    (float) (this.xA + 14),
                    (float) (this.yA + 11) + 3.0F * this.field20691,
                    10.0F,
                    RenderUtil2.applyAlpha(ClientColors.DARK_SLATE_GREY.getColor(), this.field20691)
            );
        }

        if (!this.text.equals("Return")) {
            if (!this.text.equals("Back")) {
                if (!this.text.equals("Meta")) {
                    if (!this.text.equals("Menu")) {
                        if (!this.text.equals("Space")) {
                            if (this.field20693) {
                                var4 = ResourceRegistry.RegularFont20;
                            }

                            RenderUtil.drawString(
                                    var4,
                                    (float) (this.xA + (this.widthA - var4.getWidth(this.text)) / 2),
                                    (float) (this.yA + 19) + 3.0F * this.field20691,
                                    this.text,
                                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.4F + (!this.field20693 ? 0.0F : 0.2F))
                            );
                        }
                    } else {
                        int var5 = this.xA + 25;
                        int var6 = this.yA + 25 + (int) (3.0F * this.field20691);
                        RenderUtil.method11428(
                                (float) var5,
                                (float) var6,
                                (float) (var5 + 14),
                                (float) (var6 + 3),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
                        );
                        RenderUtil.drawRoundedRect(
                                (float) var5,
                                (float) (var6 + 4),
                                (float) (var5 + 14),
                                (float) (var6 + 7),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
                        );
                        RenderUtil.method11428(
                                (float) var5,
                                (float) (var6 + 8),
                                (float) (var5 + 14),
                                (float) (var6 + 11),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
                        );
                        RenderUtil.method11428(
                                (float) var5,
                                (float) (var6 + 12),
                                (float) (var5 + 14),
                                (float) (var6 + 15),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
                        );
                    }
                } else {
                    int var7 = this.xA + 32;
                    int var10 = this.yA + 32 + (int) (3.0F * this.field20691);
                    RenderUtil.drawCircle(
                            (float) var7, (float) var10, 14.0F, RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
                    );
                }
            } else {
                int var8 = this.xA + 43;
                int var11 = this.yA + 33 + (int) (3.0F * this.field20691);
                RenderUtil.method11434(
                        (float) var8,
                        (float) var11,
                        (float) (var8 + 6),
                        (float) (var11 - 3),
                        (float) (var8 + 6),
                        (float) (var11 + 3),
                        RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
                );
                RenderUtil.drawRoundedRect(
                        (float) (var8 + 6),
                        (float) (var11 - 1),
                        (float) (var8 + 27),
                        (float) (var11 + 1),
                        RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
                );
            }
        } else {
            int var9 = this.xA + 50;
            int var12 = this.yA + 33 + (int) (3.0F * this.field20691);
            RenderUtil.method11434(
                    (float) var9,
                    (float) var12,
                    (float) (var9 + 6),
                    (float) (var12 - 3),
                    (float) (var9 + 6),
                    (float) (var12 + 3),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
            );
            RenderUtil.drawRoundedRect(
                    (float) (var9 + 6),
                    (float) (var12 - 1),
                    (float) (var9 + 27),
                    (float) (var12 + 1),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
            );
            RenderUtil.drawRoundedRect(
                    (float) (var9 + 25),
                    (float) (var12 - 8),
                    (float) (var9 + 27),
                    (float) (var12 - 1),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.field20693 ? 0.0F : 0.2F))
            );
        }

        super.draw(partialTicks);
    }

    @Override
    public void keyPressed(int keyCode) {
        if (keyCode == this.field20690) {
            this.field20692 = true;
        }

        super.keyPressed(keyCode);
    }

    @Override
    public void modifierPressed(int var1) {
        if (var1 == this.field20690) {
            this.field20692 = false;
        }

        super.modifierPressed(var1);
    }
}
