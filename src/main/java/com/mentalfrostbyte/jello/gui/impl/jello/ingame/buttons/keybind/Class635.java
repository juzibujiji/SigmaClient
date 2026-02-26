package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Keyboard;

public class Class635 implements Runnable {
   private static String[] field3171;
   public final Keyboard field3172;
   public final KeyboardScreen field3173;

   public Class635(KeyboardScreen var1, Keyboard var2) {
      this.field3173 = var1;
      this.field3172 = var2;
   }

   @Override
   public void run() {
      this.field3172.method13104();
   }
}
