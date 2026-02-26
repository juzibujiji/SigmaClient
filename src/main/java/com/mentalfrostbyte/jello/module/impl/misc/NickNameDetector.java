package com.mentalfrostbyte.jello.module.impl.misc;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import net.minecraft.entity.Entity;
import team.sdhq.eventBus.annotations.EventTarget;

public class NickNameDetector extends Module {
    public NickNameDetector() {
        super(ModuleCategory.MISC, "NickNameDetector", "Detect if a player has a custom name");
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (this.isEnabled()) {
            for (Entity entity : CombatUtil.getPlayers()) {
                if (!Client.getInstance().botManager.isBot(entity) && entity.ticksExisted > 30 && entity.hasCustomName()) {
                    MinecraftUtil.addChatMessage(entity.getName().getUnformattedComponentText() + " might have a custom nametag");
                }
            }
        }
    }
}
