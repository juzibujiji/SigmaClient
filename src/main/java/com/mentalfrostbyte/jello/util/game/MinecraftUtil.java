package com.mentalfrostbyte.jello.util.game;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.util.text.StringTextComponent;

public interface MinecraftUtil {

    Minecraft mc = Minecraft.getInstance();

    /**
     * Adds a chat message to the chat. Not to be confused with com.mentalfrostbyte.jello.util.MultiUtilities#sendChatMessage(java.lang.String)
     * @param text
     */
    static void addChatMessage(String text) {
        StringTextComponent textComp = new StringTextComponent(text);
        mc.ingameGUI.getChatGUI().printChatMessage(textComp);
    }

    static void sendChatMessage(String text) {
        mc.getConnection().sendPacket(new CChatMessagePacket(text));
    }
}
