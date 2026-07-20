package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.ModsPanel;

public class ShowModsPanelTask implements Runnable {
   public final KeyboardScreen host;
   public final KeyboardScreen owner;

   public ShowModsPanelTask(KeyboardScreen owner, KeyboardScreen host) {
      this.owner = owner;
      this.host = host;
   }

   @Override
   public void run() {
      this.host
         .addToList(
            this.owner.field20960 = new ModsPanel(
               this.host, "mods", 0, 0, KeyboardScreen.method13337(this.owner), KeyboardScreen.method13338(this.owner)
            )
         );
      this.owner.field20960.addSelectionListener((panel, target) -> {
         if (target != null) {
            target.bind(this.owner.field20957.selectedKey);
         }

         KeyboardScreen.method13339(this.owner);
      });
      this.owner.field20960.setReAddChildren(true);
   }
}
