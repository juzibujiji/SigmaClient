package com.elfmcys.yesstevemodel.gui;

import net.minecraft.client.Minecraft;

public final class OpenYsmScreens {
    private OpenYsmScreens() {
    }

    public static void openModelManager() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.displayGuiScreen(new OpenYsmModelSelectionScreen());
        }
    }

    public static void openActionWheel() {
        openActionWheel(-1);
    }

    public static void openActionWheel(int heldKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.displayGuiScreen(new OpenYsmActionWheelScreen(heldKey));
        }
    }

    public static void openExtraPlayerRender() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.displayGuiScreen(new OpenYsmExtraPlayerRenderScreen());
        }
    }
}
