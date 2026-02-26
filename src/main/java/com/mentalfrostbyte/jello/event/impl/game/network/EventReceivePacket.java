package com.mentalfrostbyte.jello.event.impl.game.network;

import com.mentalfrostbyte.jello.event.CancellableEvent;
import net.minecraft.network.IPacket;

public class EventReceivePacket extends CancellableEvent {
    public IPacket<?> packet;

    public EventReceivePacket(IPacket<?> packet) {
        this.packet = packet;
    }
}

