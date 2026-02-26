package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.panels;

import com.mentalfrostbyte.jello.gui.base.interfaces.IWidthSetter;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import net.minecraft.client.Minecraft;

public class ModListViewSize implements IWidthSetter {
   @Override
   public void setWidth(CustomGuiScreen forScreen, CustomGuiScreen fromWidthOfThisScreen) {
      forScreen.setXA(0);
      if (fromWidthOfThisScreen == null) {
         forScreen.setWidthA(Minecraft.getInstance().getMainWindow().getWidth());
      } else {
         forScreen.setWidthA(fromWidthOfThisScreen.getWidthA());
      }
   }
}
