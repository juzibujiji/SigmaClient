/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.event.events.RotationAppliedEvent (referenced by upstream sources;
 * not published in the upstream repo — an empty marker event).
 *
 * Fired once per client tick from Minecraft#runTick, immediately before processKeyBinds()
 * (the 1.16.5 equivalent of MinecraftClient#handleInputEvents) — see the port of
 * dev.southside.mixin.MixinMinecraftClient#hookRotationApplied. Upstream comment:
 * 所有模块在这个event通过RotationUtils.getRotation()拿到当前设置的转头，校验raytrace，
 * 防止多模块转头raytrace冲突 (all modules read the chosen rotation here and validate their
 * raytrace, preventing multi-module rotation/raytrace conflicts).
 */
package com.mentalfrostbyte.jello.southside.event;

import team.sdhq.eventBus.Event;

public class SSRotationAppliedEvent extends Event {
}
