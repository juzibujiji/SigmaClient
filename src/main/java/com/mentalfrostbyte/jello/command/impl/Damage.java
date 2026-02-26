package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.managers.util.command.CommandType;
import net.minecraft.network.play.client.CPlayerPacket;

public class Damage extends Command {
    public Damage() {
        super("damage", "Damages you", "dmg");
        this.registerSubCommands("hearts");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length == 0) {
            throw new CommandException();
        } else if (args.length > 1) {
            throw new CommandException("Too many arguments");
        } else if (args[0].getCommandType() != CommandType.NUMBER) {
            throw new CommandException("Invalid heart damage amount \"" + args[0].getArguments() + "\"");
        } else {
            for (int var6 = 0; (double) var6 < 80.0 + 40.0 * (args[0].getDouble() - 0.5); var6++) {
                mc.getConnection()
                        .sendPacket(
                                new CPlayerPacket.PositionPacket(mc.player.getPosX(), mc.player.getPosY() + 0.06, mc.player.getPosZ(), false)
                        );
                mc.getConnection()
                        .sendPacket(new CPlayerPacket.PositionPacket(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ(), false));
            }

            mc.getConnection()
                    .sendPacket(new CPlayerPacket.PositionPacket(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ(), false));
            mc.getConnection()
                    .sendPacket(new CPlayerPacket.PositionPacket(mc.player.getPosX(), mc.player.getPosY() + 0.02, mc.player.getPosZ(), false));
            executor.send("Sent damage packets");
        }
    }
}
