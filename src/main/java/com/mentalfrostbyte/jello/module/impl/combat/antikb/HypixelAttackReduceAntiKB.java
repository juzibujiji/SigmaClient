package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.player.EventKeepSprint;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

/**
 * Uses Minecraft's normal post-attack slowdown to reduce horizontal knockback
 * while the local player is inside the damage animation window.
 */
public class HypixelAttackReduceAntiKB extends Module {
    private static final int MIN_HURT_TIME = 3;

    public HypixelAttackReduceAntiKB() {
        super(ModuleCategory.COMBAT, "Hypixel AttackReduce",
                "Reduce horizontal knockback through the vanilla attack slowdown");
    }

    @EventTarget
    @LowestPriority
    public void onAttackSlowdown(EventKeepSprint event) {
        if (this.isEnabled() && mc.player != null && mc.player.hurtTime >= MIN_HURT_TIME) {
            // PlayerEntity applies the vanilla 0.6 horizontal multiplier after this event.
            // Lowest priority intentionally wins over KeepSprintAndMotion for this mode.
            event.greater = true;
        }
    }
}
