package com.mentalfrostbyte.jello.gui.impl.classic.clickgui.panel;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.ModuleSettingGroup;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Setting;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Checkbox;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;

public class CategoryPanel extends Element {
   public Module module;

   public CategoryPanel(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, Module var7) {
      super(var1, var2, var3, var4, var5, var6, false);
      this.module = var7;
      Checkbox var10;
      this.addToList(var10 = new Checkbox(this, "enable", 114, 9, 40, 18));
      var10.method13093(var7.isEnabled());
      var10.onPress(var2x -> var7.setEnabled(var10.method13092()));
      if (var7.getSettingMap().size() > 0) {
         Setting var11;
         this.addToList(var11 = new Setting(this, "gear", 132, 32));
         var11.onClick((var2x, var3x) -> ((ModuleSettingGroup)this.getParent()).method13486(var7));
      }
   }

   @Override
   public void draw(float partialTicks) {
      RenderUtil.drawString(
              Resources.regular17,
         (float)(this.xA + 10),
         (float)(this.yA + 8),
         this.module.getFormattedName(),
         RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), !this.module.isEnabled() ? 0.5F : 0.9F)
      );
      RenderUtil.drawString(
              Resources.regular15,
         (float)(this.xA + 15),
         (float)(this.yA + 33),
         "Bind",
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 1.0F)
      );
      RenderUtil.drawString(
              Resources.regular15,
         (float)(this.xA + 15),
         (float)(this.yA + 52),
              RenderUtil.getKeyName(this.module.parseSettingValueToIntBySettingName("Keybind")),
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.7F)
      );
      if (this.module.getSettingMap().size() > 1) {
         RenderUtil.drawString(
                 Resources.regular12,
            (float)(this.xA + 84),
            (float)(this.yA + 34),
            "Settings",
                 RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 1.0F)
         );
      }

      super.draw(partialTicks);
   }
}
