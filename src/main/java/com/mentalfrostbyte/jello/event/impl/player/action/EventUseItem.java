package com.mentalfrostbyte.jello.event.impl.player.action;

import team.sdhq.eventBus.Event;

public class EventUseItem extends Event {
    public boolean useItem;

    public EventUseItem(boolean useItem) {
        this.useItem = useItem;
    }
}
