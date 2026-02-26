package com.mentalfrostbyte.jello.event.impl.player.movement;

import com.mentalfrostbyte.jello.event.CancellableEvent;

public class EventMoveFlying extends CancellableEvent {
    public float yaw;
    public float strafe, forward, friction;

    public EventMoveFlying(float yaw, float strafe, float forward, float friction) {
        this.yaw = yaw;
        this.strafe = strafe;
        this.forward = forward;
        this.friction = friction;
    }
}