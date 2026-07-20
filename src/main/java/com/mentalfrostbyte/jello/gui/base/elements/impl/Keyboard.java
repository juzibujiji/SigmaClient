package com.mentalfrostbyte.jello.gui.base.elements.impl;

import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.Keys;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;

public class Keyboard extends Element {
   public int selectedKey;

   public Keyboard(CustomGuiScreen parent, String name, int x, int y) {
      super(parent, name, x, y, 1060, 357, false);

      for (Keys key : Keys.values()) {
         KeyButton keyButton;
         this.addToList(
            keyButton = new KeyButton(
               this,
               "KEY_" + key.row + this.getChildren().size(),
               key.getX(),
               key.method9026(),
               key.getY(),
               key.method9029(),
               key.name,
               key.row
            )
         );
         keyButton.onClick((mouseX, mouseY) -> {
            this.selectedKey = keyButton.keyCode;
            this.callUIHandlers();
         });
      }

      this.setListening(false);
   }

   @Override
   public boolean onClick(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton <= 1) {
         return super.onClick(mouseX, mouseY, mouseButton);
      } else {
         this.selectedKey = mouseButton;
         this.callUIHandlers();
         return false;
      }
   }

   @Override
   public void keyPressed(int keyCode) {
      for (Keys key : Keys.values()) {
         if (key.row == keyCode) {
            super.keyPressed(keyCode);
            return;
         }
      }

      this.selectedKey = keyCode;
      this.callUIHandlers();
      super.keyPressed(keyCode);
   }

   public void refreshKeyStates() {
      for (CustomGuiScreen child : this.getChildren()) {
         if (child instanceof KeyButton keyButton) {
			 keyButton.refreshBoundState();
         }
      }
   }

   public int[] getKeyOffset(int keycode) {
      for (Keys key : Keys.values()) {
         if (key.row == keycode) {
            return new int[]{key.getX() + key.getY() / 2, key.method9026() + key.method9029()};
         }
      }

      return new int[]{this.getWidthA() / 2, 20};
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
   }

   @Override
   public void draw(float partialTicks) {
      int x = this.xA - 20;
      int y = this.yA - 20;
      int width = this.widthA + 20 * 2;
      int height = this.heightA + 5 + 20 * 2;
      RenderUtil.drawRoundedRect((float)(x + 14 / 2), (float)(y + 14 / 2), (float)(width - 14), (float)(height - 14), 20.0F, partialTicks * 0.5F);
      RenderUtil.drawRoundedButton((float)x, (float)y, (float)width, (float)height, 14.0F, ClientColors.LIGHT_GREYISH_BLUE.getColor());
      super.draw(partialTicks);
   }
}
