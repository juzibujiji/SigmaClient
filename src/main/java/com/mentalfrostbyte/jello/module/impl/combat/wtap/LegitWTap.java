package com.mentalfrostbyte.jello.module.impl.combat.wtap;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.client.util.InputMappings;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

public class LegitWTap extends Module {
    private boolean forwardReleased;
    private boolean previousForwardState;

    public LegitWTap() {
        super(ModuleCategory.COMBAT, "Legit", "Performs a real one-tick forward-key release after valid attacks");
    }

    @EventTarget
    @LowestPriority
    public void onPacketSend(EventSendPacket event) {
        if (!this.isEnabled()) {
            return;
        }

        if (event.packet instanceof CUseEntityPacket attackPacket) {
            if (!event.cancelled && WTapSupport.isValidSprintAttack(mc, attackPacket)) {
                this.releaseForwardKey();
            }
            return;
        }

        if (!this.forwardReleased) {
            return;
        }

        boolean sprintStopped = event.packet instanceof CEntityActionPacket actionPacket
                && actionPacket.getAction() == CEntityActionPacket.Action.STOP_SPRINTING;
        if (sprintStopped || event.packet instanceof CPlayerPacket) {
            this.restoreForwardKey();
        }
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        this.restoreForwardKey();
    }

    @Override
    public void onDisable() {
        this.restoreForwardKey();
    }

    private void releaseForwardKey() {
        if (this.forwardReleased) {
            return;
        }

        this.previousForwardState = mc.gameSettings.keyBindForward.isKeyDown();
        this.forwardReleased = true;
        mc.gameSettings.keyBindForward.setPressed(false);
    }

    private void restoreForwardKey() {
        if (!this.forwardReleased) {
            return;
        }

        InputMappings.Input forwardKey = mc.gameSettings.keyBindForward.keyCode;
        boolean pressed = this.previousForwardState;
        if (forwardKey.getType() == InputMappings.Type.KEYSYM
                && forwardKey.getKeyCode() != InputMappings.INPUT_INVALID.getKeyCode()) {
            pressed = InputMappings.isKeyDown(mc.getMainWindow().getHandle(), forwardKey.getKeyCode());
        }

        mc.gameSettings.keyBindForward.setPressed(pressed);
        this.forwardReleased = false;
        this.previousForwardState = false;
    }
}
