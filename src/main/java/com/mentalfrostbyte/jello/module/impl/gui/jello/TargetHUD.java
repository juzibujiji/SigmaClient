package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;
import java.util.List;

public class TargetHUD extends Module {
    public LivingEntity target;

    public TargetHUD() {
        super(ModuleCategory.GUI, "TargetHUD", "Shows information about your target.");
        this.setAvailableOnClassic(false);
    }

    @EventTarget
    public void onRender2D(EventRender2DOffset event) {
        if (!this.isEnabled()) return;
        
        target = null;

        // Try to get KillAura target
        Module killAura = Client.getInstance().moduleManager.getModuleByClass(com.mentalfrostbyte.jello.module.impl.combat.KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            if (com.mentalfrostbyte.jello.module.impl.combat.KillAura.targetEntity instanceof LivingEntity) {
               target = (LivingEntity) com.mentalfrostbyte.jello.module.impl.combat.KillAura.targetEntity;
            }
        }
        
        // If not in chat and no killaura target, return
        if (target == null && !(mc.currentScreen instanceof ChatScreen)) {
           return;
        }

        // If in chat screen and no target, mock one for preview
        if (target == null) {
            target = mc.player;
        }

        if (target != null) {
            float width = 150;
            float height = 50;
            float x = mc.mainWindow.getWidth() / 2f + 20;
            float y = mc.mainWindow.getHeight() / 2f + 20;
            
            // Draw background
            RenderUtil.drawRoundedRect(x, y, width, height, 10, RenderUtil.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5f));
            
            // Draw name
            RenderUtil.drawString(ResourceRegistry.JelloLightFont20, x + 40, y + 5, target.getName().getString(), ClientColors.LIGHT_GREYISH_BLUE.getColor());
            
            // Draw health text
            float health = target.getHealth();
            float maxHealth = target.getMaxHealth();
            String hpText = String.format("%.1f", health) + " \u2764"; // heart symbol
            RenderUtil.drawString(ResourceRegistry.JelloLightFont14, x + 40, y + 25, hpText, ClientColors.LIGHT_GREYISH_BLUE.getColor());

            // Draw health bar
            float hpPercent = Math.min(health / maxHealth, 1.0f);
            RenderUtil.drawRoundedRect2(x + 40, y + 40, width - 50, 4, RenderUtil.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3f));
            RenderUtil.drawRoundedRect2(x + 40, y + 40, (width - 50) * hpPercent, 4, ClientColors.LIGHT_GREYISH_BLUE.getColor());
            
            // Draw head
            drawEntityOnScreen((int)(x + 20), (int)(y + 40), 20, target);
        }
    }

    private void drawEntityOnScreen(int posX, int posY, int scale, LivingEntity livingEntity) {
        float fixedYaw = livingEntity.rotationYaw;
        float fixedPitch = livingEntity.rotationPitch;

        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) posX, (float) posY, 1050.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);

        MatrixStack matrixstack = new MatrixStack();
        matrixstack.translate(0.0D, 0.0D, 1000.0D);
        matrixstack.scale((float) scale, (float) scale, (float) scale);

        Quaternion quaternion = Vector3f.ZP.rotationDegrees(180.0F);
        matrixstack.rotate(quaternion);

        float f2 = livingEntity.renderYawOffset;
        float f3 = livingEntity.rotationYaw;
        float f4 = livingEntity.rotationPitch;
        float f5 = livingEntity.prevRotationYawHead;
        float f6 = livingEntity.rotationYawHead;

        livingEntity.renderYawOffset = f2;
        livingEntity.rotationYaw = fixedYaw;
        livingEntity.rotationPitch = fixedPitch;
        livingEntity.rotationYawHead = f6;
        livingEntity.prevRotationYawHead = f5;

        EntityRendererManager entityrenderermanager = Minecraft.getInstance().getRenderManager();
        entityrenderermanager.setCameraOrientation(new Quaternion(0, 0, 0, 1));
        entityrenderermanager.setRenderShadow(false);

        IRenderTypeBuffer.Impl irendertypebuffer$impl = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();
        RenderSystem.runAsFancy(() -> {
            entityrenderermanager.renderEntityStatic(livingEntity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, matrixstack, irendertypebuffer$impl, 15728880);
        });

        irendertypebuffer$impl.finish();
        entityrenderermanager.setRenderShadow(true);

        livingEntity.renderYawOffset = f2;
        livingEntity.rotationYaw = f3;
        livingEntity.rotationPitch = f4;
        livingEntity.prevRotationYawHead = f5;
        livingEntity.rotationYawHead = f6;

        RenderSystem.popMatrix();
    }
}
