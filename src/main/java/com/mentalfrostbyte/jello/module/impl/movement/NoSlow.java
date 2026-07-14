package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import de.florianmichael.viamcp.fixes.compat.InteractionProtocol;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.util.Hand;
import team.sdhq.eventBus.annotations.EventTarget;

public class NoSlow extends Module {
    private boolean releasedAwaitingReblock;
    private PlayerEntity trackedPlayer;

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
        if (!this.isEnabled() || mc.player == null) {
            resetPacketCycle();
            return;
        }

        if (this.trackedPlayer != mc.player) {
            this.releasedAwaitingReblock = false;
            this.trackedPlayer = mc.player;
        }

        boolean shouldCycle = shouldCycleLegacySword();
        if (event.isPre()) {
            if (this.releasedAwaitingReblock) {
                if (!shouldCycle) {
                    restoreOrDiscardReleasedBlock();
                }
            } else if (shouldCycle && CombatUtil.unblock()) {
                this.releasedAwaitingReblock = true;
            }
        } else if (this.releasedAwaitingReblock) {
            restoreOrDiscardReleasedBlock();
        }
    }

    /**
     * EventMotion can be cancelled after PRE, in which case it has no POST.
     * Restore a release from that cancelled transaction before the next input
     * tick can emit vanilla's key-up release.
     */
    @EventTarget
    public void onRunTicks(EventRunTicks event) {
        if (!this.isEnabled() || event.isPre() || !this.releasedAwaitingReblock) {
            return;
        }

        if (this.trackedPlayer != mc.player) {
            resetPacketCycle();
        } else {
            restoreOrDiscardReleasedBlock();
        }
    }

    private boolean shouldCycleLegacySword() {
        Module killAuraModule = Client.getInstance().moduleManager.getModuleByClass(KillAura.class);
        boolean auraBlocking = killAuraModule instanceof KillAura
                && ((KillAura) killAuraModule).isEnabled2();
        return isModeNCP()
                && !auraBlocking
                && hasActiveLegacySwordUse();
    }

    private boolean hasActiveLegacySwordUse() {
        return mc.player != null
                && InteractionProtocol.atOrOlderThan1_8()
                && mc.gameSettings.keyBindUseItem.isKeyDown()
                && mc.player.isHandActive()
                && mc.player.getActiveHand() == Hand.MAIN_HAND
                && SwordItem.isLegacyBlockingSword(mc.player.getActiveItemStack());
    }

    private void restoreOrDiscardReleasedBlock() {
        Module killAuraModule = Client.getInstance().moduleManager.getModuleByClass(KillAura.class);
        boolean auraBlocking = killAuraModule instanceof KillAura
                && ((KillAura) killAuraModule).isEnabled2();
        if (auraBlocking || !hasActiveLegacySwordUse() || CombatUtil.block()) {
            this.releasedAwaitingReblock = false;
        }
    }

    private boolean isModeNCP() {
        return "NCP".equals(this.getStringSettingValueByName("Mode"));
    }

    @EventTarget
    public void onLoadWorld(EventLoadWorld event) {
        resetPacketCycle();
    }

    @Override
    public void onDisable() {
        if (this.releasedAwaitingReblock && this.trackedPlayer == mc.player) {
            restoreOrDiscardReleasedBlock();
        }
        resetPacketCycle();
        super.onDisable();
    }

    private void resetPacketCycle() {
        this.releasedAwaitingReblock = false;
        this.trackedPlayer = null;
    }
}
