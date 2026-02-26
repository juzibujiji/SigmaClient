package com.mentalfrostbyte.jello.managers.util.account.microsoft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.Minecraft;

import java.util.Calendar;
import java.util.Date;

public class Ban {
    private final String serverIP;
    private final Date date;

    public Ban(String serverIP, Date date) {
        this.serverIP = serverIP;
        this.date = date;
    }

    public Ban(JsonObject var1) throws JsonParseException {
        Calendar var4 = Calendar.getInstance();
        long bannedUntil;
        if (!(var1.get("until").getAsNumber() instanceof Integer)) {
            bannedUntil = var1.get("until").getAsLong();
        } else {
            bannedUntil = ((Integer) var1.get("until").getAsInt()).longValue();
        }

        if (bannedUntil == 1L) {
            bannedUntil = 9223372036854775806L;
        }

        var4.setTimeInMillis(bannedUntil);
        this.serverIP = var1.get("server").getAsString();
        this.date = var4.getTime();
    }

    public JsonObject asJSONObject() {
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("server", this.serverIP);
        jsonObj.addProperty("until", this.date.getTime());
        return jsonObj;
    }

    public String getServerIP() {
        return this.serverIP;
    }

    public Date getDate() {
        return this.date;
    }

    public ServerData getServer() {
        ServerList serverList = new ServerList(Minecraft.getInstance());
        serverList.loadServerList();
        int count = serverList.countServers();

        for (int i = 0; i < count; i++) {
            ServerData serverData = serverList.getServerData(i);
            if (serverData.serverIP.equals(this.serverIP)) {
                return serverData;
            }
        }

        return null;
    }
}
