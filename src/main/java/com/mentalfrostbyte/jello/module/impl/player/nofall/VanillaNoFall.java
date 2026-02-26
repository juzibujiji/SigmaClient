package com.mentalfrostbyte.jello.module.impl.player.nofall;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.network.play.client.CPlayerPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Objects;

public class VanillaNoFall extends Module {
    public final ModeSetting mode;
	private final BooleanSetting resetFallDistance;

	public VanillaNoFall() {
        super(ModuleCategory.PLAYER, "Vanilla", "Vanilla NoFall");
        this.registerSetting(
                this.mode = new ModeSetting(
                        "Method",
                        "How to stop fall damage?",
                        "Modify",
                        "Packet", "Modify"
                )
        );
		this.registerSetting(
				this.resetFallDistance = new BooleanSetting(
						"Reset fall distance",
						"Reset fall distance?",
						false
				)
		);
    }
    @EventTarget
    public void onUpdate(EventMotion event) {
        if (!this.isEnabled()) return;
		assert mc.player != null;
		if (mc.player.getPosY() < 2.0) return;
        if (mc.player.getMotion().y < -0.1 && mc.player.fallDistance >= 2.21) {
            switch (this.mode.currentValue) {
				case "Packet":
					Objects.requireNonNull(mc.getConnection()).sendPacket(new CPlayerPacket(true));
					break;
				case "Modify":
					event.setOnGround(true);
					break;
			}
			if (this.resetFallDistance.currentValue)
				mc.player.fallDistance = 0;
        }
    }

}
