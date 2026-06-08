package com.mentalfrostbyte.jello.module.impl.gui.jello.irc;

import com.mentalfrostbyte.jello.util.game.MinecraftUtil;

public class IRCUtlis {

    public static void printMessage(String message) {
        MinecraftUtil.addChatMessage(message);
        System.out.println(message);
    }
}
