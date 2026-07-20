package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Keyboard;

public class RefreshKeyStatesTask implements Runnable {
   public final Keyboard keyboard;
   public final KeyboardScreen screen;

   public RefreshKeyStatesTask(KeyboardScreen screen, Keyboard keyboard) {
      this.screen = screen;
      this.keyboard = keyboard;
   }

   @Override
   public void run() {
      this.keyboard.refreshKeyStates();
   }
}
