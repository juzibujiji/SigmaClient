package com.mentalfrostbyte.jello.module.impl.misc;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DebugSpeed extends Module {
    public int field23557 = 1;
    public Vector3d field23558;

    public DebugSpeed() {
        super(ModuleCategory.MISC, "DebugSpeed", "");
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (this.isEnabled()) {
            Vector3d var4 = new Vector3d(mc.player.getPosX(), 0.0, mc.player.getPosZ());
            if (mc.player.onGround && this.field23558 != null) {
                double var5 = var4.distanceTo(this.field23558) / (double) this.field23557;
                var5 *= mc.timer.timerSpeed;
                boolean var7 = Client.getInstance().playerTracker.getgroundTicks() > 1 && mc.player.jumpTicks == 0;
                double var8 = !var7 ? 0.312948 : 0.280616;
                double var10 = var5 / var8;
                BigDecimal var12 = new BigDecimal(var10);
                BigDecimal var13 = var12.setScale(4, RoundingMode.DOWN);
                float var14 = var13.floatValue();
                if ((double) var14 <= 9.0E-4) {
                    var14 = 0.0F;
                }

                if (var14 == 0.0F) {
                    return;
                }

                String var15 = Float.toString(var14);

                while (var15.length() < 6) {
                    var15 = var15 + "0";
                }

                MinecraftUtil.addChatMessage(
                        Client.getInstance().commandManager.getPrefix() + "(" + var15 + "x faster "
                                + (!var7 ? "jumping" : "walking") + ")  Speed: " + var5);
            }

            this.field23557 = !mc.player.onGround ? this.field23557++ : 1;
            this.field23558 = var4;
        }
    }
}
