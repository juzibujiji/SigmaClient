package com.mentalfrostbyte.jello.event.impl.player;

import com.mentalfrostbyte.jello.event.CancellableEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class EventUpdateHeldItem extends CancellableEvent {
    private final Hand hand;
    private ItemStack item;

    public EventUpdateHeldItem(Hand hand, ItemStack item) {
        this.hand = hand;
        this.item = item;
    }

    public Hand getHand() {
        return this.hand;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }
}
