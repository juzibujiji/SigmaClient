package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons;

import com.mentalfrostbyte.jello.util.client.render.theme.WaypointColors;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.system.math.smoothing.EasingFunctions;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import org.lwjgl.opengl.GL11;

public class Waypoint extends Button {
   private static String[] field20596;
   public final WaypointColors color;
   public boolean field20598;
   public Animation field20599;

   public Waypoint(CustomGuiScreen var1, String var2, int var3, int var4, WaypointColors color) {
      super(var1, var2, var3, var4, 18, 18);
      this.color = color;
      this.field20599 = new Animation(250, 250);
      this.field20599.changeDirection(Animation.Direction.BACKWARDS);
   }

   @Override
   public void draw(float partialTicks) {
      if (this.field20598 && partialTicks == 1.0F) {
         this.field20599.changeDirection(Animation.Direction.FORWARDS);
      }

      int var4 = (int)(EasingFunctions.easeInOutCustomBack(this.field20599.calcPercent(), 0.0F, 1.0F, 1.0F, 7.0F) * 3.0F);
      RenderUtil.drawCircle(
         (float)(this.xA + this.widthA / 2),
         (float)(this.yA + this.heightA / 2),
         25.0F,
         RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.025F * partialTicks * this.field20599.calcPercent())
      );
      RenderUtil.drawCircle(
         (float)(this.xA + this.widthA / 2),
         (float)(this.yA + this.heightA / 2),
         23.0F,
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.05F * partialTicks * this.field20599.calcPercent())
      );
      RenderUtil.drawCircle(
         (float)(this.xA + this.widthA / 2),
         (float)(this.yA + this.heightA / 2),
         (float)(18 + var4),
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks * this.field20599.calcPercent())
      );
      RenderUtil.drawCircle(
         (float)(this.xA + this.widthA / 2),
         (float)(this.yA + this.heightA / 2),
         (float)(18 - var4),
              RenderUtil2.applyAlpha(this.color.color, partialTicks)
      );
      GL11.glPushMatrix();
      super.drawChildren(partialTicks);
      GL11.glPopMatrix();
   }
}
