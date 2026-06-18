package com.elfmcys.yesstevemodel.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.FontRenderer;

public interface OpenYsmScreenSkin {
    void panel(MatrixStack matrices, int left, int top, int right, int bottom);

    void row(MatrixStack matrices, int left, int top, int right, int bottom, boolean hovered, boolean selected);

    void button(MatrixStack matrices, FontRenderer font, String label, int left, int top, int right, int bottom,
                boolean hovered, int textColor);
}
