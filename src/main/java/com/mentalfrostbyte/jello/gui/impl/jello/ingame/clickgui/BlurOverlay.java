package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import net.minecraft.client.Minecraft;

public class BlurOverlay extends AnimatedIconPanel {
    public final ClickGuiScreen field21278;

   public BlurOverlay(ClickGuiScreen var1, CustomGuiScreen var2, String var3) {
      super(var2, var3, 0, 0, Minecraft.getInstance().getMainWindow().getWidth(), Minecraft.getInstance().getMainWindow().getHeight(), false);
      this.field21278 = var1;
   }

   @Override
   public void onClick3(int mouseX, int mouseY, int mouseButton) {
      super.onClick3(mouseX, mouseY, mouseButton);
   }
}
