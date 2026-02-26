package com.mentalfrostbyte.jello.event.impl.game.render;

import com.mentalfrostbyte.jello.event.CancellableEvent;

public class EventRenderGUI extends CancellableEvent {
    public boolean pre;

    public EventRenderGUI(boolean pre) {
        this.pre = pre;
    }
}
