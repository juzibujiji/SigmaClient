package com.mentalfrostbyte.jello.event.impl.game.render;

import com.mentalfrostbyte.jello.event.CancellableEvent;
import net.minecraft.entity.Entity;

public class EventRendererLivingEntity extends CancellableEvent {
    private Entity entity;
    public Entity getEntity() {
        return this.entity;
    }
    public void setEntity(Entity entity) {
        this.entity = entity;
    }
    public boolean pre;
    private float limbSwing;
    public float getLimbSwing() {
        return this.limbSwing;
    }
    public void setLimbSwing(float limbSwing) {
        this.limbSwing = limbSwing;
    }
    private float limbSwingAmount;
    public float getLimbSwingAmount() {
        return this.limbSwingAmount;
    }
    public void setLimbSwingAmount(float limbSwingAmount) {
        this.limbSwingAmount = limbSwingAmount;
    }
    private float ageInTicks;
    public float getAgeInTicks() {
        return this.ageInTicks;
    }
    public void setAgeInTicks(float ageInTicks) {
        this.ageInTicks = ageInTicks;
    }
    private float rotationYawHead;
    public float getRotationYawHead() {
        return this.rotationYawHead;
    }
    public void setRotationYawHead(float rotationYawHead) {
        this.rotationYawHead = rotationYawHead;
    }
    private float rotationPitch;
    public float getRotationPitch() {
        return this.rotationPitch;
    }
    public void setRotationPitch(float rotationPitch) {
        this.rotationPitch = rotationPitch;
    }
    private float chestRot;
    public float getChestRot() {
        return this.chestRot;
    }
    public void setChestRot(float chestRot) {
        this.chestRot = chestRot;
    }
    private float offset;
    public float getOffset() {
        return this.offset;
    }
    public void setOffset(float offset) {
        this.offset = offset;
    }

    private boolean shouldInvisible = false;
    public boolean getShouldInvisible() {
        return this.shouldInvisible;
    }
    public void setShouldInvisible(boolean shouldInvisible) {
        this.shouldInvisible = shouldInvisible;
    }
    private float alpha = 1f;
    public float getAlpha() {
        return this.alpha;
    }
    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }
    private boolean hideLayer = false;
    public boolean getHideLayer() {
        return this.hideLayer;
    }
    public void setHideLayer(boolean hideLayer) {
        this.hideLayer = hideLayer;
    }

    public EventRendererLivingEntity(boolean type, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float rotationYawHead, float rotationPitch, float chestRot, float offset) {
        this.pre = type;
        this.entity = entity;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.rotationYawHead = rotationYawHead;
        this.rotationPitch = rotationPitch;
        this.chestRot = chestRot;
        this.offset = offset;
    }

    public EventRendererLivingEntity(boolean type, Entity entity) {
        this.pre = type;
        this.entity = entity;
    }
}
