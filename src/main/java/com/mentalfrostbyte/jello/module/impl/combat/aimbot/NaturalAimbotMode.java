package com.mentalfrostbyte.jello.module.impl.combat.aimbot;

import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.Aimbot;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Random;

/**
 * Shared 20 TPS aim-assist controller. It deliberately adds a bounded amount
 * to the camera instead of replacing the camera rotation every render frame.
 */
public abstract class NaturalAimbotMode extends Module {
    private static final float DEAD_ZONE = 0.18F;
    private static final float MANUAL_INPUT_THRESHOLD = 1.15F;

    private final Random random = new Random();
    private Entity target;
    private float yawVelocity;
    private float pitchVelocity;
    private float lastOutputYaw;
    private float lastOutputPitch;
    private boolean hasOutput;
    private float cameraPreviousYaw;
    private float cameraPreviousPitch;
    private boolean interpolateCameraThisTick;
    private int manualOverrideTicks;
    private int reactionTicks;
    private int aimDriftTicks;
    private float aimHeight = 0.70F;
    private float aimHeightTarget = 0.70F;
    private float jitterPhase;
    private float jitterDrift;
    private float jitterDriftTarget;
    private int jitterDriftTicks;

    protected NaturalAimbotMode(String name, String description, float horizontalSpeed,
                                float verticalSpeed, float acceleration, boolean zitter) {
        super(ModuleCategory.COMBAT, name, description);
        this.registerSetting(new NumberSetting<>("Range", "Maximum target range", 4.0F, 2.8F, 8.0F, 0.05F));
        this.registerSetting(new NumberSetting<>("FOV", "Maximum angle from the crosshair", 180.0F, 10.0F, 180.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("H Speed", "Maximum horizontal degrees per tick", horizontalSpeed, 1.0F, 40.0F, 0.5F));
        this.registerSetting(new NumberSetting<>("V Speed", "Maximum vertical degrees per tick", verticalSpeed, 1.0F, 30.0F, 0.5F));
        this.registerSetting(new NumberSetting<>("Acceleration", "How quickly the aim reaches its speed", acceleration, 0.05F, 1.0F, 0.01F));
        this.registerSetting(new NumberSetting<>("Prediction", "Lead moving targets by this many ticks", 0.55F, 0.0F, 1.5F, 0.05F));
        this.registerSetting(new NumberSetting<>("Reaction", "Delay after acquiring a target in ticks", 1.0F, 0.0F, 4.0F, 1.0F));
        this.registerSetting(new BooleanSetting("Manual override", "Pause assistance when the mouse is moved", true));
        this.registerSetting(new BooleanSetting("Zitter", "Add small correlated hand movement", zitter));
        this.registerSetting(new NumberSetting<>("Zitter Strength", "Maximum horizontal hand movement", 0.32F, 0.0F, 1.5F, 0.01F));
    }

    @Override
    public void onEnable() {
        this.resetController();
    }

    @Override
    public void onDisable() {
        this.resetController();
    }

    @EventTarget
    public void onRunTick(EventRunTicks event) {
        if (!event.isPre()) {
            this.restoreCameraInterpolationStart();
            return;
        }
        if (!this.isEnabled() || mc.player == null || mc.world == null) return;

        float currentYaw = mc.player.rotationYaw;
        float currentPitch = mc.player.rotationPitch;
        if (mc.currentScreen != null || !this.shouldAssist()) {
            this.releaseTarget(currentYaw, currentPitch);
            return;
        }

        if (this.getBooleanValueFromSettingName("Manual override") && this.detectManualInput(currentYaw, currentPitch)) {
            this.manualOverrideTicks = 3;
            this.yawVelocity = 0.0F;
            this.pitchVelocity = 0.0F;
        }
        if (this.manualOverrideTicks > 0) {
            this.manualOverrideTicks--;
            this.rememberOutput(currentYaw, currentPitch);
            return;
        }

        Aimbot parent = (Aimbot) this.access();
        Entity nextTarget = parent.getTarget(
                this.safeSetting("Range", 2.8F, 8.0F),
                this.safeSetting("FOV", 10.0F, 180.0F),
                this.target);
        if (nextTarget != this.target) {
            this.target = nextTarget;
            this.reactionTicks = Math.round(this.safeSetting("Reaction", 0.0F, 4.0F));
            this.aimHeightTarget = this.randomBetween(0.62F, 0.78F);
            this.aimDriftTicks = 0;
            this.yawVelocity *= 0.35F;
            this.pitchVelocity *= 0.35F;
        }
        if (this.target == null) {
            this.releaseTarget(currentYaw, currentPitch);
            return;
        }
        if (this.reactionTicks-- > 0) {
            this.rememberOutput(currentYaw, currentPitch);
            return;
        }

        double prediction = this.safeSetting("Prediction", 0.0F, 1.5F);
        AxisAlignedBB box = this.target.getBoundingBox();
        this.updateAimHeight();
        double x = this.target.getPosX() + (this.target.getPosX() - this.target.lastTickPosX) * prediction;
        double y = box.minY + (box.maxY - box.minY) * this.aimHeight
                + (this.target.getPosY() - this.target.lastTickPosY) * prediction;
        double z = this.target.getPosZ() + (this.target.getPosZ() - this.target.lastTickPosZ) * prediction;
        float[] desired = this.getDesiredRotation(x, y, z);
        if (desired == null || desired.length < 2 || !Float.isFinite(desired[0]) || !Float.isFinite(desired[1])) {
            this.releaseTarget(currentYaw, currentPitch);
            return;
        }

        float zitterStrength = this.getBooleanValueFromSettingName("Zitter")
                ? this.safeSetting("Zitter Strength", 0.0F, 1.5F) : 0.0F;
        float[] zitter = this.updateZitter(zitterStrength);
        float yawDifference = MathHelper.wrapDegrees(desired[0] + zitter[0] - currentYaw);
        float pitchDifference = desired[1] + zitter[1] - currentPitch;
        float horizontalSpeed = this.safeSetting("H Speed", 1.0F, 40.0F);
        float verticalSpeed = this.safeSetting("V Speed", 1.0F, 30.0F);
        float acceleration = this.safeSetting("Acceleration", 0.05F, 1.0F);

        this.yawVelocity = this.accelerateAxis(this.yawVelocity, yawDifference, horizontalSpeed, acceleration);
        this.pitchVelocity = this.accelerateAxis(this.pitchVelocity, pitchDifference, verticalSpeed, acceleration);
        float yawStep = this.limitWithoutOvershoot(this.yawVelocity, yawDifference);
        float pitchStep = this.limitWithoutOvershoot(this.pitchVelocity, pitchDifference);
        if (Math.abs(yawDifference) <= DEAD_ZONE) yawStep = 0.0F;
        if (Math.abs(pitchDifference) <= DEAD_ZONE) pitchStep = 0.0F;

        float newYaw = currentYaw + yawStep;
        float newPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0F, 90.0F);
        if (yawStep != 0.0F || pitchStep != 0.0F) {
            // Entity#tick copies the newly assisted rotation into prevRotation*,
            // which otherwise removes vanilla's per-frame camera interpolation.
            // Restore this pre-assist view at the end of the tick so only the
            // local camera eases across the step; gameplay still uses newYaw/newPitch.
            this.cameraPreviousYaw = currentYaw;
            this.cameraPreviousPitch = currentPitch;
            this.interpolateCameraThisTick = true;
        }
        mc.player.rotationYaw = newYaw;
        mc.player.rotationPitch = newPitch;
        mc.player.rotationYawHead = newYaw;
        RotationManager.setRotations(currentYaw,currentPitch);
        this.rememberOutput(newYaw, newPitch);
    }

