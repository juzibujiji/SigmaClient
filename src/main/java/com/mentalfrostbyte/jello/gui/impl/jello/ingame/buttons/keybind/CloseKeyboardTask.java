package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;

public class CloseKeyboardTask implements Runnable {
   public final KeyboardScreen host;
   public final KeyboardScreen owner;

   public CloseKeyboardTask(KeyboardScreen owner, KeyboardScreen host) {
      this.owner = owner;
      this.host = host;
   }

   @Override
   public void run() {
      this.owner.field20957.method13242();
      this.host.clearChildren();
      this.owner.field20961 = 0;
   }
}
