package com.mentalfrostbyte.jello.event.impl.game.render;

import com.mentalfrostbyte.jello.event.CancellableEvent;
import net.minecraft.entity.LivingEntity;

public class EventRenderEntity extends CancellableEvent {

    private float yawOffset;
    private float headYaw;
    private float yaw;
    private float pitch;
    private final float partialTicks;
    private final LivingEntity entity;
    private boolean field21533 = true;
    private boolean field21534 = true;
    private RenderState state;

    public EventRenderEntity(float yawOffset, float headYaw, float yaw, float pitch, float partialTicks, LivingEntity entity) {
        this.yawOffset = yawOffset;
        this.headYaw = headYaw;
        this.yaw = yaw;
        this.pitch = pitch;
        this.partialTicks = partialTicks;
        this.entity = entity;
        this.state = RenderState.PRE;
    }

    public void setState(RenderState state) {
        this.state = state;
    }

    public RenderState getState() {
        return this.state;
    }

    public float getYawOffset() {
        return this.yawOffset;
    }

    public float getHeadYaw() {
        return this.headYaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public float getPartialTicks() {
        return this.partialTicks;
    }

    public void setYawOffset(float yawOffset) {
        this.yawOffset = yawOffset;
    }

    public void setHeadYaw(float headYaw) {
        this.headYaw = headYaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public LivingEntity getEntity() {
        return this.entity;
    }

    public boolean method13954() {
        return this.field21533;
    }

    public void method13955(boolean var1) {
        this.field21534 = var1;
    }

    public boolean method13956() {
        return this.field21534;
    }

    public void method13957(boolean var1) {
        this.field21533 = var1;
    }

    public enum RenderState {
        PRE,
        MID,
        POST
    }
}
