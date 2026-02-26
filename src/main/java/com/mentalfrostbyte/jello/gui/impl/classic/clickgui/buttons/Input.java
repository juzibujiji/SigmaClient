package com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import org.newdawn.slick.TrueTypeFont;

public class Input extends TextField {

   public Input(CustomGuiScreen screen, String var2, int var3, int var4, int var5, int var6, ColorHelper var7, String var8, String var9, TrueTypeFont var10) {
      super(screen, var2, var3, var4, var5, var6, var7, var8, var9, var10);
      this.setTextColor(new ColorHelper(var7).setTextColor(ClientColors.LIGHT_GREYISH_BLUE.getColor()));
      this.setRoundedThingy(false);
   }

   @Override
   public void draw(float partialTicks) {
      this.setFont(ResourceRegistry.DefaultClientFont);
      RenderUtil.drawRoundedRect(
         (float)this.xA,
         (float)this.yA,
         (float)(this.xA + this.widthA),
         (float)(this.yA + this.heightA),
         ClientColors.DEEP_TEAL.getColor()
      );
      RenderUtil.method11429(
         (float)(this.xA - 2),
         (float)this.yA,
         (float)(this.xA + this.widthA + 2),
         (float)(this.yA + this.heightA),
         2,
         RenderUtil2.shiftTowardsOther(ClientColors.LIGHT_GREYISH_BLUE.getColor(), ClientColors.DEEP_TEAL.getColor(), 625.0F)
      );
      super.draw(partialTicks);
   }
}
