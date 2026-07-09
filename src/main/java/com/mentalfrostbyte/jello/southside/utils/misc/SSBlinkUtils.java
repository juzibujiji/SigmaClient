/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.misc.BlinkUtils (referenced by upstream sources; not published
 * in the upstream repo — reconstructed from usage: Scaffold#place() early-returns while
 * BlinkUtils.blinking is true, i.e. no placements while a blink-style packet hold is active).
 *
 * Bridged to this client: `blinking` is refreshed once per tick (from SSRotationUtils.choose())
 * to reflect whether the host Blink module currently holds outgoing packets.
 */
package com.mentalfrostbyte.jello.southside.utils.misc;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.impl.player.Blink;

public final class SSBlinkUtils {
    public static boolean blinking;

    private SSBlinkUtils() {
    }

    public static void update() {
        boolean hostBlink = false;
        try {
            Module blink = Client.getInstance().moduleManager.getModuleByClass(Blink.class);
            hostBlink = blink != null && blink.isEnabled();
        } catch (Exception ignored) {
        }
        blinking = hostBlink;
    }
}
