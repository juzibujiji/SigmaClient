package com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet;

/**
 * 包处理器接口
 * 用于处理不同类型的包
 */
public interface PacketHandler {
    
    /**
     * 处理聊天消息包
     */
    void handleChatMessage(PacketChatMessage packet);
    
    /**
     * 处理登录包
     */
    void handleLogin(PacketLogin packet);
    
    /**
     * 处理登出包
     */
    void handleLogout(PacketLogout packet);
    
    /**
     * 处理心跳包
     */
    void handleKeepAlive(PacketKeepAlive packet);
}
