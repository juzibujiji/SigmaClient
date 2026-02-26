package com.mentalfrostbyte.jello.module.impl.movement.spider;

import com.mentalfrostbyte.jello.event.impl.game.world.EventBlockCollision;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.impl.movement.phase.VanillaPhase;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import com.mentalfrostbyte.jello.util.system.other.SimpleEntryPair;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.util.game.player.PlayerUtil;
import net.minecraft.network.play.client.CPlayerPacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class MinemenSpider extends Module {
    private boolean field23813 = false;

    public MinemenSpider() {
        super(ModuleCategory.MOVEMENT, "Minemen", "Minemen spider");
        this.registerSetting(new BooleanSetting("AutoClimb", "Automatically climbs for you", true));
        this.registerSetting(new BooleanSetting("Ceiling", "Allows you to fly under ceiling", true));
        this.registerSetting(new BooleanSetting("SneakVClip", "Allows you to phase through ground", true));
    }

    @Override
    public void onEnable() {
        this.field23813 = false;
    }

    @EventTarget
    public void EventMove(EventMove event) {
        double var4 = 1.0E-5;
        boolean world = mc.world
                .getCollisionShapes(mc.player, mc.player.boundingBox.expand(var4, 0.0, var4).expand(-var4, 0.0, -var4))
                .count()
                > 0L;
        if (world) {
            if (!mc.player.collidedHorizontally) {
                if (!mc.player.onGround) {
                    event.setY(!mc.gameSettings.keyBindSneak.isKeyDown() ? 0.0 : event.getY());
                }
            } else if (!this.getBooleanValueFromSettingName("AutoClimb") && !mc.gameSettings.keyBindJump.isKeyDown()) {
                event.setY(!mc.gameSettings.keyBindSneak.isKeyDown() ? 0.0 : event.getY());
            } else {
                event.setY(0.6);
            }

            MovementUtil.setMotion(event, 0.689 + (double) MovementUtil.getSpeedBoost() * 0.06);
        }

        if (BlockUtil.isAboveBounds(mc.player, 0.001F) && this.getBooleanValueFromSettingName("SneakVClip")) {
            if (mc.gameSettings.keyBindSneak.isKeyDown()
                    && !this.field23813
                    && mc.world.getCollisionShapes(mc.player, mc.player.boundingBox.offset(0.0, -2.8, 0.0)).count() == 0L) {
                mc.getConnection()
                        .sendPacket(
                                new CPlayerPacket.PositionPacket(
                                        mc.player.getPositionVec().x,
                                        mc.player.getPositionVec().y - 1.0E-14,
                                        mc.player.getPositionVec().z,
                                        false
                                )
                        );
                mc.player
                        .setPosition(
                                mc.player.getPositionVec().x,
                                mc.player.getPositionVec().y - 2.8,
                                mc.player.getPositionVec().z
                        );
                mc.gameSettings.keyBindSneak.pressed = false;
                mc.player.onGround = false;
                mc.timer.timerSpeed = 0.08F;
                event.cancelled = true;
                this.field23813 = true;
                event.setY(1.0E-14);
            }
        } else {
            if (this.getBooleanValueFromSettingName("Ceiling")
                    && !mc.gameSettings.keyBindSneak.isKeyDown()
                    && mc.world.getCollisionShapes(mc.player, mc.player.boundingBox.offset(0.0, 0.01, 0.0)).count() > 0L) {
                event.setY(1.0E-14);
                MovementUtil.setMotion(event, 0.689 + (double) MovementUtil.getSpeedBoost() * 0.06);
            }

            if (this.field23813) {
                mc.timer.timerSpeed = 1.0F;
                this.field23813 = false;
                event.setY(1.0E-14);
                MovementUtil.setMotion(event, 0.28);
            }
        }

        mc.player.setMotion(mc.player.getMotion().x, event.getY(), mc.player.getMotion().z);
    }

    @EventTarget
    public void EventUpdate(EventMotion event) {
        if (this.isEnabled() && event.isPre()) {
            SimpleEntryPair var4 = PlayerUtil.findCollisionDirection(1.0E-4);
            if (this.getBooleanValueFromSettingName("Ceiling")
                    && !mc.player.onGround
                    && mc.world.getCollisionShapes(mc.player, mc.player.boundingBox.offset(0.0, 1.0E-6, 0.0)).count() > 0L) {
                event.setY(event.getY() + 4.9E-7);
            }

            double var5 = 1.0E-5;
            if (var4 != null
                    && mc.world
                    .getCollisionShapes(mc.player, mc.player.boundingBox.expand(var5, 0.0, var5).expand(-var5, 0.0, -var5))
                    .count()
                    > 0L) {
                if (!BlockUtil.isAboveBounds(mc.player, 1.0E-4F)) {
                    event.setOnGround(true);
                }

                double var7 = 4.88E-7;
				VanillaPhase.setXZ(event, var4, var7);
			}
        }
    }

    @EventTarget
    public void EventBlockCollision(EventBlockCollision event) {
        if (this.isEnabled() && mc.player != null) {
            if (event.getVoxelShape() != null
                    && !event.getVoxelShape().isEmpty()
                    && event.getVoxelShape().getBoundingBox().minY > mc.player.boundingBox.minY + 1.0) {
                event.cancelled = true;
            }
        }
    }
}