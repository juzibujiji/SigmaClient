package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

public class YsmGUI extends Module {
    public YsmGUI() {
        super(ModuleCategory.GUI, "YSM", "Toggle OpenYSM player models");
        this.enabled = YesSteveModel.isEnabled();
    }

    @Override
    public void initialize() {
        super.initialize();
        this.setEnabledBasic(YesSteveModel.isEnabled());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        YesSteveModel.setEnabled(true);
        YesSteveModel.setRenderPlayers(true);
    }

    @Override
    public void onDisable() {
        YesSteveModel.setEnabled(false);
        YesSteveModel.setRenderPlayers(false);
        super.onDisable();
    }
}
