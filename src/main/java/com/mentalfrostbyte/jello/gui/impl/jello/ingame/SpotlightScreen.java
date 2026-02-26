package com.mentalfrostbyte.jello.gui.impl.jello.ingame;

import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.SpotlightDialog;
import net.minecraft.client.Minecraft;

public class SpotlightScreen extends Screen {
   private static final Minecraft field21107 = Minecraft.getInstance();
   public SpotlightDialog field21108;

   public SpotlightScreen() {
      super("Spotlight");
      this.setListening(false);
      int var3 = (this.getWidthA() - 675) / 2;
      this.addToList(this.field21108 = new SpotlightDialog(this, "search", var3, (int)((float)this.heightA * 0.25F), 675, 60, true));
   }

   @Override
   public void keyPressed(int keyCode) {
      super.keyPressed(keyCode);
      if (keyCode == 256) {
         field21107.displayGuiScreen(null);
      }
   }
}
