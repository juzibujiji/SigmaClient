package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.world.Disabler;
import com.mentalfrostbyte.jello.module.impl.world.disabler.HypixelPredictionDisabler;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import net.minecraft.potion.Effects;
import team.sdhq.eventBus.annotations.EventTarget;

// pasted from titties client, as requested by AwayXD (lol)
public class HypixelNewSpeed extends Module {
//    public static boolean canLowHop = false;
    private int fallTicks;

    public HypixelNewSpeed() {
        super(ModuleCategory.MOVEMENT, "HypixelNew", "Speed for Hypixel");
    }

    @EventTarget
    public void onTick(EventUpdate __) {
        if (mc.player == null) return;
        fallTicks = mc.player.isOnGround() ? 0 : ++fallTicks;
    }

    @EventTarget
    public void onUpdate(EventMotion event) {
        if (mc.player != null && mc.world != null && !mc.player.isInWater() && !mc.player.isSpectator()) {
            if (Client.getInstance().moduleManager.getModuleByClass(Disabler.class).isEnabled() && HypixelPredictionDisabler.watchDogDisabled) {
            } else if (mc.player.isOnGround()) {
                if (mc.player.isOnGround()) {
                    if (MovementUtil.isMoving()) {
                        mc.player.jump();
                    }

                    if (mc.player.isPotionActive(Effects.SPEED)) {
                        MovementUtil.moveInDirection(
                                0.46F
                                        + (
                                        ((float)mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier() + 1.0F) * (float)mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier()
                                                == 0.0F
                                                ? 0.036F
                                                : 0.12F
                                )
                        );
                    } else {
                        MovementUtil.moveInDirection(0.467F);
                    }
                } else if (!mc.player.collidedHorizontally && !mc.player.isPotionActive(Effects.JUMP_BOOST) && mc.player.hurtTime == 0) {
                    double baseVelocityY = mc.player.getMotion().y;
                    switch (fallTicks) {
                        case 1:
                            MovementUtil.moveInDirection(0.33F);
                            if (mc.player.isPotionActive(Effects.SPEED)) {
                                MovementUtil.moveInDirection(0.36F);
                            }

                            event.setY(baseVelocityY + 0.057);
                            mc.player.setMotion(mc.player.getMotion().x, event.getY(), mc.player.getMotion().z);
                            break;
                        case 2:
                        default:
                            break;
                        case 3:
                            event.setY(baseVelocityY - 0.1309);
                            mc.player.setMotion(mc.player.getMotion().x, event.getY(), mc.player.getMotion().z);
                            break;
                        case 4:
                            event.setY(baseVelocityY - 0.2);
                            mc.player.setMotion(mc.player.getMotion().x, event.getY(), mc.player.getMotion().z);
                            break;
                    }
                }
                if (MovementUtil.isMoving()) {
                    mc.player.jump();
                }

                if (mc.player.isPotionActive(Effects.SPEED)) {
                    MovementUtil.moveInDirection(
                            0.46F
                                    + (
                                    ((float)mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier() + 1.0F) * (float)mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier()
                                            == 0.0F
                                            ? 0.036F
                                            : 0.12F
                            )
                    );
                } else {
                    MovementUtil.moveInDirection(0.465F);
                }
            }
        }
    }

}