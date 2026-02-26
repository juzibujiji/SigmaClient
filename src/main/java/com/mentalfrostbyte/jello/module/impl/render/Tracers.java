package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.Client;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ColorSetting;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.world.PositionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;

public class Tracers extends Module {
    public Tracers() {
        super(ModuleCategory.RENDER, "Tracers", "Shows players");
        this.registerSetting(
                new ColorSetting("Color", "The tracers color", ClientColors.LIGHT_GREYISH_BLUE.getColor()));
    }

    @EventTarget
    public void method16521(EventRender3D event) {
        if (this.isEnabled()) {
            GL11.glBlendFunc(770, 771);
            GL11.glAlphaFunc(519, 0.0F);
            GL11.glEnable(3042);
            GL11.glEnable(2848);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glLineWidth(2.0F * GuiManager.scaleFactor);
            GL11.glDepthMask(false);
            GL11.glShadeModel(7425);

            for (Entity entity : CombatUtil.getAllPlayersInWorld()) {
                if (entity != mc.player && entity.isAlive() && entity.boundingBox.getAverageEdgeLength() > 0.8
                        && entity.ticksExisted > 30 && !Client.getInstance().botManager.isBot(entity)) {
                    this.method16522(entity);
                }
            }

            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDisable(2848);
            GL11.glDisable(3042);
            GL11.glDepthMask(true);
        }
    }

    public void method16522(Entity entity) {
        double x = PositionUtil.getRelativePosition(entity).x;
        double y = PositionUtil.getRelativePosition(entity).y + (double) (entity.getHeight() / 2.0F);
        double z = PositionUtil.getRelativePosition(entity).z;
        Vector3d offset = new Vector3d(0.0, 0.0, 10.0)
                .rotatePitch(-((float) Math.toRadians(Minecraft.getInstance().player.rotationPitch)))
                .rotateYaw(-((float) Math.toRadians(Minecraft.getInstance().player.rotationYaw)));
        int color = this.parseSettingValueToIntBySettingName("Color");
        GL11.glBegin(1);
        GL11.glColor4fv(RenderUtil.method17709(RenderUtil.applyAlpha(color, 0.45F)));
        GL11.glVertex3d(offset.x, offset.y, offset.z);
        GL11.glColor4fv(RenderUtil.method17709(RenderUtil.applyAlpha(color, 0.0F)));
        GL11.glVertex3d(x, y, z);
        GL11.glEnd();
    }
}
