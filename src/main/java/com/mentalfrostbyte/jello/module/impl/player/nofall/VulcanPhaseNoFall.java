package com.mentalfrostbyte.jello.module.impl.player.nofall;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.network.play.client.CPlayerPacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class VulcanPhaseNoFall extends Module {
    private boolean isFalling = false;
    private boolean shouldPhase = false;
    private boolean hasPhased = false;

    public VulcanPhaseNoFall() {
        super(ModuleCategory.PLAYER, "VulcanPhase", "Vulcan Phase NoFall");
    }

    @EventTarget
    public void onUpdate(EventMotion event) {
        if (!this.isEnabled()) return;
        assert mc.player != null;

        if (mc.player.getMotion().y < 0 && !mc.player.onGround && mc.player.fallDistance > 3) {
            isFalling = true;
        }

        if (isFalling && mc.player.onGround) {
            isFalling = false;

            if (!hasPhased) {
                shouldPhase = true;
            }
        }

        if (shouldPhase && !hasPhased) {
            phase();
            shouldPhase = false;
            hasPhased = true;
        }
        hasPhased = false;
    }

    private void phase() {
        double cX = mc.player.getPosX();
        double cY = mc.player.getPosY();
        double cZ = mc.player.getPosZ();
        mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(cX, cY - 0.1, cZ, true));
    }

}
