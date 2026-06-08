package com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 聊天消息包
 */
public class PacketChatMessage extends IRCPacket {
    public static final int PACKET_ID = 0x01;
    
    private String username;
    private String message;
    private long timestamp;
    
    public PacketChatMessage() {
    }
    
    public PacketChatMessage(String username, String message) {
        this.username = username;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public int getPacketId() {
        return PACKET_ID;
    }
    
    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(username);
        out.writeUTF(message);
        out.writeLong(timestamp);
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.username = in.readUTF();
        this.message = in.readUTF();
        this.timestamp = in.readLong();
    }
    
    @Override
    public void handle(PacketHandler handler) {
        handler.handleChatMessage(this);
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getMessage() {
        return message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
