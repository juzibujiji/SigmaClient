package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.movement.Fly;
import com.mentalfrostbyte.jello.module.impl.movement.Jesus;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowerPriority;

public class TestSpeed extends Module {
    public int field23918 = 0;
    private double field23912;
    private float field23913;
    private float field23914;
    private boolean field23915;
    private float field23916;
    private float field23917 = 1.0F;

    public TestSpeed() {
        super(ModuleCategory.MOVEMENT, "TestSpeed", "Legit Sprint jumping.");
        this.registerSetting(new BooleanSetting("Sprint", "Sprints when walking", true));
        this.registerSetting(new BooleanSetting("AutoJump", "Automatically jumps for you.", true));
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (this.isEnabled() && mc.player != null && !Client.getInstance().moduleManager.getModuleByClass(Fly.class).isEnabled()) {
            if (mc.player.onGround && event.isPre()) {
                event.setY(event.getY() + 1.0E-14);
            }
        }
    }

    @EventTarget
    public void onMove(EventMove event) {
        if (this.isEnabled()) {
            if (mc.player.onGround
                    && mc.player.collidedVertically
                    && (mc.player.moveForward != 0.0F || mc.player.moveStrafing != 0.0F)
                    && this.getBooleanValueFromSettingName("AutoJump")) {
                mc.player.jump();
                event.setX(mc.player.getMotion().x);
                event.setY(mc.player.getMotion().y);
                event.setZ(mc.player.getMotion().z);
            }

            double var4 = event.getVector().y;
            event.getVector().y = 0.0;
            double var6 = event.getVector().length();
            event.getVector().y = var4;
            this.field23916 = MovementUtil.setMotion(event, var6, MovementUtil.getDirectionArray()[0], this.field23916, 45.0F);
            if (this.field23913 != 0.0F || this.field23914 != 0.0F) {
                this.field23913 = (float) ((double) this.field23913 * 0.85);
                this.field23914 = (float) ((double) this.field23914 * 0.85);
                this.field23917 = Math.min(1.0F, this.field23917 + 0.1F);
            }

            if (mc.player.onGround && !this.field23915) {
                this.field23913 = 0.0F;
                this.field23914 = 0.0F;
                this.field23917 = Math.min(1.0F, this.field23917 + 0.33F);
            }

            this.field23915 = false;
            this.field23918++;
        }
    }

    @EventTarget
    @LowerPriority
    public void onJump(EventJump event) {
        if (this.isEnabled() && !Jesus.isWalkingOnLiquid() && !Client.getInstance().moduleManager.getModuleByClass(Fly.class).isEnabled()) {
            if (!mc.gameSettings.keyBindJump.isKeyDown() || !Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).isEnabled()) {
                this.field23916 = MovementUtil.getDirectionArray()[0];
                this.field23918 = 0;
                event.setStrafeSpeed(event.vector.length() * 1.05F);
            }
        }
    }
}
