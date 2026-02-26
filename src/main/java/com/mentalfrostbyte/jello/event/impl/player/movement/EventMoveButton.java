package com.mentalfrostbyte.jello.event.impl.player.movement;

import team.sdhq.eventBus.Event;

public class EventMoveButton extends Event {
    public boolean forward, back, left, right, jump, sneak;

    public EventMoveButton(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak) {
        this.forward = forward;
        this.back = back;
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.sneak = sneak;
    }
}
