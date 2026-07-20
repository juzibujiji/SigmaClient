package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;

public class DeleteButton extends Element {
   public float hoverAnim;

   public DeleteButton(CustomGuiScreen parent, String name, int x, int y, int width, int height) {
      super(parent, name, x, y, width, height, false);
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
      this.hoverAnim = this.hoverAnim + (!this.method13298() ? -0.14F : 0.14F);
      this.hoverAnim = Math.min(Math.max(0.0F, this.hoverAnim), 1.0F);
   }

   @Override
   public void draw(float partialTicks) {
      RenderUtil.drawCircle(
         (float)(this.xA + this.widthA / 2),
         (float)(this.yA + this.heightA / 2),
         (float)this.widthA,
         RenderUtil2.applyAlpha(ClientColors.PALE_YELLOW.getColor(), (0.5F + this.hoverAnim * 0.3F + (!this.field20909 ? 0.0F : 0.2F)) * partialTicks)
      );
      RenderUtil.drawRoundedRect2(
         (float)(this.xA + (this.widthA - 10) / 2),
         (float)(this.yA + this.heightA / 2 - 1),
         10.0F,
         2.0F,
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.75F * partialTicks)
      );
      super.draw(partialTicks);
   }
}
