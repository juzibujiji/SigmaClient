package com.mentalfrostbyte.jello.gui.impl.classic.altmanager.submenus;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.gui.impl.classic.altmanager.ClassicAltScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.AltManagerButton;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Input;
import com.mentalfrostbyte.jello.managers.AccountManager;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;

public class EditAltScreen extends Screen {
   public Input field21027;
   public Input field21028;
   public AltManagerButton field21029;
   public AltManagerButton field21030;
   public AccountManager field21031 = Client.getInstance().accountManager;
   private String field21032 = "§7Waiting...";

   public EditAltScreen(Account var1) {
      super("Alt Manager");

      this.setListening(false);
      int var4 = 400;
      int var5 = 114;
      int var6 = (this.getWidthA() - var4) / 2;
      this.addToList(this.field21027 = new Input(this, "username", var6, var5, var4, 45, Input.field20741, "", "New name", ResourceRegistry.DefaultClientFont));
      var5 += 80;
      this.addToList(this.field21028 = new Input(this, "password", var6, var5, var4, 45, Input.field20741, "", "New password", ResourceRegistry.DefaultClientFont));
      var5 += 190;
      this.addToList(this.field21029 = new AltManagerButton(this, "edit", var6, var5, var4, 40, "Edit", ClientColors.MID_GREY.getColor()));
      var5 += 50;
      this.addToList(this.field21030 = new AltManagerButton(this, "back", var6, var5, var4, 40, "Cancel", ClientColors.MID_GREY.getColor()));
      this.field21028.setCensorText(true);
      this.field21028.method13147("*");
      this.field21029.onClick((var2, var3) -> {
         if (this.field21027.getText().length() > 0) {
            if (!this.field21027.getText().equals(var1.getEmail())) {
               var1.setName(this.field21027.getText());
            }

            var1.setEmail(this.field21027.getText());
         }

         var1.setPassword(this.field21028.getText());
         this.field21032 = "Edited!";
      });
      this.field21030.onClick((var0, var1x) -> Client.getInstance().guiManager.handleScreen(new ClassicAltScreen()));
   }

   @Override
   public void draw(float partialTicks) {
      RenderUtil.drawImage(0.0F, 0.0F, (float)this.getWidthA(), (float)this.getHeightA(), Resources.mainmenubackground);
      RenderUtil.drawRoundedRect(0.0F, 0.0F, (float)this.getWidthA(), (float)this.getHeightA(), RenderUtil2.applyAlpha(ClientColors.PALE_RED.getColor(), 0.1F));
      RenderUtil.drawRoundedRect(0.0F, 0.0F, (float)this.getWidthA(), (float)this.getHeightA(), RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.95F));
      RenderUtil.drawString(
         ResourceRegistry.DefaultClientFont, (float)(this.getWidthA() / 2), 20.0F, "Edit Alt", ClientColors.LIGHT_GREYISH_BLUE.getColor(), FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2, FontSizeAdjust.field14488
      );
      RenderUtil.drawString(
         ResourceRegistry.DefaultClientFont,
         (float)(this.getWidthA() / 2),
         40.0F,
         this.field21032,
         ClientColors.LIGHT_GREYISH_BLUE.getColor(),
         FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2,
         FontSizeAdjust.field14488,
         true
      );
      super.draw(partialTicks);
   }

   @Override
   public void keyPressed(int keyCode) {
      super.keyPressed(keyCode);
      if (keyCode == 256) {
         Client.getInstance().guiManager.handleScreen(new ClassicAltScreen());
      }
   }
}
