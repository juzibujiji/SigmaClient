package com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import org.newdawn.slick.opengl.Texture;
import org.lwjgl.opengl.GL11;

public class Image extends Element {
   public Texture field20633;
   public Texture field20634;

   public Image(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, String var7, Texture var8, Texture var9) {
      super(var1, var2, var3, var4, var5, var6, ColorHelper.field27961, var7, false);
      this.field20633 = var8;
      this.field20634 = var9;
   }

   @Override
   public void draw(float partialTicks) {
      GL11.glAlphaFunc(518, 0.1F);
      RenderUtil.drawImage(
         (float)(this.xA + (this.widthA - 64) / 2),
         (float)(this.yA + 10),
         64.0F,
         64.0F,
         !this.method13298() ? this.field20633 : this.field20634,
         ClientColors.LIGHT_GREYISH_BLUE.getColor()
      );

      RenderUtil.drawString(
              Resources.regular25,
         (float)(this.xA + (this.getWidthA() - Resources.regular25.getWidth(this.text)) / 2),
         (float)(this.yA + this.getHeightA() - 50),
         this.text,
         !this.method13298() ? -14869219 : -319475
      );
      super.draw(partialTicks);
   }
}
