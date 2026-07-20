package com.mentalfrostbyte.jello.gui.base.elements.impl;

import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.BindTarget;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.newdawn.slick.TrueTypeFont;

public class KeyButton extends Element {
    public final int keyCode;
    private float pressAnim;
    private boolean held = false;
    private boolean bound = false;

    public KeyButton(CustomGuiScreen parent, String name, int x, int width, int y, int height, String text, int keyCode) {
        super(parent, name, x, width, y, height, ColorHelper.field27961, text, false);
        this.keyCode = keyCode;
        this.refreshBoundState();
    }

    public void refreshBoundState() {
        for (BindTarget target : KeyboardScreen.method13328()) {
            int keybind = target.getKeybind();
            if (keybind == this.keyCode) {
                this.bound = true;
                return;
            }
        }

        this.bound = false;
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
        this.pressAnim = Math.max(0.0F, Math.min(1.0F, this.pressAnim + 0.2F * (float) (!this.method13212() && !this.held ? -1 : 1)));
    }

    @Override
    public void draw(float partialTicks) {
        RenderUtil.drawRoundedButton(
                (float) this.xA,
                (float) (this.yA + 5),
                (float) this.widthA,
                (float) this.heightA,
                8.0F,
                RenderUtil2.shiftTowardsOther(-3092272, -2171170, this.pressAnim)
        );
        RenderUtil.drawRoundedButton(
                (float) this.xA, (float) this.yA + 3.0F * this.pressAnim, (float) this.widthA, (float) this.heightA, 8.0F, -986896
        );
        TrueTypeFont font = ResourceRegistry.JelloLightFont20;
        if (this.text.contains("Lock")) {
            RenderUtil.drawCircle(
                    (float) (this.xA + 14),
                    (float) (this.yA + 11) + 3.0F * this.pressAnim,
                    10.0F,
                    RenderUtil2.applyAlpha(ClientColors.DARK_SLATE_GREY.getColor(), this.pressAnim)
            );
        }

        if (!this.text.equals("Return")) {
            if (!this.text.equals("Back")) {
                if (!this.text.equals("Meta")) {
                    if (!this.text.equals("Menu")) {
                        if (!this.text.equals("Space")) {
                            if (this.bound) {
                                font = ResourceRegistry.RegularFont20;
                            }

                            RenderUtil.drawString(
                                    font,
                                    (float) (this.xA + (this.widthA - font.getWidth(this.text)) / 2),
                                    (float) (this.yA + 19) + 3.0F * this.pressAnim,
                                    this.text,
                                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.4F + (!this.bound ? 0.0F : 0.2F))
                            );
                        }
                    } else {
                        int menuX = this.xA + 25;
                        int menuY = this.yA + 25 + (int) (3.0F * this.pressAnim);
                        RenderUtil.method11428(
                                (float) menuX,
                                (float) menuY,
                                (float) (menuX + 14),
                                (float) (menuY + 3),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
                        );
                        RenderUtil.drawRoundedRect(
                                (float) menuX,
                                (float) (menuY + 4),
                                (float) (menuX + 14),
                                (float) (menuY + 7),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
                        );
                        RenderUtil.method11428(
                                (float) menuX,
                                (float) (menuY + 8),
                                (float) (menuX + 14),
                                (float) (menuY + 11),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
                        );
                        RenderUtil.method11428(
                                (float) menuX,
                                (float) (menuY + 12),
                                (float) (menuX + 14),
                                (float) (menuY + 15),
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
                        );
                    }
                } else {
                    int metaX = this.xA + 32;
                    int metaY = this.yA + 32 + (int) (3.0F * this.pressAnim);
                    RenderUtil.drawCircle(
                            (float) metaX, (float) metaY, 14.0F, RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
                    );
                }
            } else {
                int backX = this.xA + 43;
                int backY = this.yA + 33 + (int) (3.0F * this.pressAnim);
                RenderUtil.method11434(
                        (float) backX,
                        (float) backY,
                        (float) (backX + 6),
                        (float) (backY - 3),
                        (float) (backX + 6),
                        (float) (backY + 3),
                        RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
                );
                RenderUtil.drawRoundedRect(
                        (float) (backX + 6),
                        (float) (backY - 1),
                        (float) (backX + 27),
                        (float) (backY + 1),
                        RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
                );
            }
        } else {
            int returnX = this.xA + 50;
            int returnY = this.yA + 33 + (int) (3.0F * this.pressAnim);
            RenderUtil.method11434(
                    (float) returnX,
                    (float) returnY,
                    (float) (returnX + 6),
                    (float) (returnY - 3),
                    (float) (returnX + 6),
                    (float) (returnY + 3),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
            );
            RenderUtil.drawRoundedRect(
                    (float) (returnX + 6),
                    (float) (returnY - 1),
                    (float) (returnX + 27),
                    (float) (returnY + 1),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
            );
            RenderUtil.drawRoundedRect(
                    (float) (returnX + 25),
                    (float) (returnY - 8),
                    (float) (returnX + 27),
                    (float) (returnY - 1),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F + (!this.bound ? 0.0F : 0.2F))
            );
        }

        super.draw(partialTicks);
    }

    @Override
    public void keyPressed(int keyCode) {
        if (keyCode == this.keyCode) {
            this.held = true;
        }

        super.keyPressed(keyCode);
    }

    @Override
    public void modifierPressed(int keyCode) {
        if (keyCode == this.keyCode) {
            this.held = false;
        }

        super.modifierPressed(keyCode);
    }
}
