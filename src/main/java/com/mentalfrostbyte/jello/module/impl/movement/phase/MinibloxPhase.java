package com.mentalfrostbyte.jello.module.impl.movement.phase;

import com.mentalfrostbyte.jello.event.impl.game.world.EventBlockCollision;
import com.mentalfrostbyte.jello.event.impl.game.world.EventPushBlock;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.util.math.shapes.VoxelShapes;
import team.sdhq.eventBus.annotations.EventTarget;

public class MinibloxPhase extends Module {
	public MinibloxPhase() {
		super(ModuleCategory.MOVEMENT, "Miniblox", "Phase for Miniblox");
	}

	@EventTarget
	public void EventPushBlock(EventPushBlock event) {
		if (!this.isEnabled()) {
			return;
		}
		event.cancelled = true;
	}

	@EventTarget
	public void TickEvent(EventBlockCollision event) {
		if (!this.isEnabled() || mc.world == null) {
			return;
		}
		assert mc.player != null;
		if ((double) event.getBlockPos().getY() >= mc.player.getPosY()) {
			event.setVoxelShape(VoxelShapes.empty());
		}
	}

	@EventTarget
	public void TickEvent(EventUpdate event) {
		if (!this.isEnabled()) return;
		assert mc.player != null;
		mc.player.jumpTicks = 3;
		if (mc.player.ticksExisted % 2 == 0) {
			if (!mc.player.isOnGround()) {
				return;
			}
			if (mc.player.isJumping) {
				mc.player
						.setPosition(mc.player.getPosX(), mc.player.getPosY() + 1.0, mc.player.getPosZ());
				return;
			}
			if (mc.player.isSneaking()) {
				mc.player
						.setPosition(mc.player.getPosX(), mc.player.getPosY() - 1.0, mc.player.getPosZ());
			}
		}
	}
}
