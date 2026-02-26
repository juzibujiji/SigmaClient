package com.mentalfrostbyte.jello.module.impl.misc;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.util.client.spam.AutoLData;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.misc.gameplay.*;

import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.InputSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.system.math.counter.TimerUtil;
import net.minecraft.client.gui.screen.ChatScreen;
import com.mentalfrostbyte.jello.util.client.logger.TimedMessage;
import team.sdhq.eventBus.annotations.EventTarget;


import java.util.ArrayList;
import java.util.Collections;

public class GamePlay extends ModuleWithModuleSettings {
    public ArrayList<String> autoLQueue = new ArrayList<String>();
    public final ArrayList<String> autoLMessages = new ArrayList<String>();
    public TimedMessage timedMessage;
    public final TimerUtil timer;
    public int seconds;

    public GamePlay() {
        super(
                ModuleCategory.MISC,
                "GamePlay",
                "Manage your gameplay experience just for you.",
                new HypixelGamePlay(),
                new CubecraftGamePlay(),
                new MineplexGamePlay(),
                new MinibloxGamePlay(),
                new FuncraftGameplay(),
                new JartexGamePlay()
        );
        registerSetting(new BooleanSetting("AutoL", "Automatically says L when you kill a player", true));
        registerSetting(new ModeSetting("AutoL Mode", "AutoL Mode", 0, "Basic", "Miniblox", "Sigmeme", "Penshen")
                .addObserver(var1 -> this.autoLQueue.clear()));
        registerSetting(new InputSetting("First character", "The characters your sentences will start with.", ""));
        registerSetting(new BooleanSetting("AutoGG", "Automatically say gg at the end of the game", true));
        registerSetting(new BooleanSetting("Auto Join", "Automatically joins another game", true));
        registerSetting(new NumberSetting<>("Auto Join delay", "Seconds before joining a new game", 4.0F, 1.0F, 10.0F, 1.0F));
        timer = new TimerUtil();
    }

    @Override
    public void onEnable() {
        if (!timer.isEnabled()) {
            timer.start();
        }

        timedMessage = null;
        autoLQueue.clear();
        autoLMessages.clear();
    }

    @Override
    public void onDisable() {
        timer.reset();
        timer.stop();
        timedMessage = null;
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (isEnabled()) {
            if (timedMessage != null) {
                if (mc.currentScreen instanceof ChatScreen) {
                    updateTimedMessage(null);
                    Client.getInstance().notificationManager.send(new Notification("Auto Join", "Auto join was canceled.", 2500));
                } else if (timedMessage.hasExpired()) {
                    MinecraftUtil.sendChatMessage(timedMessage.getMessage());
                    updateTimedMessage(null);
                } else if ((int) (timedMessage.getRemainingTime() / 1000L) + 1 < seconds) {
                    seconds = (int) (timedMessage.getRemainingTime() / 1000L) + 1;
                    Client.getInstance()
                            .notificationManager
                            .send(
                                    new Notification("Auto Join", "Joining a new game in " + seconds + " second" + (seconds > 1 ? "s" : "") + ".", 2000)
                            );
                }
            }

            if (!timer.isEnabled()) {
                timer.start();
            }

            if (!autoLMessages.isEmpty()) {
                String type = getStringSettingValueByName("Type");

                if (mc.player.ticksExisted <= 3) {
                    autoLMessages.clear();
                }

                long var5 = 3200L;
                if (!type.equalsIgnoreCase("Hypixel")) {
                    var5 = 0L;
                }

                if (type.equalsIgnoreCase("Mineplex")) {
                    var5 = 300L;
                }

                if (type.equalsIgnoreCase("Funcraft")) {
                    var5 = 1000L;
                }

                if (type.equalsIgnoreCase("Jartex")) {
                    var5 = 3200L;
                }

                if (timer.getElapsedTime() > var5 && !autoLMessages.isEmpty()) {
                    timer.reset();
                    String message = autoLMessages.get(0);
                    MinecraftUtil.sendChatMessage(message);
                    autoLMessages.remove(0);
                }
            }
        }
    }

    public void updateTimedMessage(TimedMessage timedMessage) {
        this.timedMessage = timedMessage;
        if (timedMessage != null) {
            seconds = (int) (timedMessage.getRemainingTime() / 1000L) + 1;
        }
    }

    public void initializeAutoL() {
        autoLMessages.add("gg");
    }

    public void processAutoLMessage(String input) {
        String[] parts = input.split(" ");
        String playerName = parts[0];

        String serverType = getStringSettingValueByName("Type");
        if ("Mineplex".equals(serverType) || "Funcraft".equals(serverType)) {
            playerName = parts[1];
        }

        switch (getStringSettingValueByName("AutoL Mode")) {
            case "Basic":
                autoLMessages.add(getStringSettingValueByName("First character") + "L " + playerName);
                break;
            case "Sigmeme":
            case "Miniblox":
            case "Penshen":
                if (autoLQueue.isEmpty()) {
                    switch (getStringSettingValueByName("AutoL Mode")) {
                        case "Sigmeme":
                            autoLQueue = new ArrayList<>(AutoLData.SIGMEME_QUOTES);
                            break;
                        case "Miniblox":
                            autoLQueue = new ArrayList<>(AutoLData.MINIBLOX_QUOTES);
                            break;
                        case "Penshen":
                            autoLQueue = new ArrayList<>(AutoLData.PENSHEN_QUOTES);
                            break;
                    }
                    Collections.shuffle(autoLQueue);
                }

                String autoLMessage = autoLQueue.get(0);

                if ("Cubecraft".equals(serverType)) {
                    autoLMessage = autoLMessage.replaceAll("sigma", "ＳＩＧＭＡ").replaceAll("Sigma", "ＳＩＧＭＡ");
                }
                if ("Miniblox".equals(serverType)) {
                    autoLMessage = autoLMessage.replaceAll("suck", "s\u200Buck");
                }

                autoLMessage = getStringSettingValueByName("First character") + autoLMessage;
                autoLMessages.add(autoLMessage);
                autoLQueue.remove(0);
                break;
        }
    }
}
