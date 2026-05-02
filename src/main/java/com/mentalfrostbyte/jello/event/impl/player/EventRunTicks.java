package com.mentalfrostbyte.jello.event.impl.player;

import com.mentalfrostbyte.jello.event.CancellableEvent;

/**
 * 1.16.5 equivalent of Naven's {@code EventRunTicks}. Fires at the start
 * (PRE) and end (POST) of {@link net.minecraft.client.Minecraft#runTick()}
 * so modules can run logic <em>before</em> {@code processKeyBinds()} dispatches
 * mouse {@link com.mentalfrostbyte.jello.event.impl.game.action.EventClick}s
 * and BEFORE the world / player tick runs.
 *
 * <p>This is the event Naven's {@code Scaffold} hangs its setup logic off of
 * (slot select, baseY tracking, rotation calc, sneak pulse, jump-key
 * management). Without it, scaffold setup would race the click-driven
 * placement logic and place blocks against last-tick rotation.</p>
 */
public class EventRunTicks extends CancellableEvent {
    private final boolean pre;

    public EventRunTicks(boolean pre) {
        this.pre = pre;
    }

    public boolean isPre() {
        return this.pre;
    }
}
