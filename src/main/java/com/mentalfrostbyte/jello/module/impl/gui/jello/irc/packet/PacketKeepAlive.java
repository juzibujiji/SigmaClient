package com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 心跳包
 * 用于保持连接
 */
public class PacketKeepAlive extends IRCPacket {
    public static final int PACKET_ID = 0x00;
    
    private long timestamp;
    
    public PacketKeepAlive() {
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public int getPacketId() {
        return PACKET_ID;
    }
    
    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeLong(timestamp);
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.timestamp = in.readLong();
    }
    
    @Override
    public void handle(PacketHandler handler) {
        handler.handleKeepAlive(this);
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
