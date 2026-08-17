package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import net.minecraft.network.play.client.CPlayerPacket;

public class Sync extends Command {
    public Sync() {
        super("sync", "Resyncs chunks and server data", "resync");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length > 0) {
            throw new CommandException("Too many arguments");
        } else if (mc.player == null || mc.getConnection() == null) {
            throw new CommandException("You are not connected to a server");
        } else {
            mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(
                    mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ(), mc.player.isOnGround()));

            int viewDistance = mc.gameSettings.renderDistanceChunks;
            if (viewDistance > 2) {
                mc.gameSettings.renderDistanceChunks = 2;
                mc.gameSettings.sendSettingsToServer();
                mc.gameSettings.renderDistanceChunks = viewDistance;
            }
            mc.gameSettings.sendSettingsToServer();

            mc.worldRenderer.loadRenderers();
            executor.send("Resyncing chunks & server data...");
        }
    }
}
