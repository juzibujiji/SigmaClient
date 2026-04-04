package com.mentalfrostbyte.jello.event.impl.game.network;

import com.mentalfrostbyte.jello.event.CancellableEvent;
import net.minecraft.network.IPacket;

public class EventGlobalReceivePacket extends CancellableEvent {
    public IPacket<?> packet;

    public EventGlobalReceivePacket(IPacket<?> packet) {
        this.packet = packet;
    }
}

