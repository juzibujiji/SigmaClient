package com.mentalfrostbyte.jello.util.system.render;

public class ScreenDimension {
   public int width;
   public int height;

   public ScreenDimension(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public int getWidth() {
      return this.width;
   }

   public ScreenDimension add(ScreenDimension from) {
      return new ScreenDimension(this.width + from.width, this.height + from.height);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         ScreenDimension other = (ScreenDimension)obj;
         return this.width == other.width && this.height == other.height;
      } else {
         return false;
      }
   }
}
