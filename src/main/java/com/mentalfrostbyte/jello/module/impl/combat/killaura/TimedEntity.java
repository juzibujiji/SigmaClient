package com.mentalfrostbyte.jello.module.impl.combat.killaura;


import net.minecraft.entity.Entity;

public class TimedEntity {
    private final Entity entity;
    private final ExpirationTimer expirationTimer;

    public TimedEntity(Entity entity) {
        this.entity = entity;
        this.expirationTimer = null;
    }

    public TimedEntity(Entity entity, ExpirationTimer timer) {
        this.entity = entity;
        this.expirationTimer = timer;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public boolean isExpired() {
        return this.expirationTimer != null && this.expirationTimer.hasExpired();
    }

    public ExpirationTimer getTimer() {
        return this.expirationTimer;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != this) {
            return obj instanceof TimedEntity && ((TimedEntity) obj).getEntity() == this.getEntity();
        } else {
            return true;
        }
    }
}