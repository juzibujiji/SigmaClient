package com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;

public class Exit extends Element {
   public Exit(CustomGuiScreen var1, String var2, int var3, int var4) {
      super(var1, var2, var3, var4, 30, 30, false);
   }

   @Override
   public void draw(float partialTicks) {
      RenderUtil.drawImage((float)this.xA, (float)this.yA, 30.0F, 30.0F, !this.method13298() ? Resources.xmark : Resources.xmark2);
      super.draw(partialTicks);
   }
}
