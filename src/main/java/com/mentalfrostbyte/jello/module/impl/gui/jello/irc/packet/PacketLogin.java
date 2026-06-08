package com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 登录包
 */
public class PacketLogin extends IRCPacket {
    public static final int PACKET_ID = 0x02;
    
    private String username;
    private String version;
    
    public PacketLogin() {
    }
    
    public PacketLogin(String username, String version) {
        this.username = username;
        this.version = version;
    }
    
    @Override
    public int getPacketId() {
        return PACKET_ID;
    }
    
    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(username);
        out.writeUTF(version);
    }
    
    @Override
    public void read(DataInputStream in) throws IOException {
        this.username = in.readUTF();
        this.version = in.readUTF();
    }
    
    @Override
    public void handle(PacketHandler handler) {
        handler.handleLogin(this);
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getVersion() {
        return version;
    }
}
