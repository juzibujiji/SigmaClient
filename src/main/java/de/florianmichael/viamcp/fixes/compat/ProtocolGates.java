package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;

public final class ProtocolGates {
    private ProtocolGates() {
    }

    public static ProtocolVersion targetVersion() {
        ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
        return loadingBase != null ? loadingBase.getTargetVersion() : ProtocolVersion.v1_16_4;
    }

    public static boolean atMost1_8() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8);
    }

    public static boolean atMost1_12_2() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2);
    }

    public static boolean atMost1_15_2() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2);
    }

    public static boolean between1_17And1_20_5() {
        ProtocolVersion target = targetVersion();
        return target.newerThanOrEqualTo(ProtocolVersion.v1_17)
                && target.olderThanOrEqualTo(ProtocolVersion.v1_20_5);
    }

    public static boolean between1_19And1_21_1() {
        ProtocolVersion target = targetVersion();
        return target.newerThanOrEqualTo(ProtocolVersion.v1_19)
                && target.olderThan(ProtocolVersion.v1_21_2);
    }

    public static boolean atLeast1_21_2() {
        return targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_2);
    }

    public static boolean supportsOffhand() {
        return targetVersion().newerThan(ProtocolVersion.v1_8);
    }
}
