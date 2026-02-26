package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.managers.util.command.CommandType;

import java.util.List;

public class Help extends Command {
    public Help() {
        super("help", "Show this help dialog", "vc");
        this.registerSubCommands("page/command");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        List var6 = Client.getInstance().commandManager.getCommands();
        int var7 = (int) Math.ceil((float) var6.size() / 7.0F);
        int var8 = args.length == 1 && args[0].getCommandType() == CommandType.NUMBER ? args[0].getInt() - 1 : 0;
        if (args.length == 1 && args[0].getCommandType() == CommandType.TEXT) {
            Command command = Client.getInstance().commandManager.getCommandByName(args[0].getArguments());
            if (command == null) {
                throw new CommandException();
            } else {
                executor.send("§f" + command.getName() + "§8" + " > " + "§7" + command.getDescription());
                if (command.getOptions().length() <= 0) {
                    executor.send("   [no options]");
                } else {
                    executor.send("   " + command.getOptions());
                }
            }
        } else if (args.length <= 1) {
            if (var8 + 1 <= var7 && var8 >= 0) {
                executor.send("§fHelp:§7 Page " + (var8 + 1) + "/" + var7);
                executor.send("");

                for (int var9 = 0; var9 < 7; var9++) {
                    int var10 = var9 + var8 * 7;
                    if (var6.size() > var10) {
                        Command var11 = (Command) var6.get(var10);
                        executor.send("§f" + var11.getName() + "§8" + " > " + "§7" + var11.getDescription());
                        if (var11.getOptions().isEmpty()) {
                            executor.send("   [no options]");
                        } else {
                            executor.send("   " + var11.getOptions());
                        }
                    }
                }
            } else {
                throw new CommandException("Page " + var8 + " does not exist!");
            }
        } else {
            throw new CommandException();
        }
    }
}