/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.math.MathUtils (referenced by upstream sources; not published
 * in the upstream repo — reconstructed from usage: MathUtils.getRandomInRange(0.3, -0.3),
 * i.e. bounds may arrive in either order).
 *
 */
package com.mentalfrostbyte.jello.southside.utils.math;

import java.util.concurrent.ThreadLocalRandom;

public final class SSMathUtils {
    private SSMathUtils() {
    }

    public static double getRandomInRange(double a, double b) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}
