/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.math.RandomUtils (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from usage:
 * RandomUtils.nextDouble(0.1, 0.2) and RandomUtils.generateRandomFloat(0.001f, 0.005f)).
 *
 */
package com.mentalfrostbyte.jello.southside.utils.math;

import java.util.concurrent.ThreadLocalRandom;

public final class SSRandomUtils {
    private SSRandomUtils() {
    }

    public static double nextDouble(double min, double max) {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static float generateRandomFloat(float min, float max) {
        if (min >= max) {
            return min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }
}
