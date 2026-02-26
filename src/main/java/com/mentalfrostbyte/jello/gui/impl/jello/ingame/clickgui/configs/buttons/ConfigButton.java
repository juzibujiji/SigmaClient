package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.configs.buttons;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;

public class ConfigButton extends Button {
   private static String[] field20593;

   public ConfigButton(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, ColorHelper var7, String var8) {
      super(var1, var2, var3, var4, var5, var6, var7, var8);
   }

   @Override
   public void draw(float partialTicks) {
      this.method13260().get(0).setWidth(this, this.parent);
      super.draw(partialTicks);
   }
}
