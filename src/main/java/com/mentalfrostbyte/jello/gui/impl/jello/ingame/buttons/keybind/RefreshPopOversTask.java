package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;

public class RefreshPopOversTask implements Runnable {
   public final KeyboardScreen screen;
   public final KeyboardScreen parent;

   public RefreshPopOversTask(KeyboardScreen screen, KeyboardScreen parent) {
      this.screen = screen;
      this.parent = parent;
   }

   @Override
   public void run() {
      for (CustomGuiScreen child : this.parent.getChildren()) {
         if (child instanceof PopOver popOver) {
            popOver.rebuildEntries();
         }
      }
   }
}
