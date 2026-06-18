package com.mentalfrostbyte.jello.gui.impl.irc;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

public class IRCChatHolder extends Screen {
    public IRCChatHolder(ITextComponent title) {
        super(title);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