    protected float[] getDesiredRotation(double x, double y, double z) {
        double dx = x - mc.player.getPosX();
        double dy = y - (mc.player.getPosY() + mc.player.getEyeHeight());
        double dz = z - mc.player.getPosZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
                (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F,
                (float) -Math.toDegrees(Math.atan2(dy, horizontal))
        };
    }

    static float accelerate(float velocity, float difference, float maximumSpeed, float acceleration) {
        if (!Float.isFinite(velocity) || !Float.isFinite(difference)) return 0.0F;
        float wanted = MathHelper.clamp(difference * 0.48F, -maximumSpeed, maximumSpeed);
        float maximumChange = Math.max(0.01F, maximumSpeed * acceleration);
        return velocity + MathHelper.clamp(wanted - velocity, -maximumChange, maximumChange);
    }

    private float accelerateAxis(float velocity, float difference, float speed, float acceleration) {
        return accelerate(velocity, difference, speed, acceleration);
    }

    private float limitWithoutOvershoot(float velocity, float difference) {
        if (Math.signum(velocity) != Math.signum(difference)) return 0.0F;
        return Math.abs(velocity) > Math.abs(difference) ? difference : velocity;
    }

    private boolean shouldAssist() {
        Aimbot parent = (Aimbot) this.access();
        return !parent.getBooleanValueFromSettingName("Require click") || mc.gameSettings.keyBindAttack.isKeyDown();
    }

