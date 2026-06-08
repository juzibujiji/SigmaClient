package com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 登出包
 */
public class PacketLogout extends IRCPacket {
    public static final int PACKET_ID = 0x03;
    
    private String username;
    private String reason;
    
    public PacketLogout() {
    }
    
    public PacketLogout(String username, String reason) {
        this.username = username;
        this.reason = reason;
    }
    
    @Override
    public int getPacketId() {
        return PACKET_ID;
    }
    
    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(username);
        out.writeUTF(reason != null ? reason : "");
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.username = in.readUTF();
        this.reason = in.readUTF();
    }
    
    @Override
    public void handle(PacketHandler handler) {
        handler.handleLogout(this);
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getReason() {
        return reason;
    }
}
