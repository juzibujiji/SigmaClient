package com.mentalfrostbyte.jello.event.impl.player;

import net.minecraft.util.math.vector.Vector2f;
import team.sdhq.eventBus.Event;

public class EventLook extends Event {
    public float yaw;
    public float pitch;

    public EventLook(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
