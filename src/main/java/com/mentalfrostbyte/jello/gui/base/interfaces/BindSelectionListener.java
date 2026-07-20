package com.mentalfrostbyte.jello.gui.base.interfaces;

import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.ModsPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.BindTarget;

public interface BindSelectionListener {
   void onSelect(ModsPanel panel, BindTarget target);
}
