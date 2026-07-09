/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.rotation.Priority (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from usage: RotationUtils.setRotation(rot)
 * defaults to Priority.Lower, Scaffold#rotationPriority() returns Priority.Higher + 3).
 *
 * Only relative ordering matters (SSRotationUtils#accepted / #getRandomRotation compare
 * these values); the absolute scale is arbitrary.
 */
package com.mentalfrostbyte.jello.southside.utils.rotation;

public final class SSPriority {
    public static final int Lowest = 1;
    public static final int Lower = 2;
    public static final int Normal = 3;
    public static final int Higher = 4;
    public static final int Highest = 5;

    private SSPriority() {
    }
}
