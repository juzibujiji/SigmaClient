package com.mentalfrostbyte.jello.command.impl;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders.KeyboardHolder;
import com.mentalfrostbyte.jello.managers.CommandManager;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.module.Module;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.text.StringTextComponent;

import java.util.Map.Entry;

public class Bind extends Command {
    public Bind() {
        super("bind", "Bind a module to a key");
        this.registerSubCommands("module");
        this.registerSubCommands("key/none");
    }

    @Override
    public void run(String var1, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length == 0) {
            CommandManager.runRunnable(() ->
                    mc.displayGuiScreen(new KeyboardHolder(new StringTextComponent("GuiKeybinds"))));
            return;
        }

        if (args.length == 1) {
            Module module = getModuleByName(args[0].getArguments());
            if (module == null)
                throw new CommandException("Module " + args[0].getArguments() + " not found");

            int key = Client.getInstance().moduleManager.getKeyManager().method13729(module);
            String keyPrefix = "key.keyboard.";
            String foundKey = null;

            for (Entry<String, InputMappings.Input> entry : InputMappings.Input.REGISTRY.entrySet()) {
                if (entry.getKey().startsWith(keyPrefix) && entry.getValue().getKeyCode() == key) {
                    foundKey = entry.getKey().substring(keyPrefix.length());
                    break;
                }
            }

            if (foundKey != null) {
                executor.send(module.getFormattedName() + " is bound to: " + foundKey);
            } else {
                executor.send("§c[Error] " + module.getFormattedName() + " is bound to an unknown key");
            }
            return;
        }

        if (args.length == 2) {
            Module module = getModuleByName(args[0].getArguments());
            if (module == null)
                throw new CommandException("Module " + args[0].getArguments() + " not found");

            int keyCode = getKeyCodeFromString(args[1].getArguments().toLowerCase());
            if (keyCode == -2)
                throw new CommandException("Key " + args[1].getArguments() + " not found");

            if (keyCode == -1) {
                Client.getInstance().moduleManager.getKeyManager().method13727(module);
                executor.send("Keybind was reset for module " + module.getFormattedName());
            } else {
                Client.getInstance().moduleManager.getKeyManager().method13725(keyCode, module);
                executor.send("Key " + args[1].getArguments() + " was set for module " + module.getFormattedName());
            }
            return;
        }

        throw new CommandException("Too many arguments");
    }

    public int getKeyCodeFromString(String name) {
        if (name.equals("none")) return -1;

        String keyPrefix = "key.keyboard.";
        for (Entry<String, InputMappings.Input> entry : InputMappings.Input.REGISTRY.entrySet()) {
            if (entry.getKey().startsWith(keyPrefix)) {
                String formatted = entry.getKey().substring(keyPrefix.length())
                        .replace("keypad.", "")
                        .replace(".", "_");

                if (name.equalsIgnoreCase(formatted))
                    return entry.getValue().getKeyCode();
            }
        }

        return -2; // not found
    }

    public Module getModuleByName(String name) {
        String input = name.replaceAll("\\s+", "").toLowerCase();

        for (Module mod : Client.getInstance().moduleManager.getModuleMap().values()) {
            String modName = mod.getName()
                    .replaceAll("§.", "")
                    .replaceAll("\\s+", "")     
                    .toLowerCase();

            if (modName.equals(input)) {
                return mod;
            }
        }

        return null;
    }
}
