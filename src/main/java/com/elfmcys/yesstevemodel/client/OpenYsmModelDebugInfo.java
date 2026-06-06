package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.client.animation.AnimationSourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class OpenYsmModelDebugInfo {
    private final String modelId;
    private final String sourceFileName;
    private final String sourcePath;
    private final String sourceType;
    private final String selectedTextureName;
    private final String metadataName;
    private final String rawMainModelIdentifier;
    private final String rawArmModelIdentifier;
    private final String previewAnimationName;
    private final String rawMetadataSummary;
    private final List<String> controllerNames;
    private final List<String> functionNames;
    private final List<String> extraButtonIds;
    private final List<String> extraClassifyIds;
    private final Map<String, BoneInfo> bones;

    public OpenYsmModelDebugInfo(String modelId, String sourceFileName, String sourcePath, String sourceType,
                                 String selectedTextureName, String metadataName, String rawMainModelIdentifier,
                                 String rawArmModelIdentifier, String previewAnimationName, String rawMetadataSummary,
                                 List<String> controllerNames, List<String> functionNames, List<String> extraButtonIds,
                                 List<String> extraClassifyIds, Map<String, BoneInfo> bones) {
        this.modelId = modelId == null ? "" : modelId;
        this.sourceFileName = sourceFileName == null ? "" : sourceFileName;
        this.sourcePath = sourcePath == null ? "" : sourcePath;
        this.sourceType = sourceType == null ? "" : sourceType;
        this.selectedTextureName = selectedTextureName == null ? "" : selectedTextureName;
        this.metadataName = metadataName == null ? "" : metadataName;
        this.rawMainModelIdentifier = rawMainModelIdentifier == null ? "" : rawMainModelIdentifier;
        this.rawArmModelIdentifier = rawArmModelIdentifier == null ? "" : rawArmModelIdentifier;
        this.previewAnimationName = previewAnimationName == null ? "" : previewAnimationName;
        this.rawMetadataSummary = rawMetadataSummary == null ? "" : rawMetadataSummary;
        this.controllerNames = immutableCopy(controllerNames);
        this.functionNames = immutableCopy(functionNames);
        this.extraButtonIds = immutableCopy(extraButtonIds);
        this.extraClassifyIds = immutableCopy(extraClassifyIds);
        this.bones = bones == null ? Collections.emptyMap() : Collections.unmodifiableMap(bones);
    }

    public String getModelId() {
        return this.modelId;
    }

    public String getSourceFileName() {
        return this.sourceFileName;
    }

    public String getSourcePath() {
        return this.sourcePath;
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public String getSelectedTextureName() {
        return this.selectedTextureName;
    }

    public String getMetadataName() {
        return this.metadataName;
    }

    public String getRawMainModelIdentifier() {
        return this.rawMainModelIdentifier;
    }

    public String getRawArmModelIdentifier() {
        return this.rawArmModelIdentifier;
    }

    public String getPreviewAnimationName() {
        return this.previewAnimationName;
    }

    public String getRawMetadataSummary() {
        return this.rawMetadataSummary;
    }

    public List<String> getControllerNames() {
        return this.controllerNames;
    }

    public List<String> getFunctionNames() {
        return this.functionNames;
    }

    public List<String> getExtraButtonIds() {
        return this.extraButtonIds;
    }

    public List<String> getExtraClassifyIds() {
        return this.extraClassifyIds;
    }

    public Map<String, BoneInfo> getBones() {
        return this.bones;
    }

    private static List<String> immutableCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static final class BoneInfo {
        private final String boneName;
        private final String parentName;
        private final String parentChain;
        private final boolean featureCandidate;
        private final List<CubeInfo> cubes;
        private final List<ClipTouch> touchedBy;

        public BoneInfo(String boneName, String parentName, String parentChain, boolean featureCandidate,
                        List<CubeInfo> cubes, List<ClipTouch> touchedBy) {
            this.boneName = boneName == null ? "" : boneName;
            this.parentName = parentName == null ? "" : parentName;
            this.parentChain = parentChain == null ? "" : parentChain;
            this.featureCandidate = featureCandidate;
            this.cubes = cubes == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(cubes));
            this.touchedBy = touchedBy == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(touchedBy));
        }

        public String getBoneName() {
            return this.boneName;
        }

        public String getParentName() {
            return this.parentName;
        }

        public String getParentChain() {
            return this.parentChain;
        }

        public boolean isFeatureCandidate() {
            return this.featureCandidate;
        }

        public List<CubeInfo> getCubes() {
            return this.cubes;
        }

        public List<ClipTouch> getTouchedBy() {
            return this.touchedBy;
        }
    }

    public static final class CubeInfo {
        private final int cubeIndex;
        private final int quadCount;

        public CubeInfo(int cubeIndex, int quadCount) {
            this.cubeIndex = cubeIndex;
            this.quadCount = quadCount;
        }

        public int getCubeIndex() {
            return this.cubeIndex;
        }

        public int getQuadCount() {
            return this.quadCount;
        }
    }

    public static final class ClipTouch {
        private final String clipName;
        private final String group;
        private final AnimationSourceType sourceType;
        private final String originFile;
        private final boolean mainState;
        private final boolean handCondition;
        private final boolean extraAction;
        private final boolean controllerReferenced;

        public ClipTouch(String clipName, String group, AnimationSourceType sourceType, String originFile,
                         boolean mainState, boolean handCondition, boolean extraAction, boolean controllerReferenced) {
            this.clipName = clipName == null ? "" : clipName;
            this.group = group == null ? "" : group;
            this.sourceType = sourceType == null ? AnimationSourceType.UNKNOWN : sourceType;
            this.originFile = originFile == null ? "" : originFile;
            this.mainState = mainState;
            this.handCondition = handCondition;
            this.extraAction = extraAction;
            this.controllerReferenced = controllerReferenced;
        }

        public String getClipName() {
            return this.clipName;
        }

        public String getGroup() {
            return this.group;
        }

        public AnimationSourceType getSourceType() {
            return this.sourceType;
        }

        public String getOriginFile() {
            return this.originFile;
        }

        public boolean isMainState() {
            return this.mainState;
        }

        public boolean isHandCondition() {
            return this.handCondition;
        }

        public boolean isExtraAction() {
            return this.extraAction;
        }

        public boolean isControllerReferenced() {
            return this.controllerReferenced;
        }
    }
}
