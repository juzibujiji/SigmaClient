package com.mentalfrostbyte.jello.event;

import team.sdhq.eventBus.Event;

public class CancellableEvent extends Event {
    public EventState state = EventState.PRE;
    public boolean cancelled;

    public enum EventState {
        PRE, POST
    }
}
