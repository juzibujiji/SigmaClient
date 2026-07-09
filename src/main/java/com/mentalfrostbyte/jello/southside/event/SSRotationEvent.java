/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.event.events.RotationEvent (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from usage in RotationUtils.choose():
 * a reused event instance with a resettable disableRotation flag).
 *
 * Fired once per client tick from Minecraft#runTick (before GameRenderer#getMouseOver,
 * the 1.16.5 equivalent of MinecraftClient#updateCrosshairTarget) — see the port of
 * dev.southside.mixin.MixinMinecraftClient#hookRotation. Modules compute their desired
 * rotation in handlers of this event and submit it via SSRotationUtils.setRotation with
 * a priority; SSRotationUtils.choose() then picks the winner for this tick.
 */
package com.mentalfrostbyte.jello.southside.event;

import team.sdhq.eventBus.Event;

public class SSRotationEvent extends Event {
    private boolean disableRotation;

    public boolean isDisableRotation() {
        return this.disableRotation;
    }

    public void setDisableRotation(boolean disableRotation) {
        this.disableRotation = disableRotation;
    }
}
