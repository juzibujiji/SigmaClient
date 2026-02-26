package com.mentalfrostbyte.jello.module.impl.combat.wtap;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.math.EntityRayTraceResult;
import team.sdhq.eventBus.annotations.EventTarget;
public class LegitWTap extends Module {
    public LegitWTap() {
        super(ModuleCategory.COMBAT, "Legit", "Increase the knockback you give to players");
    }
    @EventTarget
    public void TickEvent(EventUpdate event) {
        if (this.isEnabled()
                && !Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).isEnabled2()
                && mc.objectMouseOver instanceof EntityRayTraceResult entityRayTrace) {
			if (entityRayTrace.getEntity() != null) {
                mc.gameSettings.keyBindSprint.setPressed(true);

                if (mc.player.swingProgressInt != 1) {
                    if (mc.player.swingProgressInt == 0) {
                        mc.gameSettings.keyBindForward.setPressed(
                                InputMappings.isKeyDown(
                                        Minecraft.getInstance().getMainWindow().getHandle(), mc.gameSettings.keyBindForward.keyCode.getKeyCode()));
                    }
                } else {
                    mc.gameSettings.keyBindForward.setPressed(false);
                }
            }
        }
    }
}
