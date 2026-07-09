/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.ChatUtils (referenced by upstream sources; not published in
 * the upstream repo — reconstructed from usage: ChatUtils.info("working")).
 *
 * Chat output is delegated to this client's MinecraftUtil.addChatMessage — the only piece of
 * host infrastructure available for client chat lines ("实在没有" fallback).
 */
package com.mentalfrostbyte.jello.southside.utils;

import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import net.minecraft.client.Minecraft;

public final class SSChatUtils {
    private SSChatUtils() {
    }

    public static void info(String message) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        MinecraftUtil.addChatMessage(message);
    }
}
