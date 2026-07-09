/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.raytrace.RayCastUtils (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from its single observed call site,
 * FallingPlayer#rayTraceHit: RayCastUtils.raycast(distance, rotation, false, 1) returning a
 * BlockHitResult).
 *
 * Reconstruction: a vanilla outline-shape block raytrace from the player's eye position
 * along the given rotation for the given distance. The trailing boolean/int parameters
 * mirror the upstream signature; their upstream meaning is unpublished and they are unused.
 */
package com.mentalfrostbyte.jello.southside.utils.raytrace;

import com.mentalfrostbyte.jello.southside.utils.types.SSRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.vector.Vector3d;

public final class SSRayCastUtils {
    private SSRayCastUtils() {
    }

    public static BlockRayTraceResult raycast(double distance, SSRotation rotation, boolean unusedFlag, int unusedMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null || rotation == null) {
            return null;
        }

        Vector3d start = mc.player.getEyePosition(1.0F);
        Vector3d direction = Vector3d.fromPitchYaw(rotation.pitch, rotation.yaw);
        Vector3d end = start.add(direction.scale(distance));

        return mc.world.rayTraceBlocks(new RayTraceContext(
                start,
                end,
                RayTraceContext.BlockMode.OUTLINE,
                RayTraceContext.FluidMode.NONE,
                mc.player
        ));
    }
}
