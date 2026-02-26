package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.math.smoothing.EasingFunctions;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

public class Coords extends Module {
    private final Animation coordinateAnimation = new Animation(1500, 1500, Animation.Direction.BACKWARDS);
    private double playerX, playerY, playerZ;

    public Coords() {
        super(ModuleCategory.GUI, "Coords", "Displays coordinates");
    }

    @EventTarget
    public void onPlayerTick(EventUpdate event) {
        if (this.isEnabled()) {
            boolean hasMoved = playerX != mc.player.getPosX() || playerY != mc.player.getPosY() || playerZ != mc.player.getPosZ();

            playerX = mc.player.getPosX();
            playerY = mc.player.getPosY();
            playerZ = mc.player.getPosZ();

            boolean shouldAnimate = hasMoved || (!mc.player.isOnGround()) || mc.player.isSneaking();
            if (!shouldAnimate) {
                if (this.coordinateAnimation.calcPercent() == 1.0F && this.coordinateAnimation.getDirection() == Animation.Direction.FORWARDS) {
                    this.coordinateAnimation.changeDirection(Animation.Direction.BACKWARDS);
                }
            } else {
                this.coordinateAnimation.changeDirection(Animation.Direction.FORWARDS);
            }
        }
    }

    @EventTarget
    @LowestPriority
    public void onRender2D(EventRender2DOffset event) {
        if (this.isEnabled() && mc.player != null && !(mc.gameSettings.showDebugInfo || mc.gameSettings.hideGUI)) {
            float animationScale = Math.min(1.0F, 0.6F + this.coordinateAnimation.calcPercent() * 2.0F);
            String coordinatesText = String.format("%.0f %.0f %.0f", mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ());

            float textX = 85;
            int textY = event.getYOffset();
            float maxTextWidth = 150;
            float textWidth = (float) ResourceRegistry.JelloLightFont18.getWidth(coordinatesText);
            float scaleFactor = Math.min(1.0F, maxTextWidth / textWidth);

            if (this.coordinateAnimation.getDirection() != Animation.Direction.FORWARDS) {
                scaleFactor *= 0.9F + QuadraticEasing.easeInQuad(Math.min(1.0F, this.coordinateAnimation.calcPercent() * 8.0F), 0.0F, 1.0F, 1.0F) * 0.1F;
            } else {
                scaleFactor *= 0.9F + EasingFunctions.easeOutBack(Math.min(1.0F, this.coordinateAnimation.calcPercent() * 7.0F), 0.0F, 1.0F, 1.0F) * 0.1F;
            }

            GL11.glPushMatrix();
            GL11.glTranslatef(textX, (float) (textY + 10), 0.0F);
            GL11.glScalef(scaleFactor, scaleFactor, 1.0F);
            GL11.glTranslatef(-textX, (float) (-textY - 10), 0.0F);

            RenderUtil.drawString(
                    ResourceRegistry.JelloLightFont18_1,
                    textX,
                    (float) textY,
                    coordinatesText,
                    RenderUtil2.applyAlpha(-16777216, 0.5F * animationScale),
                    FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2,
                    FontSizeAdjust.field14488
            );

            RenderUtil.drawString(
                    ResourceRegistry.JelloLightFont18,
                    textX,
                    (float) textY,
                    coordinatesText,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.8F * animationScale),
                    FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2,
                    FontSizeAdjust.field14488
            );

            GL11.glPopMatrix();
        }
    }
}
