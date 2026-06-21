package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.elfmcys.yesstevemodel.gui.OpenYsmScreens;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

public class YsmGUI extends Module {
    public YsmGUI() {
        super(ModuleCategory.GUI, "YSM", "Open the OpenYSM model manager");
        this.enabled = false;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.setEnabledBasic(false);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        OpenYsmScreens.openModelManager();
        this.setEnabledBasic(false);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }
}
