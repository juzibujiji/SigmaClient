package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.*;

import java.util.List;
import java.util.regex.Pattern;

public class Friend extends Command {
    public Friend() {
        super("friend", "Manage friends", "friends", "f");
        this.registerSubCommands("add/remove/list/clear");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length == 0) {
            throw new CommandException();
        } else if (args[0].getCommandType() == CommandType.TEXT) {
            String var6 = args[0].getArguments();
            String var7 = var6.toLowerCase();
            switch (var7) {
                case "add":
                    if (args.length != 2) {
                        executor.send("Usage : .friend add <name>");
                    } else {
                        Pattern var14 = Pattern.compile("[a-zA-Z0-9_]{2,16}");
                        boolean var15 = var14.matcher(args[1].getArguments()).matches();
                        if (var15) {
                            boolean var16 = Client.getInstance().friendManager.method27001(args[1].getArguments());
                            if (!var16) {
                                executor.send("\"" + args[1].getArguments() + "\" is already your friend.");
                            } else {
                                executor.send("\"" + args[1].getArguments() + "\" is now your friend.");
                            }
                        } else {
                            executor.send("Invalid name \"" + args[1].getArguments() + "\"");
                        }
                    }
                    break;
                case "remove":
                    if (args.length != 2) {
                        executor.send("Usage : .friend remove <name>");
                    } else {
                        boolean var13 = Client.getInstance().friendManager.method27005(args[1].getArguments());
                        if (!var13) {
                            executor.send("\"" + args[1].getArguments() + "\" is not your friend :(.");
                        } else {
                            executor.send("\"" + args[1].getArguments() + "\" is no longer your friend.");
                        }
                    }
                    break;
                case "list":
                    List<String> var9 = Client.getInstance().friendManager.method27003();
                    if (var9.isEmpty()) {
                        executor.send("You have no friends :(");
                    } else {
                        executor.send("Friends : (" + var9.size() + ")");
                        String var10 = "";

                        for (String var12 : var9) {
                            if (var12.equals(var9.get(var9.size() - 1))) {
                                var10 = var10.concat(var12 + ".");
                            } else {
                                var10 = var10.concat(var12 + ", ");
                            }
                        }

                        executor.send(var10);
                    }
                    break;
                case "clear":
                    if (Client.getInstance().friendManager.method27007()) {
                        executor.send("Cleared all your friends.");
                    } else {
                        executor.send("You have no friends :(.");
                    }
                    break;
                default:
                    throw new CommandException();
            }
        } else {
            throw new CommandException();
        }
    }
}