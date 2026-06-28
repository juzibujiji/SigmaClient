package com.mentalfrostbyte.jello.managers;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventLook;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.managers.data.Manager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.movement.CorrectMovement;
import com.mentalfrostbyte.jello.module.impl.movement.Scaffold;
import com.mentalfrostbyte.jello.module.impl.movement.blockfly.BlockFlyScaffoldMode;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import team.sdhq.eventBus.annotations.EventTarget;

public class RotationManager extends Manager implements MinecraftUtil {
    public static void setRotations(final float rotationYaw,final float rotationPitch) {
        RotationCore.currentYaw = rotationYaw;
        RotationCore.currentPitch = rotationPitch;
    }

    @EventTarget
    public void onPre(EventMotion event) {
        if (event.isPre()) {
            if (!Float.isNaN(RotationCore.currentYaw) && !Float.isNaN( RotationCore.currentPitch)) {
                event.setYaw(RotationCore.currentYaw);
                event.setPitch(RotationCore.currentPitch);
            }

            RotationCore.lastYaw = event.getYaw();
            RotationCore.lastPitch = event.getPitch();
        }
    }

    private BlockFlyScaffoldMode getActiveScaffoldMode() {
        Module blockFlyModule = Client.getInstance().moduleManager.getModuleByClass(BlockFly.class);
        if (!(blockFlyModule instanceof BlockFly) || !blockFlyModule.isEnabled()) {
            return null;
        }

        BlockFly blockFly = (BlockFly) blockFlyModule;
        Module currentMode = blockFly.getModWithTypeSetToName();
        if (currentMode instanceof BlockFlyScaffoldMode && currentMode.isEnabled()) {
            return (BlockFlyScaffoldMode) currentMode;
        }

        return null;
    }

    /**
     * Returns the standalone Naven-style {@link Scaffold} when active. This
     * lets the same rotation pipeline (movement-fix, jump-yaw, strafe-yaw,
     * outbound EventMotion) drive both the legacy BlockFly scaffold and the
     * new ported Scaffold module without forcing per-module hooks.
     */
    private Scaffold getActiveStandaloneScaffold() {
        Module scaffoldModule = Client.getInstance().moduleManager.getModuleByClass(Scaffold.class);
        if (scaffoldModule instanceof Scaffold && scaffoldModule.isEnabled()) {
            return (Scaffold) scaffoldModule;
        }
        return null;
    }

    private Float getMovementFixYaw() {
        BlockFlyScaffoldMode scaffoldMode = this.getActiveScaffoldMode();
        if (scaffoldMode != null) {
            return scaffoldMode.rots.yaw;
        }

        Scaffold standaloneScaffold = this.getActiveStandaloneScaffold();
        if (standaloneScaffold != null && standaloneScaffold.rots != null) {
            return standaloneScaffold.rots.yaw;
        }

        if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
            return RotationCore.currentYaw;
        }

        return null;
    }

    @EventTarget
    public void onInput(EventMoveInput event) {
        if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
            MovementUtil.silentStrafe(event, RotationCore.currentYaw);
        }
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
            event.yaw = RotationCore.currentYaw;
        }
    }

    @EventTarget
    public void onLook(EventLook event) {
        if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).getBooleanValueFromSettingName("FixLook") && Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
            event.yaw =  RotationCore.currentYaw;
            event.pitch =  RotationCore.currentPitch;
        }
    }

    @EventTarget
    public void onStrafe(EventMoveFlying event) {
        if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
            event.yaw = RotationCore.currentYaw;
        }
    }
}
