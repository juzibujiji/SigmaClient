package com.mentalfrostbyte.jello.managers;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.command.impl.*;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.managers.data.Manager;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import com.mentalfrostbyte.jello.util.client.ClientMode;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.network.play.client.CTabCompletePacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.List;

public class CommandManager extends Manager {
    public static final String CHAT_COMMAND_CHAR = ".";
    public static final String CHAT_PREFIX = "§f[§6Sigma§f]§7";
    private static final List<Runnable> runnableList = new ArrayList<>();
    public List<Command> commands = new ArrayList<>();
    private boolean field38298 = true;

    public static void runRunnable(Runnable runnable) {
        runnableList.add(runnable);
    }

    @Override
    public void init() {
        super.init();
        this.register(new VClip());
        this.register(new HClip());
        this.register(new Damage());
        this.register(new ClearChat());
        this.register(new EntityDesync());
        this.register(new Peek());
        this.register(new Insult());
        this.register(new Bind());
        this.register(new Help());
        this.register(new Friend());
        this.register(new Enemy());
        this.register(new Toggle());
        this.register(new Config());
        this.register(new Panic());
        this.register(new HighDPI());
        this.register(new KillPotion());
        this.register(new Enchant());
        this.register(new TP());
    }

    public Command getCommandByName(String var1) {
        for (Command var5 : this.commands) {
            if (var5.getName().equals(var1)) {
                return var5;
            }
        }

        for (Command command : this.commands) {
            for (String var9 : command.getAlias()) {
                if (var9.equals(var1)) {
                    return command;
                }
            }
        }

        return null;
    }

    public List<Command> getCommands() {
        return this.commands;
    }

    private void register(Command command) {
        this.commands.add(command);
    }

    public void invalidCommandMessage(String name) {
        MinecraftUtil.addChatMessage(this.getPrefix() + " Invalid command \"" + "." + name + "\"");
        MinecraftUtil.addChatMessage(this.getPrefix() + " Use \"" + "." + "help\" for a list of commands.");
    }

    public String getPrefix() {
        if (this.field38298) {
            this.field38298 = false;
            return "§f[§6Sigma§f]§7";
        } else {
            StringBuilder var3 = new StringBuilder();

            for (int var4 = 0; var4 < 8; var4++) {
                var3.append(" ");
            }

            return var3 + "§7";
        }
    }

    public void method30236() {
        this.field38298 = true;
    }

    @EventTarget
    public void method30237(EventUpdate var1) {
        for (Runnable var5 : runnableList) {
            var5.run();
        }

        runnableList.clear();
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (Client.getInstance().clientMode != ClientMode.NOADDONS) {
            if (event.packet instanceof CChatMessagePacket packet) {
				String message = packet.getMessage();
                if (message.startsWith(".") && message.substring(1).startsWith(".")) {
                    packet.message = message.substring(1);
                    return;
                }

                if (message.startsWith(".")) {
                    event.cancelled = true;
                    this.method30236();
                    String[] parts = message.substring(".".length()).split(" ");
                    Command command = this.getCommandByName(parts[0]);
                    if (command == null) {
                        this.invalidCommandMessage(parts[0]);
                        return;
                    }

                    List<ChatCommandArguments> args = new ArrayList<>();

                    for (int i = 1; i < parts.length; i++) {
                        args.add(new ChatCommandArguments(parts[i]));
                    }

                    MinecraftUtil.addChatMessage(" ");

                    try {
                        command.run(message, args.toArray(new ChatCommandArguments[0]), msg -> MinecraftUtil.addChatMessage(this.getPrefix() + " " + msg));
                    } catch (CommandException exception) {
                        if (!exception.reason.isEmpty()) {
                            MinecraftUtil.addChatMessage(this.getPrefix() + " Error: " + exception.reason);
                        }

                        MinecraftUtil.addChatMessage(this.getPrefix() + " Usage: " + "." + command.getName() + " " + command.getOptions());
                    }

                    MinecraftUtil.addChatMessage(" ");
                }
            }

            if (event.packet instanceof CTabCompletePacket packet) {
                if (packet.getCommand().startsWith(".")) {
                    event.cancelled = true;
                }
            }
        }
    }
}