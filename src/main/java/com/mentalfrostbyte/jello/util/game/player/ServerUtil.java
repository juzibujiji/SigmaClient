package com.mentalfrostbyte.jello.util.game.player;

import com.mentalfrostbyte.jello.util.game.MinecraftUtil;

public class ServerUtil implements MinecraftUtil {

    public static boolean isMinemen() {
        return mc.getIntegratedServer() == null && mc.getCurrentServerData() != null && mc.getCurrentServerData().serverIP.toLowerCase().contains("minemen.club");
    }

    public static boolean isCubecraft() {
        return mc.getIntegratedServer() == null && mc.getCurrentServerData() != null && mc.getCurrentServerData().serverIP.toLowerCase().contains("cubecraft.net");
    }

    public static boolean isMineplex() {
        return mc.getIntegratedServer() == null && mc.getCurrentServerData() != null && mc.getCurrentServerData().serverIP.toLowerCase().contains("mineplex.com");
    }

    public static boolean isHypixel() {
        return mc.getIntegratedServer() == null
                && mc.getCurrentServerData() != null
                && mc.getCurrentServerData().serverIP.toLowerCase().contains("hypixel.net");
    }
}
