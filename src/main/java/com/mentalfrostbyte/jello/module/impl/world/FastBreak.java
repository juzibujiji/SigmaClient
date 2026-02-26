package com.mentalfrostbyte.jello.module.impl.world;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.world.GameType;
import team.sdhq.eventBus.annotations.EventTarget;

public class FastBreak extends Module {

    public FastBreak() {
        super(ModuleCategory.WORLD, "FastBreak", "Break blocks faster");
    }

    @Override
    public void onEnable() {
        if (mc.playerController.getCurrentGameType() != GameType.SURVIVAL) {
            this.setState(false);
            this.setEnabledBasic(false);
            this.setEnabled(false);
            Client.getInstance().notificationManager.send(new Notification("Invalid game mode", "FastBreak must be use in survival mode."));
            Client.getInstance().soundManager.play("error");
        }
        super.onEnable();
    }

    @EventTarget
    public void TickEvent(EventUpdate event) {
        if (this.isEnabled()) {

            if (PlayerController.curBlockDamageMP > 0.7F) {
                PlayerController.curBlockDamageMP = 1.0F;
            }
            PlayerController.curBlockDamageMP = 0F;
        }
    }

}