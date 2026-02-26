package com.mentalfrostbyte.jello.gui.base;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;

public class JelloPortal {
    public static ProtocolVersion getVersion() {
        return ViaLoadingBase.getInstance().getTargetVersion();
    }
}
