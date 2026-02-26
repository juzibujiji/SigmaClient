package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import net.minecraft.client.settings.KeyBinding;
import team.sdhq.eventBus.annotations.EventTarget;

public class VulcanSpeed extends Module {
    public VulcanSpeed() {
        super(ModuleCategory.MOVEMENT, "Vulcan", "Vulcan speed");
    }

    private int offGroundTicks;
    private int jumpCount = 0;

    @EventTarget
    public void onEvent(EventMotion event) {
        if (mc.player.onGround) {
            offGroundTicks = 0;
        } else {
            offGroundTicks++;
        }

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true);

        if (mc.player.onGround && MovementUtil.isMoving() && this.isEnabled()) {
            mc.player.jump();
            jumpCount++;
            MovementUtil.strafe(0.48);
            if (jumpCount % 6 == 0) {
                mc.timer.timerSpeed = 1.1F; // Boost timer on the 5th jump
            } else {
                mc.timer.timerSpeed = 1.05F; // Normal timer speed otherwise

            }
        }

        if (offGroundTicks < 4) {
            MovementUtil.strafe();
        }
    }

    @Override
    public void onDisable() {
        mc.timer.timerSpeed = 1.0F;
        jumpCount = 0;
    }
}
