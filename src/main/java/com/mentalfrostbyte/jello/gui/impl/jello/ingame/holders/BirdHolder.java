package com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

public class BirdHolder extends Screen {

    public BirdHolder(ITextComponent title) {
        super(title);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
