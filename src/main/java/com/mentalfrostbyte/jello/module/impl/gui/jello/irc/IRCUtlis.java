package com.mentalfrostbyte.jello.module.impl.gui.jello.irc;

import com.mentalfrostbyte.jello.util.game.MinecraftUtil;

public class IRCUtlis {

    public static void printMessage(String message) {
        printMessage(message, true);
    }

    public static void printMessage(String message, boolean addToHistory) {
        String safeMessage = IRCChatHistory.sanitizeMessage(message);
        if (safeMessage.isEmpty()) {
            return;
        }
        if (addToHistory) {
            IRCChatHistory.addSystem(safeMessage);
        }
        MinecraftUtil.addChatMessage(safeMessage);
        System.out.println(safeMessage);
    }
}
