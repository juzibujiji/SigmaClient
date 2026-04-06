package com.mentalfrostbyte.jello.module.impl.world;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.util.Hand;
import team.sdhq.eventBus.annotations.EventTarget;

public class Bow32K extends Module {
    public NumberSetting<Integer> Timeout = new NumberSetting<>("Timeout", "", 500, 100, 600, 10);
    public NumberSetting<Integer> spoofs = new NumberSetting<>("Spoofs", "", 100, 1, 300, 10);
    private long lastShootTime;
    public BooleanSetting debug = new BooleanSetting("ChatNotify", "", false);

    public Bow32K(){
        super(ModuleCategory.WORLD, "Bow32K", "Uno hitter w bows");
        this.registerSetting(Timeout,spoofs,debug);
    }

    private void doSpoofs() {
        if (System.currentTimeMillis() - this.lastShootTime >= this.Timeout.getCurrentValue()) {
            this.lastShootTime = System.currentTimeMillis();
            mc.player.connection.sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
            for (int i = 0; i < this.spoofs.getCurrentValue(); ++i) {
                mc.player.connection.sendPacket(new CPlayerPacket.PositionPacket(mc.player.getPosX(), mc.player.getPosY() + 1.0E-10, mc.player.getPosZ(), false));
                mc.player.connection.sendPacket(new CPlayerPacket.PositionPacket(mc.player.getPosX(), mc.player.getPosY() - 1.0E-10, mc.player.getPosZ(), true));
            }
            if (this.debug.getCurrentValue()) {
                MinecraftUtil.addChatMessage("E");
            }
        }
    }

    @Override
    public void onEnable() {
        if (this.isEnabled()) {
            this.lastShootTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onPacketSend(final EventSendPacket send) {
        if (send.packet instanceof CPlayerDiggingPacket) {
            final CPlayerDiggingPacket CPlayerDiggingPacket = (CPlayerDiggingPacket)send.packet;
            final ItemStack itemStack2;
            if (CPlayerDiggingPacket.getAction() == net.minecraft.network.play.client.CPlayerDiggingPacket.Action.RELEASE_USE_ITEM && !(itemStack2 = mc.player.getHeldItem(Hand.MAIN_HAND)).isEmpty() && itemStack2.getItem() != null && itemStack2.getItem() instanceof BowItem) {
                this.doSpoofs();
                if (this.debug.getCurrentValue()) {
                    MinecraftUtil.addChatMessage("trying to spoof");
                }
            }
        }
    }

}
