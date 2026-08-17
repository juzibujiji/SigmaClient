package com.mentalfrostbyte.jello.module.impl.gui;

import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.gui.chatui.RiseChatUI;

public class ChatUI extends ModuleWithModuleSettings {
    public ChatUI() {
        super(ModuleCategory.GUI, "ChatUI", "Custom chat interfaces", new RiseChatUI());
    }
}
