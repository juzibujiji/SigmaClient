package com.mentalfrostbyte.jello.module.impl.movement.phase;


import com.mentalfrostbyte.jello.event.impl.game.world.EventPushBlock;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.system.other.SimpleEntryPair;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.player.PlayerUtil;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;

public class VanillaPhase extends Module {
    public VanillaPhase() {
        super(ModuleCategory.MOVEMENT, "Vanilla", "Vanilla phase");
    }

    @EventTarget
    public void EventUpdate(EventMotion event) {
        if (this.isEnabled()) {
            if (mc.player.collidedHorizontally) {
                var var4 = PlayerUtil.findCollisionDirection(1.0E-4);
                double var5 = !JelloPortal.getVersion().equalTo(ProtocolVersion.v1_8) ? 1.0E-6 : 0.0625;
                setXZ(event, var4, var5);
            }
        }
    }

    public static void setXZ(EventMotion e, SimpleEntryPair<Direction, Vector3d> pair, double v) {
        if (pair.getKey().getAxis() != Direction.Axis.X) {
            e.setZ(
                    (double) Math.round((pair.getValue().z + 1.1921022E-8) * 10000.0) / 10000.0
                            + (double) pair.getKey().getZOffset() * v
            );
        } else {
            e.setX(
                    (double) Math.round((pair.getValue().x + 1.1921022E-8) * 10000.0) / 10000.0
                            + (double) pair.getKey().getXOffset() * v
            );
        }
    }

    @EventTarget
    public void EventMove(EventMove event) {
        if (this.isEnabled()) {
            if (mc.player.collidedHorizontally || PlayerUtil.isCollidingWithSurroundingBlocks()) {
                MovementUtil.setMotion(event, 0.0);
                MovementUtil.movePlayerInDirection(1.7);
            }
        }
    }

    @EventTarget
    public void EventPushBlock(EventPushBlock event) {
        if (this.isEnabled()) {
            event.cancelled = true;
        }
    }
}
