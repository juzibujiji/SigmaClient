package com.mentalfrostbyte.jello.event.impl.player;

import com.mentalfrostbyte.jello.event.CancellableEvent;

public class EventKeepSprint extends CancellableEvent {
    public boolean greater;

    public EventKeepSprint(boolean greater) {
        this.greater = greater;
    }
}
