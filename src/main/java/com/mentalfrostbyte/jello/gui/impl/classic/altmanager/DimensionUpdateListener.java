package com.mentalfrostbyte.jello.gui.impl.classic.altmanager;

public record DimensionUpdateListener(ClassicAltScreen altScreen) implements Runnable {
   @Override
   public void run() {
      this.altScreen.method13403();
   }
}
