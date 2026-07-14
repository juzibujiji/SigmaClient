package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.*;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class NoSlow extends Module {
    private final static BooleanSetting swordnoslow = new BooleanSetting("SwordNoSlow", "Sword NoSlow", true);
    private final static ModeSetting swordnoslowmode = new ModeSetting("SwordNoSlowMode", "Sword NoSlow Mode", 0, "Vanilla","GrimTick");
    private final static BooleanSetting bownoslow = new BooleanSetting("BowNoSlow", "Bow NoSlow", true);
    private final static ModeSetting bownoslowmode = new ModeSetting("BowNoSlowMode", "Bow NoSlow Mode", 0, "Vanilla","GrimTick");
    private final static BooleanSetting consumablenoslow = new BooleanSetting("FoodNoSlow", "Consumable NoSlow", true);
    private final static ModeSetting consumablenoslowmode = new ModeSetting("FoodNoSlowMode", "Consumable NoSlow Mode", 0, "Vanilla","GrimTick");

    public NoSlow() {
        super(ModuleCategory.MOVEMENT, "NoSlow", "Stops slowdown when using an item");
        this.registerSetting(swordnoslow, swordnoslowmode, bownoslow, bownoslowmode, consumablenoslow, consumablenoslowmode);
    }

    private boolean grimticknoslow = false;

    @EventTarget
    public void onSlowDown(EventSlowDown event) {
        if (this.isEnabled()) {
            final Item item = mc.player.getHeldItem(mc.player.getActiveHand()).getItem();
            if (swordnoslow.getCurrentValue() && item instanceof SwordItem) {
                switch (swordnoslowmode.getCurrentValue()) {
                    case "Vanilla" -> {
                        event.cancelled = true;
                    }
                    case "GrimTick" -> {
                        if (grimticknoslow) {
                            event.cancelled = true;
                            grimticknoslow = false;
                        } else grimticknoslow = true;
                    }
                }
            }
            if (bownoslow.getCurrentValue() && item instanceof BowItem) {
                switch (bownoslowmode.getCurrentValue()) {
                    case "Vanilla" -> {
                        event.cancelled = true;
                    }
                    case "GrimTick" -> {
                        if (grimticknoslow) {
                            event.cancelled = true;
                            grimticknoslow = false;
                        } else grimticknoslow = true;
                    }
                }
            }
            if (consumablenoslow.getCurrentValue() && (item.isFood() || item instanceof PotionItem || item instanceof MilkBucketItem)) {
                switch (consumablenoslowmode.getCurrentValue()) {
                    case "Vanilla" -> {
                        event.cancelled=true;
                    }
                    case "GrimTick" -> {
                        if (grimticknoslow) {
                            event.cancelled = true;
                            grimticknoslow = false;
                        } else grimticknoslow = true;
                    }
                }
            }
            mc.player.setSprinting(true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true);
        }
    }

    @EventTarget
    public void onTick(EventSendPacket event) {
        if (this.isEnabled()) {
            //每次都先grimticknoslow false
            if (grimticknoslow) {
                if (event.packet instanceof CPlayerTryUseItemPacket) {
                    grimticknoslow = false;
                }
            }
        }
    }

}