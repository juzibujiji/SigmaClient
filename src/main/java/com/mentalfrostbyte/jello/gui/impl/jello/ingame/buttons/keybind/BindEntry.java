package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;


import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;

import java.util.Date;

public class BindEntry extends Element {
   public BindTarget target;
   public Date removeStart;
   public int keyCode;
   public Date showStart;
   public DeleteButton deleteButton;

   public BindEntry(CustomGuiScreen parent, String name, int x, int y, int width, int height, BindTarget target, int keyCode) {
      super(parent, name, x, y, width, height, false);
      this.addToList(this.deleteButton = new DeleteButton(this, "delete", 200, 20, 20, 20));
      this.deleteButton.onClick((mouseX, mouseY) -> {
         this.removeStart = new Date();
         this.callUIHandlers();
      });
      this.target = target;
      this.keyCode = keyCode;
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
   }

   public void beginRemove() {
      this.setHeightA(0);
      this.showStart = new Date();
   }

   @Override
   public void draw(float partialTicks) {
      if (this.showStart != null) {
         float progress = Animation.calculateProgress(this.showStart, 150.0F);
         progress = QuadraticEasing.easeOutQuad(progress, 0.0F, 1.0F, 1.0F);
         this.setHeightA((int)(55.0F * progress));
         if (progress == 1.0F) {
            this.showStart = null;
         }
      }

      if (this.removeStart != null) {
         float progress = Animation.calculateProgress(this.removeStart, 180.0F);
         progress = QuadraticEasing.easeOutQuad(progress, 0.0F, 1.0F, 1.0F);
         this.setHeightA((int)(55.0F * (1.0F - progress)));
         if (progress == 1.0F) {
            this.removeStart = null;
         }
      }

      RenderUtil.startScissor(this.xA, this.yA, this.xA + this.widthA, this.yA + this.heightA, true);
      RenderUtil.drawString(
         ResourceRegistry.RegularFont20,
         (float)(this.xA + 25),
         (float)this.yA + (float)this.heightA / 2.0F - 17.5F,
         this.target.getDisplayName(),
         RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.6F * partialTicks)
      );
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont12,
         (float)(this.xA + 25),
         (float)this.yA + (float)this.heightA / 2.0F + 7.5F,
         this.target.getCategoryName(),
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.6F * partialTicks)
      );
      this.deleteButton.setYA((int)((float)this.heightA / 2.0F - 7.5F));
      super.draw(partialTicks);
      RenderUtil.restoreScissor();
   }
}
