package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.player.Blink;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import net.minecraft.item.SwordItem;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import team.sdhq.eventBus.annotations.EventTarget;

public class NoSlow extends Module {
    private boolean isBlocking;

    public NoSlow() {
        super(ModuleCategory.MOVEMENT, "NoSlow", "Stops slowdown when using an item");
        this.registerSetting(new ModeSetting("Mode", "NoSlow mode", 0, "Vanilla", "NCP", "Blink"));
    }

    @EventTarget
    public void onSlowDown(EventSlowDown event) {
        if (this.isEnabled()) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onUpdate(EventMotion event) {
        if (!this.isEnabled()) return;

        boolean auraEnabled = Client.getInstance().moduleManager.getModuleByClass(KillAura.class).isEnabled2();
        boolean isSwordEquipped = mc.player.getHeldItemMainhand() != null && mc.player.getHeldItemMainhand().getItem() instanceof SwordItem;

        if (!event.isPre()) {
            if (isSwordEquipped && mc.gameSettings.keyBindUseItem.isKeyDown() && !isBlocking && !auraEnabled && isModeNCP()) {
                CombatUtil.block();
                isBlocking = true;
            } else if (!isSwordEquipped && isBlocking) {
                isBlocking = false;
            }
        } else {
            handlePreEvent(isSwordEquipped, auraEnabled);
        }
    }

    private boolean isModeNCP() {
        return this.getStringSettingValueByName("Mode").equals("NCP");
    }
    private boolean isModeBlink() {
        return this.getStringSettingValueByName("Mode").equals("Blink");
    }


    private void handlePreEvent(boolean isSwordEquipped, boolean auraEnabled) {
        if (!isModeNCP()) {
            if (isBlocking && !mc.gameSettings.keyBindUseItem.isKeyDown()) {
                isBlocking = false;
            }
        } else if (isBlocking && mc.gameSettings.keyBindUseItem.isKeyDown() && !auraEnabled) {
            if (isSwordEquipped) {
                CombatUtil.unblock();
            }
            isBlocking = false;
            if (isModeBlink()) {
                if (mc.player.getHeldItemMainhand().isFood() && !mc.gameSettings.keyBindUseItem.isKeyDown()) {
                    Client.getInstance().moduleManager.getModuleByClass(Blink.class).toggle();
                }
            }
        }
    }
}