package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.elfmcys.yesstevemodel.gui.OpenYsmScreens;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

public class YsmActionsGUI extends Module {
    public YsmActionsGUI() {
        super(ModuleCategory.GUI, "YSM Actions", "Open the OpenYSM action wheel");
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
        OpenYsmScreens.openActionWheel();
        this.setEnabledBasic(false);
    }

    public void openWhilePressing(int key) {
        OpenYsmScreens.openActionWheel(key);
        this.setEnabledBasic(false);
    }
}
