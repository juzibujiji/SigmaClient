package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.groups;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.panels.SettingPanel;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.system.math.smoothing.EasingFunctions;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;

public class SettingGroup extends Element {
   public Animation animation1;
   public Animation animation;
   public int y;
   public int x;
   public int width;
   public int height;
   public SettingPanel field20668;
   public final Module module;
   public boolean field20671 = false;

   public SettingGroup(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, Module var7) {
      super(var1, var2, var3, var4, var5, var6, false);
      this.width = 500;
      this.height = (int)Math.min(600.0F, (float)var6 * 0.7F);
      this.x = (var5 - this.width) / 2;
      this.y = (var6 - this.height) / 2 + 20;
      this.module = var7;
      int var10 = 10;
      int var11 = 59;
      this.addToList(
         this.field20668 = new SettingPanel(
            this, "mods", this.x + var10, this.y + var11, this.width - var10 * 2, this.height - var11 - var10, var7
         )
      );
      this.animation1 = new Animation(200, 120);
      this.animation = new Animation(240, 200);
      this.setListening(false);
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      if (this.method13212()
         && (newHeight < this.x || newWidth < this.y || newHeight > this.x + this.width || newWidth > this.y + this.height)) {
         this.field20671 = true;
      }

      this.animation1.changeDirection(this.field20671 ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
      this.animation.changeDirection(this.field20671 ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
      super.updatePanelDimensions(newHeight, newWidth);
   }

   private boolean method13084(String var1, String var2) {
      return var1 == null || var1 == "" || var2 == null || var2.toLowerCase().contains(var1.toLowerCase());
   }

   private boolean method13085(String var1, String var2) {
      return var1 == null || var1 == "" || var2 == null || var2.toLowerCase().startsWith(var1.toLowerCase());
   }

   @Override
   public void draw(float partialTicks) {
      partialTicks = this.animation1.calcPercent();
      float var4 = EasingFunctions.easeOutBack(partialTicks, 0.0F, 1.0F, 1.0F);
      if (this.field20671) {
         var4 = QuadraticEasing.easeOutQuad(partialTicks, 0.0F, 1.0F, 1.0F);
      }

      this.method13279(0.8F + var4 * 0.2F, 0.8F + var4 * 0.2F);
      RenderUtil.drawRoundedRect(
         (float)this.xA,
         (float)this.yA,
         (float)this.widthA,
         (float)this.heightA,
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.45F * partialTicks)
      );
      super.method13224();
      RenderUtil.drawRoundedRect(
         (float)this.x,
         (float)this.y,
         (float)this.width,
         (float)this.height,
         10.0F,
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks)
      );
      RenderUtil.drawString(
         ResourceRegistry.JelloMediumFont40,
         (float)this.x,
         (float)(this.y - 60),
         this.module.getName(),
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks)
      );
      RenderUtil.startScissor((float)this.x, (float)this.y, (float)(this.width - 30), (float)this.height);
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont20,
         (float)(30 + this.x),
         (float)(30 + this.y),
         this.module.getDescription(),
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), partialTicks * 0.7F)
      );
      RenderUtil.restoreScissor();
      super.draw(partialTicks);
   }
}
