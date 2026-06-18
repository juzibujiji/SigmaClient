package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OpenYsmExtraEntityModel {
    public enum Kind {
        PROJECTILE,
        VEHICLE
    }

    private final Kind kind;
    private final String identifier;
    private final List<String> matchIds;
    private final ResourceLocation texture;
    private final List<OpenYsmBone> rootBones;
    private final Map<String, OpenYsmBone> bones;
    private final BakedGeoModel geoModel;
    private final OpenYsmAnimationSet animations;
    private final boolean allCutout;

    public OpenYsmExtraEntityModel(Kind kind, String identifier, List<String> matchIds, ResourceLocation texture,
                                   List<OpenYsmBone> rootBones, Map<String, OpenYsmBone> bones,
                                   BakedGeoModel geoModel, OpenYsmAnimationSet animations, boolean allCutout) {
        this.kind = kind;
        this.identifier = identifier == null ? "" : identifier;
        this.matchIds = matchIds;
        this.texture = texture;
        this.rootBones = rootBones;
        this.bones = bones;
        this.geoModel = geoModel;
        this.animations = animations;
        this.allCutout = allCutout;
    }

    public Kind getKind() {
        return this.kind;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public List<String> getMatchIds() {
        return this.matchIds;
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    public Map<String, OpenYsmBone> getBones() {
        return this.bones;
    }

    public BakedGeoModel getGeoModel() {
        return this.geoModel;
    }

    public OpenYsmAnimationSet getAnimations() {
        return this.animations;
    }

    public RenderType getRenderType() {
        return this.allCutout ? RenderType.getEntityCutoutNoCull(this.texture) : RenderType.getEntityTranslucent(this.texture);
    }

    public boolean matches(String id) {
        String normalized = normalize(id);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalize(this.identifier).equals(normalized)) {
            return true;
        }
        for (String matchId : this.matchIds) {
            if (normalize(matchId).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public void resetPose() {
        this.bones.values().forEach(OpenYsmBone::resetPose);
    }

    public void render(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn,
                       float red, float green, float blue, float alpha) {
        for (OpenYsmBone bone : this.rootBones) {
            bone.getRenderer().render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
