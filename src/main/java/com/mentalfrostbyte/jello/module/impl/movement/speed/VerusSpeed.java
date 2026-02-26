package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.system.math.counter.TimerUtil;
import net.minecraft.block.BlockState;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Objects;

/**
 * @author alarmingly_good (on discord)
 */
public class VerusSpeed extends Module {
    private final ModeSetting mode;
    private final NumberSetting<Float> damageBoostTime;
    private final NumberSetting<Double> timer;
    private final BooleanSetting doTimer;
    // we should go fast for ~3 seconds or more
    public TimerUtil damageTimer = new TimerUtil();
    public VerusSpeed() {
        super(ModuleCategory.MOVEMENT, "Verus", "Speed for Verus.");
        registerSetting(
                this.mode = new ModeSetting(
                        "Mode", "Mode",0,
                        "Basic", "Low",
                        "Ground", "Glide"
                )
        );
        registerSetting(new BooleanSetting("Damage boost", "Boost on damage", false));
        registerSetting(
                this.damageBoostTime = new NumberSetting<>(
                        "Damage boost time",
                        "How long in seconds to boost after damage?",
                        3,
                        0.05F,
                        11F,
                        0.5F
                )
        );
        registerSetting(
                this.doTimer = new BooleanSetting(
                        "Timer",
                        "Use timer (requires Verus Timer disabler to bypass)",
                        false
                )
        );
        registerSetting(
                this.timer = new NumberSetting<>(
                        "Timer Speed",
                        "Timer speed",
                        1.0f,
                        0.1f,
                        10f,
                        0.1f
                )
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        damageTimer.setElapsedTime((long)(damageBoostTime.currentValue * 1000L) + 3000L);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        damageTimer.stop();
        mc.timer.timerSpeed = 1.0f;
    }

    private double speed = 0;
    private int airTicks = 0;

    public BlockState blockStateUnder() {
		assert mc.player != null;
		BlockPos under = new BlockPos(
                mc.player.getPosX(),
                mc.player.getBoundingBox().minY - 0.5000001D,
                mc.player.getPosZ()
        );
		assert mc.world != null;
		return mc.world.getBlockState(under);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onMotion(EventMotion __) {
        boolean dmgBoost = getBooleanValueFromSettingName("Damage boost");
        if (doTimer.currentValue)
            mc.timer.timerSpeed = timer.currentValue;
        assert mc.player != null;
        switch (mode.currentValue) {
            case "Basic" -> {
                if (!mc.player.onGround) {
                    speed *= 0.9999999999999999D;
                    airTicks++;
                } else {
                    airTicks = 0;
                    speed = 0.377D;
                    if (mc.player.isPotionActive(Effects.SPEED)) {
                        EffectInstance speedEffect = Objects.requireNonNull(mc.player.getActivePotionEffect(Effects.SPEED));
                        speed += speedEffect.getAmplifier() * mc.player.moveStrafing == 0 ? 0.122 : 0.121;
                    }

                    if (!mc.player.isSprinting())
                        speed *= 0.78D;

                    float slipperiness = blockStateUnder().getBlock().getSlipperiness();
                    if (slipperiness != 0.6f)
                        speed += (slipperiness * 0.3652);
                    mc.player.jump();
                }
            }

            case "Low" -> {
                if (!mc.player.onGround) {
                    speed *= 0.9999999999999999D;
                    airTicks++;
                    if (airTicks == 2) {
                        mc.player.setMotion(mc.player.getMotion().x, -0.0784000015258789, mc.player.getMotion().z);
                    }
                } else {
                    airTicks = 0;
                    speed = 0.3772D;

                    EffectInstance speedEffect = mc.player.getActivePotionEffect(Effects.SPEED);
                    if (speedEffect != null) {
                        speed += (speedEffect.getAmplifier() + 1) * 0.018;
                    }

                    if (!mc.player.isSprinting())
                        speed *= 0.78D;

                    float slipperiness = blockStateUnder().getBlock().getSlipperiness();

                    if (slipperiness != 0.6f)
                        speed += (slipperiness * 0.3652);

                    mc.player.jump();
                }
            }

            case "Ground" -> {
                if (mc.player.onGround) {
                    speed = MovementUtil.getSmartSpeed() - (MovementUtil.getSpeed() * 0.2);
                    float slipperiness = blockStateUnder().getBlock().getSlipperiness();

                    if (slipperiness != 0.6f)
                        speed += (slipperiness * 0.35);

                    MovementUtil.strafe(speed);
                }
            }

            case "Glide" -> {
                if (!mc.player.onGround) {
                    speed *= 0.9999999999999999D;
                    airTicks++;
                } else {
                    airTicks = 0;
                    if (mc.player.isPotionActive(Effects.SPEED)) {
                        speed = 0.498D;
                    } else {
                        speed = 0.377D;
                    }

                    float slipperiness = blockStateUnder().getBlock().getSlipperiness();
                    if (slipperiness != 0.6f)
                        speed += (slipperiness * 0.37);

                    mc.player.jump();
                }

                if (!mc.player.isSprinting())
                    speed *= 0.78D;

                mc.player.setMotion(
                        mc.player.getMotion().x,
                        Math.max(mc.player.getMotion().y, -0.09800000190734863),
                        mc.player.getMotion().z
                );
            }
        }

        if (mc.player.hurtTime > 0 && dmgBoost) {
            damageTimer.reset();
            damageTimer.start();
        }

        if ((mc.player.hurtTime > 0 || damageTimer.getElapsedTime() <= damageBoostTime.currentValue * 1e3) && dmgBoost) {
            boolean groundMode = mode.currentValue.equals("Ground");
            MovementUtil.strafe(groundMode ? 1.2 : 0.86);
            if (groundMode)
                mc.player.setMotion(mc.player.getMotion().x, -1, mc.player.getMotion().z);
        }
        else
            MovementUtil.strafe(speed);
    }
}
