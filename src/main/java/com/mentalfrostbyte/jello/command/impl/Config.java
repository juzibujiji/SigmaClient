package com.mentalfrostbyte.jello.command.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.managers.util.command.CommandType;
import com.mentalfrostbyte.jello.managers.util.profile.Profile;
import com.mentalfrostbyte.jello.util.client.ClientMode;

import java.util.ArrayList;
import java.util.Arrays;

public class Config extends Command {
    private static final String configFolder = "/profiles/";
    private static final String configFileExtension = ".profile";

    private final ArrayList<String> saveCommands = new ArrayList<>(Arrays.asList("add", "create", "new", "save"));
    private final ArrayList<String> deleteCommands = new ArrayList<>(
            Arrays.asList("remove", "delete", "del", "rem"));

    public Config() {
        super("config", "Manage configs", "configs", "profiles", "profile");
        this.registerSubCommands("load", "save", "remove", "list");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length == 0) {
            throw new CommandException();
        } else if (args.length <= 2) {
            if (args[0].getCommandType() != CommandType.TEXT) {
                throw new CommandException();
            } else {
                String action = args[0].getArguments().toLowerCase();

                if (!action.equalsIgnoreCase("load")) {
                    if (!this.saveCommands.contains(action)) {
                        if (!this.deleteCommands.contains(action)) {
                            if (!action.equalsIgnoreCase("list")) {
                                throw new CommandException();
                            }

                            executor.send(
                                    "§l" + Client.getInstance().moduleManager.getConfigurationManager().getAllConfigs().size()
                                            + " " + this.getConfigOrProfileName() + " :");

                            for (Profile config : Client.getInstance().moduleManager.getConfigurationManager()
                                    .getAllConfigs()) {
                                boolean isCurrentConfig = Client.getInstance().moduleManager.getConfigurationManager()
                                        .getCurrentConfig() == config;
                                if (Client.getInstance().clientMode != ClientMode.CLASSIC || !isCurrentConfig) {
                                    executor.send((!isCurrentConfig ? "" : "§n") + config.profileName);
                                }
                            }
                        } else if (args.length != 1) {
                            String name = args[1].getArguments().toLowerCase();
                            if (!Client.getInstance().moduleManager.getConfigurationManager().removeConfig(name)) {
                                executor.send(this.getConfigOrProfileName() + " not found!");
                            } else {
                                executor.send("Removed " + this.getConfigOrProfileName());
                            }
                        } else {
                            executor.send("Usage : .config remove <name>");
                        }
                    } else if (args.length != 1) {
                        String name = args[1].getArguments().toLowerCase();
                        String ogName = args[1].getArguments();
                        Profile currentConfig = Client.getInstance().moduleManager.getConfigurationManager()
                                .getCurrentConfig();
                        currentConfig.moduleConfig = Client.getInstance().moduleManager
                                .loadCurrentConfig(new JsonObject());
                        Client.getInstance().moduleManager.getConfigurationManager().removeConfig(name);
                        Client.getInstance().moduleManager.getConfigurationManager()
                                .saveConfig(new Profile(name, currentConfig.moduleConfig));
                        executor.send("Saved " + this.getConfigOrProfileName());
                    } else {
                        executor.send("Usage : .config save <name>");
                    }
                } else if (args.length != 1) {
                    String name = args[1].getArguments().toLowerCase();
                    Profile config = Client.getInstance().moduleManager.getConfigurationManager()
                            .getConfigByName(name);
                    if (config == null) {
                        executor.send(this.getConfigOrProfileName() + " not found!");
                    } else {
                        Client.getInstance().moduleManager.getConfigurationManager().loadConfig(config);
                        executor.send(this.getConfigOrProfileName() + " was loaded!");
                    }
                } else {
                    executor.send("Usage : .config load <name>");
                }
            }
        } else {
            throw new CommandException("Too many arguments");
        }
    }

    public String getConfigOrProfileName() {
        return Client.getInstance().clientMode == ClientMode.JELLO ? "Profile" : "Config";
    }
}
