package com.mentalfrostbyte.jello.gui.base.elements;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.gui.base.interfaces.IHandler;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import org.newdawn.slick.TrueTypeFont;

import java.util.ArrayList;
import java.util.List;

public class Element extends AnimatedIconPanel {
    private final List<IHandler> iHandlers = new ArrayList<IHandler>();

    public Element(CustomGuiScreen screen, String typeThingIdk, int x, int y, int width, int height, boolean var7) {
        super(screen, typeThingIdk, x, y, width, height, var7);
    }

    public Element(CustomGuiScreen screen, String typeThingIdk, int x, int y, int width, int height, ColorHelper var7, boolean var8) {
        super(screen, typeThingIdk, x, y, width, height, var7, var8);
    }

    public Element(CustomGuiScreen screen, String typeThingIdk, int x, int y, int width, int height, ColorHelper var7, String text, boolean var9) {
        super(screen, typeThingIdk, x, y, width, height, var7, text, var9);
    }

    public Element(CustomGuiScreen screen, String typeThingIdk, int x, int y, int width, int height, ColorHelper var7, String var8, TrueTypeFont font, boolean var10) {
        super(screen, typeThingIdk, x, y, width, height, var7, var8, font, var10);
    }

    public final void onPress(IHandler iHandler) {
        this.iHandlers.add(iHandler);
    }

    public final void callUIHandlers() {
        for (IHandler handler : this.iHandlers) {
            handler.handle(this);
        }
    }
}
