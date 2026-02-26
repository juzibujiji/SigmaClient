package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.managers.util.command.CommandType;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.math.MathHelper;

import java.util.Collections;

public class HClip extends Command {
    public HClip() {
        super("hclip", "Horizontal clip", "hc");
        this.registerSubCommands("offset");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length != 0) {
            if (args.length <= 1) {
                if (args[0].getCommandType() != CommandType.NUMBER) {
                    throw new CommandException("Invalid distance \"" + args[0].getArguments() + "\"");
                } else {
                    float var6 = (float) Math.toRadians(mc.player.rotationYaw + 90.0F);
                    double var7 = (double) MathHelper.cos(var6) * args[0].getDouble();
                    double var9 = (double) MathHelper.sin(var6) * args[0].getDouble();
                    mc.getConnection()
                            .handlePlayerPosLook(
                                    new SPlayerPositionLookPacket(
                                            mc.player.getPosX() + var7,
                                            mc.player.getPosY(),
                                            mc.player.getPosZ() + var9,
                                            mc.player.rotationYaw,
                                            mc.player.rotationPitch,
                                            Collections.emptySet(),
                                            0
                                    )
                            );
                    executor.send("Successfully HClip'd");
                }
            } else {
                throw new CommandException("Too many arguments");
            }
        } else {
            throw new CommandException();
        }
    }
}