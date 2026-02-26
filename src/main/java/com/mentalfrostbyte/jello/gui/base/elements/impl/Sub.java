package com.mentalfrostbyte.jello.gui.base.elements.impl;

import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.dropdown.Class7262;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class Sub extends Element {
   public static final ColorHelper color = new ColorHelper(1250067, -15329770).setTextColor(ClientColors.DEEP_TEAL.getColor()).method19414(FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);
   public List<String> values;
   public int field21324 = 0;

   public Sub(CustomGuiScreen screen, String iconName, int x, int y, int width, int height, List<String> values, int var8) {
      super(screen, iconName, x, y, width, height, color, false);
      this.values = values;
      this.field21324 = var8;
      this.method13634();
   }

   private void method13634() {
      this.getChildren().clear();
      this.font = ResourceRegistry.JelloLightFont18;

      for (String value : this.values) {
         Button button;
         this.addToList(
            button = new Button(
               this,
               value,
               0,
               0,
               this.getWidthA(),
               this.getHeightA(),
               new ColorHelper(
                  ClientColors.LIGHT_GREYISH_BLUE.getColor(),
                  -1381654,
                  this.textColor.getPrimaryColor(),
                  this.textColor.getPrimaryColor(),
                  FontSizeAdjust.field14488,
                  FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
               ),
               value,
               this.getFont()
            )
         );
         button.method13034(10);
         button.onClick((var2, var3) -> {
            this.method13641(this.values.indexOf(value));
            this.callUIHandlers();
         });
      }

      this.method13246(new Class7262(1));
   }

   private int method13635() {
      return this.getHeightA() * (this.values.size() - 1);
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
   }

   @Override
   public void draw(float partialTicks) {
      RenderUtil.drawRoundedRect(
         (float)this.getXA(),
         (float)this.getYA(),
         (float)(this.getXA() + this.getWidthA()),
         (float)(this.getYA() + this.getHeightA() + this.method13635()),
         RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks)
      );
      RenderUtil.drawRoundedRect(
         (float)this.getXA(),
         (float)this.getYA(),
         (float)this.getWidthA(),
         (float)(this.getHeightA() + this.method13635() - 1),
         6.0F,
         partialTicks * 0.1F
      );
      RenderUtil.drawRoundedRect(
         (float)this.getXA(),
         (float)this.getYA(),
         (float)this.getWidthA(),
         (float)(this.getHeightA() + this.method13635() - 1),
         20.0F,
         partialTicks * 0.2F
      );
      GL11.glPushMatrix();
      super.draw(partialTicks);
      GL11.glPopMatrix();
   }

   public List<String> method13636() {
      return this.values;
   }

   public void method13637(String var1, int var2) {
      this.method13636().add(var2, var1);
      this.method13634();
   }

   public void method13638(String var1) {
      this.method13637(var1, this.values.size());
   }

   public <E extends Enum<E>> void method13639(Class<E> var1) {
      this.values.clear();

      for (Enum var7 : var1.getEnumConstants()) {
         String var8 = var7.toString().substring(0, 1).toUpperCase() + var7.toString().substring(1).toLowerCase();
         this.method13637(var8, var7.ordinal());
      }
   }

   public int method13640() {
      return this.field21324;
   }

   public void method13641(int var1) {
      this.field21324 = var1;
   }

   @Override
   public String getText() {
      return this.method13636().size() <= 0 ? null : this.method13636().get(this.method13640());
   }

   @Override
   public boolean method13114(int var1, int var2) {
      var1 -= this.method13271();
      var2 -= this.method13272();
      return var1 >= -10 && var1 <= this.getWidthA() && var2 >= 0 && var2 <= this.getHeightA() + this.method13635();
   }
}
