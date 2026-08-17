package com.mentalfrostbyte.jello.module.impl.movement.longjump;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import team.sdhq.eventBus.annotations.EventTarget;

/**
 * Port of Rise 6.9.5 GrimLongJump.
 * <p>
 * Mechanics: 2x timer while active, jump on first tick, then a burst of 20
 * ground-only CPlayerPackets on the second tick. After the server lagback
 * (SPlayerPosLook) arrives, two extra packets per tick are sent while the next
 * real movement packet is swallowed, freezing client-side motion; the module
 * times out after 20 ticks or when the server pushes negative Y velocity.
 */
public class GrimLongJump extends Module {
    private int strafeTicks;
    private int cancelNextPacket;
    private int timeoutTicks;
    private boolean started;
    private int stage;

    public GrimLongJump() {
        super(ModuleCategory.MOVEMENT, "Grim", "Timer + packet burst longjump for Grim.");
    }

    @Override
    public void onEnable() {
        this.strafeTicks = 0;
        this.started = false;
        this.stage = 0;
        this.cancelNextPacket = 0;
        this.timeoutTicks = 0;
    }

    @Override
    public void onDisable() {
        mc.timer.timerSpeed = 1.0F;
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (!this.isEnabled() || mc.player == null || !event.isPre()) {
            return;
        }

        mc.timer.timerSpeed = 2.0F;
        event.setPitch(event.getPitch() + (float) Math.random() * 0.1F);

        if (!this.started) {
            return;
        }

        mc.player.setMotion(mc.player.getMotion().x, 0.42, mc.player.getMotion().z);

        if (this.stage == 1) {
            this.stage = 2;
            this.timeoutTicks = 0;
            return;
        }

        if (this.stage != 2) {
            mc.player.setMotion(0.0, 0.0, 0.0);
            this.cancelNextPacket = 1;
            this.timeoutTicks++;
            if (this.timeoutTicks > 20) {
                this.access().toggle();
            }
            return;
        }

        for (int i = 0; i < 2; i++) {
            mc.getConnection().getNetworkManager().sendNoEventPacket(new CPlayerPacket(false));
        }
        this.cancelNextPacket = 1;
        this.stage = 0;
    }

    @EventTarget
    public void onMove(EventMove event) {
        if (!this.isEnabled() || mc.player == null) {
            return;
        }

        if (this.strafeTicks == 0) {
            mc.player.jump();
        }

        if (this.strafeTicks == 1) {
            this.started = true;
            for (int i = 0; i < 20; i++) {
                mc.getConnection().getNetworkManager().sendNoEventPacket(new CPlayerPacket(false));
            }
        }

        this.strafeTicks++;
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (!this.isEnabled() || mc.player == null) {
            return;
        }

        if (event.packet instanceof SEntityVelocityPacket packet
                && packet.getEntityID() == mc.player.getEntityId()
                && (double) packet.getMotionY() / 8000.0 < 0.0) {
            this.access().toggle();
        }

        if (event.packet instanceof SPlayerPositionLookPacket) {
            this.stage = 1;
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (!this.isEnabled()) {
            return;
        }

        if (this.cancelNextPacket == 1 && event.packet instanceof CPlayerPacket) {
            this.cancelNextPacket = 0;
            event.cancelled = true;
        }
    }
}
