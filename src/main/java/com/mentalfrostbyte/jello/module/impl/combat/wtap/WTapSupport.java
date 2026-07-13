package com.mentalfrostbyte.jello.module.impl.combat.wtap;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.play.client.CUseEntityPacket;

final class WTapSupport {
    private static final float MINIMUM_FORWARD_INPUT = 0.8F;
    private static final int MAXIMUM_TARGET_HURT_TIME = 2;
    private static final double MAXIMUM_TARGET_DISTANCE_SQUARED = 25.0D;

    private WTapSupport() {
    }

    static boolean isValidSprintAttack(Minecraft mc, CUseEntityPacket packet) {
        if (mc.player == null
                || mc.world == null
                || mc.getConnection() == null
                || packet.getAction() != CUseEntityPacket.Action.ATTACK
                || !mc.player.isSprinting()
                || !mc.player.serverSprintState
                || !mc.player.isOnGround()
                || mc.player.isPassenger()
                || mc.player.isInWater()
                || mc.player.isInLava()
                || mc.player.isOnLadder()
                || mc.player.isHandActive()
                || mc.player.movementInput.moveForward <= MINIMUM_FORWARD_INPUT
                || !isForwardPhysicallyDown(mc)
                || isBlockFlyEnabled()) {
            return false;
        }

        Entity target = packet.getEntityFromWorld(mc.world);
        return target instanceof LivingEntity livingTarget
                && target != mc.player
                && livingTarget.isAlive()
                && livingTarget.hurtTime <= MAXIMUM_TARGET_HURT_TIME
                && mc.player.getDistanceSq(target) <= MAXIMUM_TARGET_DISTANCE_SQUARED;
    }

    static boolean canResumeSprint(Minecraft mc) {
        return mc.player != null
                && mc.world != null
                && isForwardPhysicallyDown(mc)
                && mc.player.movementInput.moveForward > MINIMUM_FORWARD_INPUT
                && mc.player.isOnGround()
                && !mc.player.isPassenger()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.isOnLadder()
                && !mc.player.isHandActive()
                && !mc.player.collidedHorizontally
                && (mc.player.getFoodStats().getFoodLevel() > 6 || mc.player.abilities.allowFlying);
    }

    static boolean isForwardPhysicallyDown(Minecraft mc) {
        InputMappings.Input forwardKey = mc.gameSettings.keyBindForward.keyCode;
        if (forwardKey.getType() != InputMappings.Type.KEYSYM) {
            return mc.gameSettings.keyBindForward.isKeyDown();
        }

        return forwardKey.getKeyCode() != InputMappings.INPUT_INVALID.getKeyCode()
                && InputMappings.isKeyDown(mc.getMainWindow().getHandle(), forwardKey.getKeyCode());
    }

    private static boolean isBlockFlyEnabled() {
        Module blockFly = Client.getInstance().moduleManager.getModuleByClass(BlockFly.class);
        return blockFly != null && blockFly.isEnabled2();
    }
}
