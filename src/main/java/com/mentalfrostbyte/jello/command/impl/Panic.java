package com.mentalfrostbyte.jello.command.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.ProfileManager;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.managers.util.profile.Profile;

public class Panic extends Command {
    public Panic() {
        super("panic", "Disable all modules");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length > 0) {
            throw new CommandException("Too many arguments");
        } else {
            ProfileManager profileManager = Client.getInstance().moduleManager.getConfigurationManager();
            if (profileManager.getConfigByCaseInsensitiveName("Panic")) {
                int configCount = profileManager.getAllConfigs().size();

                for (int var8 = 0; var8 < configCount; var8++) {
                    Profile var9 = profileManager.getAllConfigs().get(var8);
                    if (var9.profileName.equals("Panic")) {
                        profileManager.checkConfig(var9);
                        var8--;
                        configCount--;
                    }
                }
            }

            Profile panicConfig = new Profile("Panic", new JsonObject());
            profileManager.saveConfig(panicConfig);
            profileManager.loadConfig(panicConfig);
            executor.send("All modules disabled.");
        }
    }
}
