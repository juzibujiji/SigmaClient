package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCUtlis;
import com.mentalfrostbyte.jello.module.settings.impl.InputSetting;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCServerConnection;

public class IRCServer extends Module {
    private final InputSetting serverport = new InputSetting("ServerPort", "Port", "25565");
    private IRCServerConnection serverConnection;

    public IRCServer() {
        super(ModuleCategory.GUI, "IRCServer", "Open a IRCServer");
        registerSetting(serverport);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        try {
            int port = Integer.parseInt(serverport.getCurrentValue());
            serverConnection = new IRCServerConnection(port);
            serverConnection.start();
            IRCUtlis.printMessage("IRC服务器已启动，端口: " + port);
        } catch (NumberFormatException e) {
            IRCUtlis.printMessage("无效的端口号");
            this.toggle();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (serverConnection != null) {
            serverConnection.stop();
            serverConnection = null;
        }
    }
}
