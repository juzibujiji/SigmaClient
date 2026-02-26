package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;

public class SpotlightDialog extends Element {
   public TextField query;
   public String field20640;

   public SpotlightDialog(CustomGuiScreen screen, String iconName, int var3, int var4, int width, int height, boolean var7) {
      super(screen, iconName, var3, var4, width, height, var7);
      this.addToList(this.query = new TextField(this, "search", 50, 0, width - 60, height - 2, TextField.field20741, "", "Search..."));
      this.query.setRoundedThingy(false);
      this.query.addChangeListener(var1x -> this.field20640 = this.query.getText());
   }

   @Override
   public void draw(float partialTicks) {
      this.query.setFocused(true);
      int var4 = 10;
      RenderUtil.drawRoundedRect(
         (float)(this.xA + var4 / 2),
         (float)(this.yA + var4 / 2),
         (float)(this.widthA - var4),
         (float)(this.heightA - var4),
         9.0F,
         partialTicks * 0.9F
      );
      RenderUtil.drawRoundedRect(
         (float)(this.xA + var4 / 2),
         (float)(this.yA + var4 / 2),
         (float)(this.widthA - var4),
         (float)(this.heightA - var4),
         30.0F,
         partialTicks * 0.4F
      );
      RenderUtil.drawRoundedRect(
         (float)this.xA,
         (float)this.yA,
         (float)this.widthA,
         (float)this.heightA,
         (float)var4,
         RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.97F)
      );
      RenderUtil.drawImage(
         (float)(this.xA + 20),
         (float)(this.yA + 20),
         20.0F,
         20.0F,
         Resources.searchPNG,
         RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F)
      );
      ArrayList<Module> var5 = this.method13064();
      if (!var5.isEmpty() && this.method13067(this.field20640, var5.get(0).getName())) {
         String var6 = var5.get(0).getName();
         String var7 = this.field20640
            + var5.get(0).getName().substring(this.field20640.length(), var6.length())
            + (!var5.get(0).isEnabled() ? " - Disabled" : " - Enabled");
         RenderUtil.drawString(
            this.query.getFont(),
            (float)(this.xA + 54),
            (float)(this.yA + 14),
            var7,
                 RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.25F)
         );
      }

      super.draw(partialTicks);
   }

   public ArrayList<Module> method13064() {
      ArrayList<Module> var3 = new ArrayList<>();
      if (this.field20640 != null && !this.field20640.isEmpty()) {
         for (Module var5 : Client.getInstance().moduleManager.getModuleMap().values()) {
            if (this.method13067(this.field20640, var5.getName())) {
               var3.add(var5);
            }
         }

         return var3;
      } else {
         return var3;
      }
   }

   @Override
   public void keyPressed(int keyCode) {
      super.keyPressed(keyCode);
      if (keyCode == 257) {
         ArrayList var4 = this.method13064();
         if (var4.size() > 0) {
            ((Module)var4.get(0)).toggle();
         }

         Minecraft.getInstance().displayGuiScreen(null);
      }
   }

   private boolean method13066(String var1, String var2) {
      return var1 == null || var1 == "" || var2 == null || var2.toLowerCase().contains(var1.toLowerCase());
   }

   private boolean method13067(String var1, String var2) {
      return var1 == null || var1 == "" || var2 == null || var2.toLowerCase().startsWith(var1.toLowerCase());
   }
}
