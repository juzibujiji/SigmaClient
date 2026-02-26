package com.mentalfrostbyte.jello.event.impl.player;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
import team.sdhq.eventBus.Event;

public class LivingDeathEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;

    public LivingDeathEvent(LivingEntity entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
    }

    public LivingEntity getEntity() {
        return this.entity;
    }

    public DamageSource getSource() {
        return this.source;
    }
}
