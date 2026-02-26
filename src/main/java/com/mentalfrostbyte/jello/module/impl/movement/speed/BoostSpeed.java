package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.module.settings.impl.SubOptionSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.annotations.EventTarget;

public class BoostSpeed extends Module {
    private final BooleanSetting autoJump;
    private final BooleanSetting assumeSprinting;
    private final BooleanSetting ignoreSneaking;
    private final BooleanSetting ignoreInWater;
    private final NumberSetting<Integer> boostAfterTicks;
    private int ticksSinceBoost;
    private final NumberSetting<Double> boostSpeed;
    private final NumberSetting<Double> normalSpeed;

    public BoostSpeed() {
        super(ModuleCategory.MOVEMENT, "Boost", "Boost Speed");
        this.registerSetting(this.autoJump = new BooleanSetting("Auto Jump", "Automatically jumps for you.", true));
        this.registerSetting(new SubOptionSetting(
                    "Ignore Slowdowns",
                    "Ignore slowdowns like sneaking, not sprinting, or being in water",
                    true,
                    this.assumeSprinting = new BooleanSetting("Assume Sprinting", "Assume you are sprinting (broken with TargetStrafe)", false),
                    this.ignoreSneaking = new BooleanSetting("Ignore Sneaking", "Ignore sneaking", true),
                    this.ignoreInWater = new BooleanSetting("Ignore In Water", "Ignore being in water", true)
                )
        );
        this.registerSetting(this.boostAfterTicks = new NumberSetting<>("Boost After Ticks", "Boost after ticks since last boost", 15f, 1f, 40f, 1f));
        this.registerSetting(this.boostSpeed = new NumberSetting<>("Boost Speed", "Boost speed", 1.5f, 1f, 10f, 0.01f));
        this.registerSetting(this.normalSpeed = new NumberSetting<>("Normal Speed", "Normal speed", 1.2f, 1f, 10f, 0.01f));
    }

    @Override
    public void onEnable() {
    }

    @EventTarget
    public void onUpdate(EventMotion event) {
        if (autoJump.currentValue && mc.player.isOnGround()) {
            mc.player.jump();
        }
    }

    @EventTarget
    public void onMove(EventMove event) {
        double calculatedSpeed = MovementUtil.getSmartSpeed();

        if (!mc.player.isSprinting() && assumeSprinting.currentValue) {
            calculatedSpeed += 0.15;
        }

        if (mc.player.isSneaking() && ignoreSneaking.currentValue) {
            calculatedSpeed *= 1.25;
        }

        if (mc.player.isInWater() && ignoreInWater.currentValue) {
            calculatedSpeed *= 1.3;
        }
        if (mc.player.isOnGround() && ticksSinceBoost >= boostAfterTicks.currentValue) {
            calculatedSpeed *= boostSpeed.currentValue;
            ticksSinceBoost = 0;
        } else {
            calculatedSpeed *= normalSpeed.currentValue;
            ticksSinceBoost++;
        }

        MovementUtil.setMotion(event, calculatedSpeed);
    }
}