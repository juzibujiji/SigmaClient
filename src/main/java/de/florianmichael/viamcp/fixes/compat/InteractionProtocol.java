package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;

public final class InteractionProtocol {
    private InteractionProtocol() {
    }

    public static ProtocolVersion targetVersion() {
        ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
        return loadingBase == null ? ProtocolVersion.v1_16_4 : loadingBase.getTargetVersion();
    }

    public static boolean atOrOlderThan1_8() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8);
    }

    public static boolean atOrOlderThan1_12_2() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2);
    }

    public static boolean atOrOlderThan1_10() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_10);
    }

    public static boolean atOrOlderThan1_14_4() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4);
    }

    public static boolean atOrOlderThan1_15_2() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2);
    }

    public static boolean atOrOlderThan1_19_4() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_4);
    }

    public static boolean atOrOlderThan1_20_3() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3);
    }

    public static boolean atOrOlderThan1_20_5() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5);
    }

    public static boolean atOrOlderThan1_21() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21);
    }

    public static boolean atOrOlderThan1_21_2() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_2);
    }

    public static boolean atOrOlderThan1_21_9() {
        return targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_9);
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

    public static boolean atOrNewerThan1_17() {
        return targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_17);
    }

    public static boolean atOrNewerThan1_21_2() {
        return targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_2);
    }

    public static boolean supportsOffhand() {
        return !atOrOlderThan1_8();
    }

    public static boolean supportsPickItemPacket() {
        return !atOrOlderThan1_8();
    }

    public static boolean usesAttackAndItemCooldowns() {
        return !atOrOlderThan1_8();
    }

    public static boolean supportsBrewingFuelSlot() {
        return !atOrOlderThan1_8();
    }

    public static boolean supportsWaterlogging() {
        return !atOrOlderThan1_12_2();
    }

    public static boolean needsOpenInventoryStatusPacket() {
        return atOrOlderThan1_8();
    }
}
