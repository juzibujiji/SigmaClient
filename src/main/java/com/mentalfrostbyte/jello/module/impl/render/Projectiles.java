package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.render.projectiles.TraceThing;
import com.mentalfrostbyte.jello.module.impl.render.projectiles.FloatVector4;
import com.mentalfrostbyte.jello.module.impl.render.projectiles.ProjectileItems;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.world.BoundingBox;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.List;

public class Projectiles extends Module {
    public EntityRendererManager renderManager = mc.getRenderManager();
    public FloatVector4 rotationX = new FloatVector4(0.0F, 0.0F, 0.0F);
    public FloatVector4 rotationY = new FloatVector4(0.0F, 0.0F, 0.0F);
    public FloatVector4 rotationZ = new FloatVector4(0.0F, 0.0F, 0.0F);

    public Projectiles() {
        super(ModuleCategory.RENDER, "Projectiles", "Predict the path of a projectile");
        this.setAvailableOnClassic(false);
    }

    @EventTarget
    public void method16523(EventRender3D var1) {
        if (this.isEnabled()) {
			assert mc.player != null;
			if (mc.player.getHeldItemMainhand() != null) {
                ProjectileItems var4 = ProjectileItems
                        .getProjectileThingyForItem(mc.player.getHeldItemMainhand().getItem());
                if (var4 != null) {
                    float rotYawRadians = (float) Math.toRadians(mc.player.rotationYaw - 25.0F);
                    float rotPitchRadians = (float) Math.toRadians(mc.player.rotationPitch);
                    double var7 = 0.2F;
                    double averageEdgeLengthDiv2 = mc.player.boundingBox.getAverageEdgeLength() / 2.0;
                    double var11 = (double) MathHelper.cos(rotYawRadians) * averageEdgeLengthDiv2;
                    double var13 = (double) MathHelper.sin(rotYawRadians) * averageEdgeLengthDiv2;
                    double var15 = mc.player.lastTickPosX
                            + (mc.player.getPosX() - mc.player.lastTickPosX) * (double) mc.timer.renderPartialTicks;
                    double var17 = mc.player.lastTickPosY
                            + (mc.player.getPosY() - mc.player.lastTickPosY) * (double) mc.timer.renderPartialTicks;
                    double var19 = mc.player.lastTickPosZ
                            + (mc.player.getPosZ() - mc.player.lastTickPosZ) * (double) mc.timer.renderPartialTicks;
                    GL11.glPushMatrix();
                    GL11.glEnable(2848);
                    GL11.glBlendFunc(770, 771);
                    GL11.glEnable(3042);
                    GL11.glDisable(3553);
                    GL11.glDisable(2929);
                    GL11.glEnable(32925);
                    GL11.glDisable(2896);
                    GL11.glShadeModel(7425);
                    GL11.glDepthMask(false);
                    GL11.glLineWidth(10.0F);
                    GL11.glColor4d(0.0, 0.0, 0.0, 0.05F);
                    GL11.glAlphaFunc(519, 0.0F);
                    GL11.glBegin(3);
                    List<TraceThing> traceThings = var4.getTraceThings();

                    for (int var22 = 0; var22 < traceThings.size(); var22++) {
                        TraceThing traceThing = traceThings.get(var22);
                        double var24 = var11 - (double) ((float) (var22 + 1) / (float) traceThings.size()) * var11;
                        double var26 = var13 - (double) ((float) (var22 + 1) / (float) traceThings.size()) * var13;
                        double var28 = var7 - (double) ((float) (var22 + 1) / (float) traceThings.size()) * var7;
                        float var30 = (float) Math.min(1, var22);
                        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.05F * var30);
                        GL11.glVertex3d(
                                traceThing.getX() - mc.gameRenderer.getActiveRenderInfo().getPos().getX() - var24,
                                traceThing.getY() - mc.gameRenderer.getActiveRenderInfo().getPos().getY() - var28,
                                traceThing.getZ() - mc.gameRenderer.getActiveRenderInfo().getPos().getZ() - var26);
                    }

                    GL11.glEnd();
                    GL11.glLineWidth(2.0F * GuiManager.scaleFactor);
                    GL11.glColor4d(1.0, 1.0, 1.0, 0.75);
                    GL11.glBegin(3);

                    for (int var38 = 0; var38 < traceThings.size(); var38++) {
                        TraceThing var39 = traceThings.get(var38);
                        double var40 = var11 - (double) ((float) (var38 + 1) / (float) traceThings.size()) * var11;
                        double var43 = var13 - (double) ((float) (var38 + 1) / (float) traceThings.size()) * var13;
                        double var46 = var7 - (double) ((float) (var38 + 1) / (float) traceThings.size()) * var7;
                        float var48 = (float) Math.min(1, var38);
                        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.75F * var48);
                        GL11.glVertex3d(
                                var39.getX() - mc.gameRenderer.getActiveRenderInfo().getPos().getX() - var40,
                                var39.getY() - mc.gameRenderer.getActiveRenderInfo().getPos().getY() - var46,
                                var39.getZ() - mc.gameRenderer.getActiveRenderInfo().getPos().getZ() - var43);
                    }

                    GL11.glEnd();
                    GL11.glDisable(2929);
                    if (var4.rayTraceResult == null) {
                        if (var4.hit != null) {
                            BoundingBox renderBoundingBox = getRenderBoundingBox(var4);
                            RenderUtil.render3DColoredBox(renderBoundingBox,
                                    RenderUtil.applyAlpha(ClientColors.DARK_BLUE_GREY.getColor(), 0.1F));
                            RenderUtil.renderWireframeBox(renderBoundingBox,
                                    RenderUtil.applyAlpha(ClientColors.DARK_BLUE_GREY.getColor(), 0.1F));
                        }

                    } else {
                        double var49 = var4.traceX - mc.gameRenderer.getActiveRenderInfo().getPos().getX();
                        double var42 = var4.traceY - mc.gameRenderer.getActiveRenderInfo().getPos().getY();
                        double var45 = var4.traceZ - mc.gameRenderer.getActiveRenderInfo().getPos().getZ();
                        GL11.glPushMatrix();
                        GL11.glTranslated(var49, var42, var45);
                        BlockPos blockPos = new BlockPos(0, 0, 0)
                                .offset(((BlockRayTraceResult) var4.rayTraceResult).getFace());
                        GL11.glRotatef(
                                45.0F,
                                this.rotationX.interpolateX((float) blockPos.getX()),
                                this.rotationX.interpolateY((float) (-blockPos.getY())),
                                this.rotationX.interpolateZ((float) blockPos.getZ()));
                        GL11.glRotatef(
                                90.0F,
                                this.rotationY.interpolateX((float) blockPos.getZ()),
                                this.rotationY.interpolateY((float) blockPos.getY()),
                                this.rotationY.interpolateZ((float) (-blockPos.getX())));
                        GL11.glTranslatef(-0.5F, 0.0F, -0.5F);
                        BoundingBox box = new BoundingBox(0.0, 0.0, 0.0, 1.0, 0.0, 1.0);
                        RenderUtil.render3DColoredBox(box,
                                RenderUtil.applyAlpha(ClientColors.PALE_ORANGE.getColor(), 0.1F));
                        RenderUtil.renderWireframeBox(box,
                                RenderUtil.applyAlpha(ClientColors.PALE_ORANGE.getColor(), 0.1F));
                        GL11.glPopMatrix();
                    }

                    GL11.glDisable(2896);
                    GL11.glColor3f(1.0F, 1.0F, 1.0F);
                    GL11.glEnable(2896);
                    GL11.glDisable(3042);
                    GL11.glEnable(3553);
                    GL11.glDisable(32925);
                    GL11.glDisable(2848);
                    GL11.glPopMatrix();
                }
            }
        }
    }

    private static @NotNull BoundingBox getRenderBoundingBox(ProjectileItems var4) {
        double x = var4.hit.lastTickPosX
                + (var4.hit.getPosX() - var4.hit.lastTickPosX)
                * (double) mc.timer.renderPartialTicks
                - mc.gameRenderer.getActiveRenderInfo().getPos().getX();
        double y = var4.hit.lastTickPosY
                + (var4.hit.getPosY() - var4.hit.lastTickPosY)
                * (double) mc.timer.renderPartialTicks
                - mc.gameRenderer.getActiveRenderInfo().getPos().getY();
        double z = var4.hit.lastTickPosZ
                + (var4.hit.getPosZ() - var4.hit.lastTickPosZ)
                * (double) mc.timer.renderPartialTicks
                - mc.gameRenderer.getActiveRenderInfo().getPos().getZ();
        double hitWidth = var4.hit.getWidth() / 2.0F + 0.2F;
        double hitHeight = var4.hit.getHeight() + 0.1F;
		return new BoundingBox(x - hitWidth, y, z - hitWidth, x + hitWidth, y + hitHeight,
				z + hitWidth);
    }
}