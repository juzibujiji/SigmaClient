package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.MainMenuHolder;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.item.Items;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import team.sdhq.eventBus.annotations.EventTarget;

public class AutoLog extends Module {
    public AutoLog() {
        super(ModuleCategory.COMBAT, "AutoLog", "Automatically logs out");
        this.registerSetting(new NumberSetting<>("Min Health", "Minimum health before it logs you out", 2.5F, 0.0F, 10.0F, 0.01F));
        this.registerSetting(new BooleanSetting("No Totems", "Logs out when you have no totems in inventory", false));
        this.registerSetting(new BooleanSetting("One Time Use", "Disables the mod every time it saves you.", true));
        this.registerSetting(new BooleanSetting("Smart Enable", "Re enables the mod when you get enough health.", false));
    }

    @EventTarget
    public void playerTick(EventUpdate var1) {
        if (mc.player == null || mc.world == null || mc.getIntegratedServer() != null || mc.getCurrentServerData() == null) {
            return;
        }

        if (this.getBooleanValueFromSettingName("Smart Enable") && ((mc.player.getHealth() / mc.player.getMaxHealth() * 10.0F > this.getNumberValueBySettingName("Min Health")) || (this.getBooleanValueFromSettingName("No Totems") && mc.player.inventory.hasAny(Items.TOTEM_OF_UNDYING)))) {
            this.setEnabled(true);
            return;
        }

        if ((mc.player.getHealth() / mc.player.getMaxHealth() * 10.0F < this.getNumberValueBySettingName("Min Health") || (this.getBooleanValueFromSettingName("No Totems") && !mc.player.inventory.hasAny(Items.TOTEM_OF_UNDYING))) && this.enabled ) {
            boolean var5 = this.getBooleanValueFromSettingName("One Time Use");
            mc.world.sendQuittingDisconnectingPacket();
            mc.unloadWorld();
            mc.displayGuiScreen(
                    new DisconnectedScreen(
                            new MultiplayerScreen(new MainMenuHolder()),
                            new TranslationTextComponent("disconnect.lost"),
                            new StringTextComponent(
                                    "AutoLog disconnected you. " + (!var5 ? "Disable it in a singleplayer world to reconnect." : "The mod is now disabled for you to reconnect.")
                            )
                    )
            );
            if (this.getBooleanValueFromSettingName("One Time Use")) {
                this.setEnabled(false);
            }
        }
    }
}

