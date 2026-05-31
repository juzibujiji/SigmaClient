package com.elfmcys.yesstevemodel.client.animation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.UUID;

/**
 * Per-frame immutable snapshot of player state used to evaluate condition predicates.
 * Captured once at the start of each render frame by AnimationRuntime so subsequent
 * lookups within the frame stay consistent.
 */
public final class PlayerStateSnapshot {
    public final UUID uuid;
    public final boolean onGround;
    public final boolean sneaking;
    public final boolean sprinting;
    public final boolean swimming;
    public final boolean inWater;
    public final boolean elytraFlying;
    public final boolean creativeFlying;
    public final boolean dead;
    public final Pose pose;
    public final float limbSwingAmount;
    public final float ageInTicks;

    public final boolean swingInProgress;
    public final Hand swingingHand;
    public final boolean usingItem;
    public final Hand usingHand;

    public final ItemStack mainhand;
    public final ItemStack offhand;
    public final boolean mainhandEmpty;
    public final boolean offhandEmpty;

    private PlayerStateSnapshot(PlayerEntity player, float limbSwingAmount, float ageInTicks) {
        this.uuid = player.getUniqueID();
        this.onGround = player.isOnGround();
        this.sneaking = player.isSneaking();
        this.sprinting = player.isSprinting();
        this.swimming = player.isSwimming();
        this.inWater = player.isInWater();
        this.elytraFlying = player.isElytraFlying();
        this.creativeFlying = player.abilities != null && player.abilities.isFlying;
        this.dead = !player.isAlive() || player.getHealth() <= 0.0F;
        this.pose = player.getPose();
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;

        this.swingInProgress = player.isSwingInProgress;
        this.swingingHand = player.swingingHand;
        this.usingItem = player.isHandActive();
        this.usingHand = this.usingItem ? player.getActiveHand() : null;

        this.mainhand = player.getHeldItemMainhand();
        this.offhand = player.getHeldItemOffhand();
        this.mainhandEmpty = this.mainhand == null || this.mainhand.isEmpty();
        this.offhandEmpty = this.offhand == null || this.offhand.isEmpty();
    }

    public static PlayerStateSnapshot capture(LivingEntity entity, float limbSwingAmount, float ageInTicks) {
        if (entity instanceof PlayerEntity) {
            return new PlayerStateSnapshot((PlayerEntity) entity, limbSwingAmount, ageInTicks);
        }
        return null;
    }

    public ItemStack handStack(Hand hand) {
        return hand == Hand.MAIN_HAND ? this.mainhand : this.offhand;
    }

    public boolean isHandEmpty(Hand hand) {
        return hand == Hand.MAIN_HAND ? this.mainhandEmpty : this.offhandEmpty;
    }

    public boolean isMoving() {
        return Math.abs(this.limbSwingAmount) > 0.05F;
    }
}
