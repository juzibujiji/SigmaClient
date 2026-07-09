/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.player.MovementUtils (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from its observed API surface:
 * public static boolean cancelMove; cancelMove(); resetMove()).
 *
 * Semantics from upstream Scaffold usage: while a scaffold target is unreachable the module
 * calls cancelMove() for up to 8 ticks (zeroing the player's movement input so the rotation
 * can land — 大kb自救/self-save), then resetMove() once the raytrace hits the desired face.
 * The input zeroing itself is applied by SSRotationManager's EventMoveInput handler.
 */
package com.mentalfrostbyte.jello.southside.utils.player;

public final class SSMovementUtils {
    public static boolean cancelMove;

    private SSMovementUtils() {
    }

    public static void cancelMove() {
        cancelMove = true;
    }

    public static void resetMove() {
        cancelMove = false;
    }
}
