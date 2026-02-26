package com.mentalfrostbyte.jello.gui.impl.classic.mainmenu;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.managers.AnimationManager;
import com.mentalfrostbyte.jello.gui.impl.classic.mainmenu.buttons.Particle;
import com.mentalfrostbyte.jello.util.system.math.RandomIntGenerator;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ParticleOverlay extends AnimatedIconPanel {
   private static String[] field21273;
   private final List<Particle> field21274 = new ArrayList<Particle>();
   private final AnimationManager field21275 = new AnimationManager();
   public RandomIntGenerator field21276 = new RandomIntGenerator();

   public ParticleOverlay(CustomGuiScreen var1, String var2) {
      super(var1, var2, 0, 0, Minecraft.getInstance().getMainWindow().getWidth(), Minecraft.getInstance().getMainWindow().getHeight(), false);
      this.setFocused(false);
      this.setHovered(false);
      this.setReAddChildren(false);
      this.method13294(true);
   }

   @Override
   public void setFocused(boolean focused) {
      super.setFocused(false);
   }

   @Override
   public void updatePanelDimensions(int newHeight, int newWidth) {
      super.updatePanelDimensions(newHeight, newWidth);
   }

   @Override
   public void draw(float partialTicks) {
      this.method13225();
      int var4 = Minecraft.getInstance().getMainWindow().getScaledWidth();
      int var5 = Minecraft.getInstance().getMainWindow().getScaledHeight();
      int var6 = (int)((float)var4 / 4.0F);
      boolean var7 = false;
      if (this.field21274.size() < var6) {
         this.field21274.add(new Particle((float)this.field21276.nextInt(var4), (float)this.field21276.nextInt(var5)));
      }

      while (this.field21274.size() > var6) {
         this.field21274.remove(0);
      }

      if (var7) {
         for (int var8 = 0; var8 < this.field21274.size(); var8++) {
            this.field21274.get(var8).field45023 = (float)this.field21276.nextInt(var4);
            this.field21274.get(var8).field45024 = (float)this.field21276.nextInt(var5);
         }
      }

      this.field21275.update();
      Iterator var10 = this.field21274.iterator();

      while (var10.hasNext()) {
         Particle var9 = (Particle)var10.next();
         var9.method37521();
         if (!(var9.field45023 < -50.0F)
            && !(var9.field45023 > (float)(var4 + 50))
            && !(var9.field45024 < -50.0F)
            && !(var9.field45024 > (float)(var5 + 50))
            && Particle.method37522(var9) != 0.0F) {
            var9.method37519(partialTicks);
         } else {
            var10.remove();
         }
      }

      super.draw(partialTicks);
   }
}
