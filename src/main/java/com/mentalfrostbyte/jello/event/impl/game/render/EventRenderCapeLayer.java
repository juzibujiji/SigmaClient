package com.mentalfrostbyte.jello.event.impl.game.render;

import team.sdhq.eventBus.Event;

public class EventRenderCapeLayer extends Event {
    public float factor1, factor2;

    public EventRenderCapeLayer(float factor1, float factor2) {
        this.factor1 = factor1;
        this.factor2 = factor2;
    }
}