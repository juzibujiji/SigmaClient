package com.mentalfrostbyte.jello.module.data;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ModuleSettingInitializr implements Runnable {
    public static Thread thisThread = new Thread(new ModuleSettingInitializr());
    public static HashMap<Object, Integer> modOffsetMap;

    static {
        thisThread.start();
    }

    @Override
    public void run() {
        modOffsetMap = new HashMap<>();
        List<Module> mods = null;

        while (!Thread.currentThread().isInterrupted()) {
            if (Minecraft.getInstance().world == null || Client.getInstance().moduleManager == null) return;
            if (mods == null)
                mods = new ArrayList<>(Client.getInstance().moduleManager.getModuleMap().values());

			for (Module mod : mods) {
                if (mod instanceof ModuleWithModuleSettings) {
                    mods.addAll(Arrays.asList(((ModuleWithModuleSettings) mod).moduleArray));
                }
            }

            for (Module mod : mods) {
                if (mod.getClass().getSuperclass() != Module.class && mod.getClass().getSuperclass() != ModuleWithModuleSettings.class) {
                    modOffsetMap.put(mod, mod.getRandomAssOffset());
                }
            }
        }
    }
}
