package com.mentalfrostbyte.jello.gui.base.elements.impl.button.types;


import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;

public class EditButton extends AnimatedIconPanel {
   private static String[] field20767;
   public final int field20768;

   public EditButton(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6) {
      super(var1, var2, var3, var4, var5, var6, false);
      this.field20768 = var5;
   }

   @Override
   public void draw(float partialTicks) {
      if (this.getWidthA() != 0) {
         this.method13225();
         float var4 = 1.0F - Math.min(1.0F, Math.max((float)this.getWidthA() / (float)this.field20768, 0.0F));
         RenderUtil.drawRoundedRect2(
            (float)this.xA, (float)this.yA, (float)this.field20768, (float)this.heightA, RenderUtil2.applyAlpha(-3254955, partialTicks)
         );
         super.draw(partialTicks * (1.0F - var4));
         RenderUtil.drawImage(
            0.0F, 0.0F, 20.0F, (float)this.heightA, Resources.shadowRightPNG, RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4 * partialTicks)
         );
      }
   }
}
