package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.animation.ActiveAnimationSet;
import com.elfmcys.yesstevemodel.client.animation.controller.ControllerLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenYsmDebugLogger {
    private static final Set<String> LOGGED_MODELS = ConcurrentHashMap.newKeySet();

    private OpenYsmDebugLogger() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("yes_steve_model.debugModelFeatures")
                || Boolean.getBoolean("yes_steve_model.debugAnimationState");
    }

    public static void logModelLoad(OpenYsmBakedPlayerModel bakedModel) {
        if (!isEnabled() || bakedModel == null || bakedModel.getDebugInfo() == null) {
            return;
        }

        OpenYsmModelDebugInfo debugInfo = bakedModel.getDebugInfo();
        if (!matchesModel(debugInfo.getModelId())) {
            return;
        }

        String modelKey = debugInfo.getModelId() + "|" + debugInfo.getSourceFileName();
        if (!LOGGED_MODELS.add(modelKey)) {
            return;
        }

        YesSteveModel.LOGGER.info(
                "[DEBUG-ysm-feature] modelId={} ysmFile={} sourceType={} sourcePath={} metadataName={} mainModel={} armModel={} texture={} previewAnimation={} rawMetadata={} controllers={} functions={} extraButtons={} extraClassify={}",
                debugInfo.getModelId(), debugInfo.getSourceFileName(), debugInfo.getSourceType(), debugInfo.getSourcePath(),
                debugInfo.getMetadataName(), debugInfo.getRawMainModelIdentifier(), debugInfo.getRawArmModelIdentifier(),
                debugInfo.getSelectedTextureName(), debugInfo.getPreviewAnimationName(), debugInfo.getRawMetadataSummary(),
                debugInfo.getControllerNames(), debugInfo.getFunctionNames(), debugInfo.getExtraButtonIds(), debugInfo.getExtraClassifyIds());

        for (OpenYsmModelDebugInfo.BoneInfo boneInfo : debugInfo.getBones().values()) {
            if (!shouldLogBone(boneInfo)) {
                continue;
            }
            YesSteveModel.LOGGER.info(
                    "[DEBUG-ysm-feature] modelId={} ysmFile={} bone={} parent={} parentChain={} defaultHidden={} cubes={} touchedBy={}",
                    debugInfo.getModelId(), debugInfo.getSourceFileName(), boneInfo.getBoneName(), boneInfo.getParentName(),
                    boneInfo.getParentChain(), defaultHidden(bakedModel, boneInfo.getBoneName()),
                    describeCubes(boneInfo.getCubes()), describeTouchedBy(boneInfo.getTouchedBy()));
        }
    }

    public static void logActiveState(OpenYsmBakedPlayerModel bakedModel, ActiveAnimationSet active) {
        if (!isEnabled() || bakedModel == null || active == null || bakedModel.getDebugInfo() == null) {
            return;
        }

        String boneFilter = boneFilter();
        if (boneFilter.isEmpty() || !matchesModel(bakedModel.getId())) {
            return;
        }

        for (Map.Entry<String, OpenYsmModelDebugInfo.BoneInfo> entry : bakedModel.getDebugInfo().getBones().entrySet()) {
            OpenYsmModelDebugInfo.BoneInfo boneInfo = entry.getValue();
            if (!matchesBone(boneInfo.getBoneName())) {
                continue;
            }

            OpenYsmBone bone = bakedModel.getBones().get(entry.getKey());
            if (bone == null) {
                continue;
            }

            String effectiveVisibility = describeEffectiveVisibility(bakedModel, boneInfo, bone);
            boolean effectiveHidden = !"visible".equals(effectiveVisibility);

            YesSteveModel.LOGGER.info(
                    "[DEBUG-ysm-feature] modelId={} ysmFile={} bone={} currentVisible={} currentHidden={} effectiveHidden={} effectiveVisibility={} defaultHidden={} currentTransform=pos({}, {}, {}) rot({}, {}, {}) currentScale=({}, {}, {}) activeClips={} activeControllerState={} activeExtraCustomAction={} touchedBy={}",
                    bakedModel.getDebugInfo().getModelId(), bakedModel.getDebugInfo().getSourceFileName(), boneInfo.getBoneName(),
                    bone.isVisible(), !bone.isVisible(), effectiveHidden, effectiveVisibility, bone.isDefaultHidden(),
                    bone.getRenderer().rotationPointX, bone.getRenderer().rotationPointY, bone.getRenderer().rotationPointZ,
                    bone.getRenderer().rotateAngleX, bone.getRenderer().rotateAngleY, bone.getRenderer().rotateAngleZ,
                    bone.getScaleX(), bone.getScaleY(), bone.getScaleZ(),
                    describeActiveClips(active), describeControllerStates(active),
                    active.extraActionClip.map(clip -> clip.name + "/" + active.actionSource).orElse("none/" + active.actionSource),
                    describeTouchedBy(boneInfo.getTouchedBy()));
        }
    }

    private static boolean defaultHidden(OpenYsmBakedPlayerModel bakedModel, String boneName) {
        OpenYsmBone bone = bakedModel.getBones().get(boneName);
        return bone != null && bone.isDefaultHidden();
    }

    private static String describeEffectiveVisibility(OpenYsmBakedPlayerModel bakedModel,
                                                      OpenYsmModelDebugInfo.BoneInfo boneInfo,
                                                      OpenYsmBone bone) {
        if (!bone.isVisible()) {
            return "hidden:self_hidden";
        }
        if (hasZeroScale(bone)) {
            return "hidden:self_zero_scale";
        }

        String parentName = boneInfo.getParentName();
        int guard = 0;
        while (parentName != null && !parentName.isEmpty() && guard++ < bakedModel.getBones().size() + 1) {
            OpenYsmBone parent = bakedModel.getBones().get(parentName);
            if (parent == null) {
                return "visible";
            }
            if (!parent.isVisible()) {
                return "hidden:parent_hidden(" + parentName + ")";
            }
            if (hasZeroScale(parent)) {
                return "hidden:parent_zero_scale(" + parentName + ")";
            }

            OpenYsmModelDebugInfo.BoneInfo parentInfo = bakedModel.getDebugInfo().getBones().get(parentName);
            parentName = parentInfo == null ? "" : parentInfo.getParentName();
        }
        return "visible";
    }

    private static boolean hasZeroScale(OpenYsmBone bone) {
        return bone.getScaleX() == 0.0F && bone.getScaleY() == 0.0F && bone.getScaleZ() == 0.0F;
    }

    private static boolean shouldLogBone(OpenYsmModelDebugInfo.BoneInfo boneInfo) {
        return matchesBone(boneInfo.getBoneName()) || boneInfo.isFeatureCandidate();
    }

    private static boolean matchesModel(String modelId) {
        String filter = System.getProperty("yes_steve_model.debugModelId", "").trim();
        return filter.isEmpty() || containsIgnoreCase(modelId, filter);
    }

    private static boolean matchesBone(String boneName) {
        String filter = boneFilter();
        return filter.isEmpty() || containsIgnoreCase(boneName, filter);
    }

    private static String boneFilter() {
        return System.getProperty("yes_steve_model.debugBoneName", "").trim();
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String describeCubes(List<OpenYsmModelDebugInfo.CubeInfo> cubes) {
        List<String> parts = new ArrayList<>();
        for (OpenYsmModelDebugInfo.CubeInfo cube : cubes) {
            parts.add("#" + cube.getCubeIndex() + "(quads=" + cube.getQuadCount() + ")");
        }
        return parts.toString();
    }

    private static String describeTouchedBy(List<OpenYsmModelDebugInfo.ClipTouch> touchedBy) {
        List<String> parts = new ArrayList<>();
        for (OpenYsmModelDebugInfo.ClipTouch touch : touchedBy) {
            parts.add(touch.getGroup() + ":" + touch.getClipName()
                    + "(sourceType=" + touch.getSourceType()
                    + ", origin=" + touch.getOriginFile()
                    + ", mainState=" + touch.isMainState()
                    + ", handCondition=" + touch.isHandCondition()
                    + ", extraAction=" + touch.isExtraAction()
                    + ", controllerReferenced=" + touch.isControllerReferenced()
                    + ")");
        }
        return parts.toString();
    }

    private static String describeActiveClips(ActiveAnimationSet active) {
        List<String> parts = new ArrayList<>();
        for (ActiveAnimationSet.ActiveClip activeClip : active.activeClipsInOrder()) {
            parts.add(activeClip.getClip().name + "@" + activeClip.getLayer());
        }
        return parts.toString();
    }

    private static String describeControllerStates(ActiveAnimationSet active) {
        List<String> parts = new ArrayList<>();
        for (ActiveAnimationSet.ActiveClip activeClip : active.controllerLayerClips) {
            ControllerLayer layer = activeClip.getLayer();
            parts.add(activeClip.getControllerName() + ":" + activeClip.getStateName() + ":" + activeClip.getClip().name + "@" + layer);
        }
        return parts.toString();
    }
}
