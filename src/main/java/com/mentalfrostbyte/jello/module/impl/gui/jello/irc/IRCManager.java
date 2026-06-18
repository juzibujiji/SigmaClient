package com.mentalfrostbyte.jello.module.impl.gui.jello.irc;

import com.mentalfrostbyte.jello.module.impl.gui.jello.IRCClient;

/**
 * IRC管理器
 * 提供全局访问IRC客户端的方法
 */
public class IRCManager {
    private static IRCClient ircClientModule;
    
    /**
     * 设置IRC客户端模块
     */
    public static void setIRCClient(IRCClient client) {
        ircClientModule = client;
    }
    
    /**
     * 获取IRC客户端模块
     */
    public static IRCClient getIRCClient() {
        return ircClientModule;
    }
    
    /**
     * 检查是否已连接
     */
    public static boolean isConnected() {
        return ircClientModule != null && ircClientModule.isEnabled();
    }

    public static boolean shouldNotify() {
        return ircClientModule != null && ircClientModule.isNotificationsEnabled();
    }
    
    /**
     * 发送消息
     */
    public static void sendMessage(String message) {
        if (ircClientModule != null) {
            ircClientModule.sendMessage(IRCChatHistory.sanitizeMessage(message));
        }
    }
}
