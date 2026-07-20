package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;

public class OpenBindPickerTask implements Runnable {
   public final KeyboardScreen host;
   public final KeyboardScreen owner;

   public OpenBindPickerTask(KeyboardScreen owner, KeyboardScreen host) {
      this.owner = owner;
      this.host = host;
   }

   @Override
   public void run() {
      for (CustomGuiScreen child : this.host.getChildren()) {
         if (child instanceof PopOver popOver) {
            popOver.rebuildEntries();
            this.owner.field20957.refreshKeyStates();
            popOver.setReAddChildren(true);
            popOver.method13242();
            this.host.method13234(this.owner.field20960);
         }
      }
   }
}
