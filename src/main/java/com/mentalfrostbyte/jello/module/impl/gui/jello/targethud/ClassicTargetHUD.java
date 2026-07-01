package com.mentalfrostbyte.jello.module.impl.gui.jello.targethud;

import com.mentalfrostbyte.jello.module.RenderModule;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

/**
 * Classic 风格的 TargetHUD 样式。
 * 渲染逻辑待实现，当前仅作为 ModuleWithModuleSettings 的子模块占位。
 */
public class ClassicTargetHUD extends RenderModule {

    public ClassicTargetHUD() {
        super(ModuleCategory.GUI, "Classic", "Classic style TargetHUD");
    }
}
