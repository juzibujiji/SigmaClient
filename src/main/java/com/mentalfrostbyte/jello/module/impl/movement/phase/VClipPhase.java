package com.mentalfrostbyte.jello.module.impl.movement.phase;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Collections;
import java.util.Objects;

public class VClipPhase extends Module {

    private final NumberSetting<Double> blocksToClip;
    private long lastClipTime = 0;

    public VClipPhase() {
        super(ModuleCategory.MOVEMENT, "VClip", "Vclip phase (click shift)");
        registerSetting(this.blocksToClip = new NumberSetting<>(
                "Blocks to clip",
                "yes",
                -4,
                -1e2f,
                1e2f,
                0.02f
        ));
    }

    @EventTarget
    public void onUpdate(EventMotion __) {
        long currentTime = System.currentTimeMillis();
        if (this.isEnabled() && mc.gameSettings.keyBindSneak.isKeyDown() && currentTime - lastClipTime >= 500) {
            lastClipTime = currentTime;
            assert mc.player != null;
            Objects.requireNonNull(mc.getConnection())
                    .handlePlayerPosLook(
                            new SPlayerPositionLookPacket(
                                    mc.player.getPosX(),
                                    mc.player.getPosY() + blocksToClip.currentValue,
                                    mc.player.getPosZ(),
                                    mc.player.rotationYaw,
                                    mc.player.rotationPitch,
                                    Collections.emptySet(),
                                    (int) (2.147483647E9 * Math.random())
                            )
                    );
        }
    }
}
