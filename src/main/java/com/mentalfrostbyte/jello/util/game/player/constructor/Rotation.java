package com.mentalfrostbyte.jello.util.game.player.constructor;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class Rotation {
    public float yaw, pitch;
    public float lastYaw, lastPitch;

    public Rotation(float yaw, float pitch) {
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    public Rotation(float yaw, float pitch, float lastYaw, float lastPitch) {
        this.lastYaw = lastYaw;
        this.lastPitch = lastPitch;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    @NotNull
    public static Rotation of(@NotNull Entity entity, float partialTicks) {
        return new Rotation(entity.getYaw(partialTicks), entity.getPitch(partialTicks), entity.prevRotationYaw, entity.prevRotationPitch);
    }
    @NotNull
    public static Rotation of(@NotNull Entity entity) {
        return Rotation.of(entity, 1);
    }
    @NotNull
    public static Rotation of(@NotNull EventRenderEntity event) {
        return new Rotation(event.getYaw(), event.getPitch(), event.getEntity().prevRotationYaw, event.getEntity().prevRotationPitch);
    }
}