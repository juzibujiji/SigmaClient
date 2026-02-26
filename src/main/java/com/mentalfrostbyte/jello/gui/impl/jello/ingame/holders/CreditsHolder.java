package com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

public class CreditsHolder extends Screen {
   private static String[] field6188;

   public CreditsHolder(ITextComponent var1) {
      super(var1);
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }
}
