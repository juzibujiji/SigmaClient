package com.mentalfrostbyte.jello.gui.impl.classic.clickgui;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Image;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.panel.ClickGuiPanel;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Exit;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class CategoryHolder extends ClickGuiPanel {
   private final List<Button> field21150 = new ArrayList<Button>();
   public Image field21152;
   public Image field21153;
   public Image field21154;
   public Image field21155;
   public Image field21156;
   public Image field21157;

   public CategoryHolder(CustomGuiScreen var1, String var2, int var3, int var4) {
      super(var1, var2, var3 - 198, var4 - 298, 396, 596);
      this.addToList(this.field21152 = new Image(this, "combat", 24, 58, 170, 130, "Combat", Resources.combat, Resources.combat2));
      this.addToList(this.field21153 = new Image(this, "movement", 24, 208, 170, 130, "Movement", Resources.movement, Resources.movement2));
      this.addToList(this.field21157 = new Image(this, "world", 24, 358, 170, 130, "World", Resources.world, Resources.world2));
      this.addToList(this.field21155 = new Image(this, "player", 201, 58, 170, 130, "Player", Resources.player, Resources.player2));
      this.addToList(this.field21156 = new Image(this, "visuals", 201, 208, 170, 130, "Visuals", Resources.visuals, Resources.visuals2));
      this.addToList(this.field21154 = new Image(this, "others", 201, 358, 170, 130, "Others", Resources.others, Resources.others2));
      Exit var7;
      this.addToList(var7 = new Exit(this, "exit", this.getWidthA() - 41, 9));
      var7.onClick((var0, var1x) -> Minecraft.getInstance().displayGuiScreen(null));
      ClassicClickGui var8 = (ClassicClickGui)this.getParent();
      this.field21152.onClick((var1x, var2x) -> var8.method13418("Combat", ModuleCategory.COMBAT));
      this.field21153.onClick((var1x, var2x) -> var8.method13418("Movement", ModuleCategory.MOVEMENT));
      this.field21157.onClick((var1x, var2x) -> var8.method13418("World", ModuleCategory.WORLD));
      this.field21155.onClick((var1x, var2x) -> var8.method13418("Player", ModuleCategory.PLAYER));
      this.field21156.onClick((var1x, var2x) -> var8.method13418("Visuals", ModuleCategory.RENDER, ModuleCategory.GUI));
      this.field21154.onClick((var1x, var2x) -> var8.method13418("Others", ModuleCategory.MISC));
      this.setListening(false);
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
   }
}
