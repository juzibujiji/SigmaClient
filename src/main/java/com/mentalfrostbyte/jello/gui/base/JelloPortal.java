package com.mentalfrostbyte.jello.gui.base;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;

public class JelloPortal {
    public static ProtocolVersion getVersion() {
        return ViaLoadingBase.getInstance().getTargetVersion();
    }

    /**
     * 与 {@link #getVersion()} 相同，但在 ViaLoadingBase 尚未初始化时回退到原生版本
     * 1.16.4，而不是抛 NPE。
     *
     * <p>供在 Via 初始化之前、或不经过完整客户端启动流程时也会跑到的代码路径使用
     * （例如方块硬度查询、离线的注册表检查）。回退到 1.16.4 语义上正确：没有目标版本
     * 就意味着不需要做任何跨版本修正。
     */
    public static ProtocolVersion getVersionSafe() {
        try {
            return getVersion();
        } catch (Exception e) {
            return ProtocolVersion.v1_16_4;
        }
    }
}
