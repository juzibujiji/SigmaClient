package com.elfmcys.yesstevemodel.client.animation;

import net.minecraft.util.ResourceLocation;

public final class ActionEntry {
    private final String animationName;
    private final String displayName;
    private final AnimationSourceType sourceType;
    private final boolean global;
    private final String modelId;
    private final ResourceLocation icon;
    private final LoopMode loopMode;
    private final String description;

    public ActionEntry(String animationName, String displayName, AnimationSourceType sourceType, boolean global,
                       String modelId, ResourceLocation icon, LoopMode loopMode, String description) {
        this.animationName = animationName;
        this.displayName = displayName == null || displayName.isEmpty() ? animationName : displayName;
        this.sourceType = sourceType;
        this.global = global;
        this.modelId = modelId;
        this.icon = icon;
        this.loopMode = loopMode;
        this.description = description == null ? "" : description;
    }

    public String getAnimationName() {
        return this.animationName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public AnimationSourceType getSourceType() {
        return this.sourceType;
    }

    public boolean isGlobal() {
        return this.global;
    }

    public String getModelId() {
        return this.modelId;
    }

    public ResourceLocation getIcon() {
        return this.icon;
    }

    public LoopMode getLoopMode() {
        return this.loopMode;
    }

    public String getDescription() {
        return this.description;
    }
}
