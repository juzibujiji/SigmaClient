package com.mentalfrostbyte.jello.gui.base.elements.impl.maps;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.network.ImageUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.util.BufferedImageUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Zoom extends Element {
    public int field20684;
    public List<Class7086> field20685 = new ArrayList<Class7086>();
    public int field20686 = 0;
    public boolean field20687 = true;
    private Texture field20688;

    public Zoom(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6) {
        super(var1, var2, var3, var4, var5, var6, false);
        this.setListening(false);
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
        if (this.field20909 && this.field20686 <= 0) {
            if (newWidth >= this.method13272() + this.getHeightA() / 2) {
                ((MapFrame) this.parent).method13076(false);
                this.field20685.add(new Class7086(this, false));
            } else {
                ((MapFrame) this.parent).method13076(true);
                this.field20685.add(new Class7086(this, true));
            }

            if (this.field20686 != 0) {
                this.field20686 = 14;
            } else {
                this.field20686 = 3;
            }
        }

        this.field20686--;
        if (!this.field20909) {
            this.field20686 = -1;
        }
    }

    @Override
	protected void finalize() throws Throwable {
        try {
            if (this.field20688 != null) {
                Client.getInstance().addTexture(this.field20688);
            }
        } finally {
            super.finalize();
        }
    }

    @Override
    public void draw(float partialTicks) {
        Iterator var4 = this.field20685.iterator();

        try {
            if (this.field20687) {
                BufferedImage var6 = ImageUtil.method35039(this.method13271(), this.method13272(), this.widthA, this.heightA, 3, 10, true);
                this.field20684 = RenderUtil2.calculateAverageColor(new Color(var6.getRGB(6, 7)), new Color(var6.getRGB(6, 22))).getRGB();
                this.field20684 = RenderUtil2.shiftTowardsBlack(this.field20684, 0.25F);
                if (this.field20688 != null) {
                    this.field20688.release();
                }

                this.field20688 = BufferedImageUtil.getTexture("blur", var6);
                this.field20687 = false;
            }

            if (this.field20688 != null) {
                RenderUtil.drawRoundedRect(
                        (float) (this.xA + 8),
                        (float) (this.yA + 8),
                        (float) (this.widthA - 8 * 2),
                        (float) (this.heightA - 8 * 2),
                        20.0F,
                        partialTicks * 0.5F
                );
                RenderUtil.drawRoundedRect(
                        (float) (this.xA + 8),
                        (float) (this.yA + 8),
                        (float) (this.widthA - 8 * 2),
                        (float) (this.heightA - 8 * 2),
                        14.0F,
                        partialTicks
                );
                GL11.glPushMatrix();
                RenderUtil.initStencilBuffer();
                RenderUtil.drawRoundedButton(
                        (float) this.xA, (float) this.yA, (float) this.widthA, (float) this.heightA, 8.0F, ClientColors.LIGHT_GREYISH_BLUE.getColor()
                );
                RenderUtil.configureStencilTest();
                RenderUtil.drawTexture(
                        (float) (this.xA - 1),
                        (float) (this.yA - 1),
                        (float) (this.widthA + 2),
                        (float) (this.heightA + 2),
                        this.field20688,
                        ClientColors.LIGHT_GREYISH_BLUE.getColor()
                );

                while (var4.hasNext()) {
                    Class7086 var11 = (Class7086) var4.next();
                    int var7 = this.heightA / 2;
                    int var8 = this.yA + (var11.field30491 ? 0 : var7);
                    int var9 = this.widthA / 2;
                    RenderUtil.startScissor(this.xA, var8, this.xA + this.widthA, var8 + var7, true);
                    RenderUtil.drawFilledArc(
                            (float) (this.xA + var9),
                            (float) (var8 + this.heightA / 4),
                            (float) (var9 * 2 - 4) * var11.field30490 + 4.0F,
                            RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), (1.0F - var11.field30490 * (0.5F + var11.field30490 * 0.5F)) * 0.4F)
                    );
                    RenderUtil.restoreScissor();
                    var11.field30490 = Math.min(var11.field30490 + 3.0F / (float) Minecraft.getFps(), 1.0F);
                    if (var11.field30490 == 1.0F) {
                        var4.remove();
                    }
                }

                RenderUtil.restorePreviousStencilBuffer();
                RenderUtil.drawRoundedRect(
                        (float) this.xA,
                        (float) this.yA,
                        (float) this.widthA,
                        (float) this.heightA,
                        6.0F,
                        RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F)
                );
                GL11.glPopMatrix();
                RenderUtil.drawString(
                        ResourceRegistry.JelloMediumFont20,
                        (float) (this.xA + 14),
                        (float) (this.yA + 8),
                        "+",
                        RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.8F)
                );
                RenderUtil.drawRoundedRect2(
                        (float) (this.xA + 16), (float) (this.yA + 65), 8.0F, 2.0F, RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.8F)
                );
            }
        } catch (IOException var10) {
        }

        super.draw(partialTicks);
    }

    public static class Class7086 {
        private static String[] field30489;
        public float field30490;
        public boolean field30491;
        public final Zoom field30492;

        public Class7086(Zoom var1, boolean var2) {
            this.field30492 = var1;
            this.field30490 = 0.0F;
            this.field30491 = var2;
        }
    }
}
