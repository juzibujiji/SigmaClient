/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.types.Rotation (referenced by upstream sources; class body not
 * published in the upstream repo — reconstructed from its observed API surface).
 *
 * Upstream API used by Scaffold/FallingPlayer/RotationUtils:
 *   new Rotation(yaw, pitch); public fields yaw/pitch; getYaw()/getPitch();
 *   fixedSensitivity(float mouseSensitivity).
 * Named SSRotation because this project already has an unrelated
 * com.mentalfrostbyte.jello.util.game.player.constructor.Rotation.
 */
package com.mentalfrostbyte.jello.southside.utils.types;

import net.minecraft.client.Minecraft;

public class SSRotation {
    public float yaw;
    public float pitch;

    public SSRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    /**
     * Quantizes this rotation onto the mouse-sensitivity GCD grid relative to the last
     * rotation reported to the server, so the deltas the server sees are producible by
     * real mouse input (defeats GCD/"invalid sensitivity" rotation checks).
     *
     * Upstream calls {@code rot.fixedSensitivity(mc.options.getMouseSensitivity().getValue().floatValue())}.
     * Implementation reconstructed with the canonical GCD formula
     * (f = sens * 0.6 + 0.2; gcd = f^3 * 1.2 == f^3 * 8 * 0.15).
     */
    public void fixedSensitivity(float sensitivity) {
        float f = sensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 1.2F;

        Minecraft mc = Minecraft.getInstance();
        float baseYaw;
        float basePitch;
        if (mc.player != null) {
            baseYaw = mc.player.lastReportedYaw;
            basePitch = mc.player.lastReportedPitch;
        } else {
            baseYaw = 0.0F;
            basePitch = 0.0F;
        }

        float deltaYaw = this.yaw - baseYaw;
        float deltaPitch = this.pitch - basePitch;

        this.yaw = baseYaw + (deltaYaw - deltaYaw % gcd);
        this.pitch = basePitch + (deltaPitch - deltaPitch % gcd);

        if (this.pitch > 90.0F) {
            this.pitch = 90.0F;
        } else if (this.pitch < -90.0F) {
            this.pitch = -90.0F;
        }
    }
}
