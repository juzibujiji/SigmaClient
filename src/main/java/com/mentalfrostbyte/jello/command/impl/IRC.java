package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.module.impl.gui.jello.IRCClient;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCManager;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;

public class IRC extends Command {
    private static final String[] SUBCOMMANDS = {
            "chat", "connect", "disconnect", "help"
    };

    public IRC() {
        super("irc", "IRC command system");
        this.registerSubCommands(SUBCOMMANDS);
    }

    @Override
    public void run(String message, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length < 1) {
            showHelp();
            return;
        }

        String subCommand = args[0].getArguments().toLowerCase();

        switch (subCommand) {
            case "chat":
            case "c":
                handleChat(args);
                break;
                
            case "connect":
                handleConnect();
                break;
                
            case "disconnect":
            case "dc":
                handleDisconnect();
                break;
                
            case "help":
            case "h":
                showHelp();
                break;
                
            default:
                MinecraftUtil.addChatMessage("[IRC] Unknown command. Use 'irc help' for help");
        }
    }

    private void handleChat(ChatCommandArguments[] args) {
        if (!IRCManager.isConnected()) {
            MinecraftUtil.addChatMessage("[IRC] Not connected to server");
            return;
        }

        if (args.length < 2) {
            MinecraftUtil.addChatMessage("[IRC] Usage: irc chat <message>");
            return;
        }

        // 合并所有参数为消息
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) messageBuilder.append(" ");
            messageBuilder.append(args[i].getArguments());
        }

        String chatMessage = messageBuilder.toString();
        IRCManager.sendMessage(chatMessage);
        //MinecraftUtil.addChatMessage("[IRC] Message sent: " + chatMessage);
    }

    private void handleConnect() {
        if (IRCManager.isConnected()) {
            MinecraftUtil.addChatMessage("[IRC] Already connected to server");
            return;
        }
        MinecraftUtil.addChatMessage("[IRC] Please enable IRC Client module to connect");
    }

    private void handleDisconnect() {
        if (!IRCManager.isConnected()) {
            MinecraftUtil.addChatMessage("[IRC] Not connected to server");
            return;
        }
        IRCClient client = (IRCClient) Client.getInstance().moduleManager.getModuleByClass(IRCClient.class);
        client.enabled = false;
        //MinecraftUtil.addChatMessage("[IRC] Please disable IRC Client module to disconnect");
    }

    private void showHelp() {
        MinecraftUtil.addChatMessage("§6=== IRC Commands ===");
        MinecraftUtil.addChatMessage("§7irc chat <message> §f- Send message to IRC");
        MinecraftUtil.addChatMessage("§7irc connect §f- Connect to IRC server");
        MinecraftUtil.addChatMessage("§7irc disconnect §f- Disconnect from server");
        MinecraftUtil.addChatMessage("§7irc help §f- Show this help message");
    }
}
