package com.mentalfrostbyte.jello.gui.base.interfaces;

import com.mentalfrostbyte.jello.gui.base.elements.Element;

// Naming reason: `callUIHandlers` in `UIBase` calls everything in `uiHandlers` (which is a list of `UIHandler`s)
// and passes itself as the argument
public interface IHandler {
   void handle(Element element);
}
