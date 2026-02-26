package com.mentalfrostbyte.jello.module.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;
import team.sdhq.eventBus.EventBus;

import java.util.ArrayList;
import java.util.List;

public class ModuleWithModuleSettings extends com.mentalfrostbyte.jello.module.Module {

    private final List<ModuleStateListener> moduleStateListeners = new ArrayList<>();
    public com.mentalfrostbyte.jello.module.Module[] moduleArray;
    public com.mentalfrostbyte.jello.module.Module parentModule;
    public ModeSetting modeSetting;
    public String customModeName;
    private final List<String> stringList = new ArrayList<>();

    public ModuleWithModuleSettings(ModuleCategory category, String type, String description, com.mentalfrostbyte.jello.module.Module... modules) {
        this(category, type, description, "Type", modules);
    }

    public ModuleWithModuleSettings(ModuleCategory category, String type, String description, String customModeName, com.mentalfrostbyte.jello.module.Module... modules) {
        super(category, type, description);
        this.moduleArray = modules;

        for (com.mentalfrostbyte.jello.module.Module moduleFromArray : moduleArray) {
            EventBus.register(moduleFromArray);
            stringList.add(moduleFromArray.getName());
            moduleFromArray.setSomeMod(this);
        }

        this.customModeName = customModeName;
        this.registerSetting(modeSetting = new ModeSetting(customModeName, type + " mode", 0, stringList.toArray(new String[0])));
        this.modeSetting.addObserver(var1x -> calledOnEnable());
        this.calledOnEnable();
    }

    public void calledOnEnable() {
        this.isTypeSetToThisModName();

        for (com.mentalfrostbyte.jello.module.Module module : this.moduleArray) {
            boolean isParent = this.getStringSettingValueByName(this.customModeName).equals(module.name);
            if (this.isEnabled() && mc.player != null) {
                module.setState(isParent);
                if (isParent) {
                    this.parentModule = module;
                }
            } else if (this.isEnabled()) {
                module.setEnabledBasic(isParent);
            }

            this.setModuleEnabled(module, isParent);
        }
    }

    private void isTypeSetToThisModName() {
        boolean isOurName = false;

        for (com.mentalfrostbyte.jello.module.Module module : this.moduleArray) {
            if (this.getStringSettingValueByName(this.customModeName).equals(module.name)) {
                isOurName = true;
            }
        }

        if (!isOurName) {
            this.setSetting(this.customModeName, this.moduleArray[0].name);
        }
    }

    public com.mentalfrostbyte.jello.module.Module getModWithTypeSetToName() {
        this.isTypeSetToThisModName();

        for (com.mentalfrostbyte.jello.module.Module mod : this.moduleArray) {
            if (this.getStringSettingValueByName(this.customModeName).equals(mod.name)) {
                return mod;
            }
        }

        return null;
    }

    @Override
    public boolean isEnabled2() {
        if (this.parentModule == null) {
            this.calledOnEnable();
        }

        return this.parentModule != null ? this.parentModule.isEnabled2() : this.isEnabled();
    }

    @Override
    public JsonObject initialize(JsonObject config) throws JsonParseException {
        JsonObject subOptions = GsonUtil.getJSONObjectOrNull(config, "sub-options");
        if (subOptions != null) {
            for (com.mentalfrostbyte.jello.module.Module module : this.moduleArray) {
                JsonArray var9 = GsonUtil.getJSONArrayOrNull(subOptions, module.getName());
                if (var9 != null) {
                    for (int var10 = 0; var10 < var9.size(); var10++) {
                        JsonObject var11 = var9.get(var10).getAsJsonObject();
                        String var12 = GsonUtil.getStringOrDefault(var11, "name", null);

                        for (Setting<?> var14 : module.settingMap.values()) {
                            if (var14.getName().equals(var12)) {
                                try {
                                    var14.loadCurrentValueFromJSONObject(var11);
                                } catch (JsonParseException e) {
                                    throw new RuntimeException(e);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        JsonObject var18 = super.initialize(config);
        if (this.enabled) {
            this.calledOnEnable();
        }

        return var18;
    }

    @Override
    public JsonObject buildUpModuleData(JsonObject obj) {
        try {
            JsonObject subOptionsObj = new JsonObject();

            for (com.mentalfrostbyte.jello.module.Module mod : this.moduleArray) {
                JsonArray arr = new JsonArray();

                for (Setting<?> setting : mod.settingMap.values()) {
                    arr.add(setting.buildUpSettingData(new JsonObject()));
                }

                subOptionsObj.add(mod.getName(), arr);
            }

            obj.add("sub-options", subOptionsObj);
            return super.buildUpModuleData(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onEnable() {
        this.calledOnEnable();
    }

    @Override
    public void onDisable() {
        for (com.mentalfrostbyte.jello.module.Module var6 : this.moduleArray) {
            var6.setEnabled(false);
        }
    }

    @Override
    public void resetModuleState() {
        for (com.mentalfrostbyte.jello.module.Module var6 : this.moduleArray) {
            var6.setState(false);
        }

        super.resetModuleState();
    }

    public final void addModuleStateListener(ModuleStateListener listener) {
        this.moduleStateListeners.add(listener);
    }

    public final void setModuleEnabled(com.mentalfrostbyte.jello.module.Module module, boolean enabled) {
        for (ModuleStateListener listener : this.moduleStateListeners) {
            listener.onModuleEnabled(this, module, enabled);
        }
    }

    @Override
    public void initialize() {
        super.initialize();

        for (Module mod : this.moduleArray) {
            mod.initialize();
        }
    }

}
