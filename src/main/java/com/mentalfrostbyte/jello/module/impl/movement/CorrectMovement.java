package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.event.impl.player.EventLook;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HighestPriority;

public class CorrectMovement extends Module {
    public CorrectMovement() {
        super(ModuleCategory.MOVEMENT, "CorrectMovement", "Correct your movement.");
        this.registerSetting(new BooleanSetting("FixLook","FixLook",true));
    }

    /**
     * In RotationManager
     */
    /*
    @EventTarget
    //@HighestPriority
    public void onInput(EventMoveInput event) {
        MovementUtil.silentStrafe(event, RotationCore.currentYaw);
    }

    @EventTarget
    //@HighestPriority
    public void onJump(EventJump event) {
        event.yaw = RotationCore.currentYaw;
    }

    @EventTarget
    //@HighestPriority
    public void onLook(EventLook event) {
        if (this.getBooleanValueFromSettingName("FixLook")) {
            event.yaw = RotationCore.currentYaw;
            event.pitch = RotationCore.currentPitch;
        }
    }

    @EventTarget
    //@HighestPriority
    public void onStrafe(EventMoveFlying event) {
        event.yaw = RotationCore.currentYaw;
    }*/
}