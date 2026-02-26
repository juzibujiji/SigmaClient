package com.mentalfrostbyte.jello.gui.base.elements.impl.image.types;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.impl.LoadingScreen;
import com.mentalfrostbyte.jello.gui.combined.impl.SwitchScreen;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.math.SmoothInterpolator;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.Texture;

public class FadedImage extends Button {
    private final Texture texture;
    private final Animation hoverAnim = new Animation(150, 190, Animation.Direction.BACKWARDS);
    private boolean hover;

    public FadedImage(CustomGuiScreen var1, String name, int x, int y, int width, int height, Texture texture) {
        super(var1, name, x, y, width, height);
        this.texture = texture;
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        this.hover = this.method13298();
        super.updatePanelDimensions(newHeight, newWidth);
    }

    @Override
    public void draw(float partialTicks) {
        this.hoverAnim.changeDirection(!this.hover ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
        float var4 = SmoothInterpolator.interpolate(this.hoverAnim.calcPercent(), 0.07, 0.73, 0.63, 1.01);
        if (this.hoverAnim.getDirection() == Animation.Direction.BACKWARDS) {
            var4 = SmoothInterpolator.interpolate(this.hoverAnim.calcPercent(), 0.71, 0.18, 0.95, 0.57);
        }

        RenderUtil.startScissor((float) this.getXA(), (float) this.getYA() - var4 * 3.0F, (float) this.getWidthA(), (float) this.getHeightA());
        int var5 = 40;
        float var6 = -SwitchScreen.field21070 / (float) Minecraft.getInstance().getMainWindow().getWidth();
        float var7 = -SwitchScreen.field21071 / (float) Minecraft.getInstance().getMainWindow().getHeight();
        RenderUtil.drawImage(
                (float) var5 * var6,
                (float) var5 * var7,
                (float) (Minecraft.getInstance().getMainWindow().getWidth() + var5),
                (float) (Minecraft.getInstance().getMainWindow().getHeight() + var5),
                LoadingScreen.background
        );
        RenderUtil.restoreScissor();
        if (this.hover) {
            RenderUtil.drawRoundedRect2(
                    (float) this.getXA(),
                    (float) this.getYA() - var4 * 3.0F,
                    (float) this.getWidthA(),
                    (float) this.getHeightA(),
                    RenderUtil2.applyAlpha(-12319668, 0.5F)
            );
        }

        RenderUtil.drawImage(
                (float) this.getXA(),
                (float) this.getYA() - var4 * 3.0F,
                (float) this.getWidthA(),
                (float) this.getHeightA(),
                this.texture,
                ClientColors.LIGHT_GREYISH_BLUE.getColor()
        );
        GL11.glPushMatrix();
        super.drawChildren(partialTicks);
        GL11.glPopMatrix();
    }
}
