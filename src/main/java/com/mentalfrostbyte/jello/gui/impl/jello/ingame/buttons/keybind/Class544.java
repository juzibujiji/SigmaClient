package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;

public class Class544 implements Runnable {
   private static String[] field2605;
   public final KeyboardScreen field2606;
   public final KeyboardScreen field2607;

   public Class544(KeyboardScreen var1, KeyboardScreen var2) {
      this.field2607 = var1;
      this.field2606 = var2;
   }

   @Override
   public void run() {
      for (CustomGuiScreen var4 : this.field2606.getChildren()) {
         if (var4 instanceof PopOver var5) {
			 var5.method13712();
         }
      }
   }
}
