package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.InputSetting;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCClientConnection;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCManager;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;

public class IRCClient extends Module {
    private final InputSetting ircname = new InputSetting("IRCName", "Your IRC name", "SigmaUser");
    private final InputSetting serverAddress = new InputSetting("ServerIP", "Server IP", "127.0.0.1");
    private final InputSetting serverPort = new InputSetting("ServerPort", "Server port", "25565");
    private IRCClientConnection clientConnection;

    public IRCClient() {
        super(ModuleCategory.GUI, "IRCClient", "Connect IRC Server");
        registerSetting(ircname);
        registerSetting(serverAddress);
        registerSetting(serverPort);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        try {
            String address = serverAddress.getCurrentValue();
            int port = Integer.parseInt(serverPort.getCurrentValue());
            String username = ircname.getCurrentValue();

            clientConnection = new IRCClientConnection(address, port, username);
            clientConnection.connect(); // 异步连接，不会阻塞
            IRCManager.setIRCClient(this);
            MinecraftUtil.addChatMessage("[IRC] Connecting to server...");
        } catch (NumberFormatException e) {
            MinecraftUtil.addChatMessage("[IRC] Invalid port number");
            this.toggle();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (clientConnection != null) {
            clientConnection.disconnect();
            clientConnection = null;
        }
        IRCManager.setIRCClient(null);
    }

    public void sendMessage(String message) {
        if (clientConnection != null && clientConnection.isConnected()) {
            clientConnection.sendMessage(message);
        }
    }
    
    public IRCClientConnection getConnection() {
        return clientConnection;
    }
}
