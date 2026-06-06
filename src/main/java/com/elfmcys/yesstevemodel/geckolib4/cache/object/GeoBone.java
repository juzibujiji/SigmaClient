package com.elfmcys.yesstevemodel.geckolib4.cache.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable GeckoLib 4 style bone object for MCP rendering and animation.
 */
public final class GeoBone {
    private final String name;
    private final List<GeoBone> children = new ArrayList<>();
    private final List<GeoCube> cubes = new ArrayList<>();

    private float basePositionX;
    private float basePositionY;
    private float basePositionZ;
    private float baseRotX;
    private float baseRotY;
    private float baseRotZ;
    private boolean baseHidden;

    private float positionX;
    private float positionY;
    private float positionZ;
    private float rotX;
    private float rotY;
    private float rotZ;
    private float scaleX = 1.0F;
    private float scaleY = 1.0F;
    private float scaleZ = 1.0F;
    private boolean hidden;
    private boolean childrenHidden;

    public GeoBone(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public List<GeoBone> getChildBones() {
        return Collections.unmodifiableList(this.children);
    }

    public List<GeoCube> getCubes() {
        return Collections.unmodifiableList(this.cubes);
    }

    public void addChild(GeoBone child) {
        this.children.add(child);
    }

    public void addCube(GeoCube cube) {
        this.cubes.add(cube);
    }

    public void setInitialPose(float positionX, float positionY, float positionZ,
                               float rotX, float rotY, float rotZ, boolean hidden) {
        this.basePositionX = positionX;
        this.basePositionY = positionY;
        this.basePositionZ = positionZ;
        this.baseRotX = rotX;
        this.baseRotY = rotY;
        this.baseRotZ = rotZ;
        this.baseHidden = hidden;
        resetPose();
    }

    public void resetPoseRecursive() {
        resetPose();
        for (GeoBone child : this.children) {
            child.resetPoseRecursive();
        }
    }

    public void resetPose() {
        this.positionX = this.basePositionX;
        this.positionY = this.basePositionY;
        this.positionZ = this.basePositionZ;
        this.rotX = this.baseRotX;
        this.rotY = this.baseRotY;
        this.rotZ = this.baseRotZ;
        this.scaleX = 1.0F;
        this.scaleY = 1.0F;
        this.scaleZ = 1.0F;
        this.hidden = this.baseHidden;
        this.childrenHidden = false;
    }

    public float getPosX() {
        return this.positionX;
    }

    public float getPosY() {
        return this.positionY;
    }

    public float getPosZ() {
        return this.positionZ;
    }

    public float getRotX() {
        return this.rotX;
    }

    public float getRotY() {
        return this.rotY;
    }

    public float getRotZ() {
        return this.rotZ;
    }

    public float getScaleX() {
        return this.scaleX;
    }

    public float getScaleY() {
        return this.scaleY;
    }

    public float getScaleZ() {
        return this.scaleZ;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public boolean isHidingChildren() {
        return this.childrenHidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void setChildrenHidden(boolean childrenHidden) {
        this.childrenHidden = childrenHidden;
    }

    public void setScale(float scaleX, float scaleY, float scaleZ) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
    }

    public void setRotation(float rotX, float rotY, float rotZ) {
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
    }

    public void setPosition(float positionX, float positionY, float positionZ) {
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
    }

    public void addRotation(float rotX, float rotY, float rotZ) {
        this.rotX += rotX;
        this.rotY += rotY;
        this.rotZ += rotZ;
    }

    public void addPosition(float positionX, float positionY, float positionZ) {
        this.positionX += positionX;
        this.positionY += positionY;
        this.positionZ += positionZ;
    }
}
