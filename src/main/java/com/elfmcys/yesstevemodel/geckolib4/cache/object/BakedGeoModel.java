package com.elfmcys.yesstevemodel.geckolib4.cache.object;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GeckoLib 4 style baked model container.
 */
public final class BakedGeoModel {
    private final List<GeoBone> topLevelBones;
    private final Map<String, GeoBone> bonesByName;

    public BakedGeoModel(List<GeoBone> topLevelBones) {
        this.topLevelBones = Collections.unmodifiableList(new ArrayList<>(topLevelBones));
        this.bonesByName = indexBones(this.topLevelBones);
    }

    public List<GeoBone> topLevelBones() {
        return this.topLevelBones;
    }

    public Optional<GeoBone> getBone(String name) {
        return Optional.ofNullable(this.bonesByName.get(name));
    }

    public Map<String, GeoBone> bonesByName() {
        return Collections.unmodifiableMap(this.bonesByName);
    }

    public void resetPose() {
        for (GeoBone bone : this.topLevelBones) {
            bone.resetPoseRecursive();
        }
    }

    private static Map<String, GeoBone> indexBones(List<GeoBone> roots) {
        Map<String, GeoBone> indexed = new LinkedHashMap<>();
        ArrayDeque<GeoBone> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            GeoBone bone = queue.removeFirst();
            indexed.put(bone.getName(), bone);
            queue.addAll(bone.getChildBones());
        }
        return indexed;
    }
}
