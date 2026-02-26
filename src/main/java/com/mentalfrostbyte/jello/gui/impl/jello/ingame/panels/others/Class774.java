package com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.others;

import com.mentalfrostbyte.jello.gui.impl.jello.ingame.MapsScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.WaypointPanel;
import net.minecraft.util.math.vector.Vector3i;

public class Class774 implements Runnable {
   public final MapsScreen field4039;
   public final int field4040;
   public final int field4041;
   public final Vector3i field4042;
   public final MapsScreen field4043;

   public Class774(MapsScreen var1, MapsScreen var2, int var3, int var4, Vector3i var5) {
      this.field4043 = var1;
      this.field4039 = var2;
      this.field4040 = var3;
      this.field4041 = var4;
      this.field4042 = var5;
   }

   @Override
   public void run() {
      if (this.field4043.field21041 == null) {
         this.field4039.addToList(this.field4043.field21041 = new WaypointPanel(this.field4039, "popover", this.field4040, this.field4041, this.field4042));
         MapsScreen.method13394(this.field4043, this.field4043.field21041);
      }
   }
}
