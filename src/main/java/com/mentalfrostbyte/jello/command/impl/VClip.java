package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.managers.util.command.CommandType;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;

import java.util.Collections;

public class VClip extends Command {
    public VClip() {
        super("vclip", "Vertical clip through blocks", "vc");
        this.registerSubCommands("offset");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length != 0) {
            if (args.length == 1) {
                if (args[0].getCommandType() != CommandType.NUMBER) {
                    throw new CommandException("Invalid vertical distance \"" + args[0].getArguments() + "\"");
                } else {
                    mc.getConnection()
                            .handlePlayerPosLook(
                                    new SPlayerPositionLookPacket(
                                            mc.player.getPosX(),
                                            mc.player.getPosY() + args[0].getDouble(),
                                            mc.player.getPosZ(),
                                            mc.player.rotationYaw,
                                            mc.player.rotationPitch,
                                            Collections.emptySet(),
                                            (int) (2.147483647E9 * Math.random())
                                    )
                            );
                    executor.send("VClip'd to position " + (mc.player.getPosY() + args[0].getDouble()));
                }
            } else {
                throw new CommandException("Too many arguments");
            }
        } else {
            throw new CommandException();
        }
    }
}