    private boolean detectManualInput(float yaw, float pitch) {
        if (!this.hasOutput) return false;
        float yawInput = Math.abs(MathHelper.wrapDegrees(yaw - this.lastOutputYaw));
        float pitchInput = Math.abs(pitch - this.lastOutputPitch);
        return yawInput > MANUAL_INPUT_THRESHOLD || pitchInput > MANUAL_INPUT_THRESHOLD;
    }

    private void updateAimHeight() {
        if (this.aimDriftTicks-- <= 0) {
            this.aimHeightTarget = this.randomBetween(0.62F, 0.78F);
            this.aimDriftTicks = 8 + this.random.nextInt(13);
        }
        this.aimHeight += (this.aimHeightTarget - this.aimHeight) * 0.08F;
    }

    private float[] updateZitter(float strength) {
        if (strength <= 0.0F) return new float[]{0.0F, 0.0F};
        this.jitterPhase += 0.31F + this.random.nextFloat() * 0.08F;
        if (this.jitterDriftTicks-- <= 0) {
            this.jitterDriftTarget = this.randomBetween(-strength * 0.45F, strength * 0.45F);
            this.jitterDriftTicks = 5 + this.random.nextInt(8);
        }
        this.jitterDrift += (this.jitterDriftTarget - this.jitterDrift) * 0.18F;
        return new float[]{
                (float) Math.sin(this.jitterPhase) * strength * 0.55F + this.jitterDrift,
                (float) Math.cos(this.jitterPhase * 0.73F) * strength * 0.32F
        };
    }

    private float safeSetting(String name, float minimum, float maximum) {
        float value = this.getNumberValueBySettingName(name);
        return Float.isFinite(value) ? MathHelper.clamp(value, minimum, maximum) : minimum;
    }

    private float randomBetween(float minimum, float maximum) {
        return minimum + this.random.nextFloat() * (maximum - minimum);
    }

    private void releaseTarget(float yaw, float pitch) {
        this.target = null;
        this.yawVelocity *= 0.35F;
        this.pitchVelocity *= 0.35F;
        this.rememberOutput(yaw, pitch);
    }

    private void rememberOutput(float yaw, float pitch) {
        this.lastOutputYaw = yaw;
        this.lastOutputPitch = pitch;
        this.hasOutput = true;
    }

    private void restoreCameraInterpolationStart() {
        if (!this.interpolateCameraThisTick) return;
        this.interpolateCameraThisTick = false;
        if (mc.player == null || !Float.isFinite(this.cameraPreviousYaw)
                || !Float.isFinite(this.cameraPreviousPitch)) return;

        mc.player.prevRotationYaw = this.cameraPreviousYaw;
        mc.player.prevRotationPitch = this.cameraPreviousPitch;
    }

    private void resetController() {
        this.target = null;
        this.yawVelocity = 0.0F;
        this.pitchVelocity = 0.0F;
        this.hasOutput = false;
        this.interpolateCameraThisTick = false;
        this.manualOverrideTicks = 0;
        this.reactionTicks = 0;
        this.aimHeight = this.aimHeightTarget = 0.70F;
    }
}
