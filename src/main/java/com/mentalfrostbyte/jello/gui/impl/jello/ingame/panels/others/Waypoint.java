package com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.others;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.MapsScreen;

public class Waypoint implements Runnable {
   private static String[] field2986;
   public final MapsScreen field2987;
   public final CustomGuiScreen field2988;
   public final MapsScreen field2989;

   public Waypoint(MapsScreen var1, MapsScreen var2, CustomGuiScreen var3) {
      this.field2989 = var1;
      this.field2987 = var2;
      this.field2988 = var3;
   }

   @Override
   public void run() {
      this.field2987.removeChildren(this.field2988);
      this.field2987.field21041 = null;
   }
}
