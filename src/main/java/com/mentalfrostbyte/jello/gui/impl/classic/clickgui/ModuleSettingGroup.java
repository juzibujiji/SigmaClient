package com.mentalfrostbyte.jello.gui.impl.classic.clickgui;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.panel.ClickGuiPanel;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Class4345;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Exit;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.panel.CategoryPanel;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;

public class ModuleSettingGroup extends ClickGuiPanel {
   public Class4345 field21181;
   public int field21182 = 0;

   public ModuleSettingGroup(CustomGuiScreen var1, String var2, int var3, int var4, ModuleCategory[] var5) {
      super(var1, var2, var3 - 296, var4 - 346, 592, 692);

      for (Module var9 : Client.getInstance().moduleManager.getModuleMap().values()) {
         if (var9.isAvailableOnClassic()) {
            for (ModuleCategory var13 : var5) {
               if (var9.getCategoryBasedOnMode().equals(var13)) {
                  this.method13485(var9);
               }
            }
         }
      }

      Exit var14;
      this.addToList(var14 = new Exit(this, "exit", this.getWidthA() - 47, 18));
      var14.onClick((var1x, var2x) -> {
         if (this.field21181 == null) {
            ((ClassicClickGui)this.getParent()).method13417();
         } else {
            this.field21181.method13556();
         }
      });
      this.setListening(false);
   }

   private void method13485(Module var1) {
      int var4 = this.field21182 % 3;
      int var5 = (int)Math.floor((float)this.field21182 / 3.0F);
      int var6 = 170;
      int var7 = 80;
      this.addToList(new CategoryPanel(this, var1.getName(), 40 + var6 * var4, 72 + var7 * var5, var6, var7, var1));
      this.field21182++;
   }

   public void method13486(Module var1) {
      this.runThisOnDimensionUpdate(() -> {
         if (this.field21181 == null) {
            this.addToList(this.field21181 = new Class4345(this, "settings", 5, 70, this.getWidthA() - 10, this.getHeightA() - 75, var1));
            this.field21181.setReAddChildren(true);
         }
      });
   }

   @Override
   public void draw(float partialTicks) {
      super.draw(partialTicks);
      if (this.field21181 == null) {
         for (CustomGuiScreen var5 : this.getChildren()) {
            if (var5 instanceof CategoryPanel var6 && this.field21149.calcPercent() == 1.0F && var5.method13114(this.getHeightO(), this.getWidthO())) {
				RenderUtil.drawString(Resources.regular17, 20.0F, (float)(this.getHeightA() - 26), var6.module.getDescription(), -14540254);
               RenderUtil.startScissor(5.0F, (float)(this.getHeightA() - 27), 12.0F, 24.0F);
               RenderUtil.drawImage(5.0F, (float)(this.getHeightA() - 27), 24.0F, 24.0F, Resources.xmark);
               RenderUtil.restoreScissor();
               break;
            }
         }
      }
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
      if (this.field21181 != null && this.field21181.method13557()) {
         this.runThisOnDimensionUpdate(() -> {
            this.removeChildren(this.field21181);
            this.field21181 = null;
         });
      }
   }
}
