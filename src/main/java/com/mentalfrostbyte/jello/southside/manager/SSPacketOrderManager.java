/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.manager.PacketOrderManager (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from usage: Scaffold sets
 * PacketOrderManager.swap = true after sending the offhand-swap PlayerAction packet and
 * PacketOrderManager.rightClicking = true after interactBlock/interactItem; the upstream
 * consumer that re-orders packets for Grim's post/order checks is not part of the repo).
 *
 * The flags are tracked faithfully (set by the SouthSide scaffold exactly where upstream sets
 * them, cleared at the start of each tick) so downstream consumers can be added later.
 */
package com.mentalfrostbyte.jello.southside.manager;

public final class SSPacketOrderManager {
    public static boolean swap;
    public static boolean rightClicking;

    private SSPacketOrderManager() {
    }

    /** Called once per client tick from SSRotationUtils.choose(). */
    public static void onTick() {
        swap = false;
        rightClicking = false;
    }
}
