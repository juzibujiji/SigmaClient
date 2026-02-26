package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.snake;

import com.mentalfrostbyte.jello.util.system.render.ScreenDimension;

public class Class8437 {
   private static String[] field36150;

   public static ScreenDimension method29649(Class2097 var0) {
      switch (Class7463.field32099[var0.ordinal()]) {
         case 1:
            return new ScreenDimension(0, -1);
         case 2:
            return new ScreenDimension(0, 1);
         case 3:
            return new ScreenDimension(-1, 0);
         default:
            return new ScreenDimension(1, 0);
      }
   }
}
