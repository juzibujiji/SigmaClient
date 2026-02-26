package com.mentalfrostbyte.jello.event.impl.game.render;

import com.mentalfrostbyte.jello.event.CancellableEvent;

public class EventRenderChat extends CancellableEvent {
    private int yOffset = 0;

    public int getYOffset() {
        return this.yOffset;
    }

    public void setYOffset(int yOffset) {
        this.yOffset = yOffset;
    }

    public void addOffset(int yOffset) {
        this.yOffset += yOffset;
    }
}
