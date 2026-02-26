package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.module.Module;

public class Toggle extends Command {
    public Toggle() {
        super("toggle", "Toggle a module", "t");
        this.registerSubCommands("module");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length != 1) {
            throw new CommandException();
        } else {
            Module module = this.getModuleByName(args[0].getArguments());
            if (module != null) {
                module.setEnabled(!module.isEnabled());
                executor.send(module.getName() + " was " + (!module.isEnabled() ? "disabled" : "enabled"));
            } else {
                throw new CommandException("Module \"" + args[0].getArguments() + "\" not found");
            }
        }
    }

    public Module getModuleByName(String name) {
        for (Module var5 : Client.getInstance().moduleManager.getModuleMap().values()) {
            if (var5.getName().replace(" ", "").equalsIgnoreCase(name)) {
                return var5;
            }
        }

        return null;
    }
}
