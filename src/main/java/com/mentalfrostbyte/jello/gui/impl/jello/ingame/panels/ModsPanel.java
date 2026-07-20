package com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.base.interfaces.BindSelectionListener;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.gui.base.elements.impl.VerticalScrollBar;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.BindTarget;
import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.system.math.smoothing.EasingFunctions;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class ModsPanel extends Element {
   public Animation openAnimation;
   public int panelY;
   public int panelX;
   public int panelWidth;
   public int panelHeight;
   public String searchQuery;
   public ScrollableContentPanel contentPanel;
   public BindTarget selectedTarget;
   public boolean closing = false;
   private final List<BindSelectionListener> listeners = new ArrayList<>();

   public ModsPanel(CustomGuiScreen parent, String name, int x, int y, int width, int height) {
      super(parent, name, x, y, width, height, false);
      this.panelWidth = 500;
      this.panelHeight = 600;
      this.panelX = (width - this.panelWidth) / 2;
      this.panelY = (height - this.panelHeight) / 2;
      TextField searchField;
      this.addToList(
         searchField = new TextField(
            this, "search", this.panelX + 30, this.panelY + 30 + 50, this.panelWidth - 30 * 2, 60, TextField.field20741, "", "Search..."
         )
      );
      searchField.addChangeListener(text -> {
         this.searchQuery = searchField.getText();
         this.contentPanel.method13512(0);
      });
      searchField.method13242();
      this.addToList(
         this.contentPanel = new ScrollableContentPanel(
            this, "mods", this.panelX + 30, this.panelY + 30 + 120, this.panelWidth - 30 * 2, this.panelHeight - 30 * 2 - 120
         )
      );
      int yOffset = 10;

      for (Entry screenEntry : GuiManager.screenToScreenName.entrySet()) {
         BindTarget target = new BindTarget((Class<? extends Screen>)screenEntry.getKey());
         ColorHelper colorHelper = new ColorHelper(RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.02F), -986896)
            .setTextColor(RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5F))
            .method19412(FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);
         Button button;
         this.contentPanel
            .addToList(
               button = new Button(this.contentPanel, target.getDisplayName(), 0, yOffset++ * 55, this.contentPanel.getWidthA(), 55, colorHelper, target.getDisplayName())
            );
         button.onClick((mouseX, mouseY) -> {
            for (Entry innerEntry : GuiManager.screenToScreenName.entrySet()) {
               BindTarget innerTarget = new BindTarget((Class<? extends Screen>)innerEntry.getKey());
               if (innerTarget.getDisplayName().equals(button.getName()) && !this.closing) {
                  this.selectedTarget = innerTarget;
                  this.closing = true;
                  break;
               }
            }
         });
      }

      yOffset += 50;

      for (Module module : Client.getInstance().moduleManager.getModuleMap().values()) {
         ColorHelper colorHelper = new ColorHelper(16777215, -986896).setTextColor(ClientColors.DEEP_TEAL.getColor()).method19412(FontSizeAdjust.field14488);
         Button button;
         this.contentPanel
            .addToList(
               button = new Button(
                  this.contentPanel, module.getName(), 0, yOffset++ * 40, this.contentPanel.getWidthA(), 40, colorHelper, new BindTarget(module).getDisplayName()
               )
            );
         button.method13034(10);
         button.onClick((mouseX, mouseY) -> {
            for (Module innerModule : Client.getInstance().moduleManager.getModuleMap().values()) {
               if (innerModule.getName().equals(button.getText()) && !this.closing) {
                  this.selectedTarget = new BindTarget(innerModule);
                  this.closing = true;
                  break;
               }
            }
         });
      }

      this.openAnimation = new Animation(200, 120);
      this.setListening(false);
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      if (this.method13212()
         && (newHeight < this.panelX || newWidth < this.panelY || newHeight > this.panelX + this.panelWidth || newWidth > this.panelY + this.panelHeight)) {
         this.closing = true;
      }

      this.openAnimation.changeDirection(this.closing ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
      Map<String, Button> headerButtons = new TreeMap();
      Map<String, Button> startsWithMatches = new TreeMap();
      Map<String, Button> containsMatches = new TreeMap();
      List<Button> hiddenButtons = new ArrayList();

      for (CustomGuiScreen child : this.contentPanel.getChildren()) {
         if (!(child instanceof VerticalScrollBar)) {
            for (CustomGuiScreen subChild : child.getChildren()) {
               if (subChild instanceof Button button) {
				   boolean isHeader = button.getHeightA() != 40;
                  if (!isHeader || this.searchQuery != null && (this.searchQuery == null || this.searchQuery.length() != 0)) {
                     if (!isHeader && this.matchesStartsWith(this.searchQuery, button.getText())) {
                        startsWithMatches.put(button.getText(), button);
                     } else if (!isHeader && this.matchesContains(this.searchQuery, button.getText())) {
                        containsMatches.put(button.getText(), button);
                     } else {
                        hiddenButtons.add(button);
                     }
                  } else {
                     headerButtons.put(button.getText(), button);
                  }
               }
            }
         }
      }

      int yPos = headerButtons.size() <= 0 ? 0 : 10;

      for (Button button : headerButtons.values()) {
         button.setSelfVisible(true);
         button.setYA(yPos);
         yPos += button.getHeightA();
      }

      if (headerButtons.size() > 0) {
         yPos += 10;
      }

      for (Button button : startsWithMatches.values()) {
         button.setSelfVisible(true);
         button.setYA(yPos);
         yPos += button.getHeightA();
      }

      for (Button button : containsMatches.values()) {
         button.setSelfVisible(true);
         button.setYA(yPos);
         yPos += button.getHeightA();
      }

      for (Button button : hiddenButtons) {
         button.setSelfVisible(false);
      }

      super.updatePanelDimensions(newHeight, newWidth);
   }

   private boolean matchesContains(String query, String text) {
      return query == null || query == "" || text == null || text.toLowerCase().contains(query.toLowerCase());
   }

   private boolean matchesStartsWith(String query, String text) {
      return query == null || query == "" || text == null || text.toLowerCase().startsWith(query.toLowerCase());
   }

   @Override
   public void draw(float partialTicks) {
      partialTicks = this.openAnimation.calcPercent();
      float scale = EasingFunctions.easeOutBack(partialTicks, 0.0F, 1.0F, 1.0F);
      if (this.closing) {
         scale = QuadraticEasing.easeOutQuad(partialTicks, 0.0F, 1.0F, 1.0F);
      }

      this.method13279(0.8F + scale * 0.2F, 0.8F + scale * 0.2F);
      if (partialTicks == 0.0F && this.closing) {
         this.notifySelection(this.selectedTarget);
      }

      RenderUtil.drawRoundedRect(
         (float)this.xA,
         (float)this.yA,
         (float)this.widthA,
         (float)this.heightA,
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F * partialTicks)
      );
      super.method13224();
      RenderUtil.drawRoundedRect(
         (float)this.panelX,
         (float)this.panelY,
         (float)this.panelWidth,
         (float)this.panelHeight,
         10.0F,
              RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks)
      );
      RenderUtil.drawString(
         ResourceRegistry.JelloLightFont36,
         (float)(30 + this.panelX),
         (float)(30 + this.panelY),
         "Select mod to bind",
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), partialTicks * 0.7F)
      );
      super.draw(partialTicks);
   }

   public final void addSelectionListener(BindSelectionListener listener) {
      this.listeners.add(listener);
   }

   public final void notifySelection(BindTarget target) {
      for (BindSelectionListener listener : this.listeners) {
         listener.onSelect(this, target);
      }
   }
}
