package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Map;

public final class OpenYsmBakedPlayerModel {
    private final String id;
    private final ResourceLocation texture;
    private final List<OpenYsmBone> rootBones;
    private final Map<String, OpenYsmBone> bones;
    private final OpenYsmAnimationSet animations;
    private final float widthScale;
    private final float heightScale;

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, OpenYsmAnimationSet animations,
                                   float widthScale, float heightScale) {
        this.id = id;
        this.texture = texture;
        this.rootBones = rootBones;
        this.bones = bones;
        this.animations = animations;
        this.widthScale = widthScale;
        this.heightScale = heightScale;
    }

    public String getId() {
        return this.id;
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    public List<OpenYsmBone> getRootBones() {
        return this.rootBones;
    }

    public Map<String, OpenYsmBone> getBones() {
        return this.bones;
    }

    public OpenYsmAnimationSet getAnimations() {
        return this.animations;
    }

    public float getWidthScale() {
        return this.widthScale;
    }

    public float getHeightScale() {
        return this.heightScale;
    }
}
