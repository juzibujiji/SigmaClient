package com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 包注册器
 * 管理所有包类型的注册和创建
 */
public class PacketRegistry {
    private static final Map<Integer, Class<? extends IRCPacket>> packetMap = new HashMap<>();
    
    static {
        // 注册所有包类型
        register(PacketKeepAlive.PACKET_ID, PacketKeepAlive.class);
        register(PacketChatMessage.PACKET_ID, PacketChatMessage.class);
        register(PacketLogin.PACKET_ID, PacketLogin.class);
        register(PacketLogout.PACKET_ID, PacketLogout.class);
    }
    
    /**
     * 注册包类型
     */
    public static void register(int id, Class<? extends IRCPacket> packetClass) {
        packetMap.put(id, packetClass);
    }
    
    /**
     * 根据ID创建包实例
     */
    public static IRCPacket createPacket(int id) {
        Class<? extends IRCPacket> packetClass = packetMap.get(id);
        if (packetClass == null) {
            return null;
        }
        
        try {
            return packetClass.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 发送包
     */
    public static void sendPacket(IRCPacket packet, DataOutputStream out) throws IOException {
        out.writeInt(packet.getPacketId());
        packet.write(out);
        out.flush();
    }
    
    /**
     * 接收包
     */
    public static IRCPacket receivePacket(DataInputStream in) throws IOException {
        int packetId = in.readInt();
        IRCPacket packet = createPacket(packetId);
        
        if (packet == null) {
            throw new IOException("Unknown packet ID: " + packetId);
        }
        
        packet.read(in);
        return packet;
    }
}
