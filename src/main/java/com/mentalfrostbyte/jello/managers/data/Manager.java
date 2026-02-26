package com.mentalfrostbyte.jello.managers.data;

import team.sdhq.eventBus.EventBus;

public class Manager {

    public void init() {
        EventBus.register(this);
    }


}
