package com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.others;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.util.client.render.theme.WaypointColors;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.Waypoint;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;

public class BadgeSelect extends Element {
   public int field21296;

   public BadgeSelect(CustomGuiScreen var1, String var2, int var3, int var4) {
      super(var1, var2, var3, var4, 200, 18, ColorHelper.field27961, false);
      int offset = 0;
      boolean var7 = true;

      for (WaypointColors var11 : WaypointColors.values()) {
         String var10004 = "badge" + var11.name;
         offset += 25;
         Waypoint var12;
         this.addToList(var12 = new Waypoint(this, var10004, offset, 0, var11));
         if (var7) {
            var12.field20598 = true;
            this.field21296 = var11.color;
         }

         var12.onClick((var1x, var2x) -> {
            for (CustomGuiScreen var6 : var1x.getParent().getChildren()) {
               if (var6 instanceof Waypoint) {
                  ((Waypoint)var6).field20598 = false;
                  ((Waypoint)var6).field20599.changeDirection(Animation.Direction.BACKWARDS);
               }
            }

            ((Waypoint)var1x).field20598 = true;
            ((Waypoint)var1x).field20599.changeDirection(Animation.Direction.FORWARDS);
            this.field21296 = ((Waypoint)var1x).color.color;
         });
         var7 = false;
      }
   }
}
