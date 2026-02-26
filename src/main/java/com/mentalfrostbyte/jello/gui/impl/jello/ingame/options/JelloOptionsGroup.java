package com.mentalfrostbyte.jello.gui.impl.jello.ingame.options;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Checkbox;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.TextButton;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders.ClickGuiHolder;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders.CreditsHolder;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders.KeyboardHolder;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import net.minecraft.util.text.StringTextComponent;

public class JelloOptionsGroup extends CustomGuiScreen {
   public JelloOptionsGroup(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6) {
      super(var1, var2, var3, var4, var5, var6);
      this.setListening(false);
      ColorHelper var9 = ColorHelper.field27961.clone();
      var9.setPrimaryColor(ClientColors.LIGHT_GREYISH_BLUE.getColor());
      TextButton var10;
      this.addToList(var10 = new TextButton(this, "openKeybinds", var5 / 2 - 300, var6 - 80, 300, 38, var9, "Open Keybind Manager", ResourceRegistry.JelloLightFont24));
      TextButton var11;
      this.addToList(var11 = new TextButton(this, "openGui", var5 / 2, var6 - 80, 300, 38, var9, "Open Jello's Click GUI", ResourceRegistry.JelloLightFont24));
      TextButton var12;
      this.addToList(var12 = new TextButton(this, "credits", var5 / 2 - 100, var6 - 280, 200, 38, var9, "Credits", ResourceRegistry.JelloLightFont18));
      var10.onClick((var0, var1x) -> JelloOptions.showGUI(new KeyboardHolder(new StringTextComponent("Keybind Manager"))));
      var11.onClick((var0, var1x) -> JelloOptions.showGUI(new ClickGuiHolder(new StringTextComponent("Click GUI"))));
      var12.onClick((var0, var1x) -> JelloOptions.showGUI(new CreditsHolder(new StringTextComponent("GuiCredits"))));
      Checkbox var13;
      this.addToList(var13 = new Checkbox(this, "guiBlurCheckBox", var5 / 2 - 70, var6 - 220, 25, 25));
      var13.method13705(Client.getInstance().guiManager.getGuiBlur(), false);
      var13.onPress(var1x -> Client.getInstance().guiManager.setGuiBlur(var13.method13703()));
      Checkbox var14;
      this.addToList(var14 = new Checkbox(this, "guiBlurIngameCheckBox", var5 / 2 + 130, var6 - 220, 25, 25));
      var14.method13705(Client.getInstance().guiManager.getHqIngameBlur(), false);
      var14.onPress(var1x -> Client.getInstance().guiManager.setHqIngameBlur(var14.method13703()));
   }

   @Override
   public void draw(float partialTicks) {
      this.method13463(this.xA + (this.getWidthA() - 202) / 2, this.yA + 10, partialTicks);
      StringBuilder var10000 = new StringBuilder().append("You're currently using Sigma ");
      Client.getInstance();
      String var4 = var10000.append(Client.FULL_VERSION).toString();
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont20,
         (float)(this.xA + (this.getWidthA() - ResourceRegistry.JelloLightFont20.getWidth(var4)) / 2),
         (float)(this.yA + 70),
         var4,
         RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.4F * partialTicks)
      );
      String var5 = "Click GUI is currently bound to: "
         + RenderUtil.getKeyName(Client.getInstance().moduleManager.getKeyManager().getKeybindFor(ClickGuiHolder.class))
         + " Key";
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont20,
         (float)(this.getXA() + (this.getWidthA() - ResourceRegistry.JelloLightFont20.getWidth(var5)) / 2),
         (float)(this.getYA() + this.getHeightA() - 180),
         var5,
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.6F * partialTicks)
      );
      String var6 = "Configure all your keybinds in the keybind manager!";
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont14,
         (float)(this.getXA() + (this.getWidthA() - ResourceRegistry.JelloLightFont14.getWidth(var6)) / 2),
         (float)(this.getYA() + this.getHeightA() - 150),
         var6,
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.4F * partialTicks)
      );
      String var7 = "GUI Blur: ";
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont20,
         (float)(this.getXA() + (this.getWidthA() - ResourceRegistry.JelloLightFont20.getWidth(var7)) / 2 - 114),
         (float)(this.getYA() + this.getHeightA() - 221),
         var7,
         RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.5F * partialTicks)
      );
      String var8 = "GPU Accelerated: ";
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont20,
         (float)(this.getXA() + (this.getWidthA() - ResourceRegistry.JelloLightFont20.getWidth(var8)) / 2 + 52),
         (float)(this.getYA() + this.getHeightA() - 221),
         var8,
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.5F * partialTicks)
      );
      super.draw(partialTicks);
   }

   private void method13463(int var1, int var2, float var3) {
      RenderUtil.drawString(ResourceRegistry.JelloMediumFont40, (float)var1, (float)(var2 + 1), "Jello", RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var3));
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont25, (float)(var1 + 95), (float)(var2 + 14), "for Sigma", RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.86F * var3)
      );
   }
}
