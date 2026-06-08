package com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * IRC通信包基类
 * 类似Minecraft的packet系统
 */
public abstract class IRCPacket {
    
    /**
     * 获取包的ID
     */
    public abstract int getPacketId();
    
    /**
     * 将包数据写入输出流
     */
    public abstract void write(DataOutputStream out) throws IOException;
    
    /**
     * 从输入流读取包数据
     */
    public abstract void read(DataInputStream in) throws IOException;
    
    /**
     * 处理包（在接收端执行）
     */
    public abstract void handle(PacketHandler handler);
}
