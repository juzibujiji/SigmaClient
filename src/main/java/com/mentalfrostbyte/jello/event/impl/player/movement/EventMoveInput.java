package com.mentalfrostbyte.jello.event.impl.player.movement;

import team.sdhq.eventBus.Event;

public class EventMoveInput extends Event {
    public float forward, strafe;
    public boolean jumping, sneaking;
    public float sneakFactor;

    public EventMoveInput(float forward, float strafe, boolean jumping, boolean sneaking, float sneakFactor) {
        this.forward = forward;
        this.strafe = strafe;
        this.jumping = jumping;
        this.sneaking = sneaking;
        this.sneakFactor = sneakFactor;
    }
}
