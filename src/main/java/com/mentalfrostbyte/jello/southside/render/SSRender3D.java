/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.render.Render3D (referenced by upstream sources; not published in
 * the upstream repo — reconstructed from usage: Render3D.boxLines(Box, java.awt.Color) and
 * Render3D.boxSides(Box, java.awt.Color) drawing the scaffold Mark box in world space).
 *
 * Rendering delegates to the host RenderUtil 3D box primitives ("实在没有" fallback — the
 * upstream renderer is unpublished), converting world-space AABBs to the camera-relative
 * coordinates SigmaClient's EventRender3D context expects.
 */
package com.mentalfrostbyte.jello.southside.render;

import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.world.BoundingBox;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public final class SSRender3D {
    private SSRender3D() {
    }

    public static void boxLines(AxisAlignedBB box, Color color) {
        BoundingBox relative = toCameraRelative(box);
        if (relative == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glAlphaFunc(GL11.GL_ALWAYS, 0.0F);
        RenderUtil.renderWireframeBox(relative, 2.0F, color.getRGB());
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    public static void boxSides(AxisAlignedBB box, Color color) {
        BoundingBox relative = toCameraRelative(box);
        if (relative == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glAlphaFunc(GL11.GL_ALWAYS, 0.0F);
        RenderUtil.render3DColoredBox(relative, color.getRGB());
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static BoundingBox toCameraRelative(AxisAlignedBB box) {
        Minecraft mc = Minecraft.getInstance();
        if (box == null || mc.gameRenderer == null || mc.gameRenderer.getActiveRenderInfo() == null) {
            return null;
        }
        Vector3d cam = mc.gameRenderer.getActiveRenderInfo().getProjectedView();
        return new BoundingBox(
                box.minX - cam.x,
                box.minY - cam.y,
                box.minZ - cam.z,
                box.maxX - cam.x,
                box.maxY - cam.y,
                box.maxZ - cam.z
        );
    }
}
