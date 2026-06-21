package com.elfmcys.yesstevemodel.client.animation;

import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

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
    public final boolean attacked;
    public final Pose pose;
    public final float limbSwingAmount;
    public final float ageInTicks;
    public final World world;
    public final double posX;
    public final double posY;
    public final double posZ;
    public final double deltaX;
    public final double deltaY;
    public final double deltaZ;
    public final int cardinalFacing2d;
    public final float headYaw;
    public final float headPitch;
    public final float inputHorizontal;
    public final float inputVertical;
    public final boolean inputJumping;
    public final int foodLevel;

    public final boolean swingInProgress;
    public final Hand swingingHand;
    public final boolean usingItem;
    public final Hand usingHand;

    public final ItemStack mainhand;
    public final ItemStack offhand;
    public final boolean mainhandEmpty;
    public final boolean offhandEmpty;

    private PlayerStateSnapshot(PlayerEntity player, float limbSwingAmount, float ageInTicks,
                                float headYaw, float headPitch) {
        this.uuid = player.getUniqueID();
        this.onGround = player.isOnGround();
        this.sneaking = player.isSneaking();
        this.sprinting = player.isSprinting();
        this.swimming = player.isSwimming();
        this.inWater = player.isInWater();
        this.elytraFlying = player.isElytraFlying();
        this.creativeFlying = player.abilities != null && player.abilities.isFlying;
        this.dead = !player.isAlive() || player.getHealth() <= 0.0F;
        this.attacked = player.hurtTime > 0;
        this.pose = player.getPose();
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.world = player.world;
        this.posX = player.getPosX();
        this.posY = player.getPosY();
        this.posZ = player.getPosZ();
        this.deltaX = player.getPosX() - player.prevPosX;
        this.deltaY = player.getPosY() - player.prevPosY;
        this.deltaZ = player.getPosZ() - player.prevPosZ;
        this.cardinalFacing2d = Direction.fromAngle(player.rotationYaw).getIndex();
        this.headYaw = headYaw;
        this.headPitch = headPitch;
        this.inputHorizontal = player instanceof ClientPlayerEntity
                && ((ClientPlayerEntity) player).movementInput != null
                ? ((ClientPlayerEntity) player).movementInput.moveStrafe
                : player.moveStrafing;
        this.inputVertical = player instanceof ClientPlayerEntity
                && ((ClientPlayerEntity) player).movementInput != null
                ? ((ClientPlayerEntity) player).movementInput.moveForward
                : player.moveForward;
        this.inputJumping = player instanceof ClientPlayerEntity
                && ((ClientPlayerEntity) player).movementInput != null
                ? ((ClientPlayerEntity) player).movementInput.jump
                : !player.isOnGround();
        this.foodLevel = player.getFoodStats() == null ? 20 : player.getFoodStats().getFoodLevel();

        this.swingInProgress = player.isSwingInProgress;
        this.swingingHand = player.swingingHand;
        this.usingItem = player.isHandActive();
        this.usingHand = this.usingItem ? player.getActiveHand() : null;

        this.mainhand = player.getHeldItemMainhand();
        this.offhand = player.getHeldItemOffhand();
        this.mainhandEmpty = this.mainhand == null || this.mainhand.isEmpty();
        this.offhandEmpty = this.offhand == null || this.offhand.isEmpty();
    }

    private PlayerStateSnapshot(Entity entity, float limbSwingAmount, float ageInTicks,
                                float headYaw, float headPitch) {
        this.uuid = entity.getUniqueID();
        this.onGround = entity.isOnGround();
        this.sneaking = false;
        this.sprinting = false;
        this.swimming = false;
        this.inWater = entity.isInWater();
        this.elytraFlying = false;
        this.creativeFlying = false;
        this.dead = !entity.isAlive();
        this.attacked = entity instanceof LivingEntity && ((LivingEntity) entity).hurtTime > 0;
        this.pose = entity.getPose() == null ? Pose.STANDING : entity.getPose();
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.world = entity.world;
        this.posX = entity.getPosX();
        this.posY = entity.getPosY();
        this.posZ = entity.getPosZ();
        this.deltaX = entity.getPosX() - entity.prevPosX;
        this.deltaY = entity.getPosY() - entity.prevPosY;
        this.deltaZ = entity.getPosZ() - entity.prevPosZ;
        this.cardinalFacing2d = Direction.fromAngle(entity.rotationYaw).getIndex();
        this.headYaw = headYaw;
        this.headPitch = headPitch;
        this.inputHorizontal = 0.0F;
        this.inputVertical = 0.0F;
        this.inputJumping = !entity.isOnGround();
        this.foodLevel = 20;

        this.swingInProgress = false;
        this.swingingHand = null;
        this.usingItem = false;
        this.usingHand = null;

        this.mainhand = ItemStack.EMPTY;
        this.offhand = ItemStack.EMPTY;
        this.mainhandEmpty = true;
        this.offhandEmpty = true;
    }

    public static PlayerStateSnapshot capture(LivingEntity entity, float limbSwingAmount, float ageInTicks) {
        return capture(entity, limbSwingAmount, ageInTicks, entity == null ? 0.0F : entity.rotationYawHead,
                entity == null ? 0.0F : entity.rotationPitch);
    }

    public static PlayerStateSnapshot capture(LivingEntity entity, float limbSwingAmount, float ageInTicks,
                                              float headYaw, float headPitch) {
        if (entity instanceof PlayerEntity) {
            return new PlayerStateSnapshot((PlayerEntity) entity, limbSwingAmount, ageInTicks, headYaw, headPitch);
        }
        return null;
    }

    public static PlayerStateSnapshot captureEntity(Entity entity, float limbSwingAmount, float ageInTicks) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            return new PlayerStateSnapshot(player, limbSwingAmount, ageInTicks,
                    player.rotationYawHead, player.rotationPitch);
        }
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            return new PlayerStateSnapshot(entity, limbSwingAmount, ageInTicks,
                    living.rotationYawHead, living.rotationPitch);
        }
        return new PlayerStateSnapshot(entity, limbSwingAmount, ageInTicks, entity.rotationYaw, entity.rotationPitch);
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
