package com.mentalfrostbyte.jello.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.action.EventKeyPress;
import com.mentalfrostbyte.jello.gui.impl.others.ChatUtil;
import com.mentalfrostbyte.jello.managers.data.Manager;
import team.sdhq.eventBus.annotations.EventTarget;
import net.minecraft.entity.Entity;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FriendManager extends Manager {
    public List<String> pureTextFriends = new CopyOnWriteArrayList<>();
    public List<String> enemies = new CopyOnWriteArrayList<>();
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void init() {
        super.init();

        try {
            this.loadFromCurrentConfig();
        } catch (JsonParseException e) {
            Client.logger.warn(e);
        }
    }

    @EventTarget
    public void onKeyPress(EventKeyPress event) throws JsonParseException {
        if (event.getKey() == this.mc.gameSettings.keyBindPickBlock.keyCode.getKeyCode()
                && this.mc.pointedEntity != null
                && this.mc.pointedEntity.getName() != null) {
            CommandManager var4 = Client.getInstance().commandManager;
            var4.method30236();
            if (this.isFriendPure(this.mc.pointedEntity)) {
                this.method27005(this.mc.pointedEntity.getName().getUnformattedComponentText());
                ChatUtil.printMessage(
                        var4.getPrefix() + " " + this.mc.pointedEntity.getName().getUnformattedComponentText() + " is no longer your friend."
                );
            } else {
                this.method27001(this.mc.pointedEntity.getName().getUnformattedComponentText());
                ChatUtil.printMessage(var4.getPrefix() + " " + this.mc.pointedEntity.getName().getUnformattedComponentText() + " is now your friend.");
            }

            this.saveFriends();
        }
    }

    public boolean isFriendPure(Entity name) {
        return this.pureTextFriends.contains(name.getName().getUnformattedComponentText().toLowerCase());
    }

    public boolean isFriendPure(String name) {
        return this.pureTextFriends.contains(name.toLowerCase());
    }

    public boolean isFriend(Entity name) {
        return this.enemies.contains(name.getName().getUnformattedComponentText().toLowerCase());
    }

    public boolean isFriend(String var1) {
        return this.enemies.contains(var1.toLowerCase());
    }

    public boolean method27001(String var1) {
        if (this.isFriendPure(var1)) {
            return false;
        } else {
            this.pureTextFriends.add(var1.toLowerCase());
            this.saveFriends();
            return true;
        }
    }

    public boolean method27002(String var1) {
        if (this.isFriend(var1)) {
            return false;
        } else {
            this.enemies.add(var1.toLowerCase());
            this.saveEnemies();
            return true;
        }
    }

    public List<String> method27003() {
        return this.pureTextFriends;
    }

    public List<String> method27004() {
        return this.enemies;
    }

    public boolean method27005(String var1) {
        boolean var4 = this.pureTextFriends.remove(var1.toLowerCase());
        if (var4) {
            this.saveFriends();
        }

        return var4;
    }

    public boolean method27006(String var1) {
        boolean var4 = this.enemies.remove(var1.toLowerCase());
        if (var4) {
            this.saveEnemies();
        }

        return var4;
    }

    public boolean method27007() {
        if (!this.pureTextFriends.isEmpty()) {
            this.pureTextFriends.clear();
            this.saveFriends();
            return true;
        } else {
            return false;
        }
    }

    public boolean method27008() {
        if (!this.enemies.isEmpty()) {
            this.enemies.clear();
            this.saveEnemies();
            return true;
        } else {
            return false;
        }
    }

    public void saveFriends() {
        JsonArray friendsArray = new JsonArray();
        for (String friend : this.pureTextFriends) {
            friendsArray.add(friend);
        }

        Client.getInstance().config.add("friends", friendsArray);
    }

    public void saveEnemies() {
        JsonArray enemiesArray = new JsonArray();
        for (String enemy : this.enemies) {
            enemiesArray.add(enemy);
        }

        Client.getInstance().config.add("enemies", enemiesArray);
    }

    private void loadFromCurrentConfig() throws JsonParseException {
        this.pureTextFriends.clear();
        this.enemies.clear();

        if (Client.getInstance().config.has("friends")) {
            JsonArray var3 = Client.getInstance().config.getAsJsonArray("friends");
            if (var3 != null) {
                var3.forEach(var1 -> this.pureTextFriends.add(var1.getAsString()));
            }
        }

        if (Client.getInstance().config.has("enemies")) {
            JsonArray var4 = Client.getInstance().config.getAsJsonArray("enemies");
            if (var4 != null) {
                var4.forEach(var1 -> this.enemies.add(var1.getAsString()));
            }
        }
    }
}