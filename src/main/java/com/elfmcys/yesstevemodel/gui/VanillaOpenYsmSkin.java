package com.elfmcys.yesstevemodel.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;

public final class VanillaOpenYsmSkin implements OpenYsmScreenSkin {
    public static final VanillaOpenYsmSkin INSTANCE = new VanillaOpenYsmSkin();

    private VanillaOpenYsmSkin() {
    }

    @Override
    public void panel(MatrixStack matrices, int left, int top, int right, int bottom) {
        AbstractGui.fill(matrices, left, top, right, bottom, 0xAA05080C);
        AbstractGui.fill(matrices, left, top, right, top + 1, 0x66B9D7FF);
        AbstractGui.fill(matrices, left, bottom - 1, right, bottom, 0x55233244);
    }

    @Override
    public void row(MatrixStack matrices, int left, int top, int right, int bottom, boolean hovered, boolean selected) {
        int color = selected ? 0xAA2EA44F : hovered ? 0x774A6C86 : 0x44000000;
        AbstractGui.fill(matrices, left, top, right, bottom, color);
    }

    @Override
    public void button(MatrixStack matrices, FontRenderer font, String label, int left, int top, int right, int bottom,
                       boolean hovered, int textColor) {
        AbstractGui.fill(matrices, left, top, right, bottom, hovered ? 0xAA4A6C86 : 0x88405060);
        AbstractGui.drawCenteredString(matrices, font, label, (left + right) / 2,
                top + Math.max(4, (bottom - top - 8) / 2), textColor);
    }
}
