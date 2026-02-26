package com.mentalfrostbyte.jello.gui.impl.jello.ingame;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.options.Waypoint2;
import com.mentalfrostbyte.jello.util.system.math.smoothing.EasingFunctions;
import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.MapPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.others.Class774;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.others.Waypoint;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.WaypointPanel;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import org.newdawn.slick.opengl.Texture;
import net.minecraft.client.Minecraft;

import java.util.Date;

public class MapsScreen extends Screen {
   private static final Minecraft field21033 = Minecraft.getInstance();
   private Texture field21034;
   public Date field21035;
   public MapPanel field21036;
   public boolean field21037 = false;
   public boolean field21038;
   public int field21039;
   public boolean field21040;
   public WaypointPanel field21041;
   private boolean field21042;
   private boolean field21043;

   public MapsScreen() {
      super("KeybindManager");
      this.field21035 = new Date();
      int var3 = Math.max(300, Math.min(850, Minecraft.getInstance().getMainWindow().getWidth() - 40));
      int var4 = Math.max(200, Math.min(550, Minecraft.getInstance().getMainWindow().getHeight() - 80));
      this.addToList(this.field21036 = new MapPanel(this, "mapView", (this.widthA - var3) / 2, (this.heightA - var4) / 2, var3, var4));
      this.field21036.field20614.method13080((var2, var3x, var4x, var5) -> this.runThisOnDimensionUpdate(new Class774(this, this, var3x, var4x, var5)));
      this.field21036.field20614.method13082(var1 -> this.method13390());
      RenderUtil2.blur();
   }

   private void method13389(WaypointPanel var1) {
      var1.method13131((var1x, var2, var3, var4) -> {
         this.field21036.waypointList.addWaypoint(var2, var3, var4);
         Client.getInstance().waypointsManager.method29990(new Waypoint2(var2, var3.getX(), var3.getZ(), var4));
         this.method13390();
      });
   }

   private void method13390() {
      MapsScreen var3 = this;

      for (CustomGuiScreen var5 : this.getChildren()) {
         if (var5 instanceof WaypointPanel var6) {
			 this.runThisOnDimensionUpdate(new Waypoint(this, var3, var5));
         }
      }
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
      this.setListening(false);
   }

   @Override
   public int getFPS() {
      return 60;
   }

   @Override
   public void keyPressed(int keyCode) {
      super.keyPressed(keyCode);
      if (keyCode == 256) {
         RenderUtil2.resetShaders();
         field21033.displayGuiScreen(null);
      }
   }

   @Override
   public void draw(float partialTicks) {
      partialTicks = (float)Math.min(200L, new Date().getTime() - this.field21035.getTime()) / 200.0F;
      float var4 = EasingFunctions.easeOutBack(partialTicks, 0.0F, 1.0F, 1.0F);
      this.method13279(0.8F + var4 * 0.2F, 0.8F + var4 * 0.2F);
      float var5 = 0.25F * partialTicks;
      RenderUtil.drawRoundedRect(
         (float)this.xA,
         (float)this.yA,
         (float)(this.xA + this.widthA),
         (float)(this.yA + this.heightA),
              RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), var5)
      );
      super.method13224();
      super.draw(partialTicks);
   }

   // $VF: synthetic method
   public static void method13394(MapsScreen var0, WaypointPanel var1) {
      var0.method13389(var1);
   }
}
