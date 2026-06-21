package com.elfmcys.yesstevemodel.client.animation;

import com.elfmcys.yesstevemodel.client.OpenYsmExtraEntityModel;
import com.elfmcys.yesstevemodel.client.OpenYsmPlayerModelState;
import com.elfmcys.yesstevemodel.client.animation.controller.ControllerLayer;
import com.elfmcys.yesstevemodel.client.animation.controller.OpenYsmControllerRuntime;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.util.math.vector.Vector3d;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class OpenYsmExtraEntityAnimationResolver {
    private static final float VEHICLE_MOVE_THRESHOLD = 0.05F;

    private OpenYsmExtraEntityAnimationResolver() {
    }

    public static ActiveAnimationSet resolve(OpenYsmExtraEntityModel model, Entity entity, float partialTicks) {
        ActiveAnimationSet active = new ActiveAnimationSet();
        if (model == null || entity == null || model.getAnimations() == null
                || model.getAnimations().getClips().isEmpty()) {
            return active;
        }

        float seconds = Math.max(0.0F, entity.ticksExisted + partialTicks) / 20.0F;
        PlayerStateSnapshot snapshot = PlayerStateSnapshot.captureEntity(entity, 0.0F, entity.ticksExisted + partialTicks);
        OpenYsmControllerRuntime.Result controllerResult = OpenYsmControllerRuntime.tick(
                model.getAnimations().getModelId(),
                model.getAnimations().getControllerDefinitions(),
                model.getAnimations().getClips(),
                snapshot,
                OpenYsmPlayerModelState.getExtraEntityVariables(entity));
        for (OpenYsmControllerRuntime.ActiveControllerAnimation controllerAnimation
                : controllerResult.getActiveAnimations()) {
            float clipWeight = model.getAnimations().evaluateClipBlendWeight(controllerAnimation.getClip(), snapshot);
            active.addControllerClip(controllerAnimation.getClip(), controllerAnimation.getLayer(),
                    controllerAnimation.getTimeSeconds(), controllerAnimation.getWeight() * clipWeight,
                    controllerAnimation.getControllerName(), controllerAnimation.getStateName());
        }
        active.controllerEvents.addAll(controllerResult.getControllerEvents());
        if (!controllerResult.getActiveAnimations().isEmpty()) {
            return active;
        }

        Set<String> added = new LinkedHashSet<>();
        if (model.getKind() == OpenYsmExtraEntityModel.Kind.PROJECTILE) {
            resolveProjectile(model.getAnimations(), entity, active, added, seconds, snapshot);
        } else if (model.getKind() == OpenYsmExtraEntityModel.Kind.VEHICLE) {
            resolveVehicle(model.getAnimations(), entity, active, added, seconds, snapshot);
        }
        return active;
    }

    private static void resolveProjectile(OpenYsmAnimationSet animations, Entity entity, ActiveAnimationSet active,
                                          Set<String> added, float seconds, PlayerStateSnapshot snapshot) {
        addStateClip(animations, active, added, "pre_main", ControllerLayer.PRE_MAIN,
                seconds, "projectile.pre_main", "pre_main", snapshot);

        String state = projectileMainState(entity);
        String clipName = state.equals("air") && findClip(animations, "air") == null
                ? firstExistingClipName(animations, "fly", "air")
                : state;
        addStateClip(animations, active, added, clipName, ControllerLayer.MAIN,
                seconds, "projectile.main", state, snapshot);

        addStateClip(animations, active, added, "post_main", ControllerLayer.POST_MAIN,
                seconds, "projectile.post_main", "post_main", snapshot);
        addNumberedClips(animations, active, added, "parallel", ControllerLayer.PARALLEL,
                seconds, "projectile.parallel", snapshot);
    }

    private static void resolveVehicle(OpenYsmAnimationSet animations, Entity entity, ActiveAnimationSet active,
                                       Set<String> added, float seconds, PlayerStateSnapshot snapshot) {
        addNumberedClips(animations, active, added, "pre_parallel", ControllerLayer.PRE_MAIN,
                seconds, "vehicle.pre_parallel", snapshot);
        addStateClip(animations, active, added, "pre_main", ControllerLayer.PRE_MAIN,
                seconds, "vehicle.pre_main", "pre_main", snapshot);

        String mainState = vehicleMainState(entity);
        addStateClip(animations, active, added, mainState, ControllerLayer.MAIN,
                seconds, "vehicle.main", mainState, snapshot);

        String moveState = vehicleMoveState(entity);
        addStateClip(animations, active, added, moveState, ControllerLayer.MAIN,
                seconds, "vehicle.move", moveState, snapshot);

        String rideState = entity.getPassengers().isEmpty() ? "not_ride" : "has_ride";
        addStateClip(animations, active, added, rideState, ControllerLayer.MAIN,
                seconds, "vehicle.ride", rideState, snapshot);

        addStateClip(animations, active, added, "post_main", ControllerLayer.POST_MAIN,
                seconds, "vehicle.post_main", "post_main", snapshot);
        addNumberedClips(animations, active, added, "parallel", ControllerLayer.PARALLEL,
                seconds, "vehicle.parallel", snapshot);
    }

    private static String projectileMainState(Entity entity) {
        if (entity.isInWater()) {
            return "water";
        }
        if (entity.isBurning()) {
            return "fire";
        }
        if (isProjectileInGround(entity)) {
            return "ground";
        }
        return "air";
    }

    private static boolean isProjectileInGround(Entity entity) {
        if (entity instanceof AbstractArrowEntity) {
            return ((AbstractArrowEntity) entity).yesSteveModel$isInGround();
        }
        return entity.isOnGround();
    }

    private static String vehicleMainState(Entity entity) {
        if (entity.isInWater()) {
            return "water";
        }
        if (entity.isOnGround()) {
            return "ground";
        }
        return "fly";
    }

    private static String vehicleMoveState(Entity entity) {
        Vector3d motion = entity.getMotion();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        return horizontal > VEHICLE_MOVE_THRESHOLD ? "forward" : "idle";
    }

    private static void addNumberedClips(OpenYsmAnimationSet animations, ActiveAnimationSet active, Set<String> added,
                                         String clipPrefix, ControllerLayer layer, float seconds,
                                         String controllerPrefix, PlayerStateSnapshot snapshot) {
        for (int index = 0; index <= 7; index++) {
            String clipName = clipPrefix + index;
            addStateClip(animations, active, added, clipName, layer, seconds,
                    controllerPrefix + "_" + index, clipName, snapshot);
        }
    }

    private static void addStateClip(OpenYsmAnimationSet animations, ActiveAnimationSet active, Set<String> added,
                                     String clipName, ControllerLayer layer, float seconds,
                                     String controllerName, String stateName, PlayerStateSnapshot snapshot) {
        OpenYsmAnimationSet.Clip clip = findClip(animations, clipName);
        if (clip == null || (clip.touchedBones.isEmpty() && clip.boneTracks.isEmpty())) {
            return;
        }
        String key = controllerName + "|" + clip.name;
        if (!added.add(key)) {
            return;
        }
        active.addControllerClip(clip, layer, seconds, animations.evaluateClipBlendWeight(clip, snapshot),
                controllerName, stateName);
    }

    private static String firstExistingClipName(OpenYsmAnimationSet animations, String first, String second) {
        return findClip(animations, first) != null ? first : second;
    }

    private static OpenYsmAnimationSet.Clip findClip(OpenYsmAnimationSet animations, String name) {
        if (animations == null || name == null || name.isEmpty()) {
            return null;
        }
        OpenYsmAnimationSet.Clip exact = animations.findClip(name).orElse(null);
        if (exact != null) {
            return exact;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (OpenYsmAnimationSet.Clip clip : animations.getClips().values()) {
            if (clip.name.toLowerCase(Locale.ROOT).equals(lower)) {
                return clip;
            }
        }
        return null;
    }
}
