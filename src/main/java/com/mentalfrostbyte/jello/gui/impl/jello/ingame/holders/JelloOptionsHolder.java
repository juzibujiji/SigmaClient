package com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

public class JelloOptionsHolder extends Screen {
   public JelloOptionsHolder() {
      super(new StringTextComponent("Jello Options"));
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }
}
