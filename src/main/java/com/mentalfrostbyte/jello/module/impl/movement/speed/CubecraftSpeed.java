package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2D;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.world.Timer;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.PlayerUtil;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import team.sdhq.eventBus.annotations.EventTarget;

public class CubecraftSpeed extends Module {
    private int field23618;
    private double field23619;
    private double yPos;

    public CubecraftSpeed() {
        super(ModuleCategory.MOVEMENT, "Cubecraft", "Speed for Cubecraft");
        this.registerSetting(new ModeSetting("Mode", "Mode", 0, "Basic", "Hop", "YPort"));
        this.registerSetting(new NumberSetting<Float>("Speed", "Speed value", 0.75F, 0.1F, 1.0F, 0.01F));
        this.registerSetting(new BooleanSetting("AutoJump", "Automatically jumps for you.", false));
    }

    @Override
    public void onEnable() {
        this.field23618 = 0;
        this.yPos = -1.0;
    }

    @Override
    public void onDisable() {
        mc.timer.timerSpeed = 1.0F;
        MovementUtil.strafe(0.2);
        if (mc.player.getMotion().y > 0.0) {
            mc.player.setMotion(mc.player.getMotion().x, -0.078, mc.player.getMotion().z);
        }
    }

    @EventTarget
    public void method16361(EventMove var1) {
        if (this.isEnabled()
                && !Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).isEnabled()
                && !PlayerUtil.inLiquid(mc.player)) {
            String var4 = this.getStringSettingValueByName("Mode");
            switch (var4) {
                case "Basic":
                    this.field23618++;
                    this.field23619 = 0.27;
                    mc.timer.timerSpeed = 0.7F;
                    if (BlockUtil.isAboveBounds(mc.player, 0.01F)) {
                        if (this.getBooleanValueFromSettingName("AutoJump")) {
                            mc.player.jump();
                            var1.setY(mc.player.getMotion().y);
                            MovementUtil.setMotion(var1, this.field23619);
                        }

                        if (this.field23618 == 1) {
                            this.field23619 = (double) this.getNumberValueBySettingName("Speed") * 2.4;
                        } else if (this.field23618 == 2) {
                            mc.timer.timerSpeed = 1.0F;
                            this.field23619 = 0.26;
                        } else if (this.field23618 >= 3) {
                            this.field23618 = 0;
                            this.field23619 = 0.26;
                        }
                    } else {
                        if (mc.timer.timerSpeed == 0.7F) {
                            mc.timer.timerSpeed = 1.0F;
                        }

                        if (this.field23618 == 1) {
                            this.field23619 = 0.27;
                            if (var1.getY() > 0.0) {
                                this.field23619 = 2.0;
                            }
                        } else if (this.field23618 > 1) {
                            this.field23618 = 0;
                        }
                    }

                    MovementUtil.setMotion(var1, this.field23619);
                    break;
                case "Hop":
                    if (!mc.player.collidedVertically || !BlockUtil.isAboveBounds(mc.player, 0.001F) || !MovementUtil.isMoving()) {
                        this.field23618++;
                        if (this.field23618 == 1) {
                            this.field23619 = 0.4 + (double) MovementUtil.getSpeedBoost() * 0.1;
                        }

                        this.field23619 -= 0.015;
                        if (mc.gameSettings.keyBindBack.pressed) {
                            this.field23619 -= 0.07;
                        }

                        this.field23619 = Math.max(this.field23619, 0.2);
                        MovementUtil.setMotion(var1, this.field23619);
                    } else if (this.getBooleanValueFromSettingName("AutoJump")) {
                        mc.player.jump();
                        var1.setY(mc.player.getMotion().y);
                        MovementUtil.setMotion(var1, this.field23619);
                    }
                    break;
                case "YPort":
                    if (mc.player.onGround) {
                        if (MovementUtil.isMoving()) {
                            var1.setY(0.53000000000001);
                            MovementUtil.setMotion(var1, 3.67 * (double) this.getNumberValueBySettingName("Speed"));
                            this.field23618 = 0;
                        }

                        this.yPos = mc.player.getPosY();
                        if (!Client.getInstance().moduleManager.getModuleByClass(Timer.class).isEnabled()) {
                            mc.timer.timerSpeed = 1.0F;
                        }
                    } else {
                        mc.timer.timerSpeed = 1.0F - this.getNumberValueBySettingName("Speed") * 0.13F;
                        if (this.field23618 == 0 && var1.getY() == 0.44100000858307864) {
                            this.field23618 = 1;
                            MovementUtil.setMotion(var1, 0.286);
                            var1.setY(-0.265);
                            mc.player.setMotion(mc.player.getMotion().x, var1.getY(), mc.player.getMotion().z);
                        } else if (this.field23618 == 1) {
                            this.field23618 = -1;
                            MovementUtil.setMotion(var1, 0.285);
                        }
                    }
            }
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (this.isEnabled() && !(this.yPos < 0.0) && this.getStringSettingValueByName("Mode").equals("YPort")) {
            if (mc.player.onGround && BlockUtil.isAboveBounds(mc.player, 0.001F)) {
                yPos = mc.player.getPosY();
            }

            mc.player.positionVec.y = this.yPos;
            mc.player.lastTickPosY = this.yPos;
            mc.player.chasingPosY = this.yPos;
            mc.player.prevPosY = this.yPos;
            if (MovementUtil.isMoving()) {
                mc.player.cameraYaw = 0.099999994F;
            }
        }
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (this.isEnabled()) {
            event.vector.y = 0.4;
            this.field23619 = 0.6 + (double) MovementUtil.getSpeedBoost() * 0.1;
            this.field23618 = 0;
            event.setStrafeSpeed(this.field23619);
        }
    }
}
