package com.mentalfrostbyte.jello.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.interfaces.OnlineProfileListener;
import com.mentalfrostbyte.jello.managers.util.profile.Profile;
import com.mentalfrostbyte.jello.module.Module;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OnlineProfilesManager {
    public static List<String> cachedOnlineProfiles;

    public OnlineProfilesManager(OnlineProfileListener listener) {
        new Thread(() -> {
            if (cachedOnlineProfiles == null) {
                cachedOnlineProfiles = this.fetchOnlineProfiles();
            }

            listener.onProfilesRetrieved(cachedOnlineProfiles);
        }).start();
    }

    public List<String> fetchOnlineProfiles() {
        try {
            HttpGet request = new HttpGet("https://jelloconnect.sigmaclient.cloud/profiles.php?v=" + Client.RELEASE_TARGET);
            CloseableHttpResponse response = HttpClients.createDefault().execute(request);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream inputStream = entity.getContent()) {
                    String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    JsonArray jsonArray = JsonParser.parseString(content).getAsJsonArray();
                    List<String> profileNames = new ArrayList<>();

                    for (int i = 0; i < jsonArray.size(); i++) {
                        profileNames.add(jsonArray.get(i).getAsString());
                    }
                    return profileNames;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    private String encode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    public JsonObject fetchProfileConfig(String profileName) {
        try {
            HttpGet request = new HttpGet("https://jelloconnect.sigmaclient.cloud/profiles/" + encode(profileName) + ".profile?v=" + Client.RELEASE_TARGET);
            CloseableHttpResponse response = HttpClients.createDefault().execute(request);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream inputStream = entity.getContent()) {
                    String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    return JsonParser.parseString(content).getAsJsonObject();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new JsonObject();
    }

    public Profile createProfileFromOnlineConfig(Profile baseProfile, String profileName) {
        Profile newProfile = new Profile(profileName, baseProfile);
        try {
            newProfile.disableNonGuiModules();
            Profile settingsProfile = new Profile("settings", fetchProfileConfig(profileName).getAsJsonObject("modConfig"));
            for (Module module : Client.getInstance().moduleManager.getModuleMap().values()) {
                JsonObject moduleConfig = settingsProfile.getModuleConfig(module);
                if (moduleConfig != null) {
                    newProfile.updateModuleConfig(moduleConfig, module);
                }
            }
        } catch (JsonParseException e) {
            throw new RuntimeException("Failed to parse profile configuration", e);
        }
        return newProfile;
    }
}
