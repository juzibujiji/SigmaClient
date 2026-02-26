package com.mentalfrostbyte.jello.managers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.*;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.ClientMode;
import com.mentalfrostbyte.jello.managers.util.profile.Profile;
import com.mentalfrostbyte.jello.module.data.ModuleSettingInitializr;
import org.apache.commons.io.IOUtils;

public class ProfileManager {
    private final List<Profile> savedConfigs = new ArrayList<>();
    private Profile currentConfigs;

    private static final String configFolder = "/profiles/";
    private static final String configFileExtension = ".profile";

    public void saveConfig(Profile config) {
        try {
            this.savedConfigs.add(0, config);

            File configItself = new File(Client.getInstance().file + configFolder + config.profileName + configFileExtension);

            if (configItself.getParentFile() != null) {
                configItself.getParentFile().mkdirs();
            }

            JsonObject jsonConfig = config.saveToJson(new JsonObject());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJson = gson.toJson(new com.google.gson.JsonParser().parse(jsonConfig.toString()));

            Files.write(configItself.toPath(), prettyJson.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save config: " + config.profileName, e);
        }
    }

    public void listOnly(Profile config) {
        this.savedConfigs.add(config);
    }

    public void removeConfig(Profile config) {
        this.savedConfigs.remove(config);
    }

    public boolean checkConfig(Profile config) {
        if (Client.getInstance().clientMode == ClientMode.CLASSIC && config.profileName.equals("Classic")) {
            return false;
        } else if (this.savedConfigs.size() <= 1) {
            return false;
        } else {
            this.savedConfigs.remove(config);
            if (config == this.currentConfigs) {
                this.loadConfig(this.savedConfigs.get(0));
            }

            return true;
        }
    }

    public boolean removeConfig(String configName) {
        for (Profile var5 : this.savedConfigs) {
            if (var5.profileName.equals(configName) && this.checkConfig(var5)) {
                return true;
            }
        }

        return false;
    }

    public Profile getConfigByName(String var1) {
        for (Profile var5 : this.savedConfigs) {
            if (var5.profileName.equalsIgnoreCase(var1)) {
                return var5;
            }
        }

        return null;
    }

    public void loadProfile(String name) throws IOException {
        File configFolderFolder = new File(Client.getInstance().file + configFolder);
        if (!configFolderFolder.exists()) {
            configFolderFolder.mkdirs();
        }

        File[] configsFound = configFolderFolder.listFiles((var0, var1x) -> var1x.toLowerCase().endsWith(configFileExtension));

        for (File config : configsFound) {
            try {
                String jsonContent = IOUtils.toString(Files.newInputStream(config.toPath()), StandardCharsets.UTF_8);
                JsonObject object = JsonParser.parseString(jsonContent).getAsJsonObject();

                Profile profile = new Profile().loadFromJson(object);
                profile.profileName = config.getName().substring(0, config.getName().length() - ".profile".length());

                this.savedConfigs.add(profile);
                if (profile.profileName.equalsIgnoreCase(name)) {
                    this.currentConfigs = profile;
                }
            } catch (JsonParseException var12) {
                System.err.println("Unable to load profile from " + config.getName());
            }
        }

        // If no profiles were loaded or the current config is null, create a default one
        if (this.savedConfigs.isEmpty() || this.currentConfigs == null) {
            if (name == null || name.isEmpty()) {
                name = "Default";
            }

            this.savedConfigs.add(this.currentConfigs = new Profile(name, new JsonObject()));
        }

        // Load module configurations for the current profile
        Client.getInstance().moduleManager.load(this.currentConfigs.moduleConfig);
    }

    public boolean getConfigByCaseInsensitiveName(String name) {
        for (Profile config : this.savedConfigs) {
            if (config.profileName.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    public void saveAndReplaceConfigs() throws IOException {
        this.currentConfigs.moduleConfig = Client.getInstance().moduleManager.loadCurrentConfig(new JsonObject());
        File configFolderFolder = new File(Client.getInstance().file + configFolder);
        if (!configFolderFolder.exists()) {
            configFolderFolder.mkdirs();
        }

        File[] configs = configFolderFolder.listFiles((var0, var1) -> var1.toLowerCase().endsWith(configFileExtension));

        for (File configItself : configs) {
            configItself.delete();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        for (Profile savedConfig : this.savedConfigs) {
            File configItself = new File(Client.getInstance().file + configFolder + savedConfig.profileName + configFileExtension);
            if (!configItself.exists()) {
                configItself.createNewFile();
            }

            String json = gson.toJson(savedConfig.saveToJson(new JsonObject()));

            try (FileOutputStream outputStream = new FileOutputStream(configItself)) {
                IOUtils.write(json, outputStream, "UTF-8");
            }
        }
    }

    public Profile getCurrentConfig() {
        return this.currentConfigs;
    }

    public void loadConfig(Profile config) {
        Client.getInstance().saveClientData();
        ModuleSettingInitializr.modOffsetMap = new HashMap<>();

        if (Client.getInstance().clientMode != ClientMode.CLASSIC) {
            this.currentConfigs.moduleConfig = Client.getInstance().moduleManager.loadCurrentConfig(new JsonObject());
            this.currentConfigs = config;
            Client.getInstance().config.addProperty("profile", config.profileName);
        } else {
            this.currentConfigs.moduleConfig = config.getDefaultConfig();
            Client.getInstance().config.addProperty("profile", "Classic");
        }

        Client.getInstance().moduleManager.load(config.moduleConfig);
        Client.getInstance().saveClientData();
    }

    public List<Profile> getAllConfigs() {
        return this.savedConfigs;
    }
}
