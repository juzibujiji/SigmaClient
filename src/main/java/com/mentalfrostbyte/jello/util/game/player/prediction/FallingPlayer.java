package com.mentalfrostbyte.jello.util.game.player.prediction;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.MathHelper;

/**
 * Lightweight, side-effect free physics simulator that predicts where the
 * player will be after a given number of ticks if they keep their current
 * input/motion. Mirrors the algorithm used by HeyPixel-style scaffolds in
 * 1.20.1 (Naven's {@code FallingPlayer}) but adapted for the 1.16.5 API.
 *
 * <p>Only the {@link #calculate(int)} flow is actually consumed by the
 * scaffold; {@link #calculateMLG(int)} is provided for parity with the source
 * algorithm so future modules (auto-MLG, etc.) can reuse it.</p>
 */
public class FallingPlayer {
    private static final Minecraft mc = Minecraft.getInstance();

    public double x;
    public double y;
    public double z;

    private double motionX;
    private double motionY;
    private double motionZ;

    private final float yaw;
    private final float strafe;
    private final float forward;
    private float jumpMovementFactor;

    public FallingPlayer(double x, double y, double z,
                         double motionX, double motionY, double motionZ,
                         float yaw, float strafe, float forward) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.yaw = yaw;
        this.strafe = strafe;
        this.forward = forward;
    }

    public FallingPlayer(PlayerEntity player) {
        this(
                player.getPosX(),
                player.getPosY(),
                player.getPosZ(),
                player.getMotion().x,
                player.getMotion().y,
                player.getMotion().z,
                player.rotationYaw,
                player.moveStrafing,
                player.moveForward
        );

        // 1.20.1's FallingPlayer pulls block jump factors via Block#getJumpFactor()
        // and Player#getJumpBoostPower(). Both translate cleanly to 1.16.5: the
        // jump-factor accessor is on Block since 1.16, and the jump-boost power
        // is rebuilt from the JUMP_BOOST status effect (Effects.JUMP_BOOST).
        float standingFactor = player.world
                .getBlockState(player.getPosition())
                .getBlock()
                .getJumpFactor();
        float belowFactor = player.world
                .getBlockState(player.getPosition().down())
                .getBlock()
                .getJumpFactor();

        float effectiveFactor = standingFactor == 1.0F ? belowFactor : standingFactor;
        this.jumpMovementFactor = 0.42F * effectiveFactor + getJumpBoostPower(player);
    }

    private static float getJumpBoostPower(LivingEntity entity) {
        if (!entity.isPotionActive(Effects.JUMP_BOOST)) {
            return 0.0F;
        }
        return 0.1F * (float) (entity.getActivePotionEffect(Effects.JUMP_BOOST).getAmplifier() + 1);
    }

    /**
     * Simulates {@code ticks} game ticks of free-fall + air-control physics.
     * Used by the scaffold to project where the player will be when their
     * predicted Y-velocity has actually carried them downward.
     */
    public void calculate(int ticks) {
        for (int i = 0; i < ticks; i++) {
            this.calculateForTick();
        }
    }

    /**
     * Variant that omits the post-tick horizontal damping, matching the
     * second physics pass used by MLG/landing predictions.
     */
    public void calculateMLG(int ticks) {
        for (int i = 0; i < ticks; i++) {
            this.calculateForTickMLG();
        }
    }

    private void calculateForTick() {
        float sr = this.strafe * 0.98F;
        float fw = this.forward * 0.98F;
        float v = sr * sr + fw * fw;
        if (v >= 1.0E-4F) {
            v = MathHelper.sqrt(v);
            if (v < 1.0F) {
                v = 1.0F;
            }

            float fixedJumpFactor = this.jumpMovementFactor;
            if (mc.player != null && mc.player.isSprinting()) {
                fixedJumpFactor *= 1.3F;
            }

            v = fixedJumpFactor / v;
            sr *= v;
            fw *= v;
            float sin = MathHelper.sin(this.yaw * (float) Math.PI / 180.0F);
            float cos = MathHelper.cos(this.yaw * (float) Math.PI / 180.0F);
            this.motionX += (double) (sr * cos - fw * sin);
            this.motionZ += (double) (fw * cos + sr * sin);
        }

        this.motionY -= 0.08;
        this.motionY *= 0.98F;
        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;
        this.motionX *= 0.91;
        this.motionZ *= 0.91;
    }

    private void calculateForTickMLG() {
        float sr = this.strafe;
        float fw = this.forward;
        float v = sr * sr + fw * fw;
        if (v >= 1.0E-4F) {
            v = MathHelper.sqrt(v);
            if (v < 1.0F) {
                v = 1.0F;
            }

            float fixedJumpFactor = this.jumpMovementFactor;
            if (mc.player != null && mc.player.isSprinting()) {
                fixedJumpFactor *= 1.3F;
            }

            v = fixedJumpFactor / v;
            sr *= v;
            fw *= v;
            float sin = MathHelper.sin(this.yaw * (float) Math.PI / 180.0F);
            float cos = MathHelper.cos(this.yaw * (float) Math.PI / 180.0F);
            this.motionX += (double) (sr * cos - fw * sin);
            this.motionZ += (double) (fw * cos + sr * sin);
        }

        this.motionY -= 0.08;
        this.motionY *= 0.98F;
        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;
    }
}
