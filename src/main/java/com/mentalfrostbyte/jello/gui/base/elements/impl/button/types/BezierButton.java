package com.mentalfrostbyte.jello.gui.base.elements.impl.button.types;

import com.mentalfrostbyte.jello.gui.base.elements.impl.Bezier;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;

public class BezierButton extends AnimatedIconPanel {
   public Bezier field20737;

   public BezierButton(Bezier var1, int var2, String var3) {
      super(var1, "bezierButton-" + var3, 0, 0, var2, var2, true);
      this.method13215(true);
      this.field20886 = true;
      this.field20737 = var1;
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
      int var5 = this.field20737.getWidthA() - this.field20737.field20610;
      int var6 = this.field20737.getHeightA() - this.getHeightA();
      int var7 = this.field20737.field20610;
      if (this.getXA() > var5) {
         this.setXA(var5);
      }

      if (this.getYA() > var6) {
         this.setYA(var6);
      }

      if (this.getXA() < var7) {
         this.setXA(var7);
      }
   }

   public void method13144(float var1, float var2) {
      this.xA = (int)var1;
      this.yA = (int)var2;
   }

   @Override
   public void draw(float partialTicks) {
      RenderUtil.drawCircle(
         (float)(this.xA + 5),
         (float)(this.yA + 5),
         10.0F,
         RenderUtil2.applyAlpha(!this.method13216() ? ClientColors.DARK_GREEN.getColor() : ClientColors.DARK_BLUE_GREY.getColor(), partialTicks)
      );
      super.draw(partialTicks);
   }
}
