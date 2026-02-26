package com.mentalfrostbyte.jello.event.impl.game;

import com.mentalfrostbyte.jello.event.CancellableEvent;

public class EventReplaceText extends CancellableEvent {
    private String text;

    public EventReplaceText(String var1) {
        this.text = var1;
    }

    public String getText() {
        return this.text;
    }

    public void getText(String var1) {
        this.text = var1;
    }
}
