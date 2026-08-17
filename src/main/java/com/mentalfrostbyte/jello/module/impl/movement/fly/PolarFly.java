package com.mentalfrostbyte.jello.module.impl.movement.fly;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import net.minecraft.network.play.server.SConfirmTransactionPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PolarFly extends Module {
    private final Deque<SConfirmTransactionPacket> polarTransactions = new ConcurrentLinkedDeque<>();
    private double verticalMotion = 0.0;
    private BoatEntity polarBoat = null;
    private boolean polarAttackedBoat = false;
    private boolean polarMovementCheckDisabled = false;

    public PolarFly() {
        super(ModuleCategory.MOVEMENT, "Polar", "Boat fly: attack the boat you ride to disable movement checks");
        this.registerSetting(
                new NumberSetting<Float>("Speed", "Horizontal fly speed", 5.0F, 0.0F, 10.0F, 0.1F),
                new NumberSetting<Float>("Vertical Speed", "Vertical fly speed", 3.0F, 0.0F, 10.0F, 0.1F)
        );
    }

    private void resetPolar() {
        this.polarBoat = null;
        this.polarAttackedBoat = false;
        this.polarMovementCheckDisabled = false;
        this.polarTransactions.clear();
    }

    private void flushPolarTransactions() {
        if (mc.getConnection() == null) {
            return;
        }

        SConfirmTransactionPacket packet;
        while ((packet = this.polarTransactions.poll()) != null) {
            packet.processPacket(mc.getConnection());
        }
    }

    private boolean isPolarBoatGone() {
        return this.polarBoat == null
                || !this.polarBoat.isAlive()
                || mc.world == null
                || mc.world.getEntityByID(this.polarBoat.getEntityId()) != this.polarBoat;
    }

    private boolean isPolarDismountPacket(IPacket<?> packet) {
        return packet instanceof CEntityActionPacket
                && ((CEntityActionPacket) packet).getAction() == CEntityActionPacket.Action.PRESS_SHIFT_KEY
                && (mc.player.getRidingEntity() instanceof BoatEntity || this.polarBoat != null);
    }

    private void updatePolarState() {
        if (mc.player == null) {
            return;
        }

        if (!this.polarAttackedBoat && mc.player.getRidingEntity() instanceof BoatEntity) {
            this.polarBoat = (BoatEntity) mc.player.getRidingEntity();
            mc.getConnection().sendPacket(new CUseEntityPacket(this.polarBoat, false));
            this.polarAttackedBoat = true;
        }

        if (this.polarAttackedBoat && !this.polarMovementCheckDisabled && this.isPolarBoatGone()) {
            this.polarMovementCheckDisabled = true;
        }
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (this.isEnabled() && event.isPre()) {
            this.updatePolarState();
            if (!this.polarMovementCheckDisabled) {
                return;
            }

            this.verticalMotion = 0.0;
            if (mc.currentScreen == null) {
                float verticalSpeed = this.getNumberValueBySettingName("Vertical Speed");
                if (mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.verticalMotion += verticalSpeed * 0.42F;
                }
                if (mc.gameSettings.keyBindSneak.isKeyDown()) {
                    this.verticalMotion -= verticalSpeed * 0.42F;
                }
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.keyCode, false);
            }
        }
    }

    @EventTarget
    public void onMove(EventMove event) {
        if (this.isEnabled() && mc.player != null && this.polarMovementCheckDisabled) {
            if (mc.player.getPosY() % 1.0 != 0.0) {
                event.setY(this.verticalMotion);
                mc.player.setMotion(mc.player.getMotion().x, this.verticalMotion, mc.player.getMotion().z);
            }

            MovementUtil.setMotion(event, MovementUtil.getDumberSpeed() * this.getNumberValueBySettingName("Speed"));
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (this.isEnabled() && mc.player != null && this.isPolarDismountPacket(event.packet)) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (this.isEnabled() && this.polarAttackedBoat && event.packet instanceof SConfirmTransactionPacket) {
            this.polarTransactions.offer((SConfirmTransactionPacket) event.packet);
            event.cancelled = true;
        }
    }

    @Override
    public void onEnable() {
        this.resetPolar();
    }

    @Override
    public void onDisable() {
        this.flushPolarTransactions();
        this.resetPolar();
        if (mc.player != null) {
            MovementUtil.stop(true);
        }
        if (mc.mainWindow != null) {
            KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindSneak.keyCode,
                    InputMappings.isKeyDown(mc.mainWindow.getHandle(), mc.gameSettings.keyBindSneak.keyCode.getKeyCode())
            );
        }
    }
}
