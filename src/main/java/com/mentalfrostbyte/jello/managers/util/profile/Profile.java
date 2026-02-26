package com.mentalfrostbyte.jello.managers.util.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

public class Profile {
    public JsonObject moduleConfig;
    public String profileName;

    public Profile() {
    }

    public Profile(String profileName, JsonObject moduleConfig) {
        this.profileName = profileName;
        this.moduleConfig = moduleConfig;
    }

    public Profile(String profileName, Profile profile) {
        this.profileName = profileName;
        this.moduleConfig = profile.moduleConfig;
    }

    public Profile loadFromJson(JsonObject jsonObject) throws JsonParseException {
        this.moduleConfig = jsonObject.getAsJsonObject("modConfig");
        this.profileName = jsonObject.get("name").getAsString();
        return this;
    }

    public JsonObject saveToJson(JsonObject jsonObject) {
        jsonObject.add("modConfig", this.moduleConfig);
        jsonObject.addProperty("name", this.profileName);
        return jsonObject;
    }

    public JsonObject getDefaultConfig() {
        return null;
    }

    public Profile cloneWithName(String newName) {
        return new Profile(newName, this.moduleConfig);
    }

    public void disableNonGuiModules() throws JsonParseException {
        JsonArray modulesArray = null;

        try {
            modulesArray = this.moduleConfig.getAsJsonArray("mods");
        } catch (JsonParseException ignored) {
        }

        if (modulesArray != null) {
            for (int i = 0; i < modulesArray.size(); i++) {
                JsonObject moduleObject = modulesArray.get(i).getAsJsonObject();
                String moduleName = null;

                try {
                    moduleName = moduleObject.getAsJsonObject("name").getAsString();
                } catch (JsonParseException e) {
                    System.out.println("Invalid name in mod list config");
                }

                for (Module module : Client.getInstance().moduleManager.getModuleMap().values()) {
                    if (module.getName().equals(moduleName) && module.getCategoryBasedOnMode() != ModuleCategory.GUI && module.getCategoryBasedOnMode() != ModuleCategory.RENDER) {
                        moduleObject.addProperty("enabled", "false");
                    }
                }
            }
        }
    }

    public void updateModuleConfig(JsonObject newConfig, Module module) {
        JsonArray modulesArray = null;

        try {
            modulesArray = this.moduleConfig.getAsJsonArray("mods");
        } catch (JsonParseException ignored) {
        }

        boolean updated = false;
        if (modulesArray != null) {
            for (int i = 0; i < modulesArray.size(); i++) {
                try {
                    JsonObject moduleObject = modulesArray.get(i).getAsJsonObject();
                    String moduleName = moduleObject.get("name").getAsString();

                    if (module.getName().equals(moduleName)) {
                        if (module.getCategoryBasedOnMode() != ModuleCategory.GUI && module.getCategoryBasedOnMode() != ModuleCategory.RENDER) {
                            modulesArray.add(newConfig);
                        }

                        updated = true;
                    }
                } catch (JsonParseException e) {
                    System.out.println("Invalid name in mod list config");
                }

            }
        }

        if (!updated) {
            assert modulesArray != null;
            modulesArray.add(newConfig);
        }
    }

    public JsonObject getModuleConfig(Module module) {
        JsonArray modulesArray = null;

        try {
            modulesArray = this.moduleConfig.getAsJsonArray("mods");
        } catch (JsonParseException ignored) {
        }

        if (modulesArray != null) {
            for (int i = 0; i < modulesArray.size(); i++) {
                JsonObject moduleObject;

                try {
                    moduleObject = modulesArray.get(i).getAsJsonObject();
                } catch (JsonParseException e) {
                    throw new RuntimeException(e);
                }

                String moduleName = null;

                try {
                    moduleName = moduleObject.get("name").getAsString();
                } catch (JsonParseException e) {
                    System.out.println("Invalid name in mod list config");
                }

                if (module.getName().equals(moduleName)) {
                    return moduleObject;
                }
            }
        }

        return null;
    }
}
