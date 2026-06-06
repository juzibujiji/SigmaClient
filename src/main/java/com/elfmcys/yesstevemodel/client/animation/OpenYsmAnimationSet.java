package com.elfmcys.yesstevemodel.client.animation;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.OpenYsmBone;
import com.elfmcys.yesstevemodel.client.animation.controller.ControllerAnimationRef;
import com.elfmcys.yesstevemodel.client.animation.controller.ControllerDefinition;
import com.elfmcys.yesstevemodel.client.animation.controller.ControllerLayer;
import com.elfmcys.yesstevemodel.client.animation.controller.ControllerStateDefinition;
import com.elfmcys.yesstevemodel.client.animation.controller.ControllerTransition;
import com.elfmcys.yesstevemodel.client.animation.controller.OpenYsmControllerRuntime;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OpenYsmAnimationSet {
    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;
    private static final String[] MAIN_STATE_PRIORITY = new String[]{"idle", "walk", "run", "sneak", "sneaking"};

    private final String modelId;
    private final Map<String, Clip> clips = new LinkedHashMap<>();
    private final Map<String, ActionMetadata> actionMetadata = new LinkedHashMap<>();
    private final Map<String, ControllerDefinition> controllerDefinitions = new LinkedHashMap<>();
    private final Set<String> explicitWheelActions = new LinkedHashSet<>();
    private final Set<String> controllerReferencedNames = new LinkedHashSet<>();
    private final Set<String> hiddenByDefaultBones = new LinkedHashSet<>();
    private String previewAnimationName = "";

    public OpenYsmAnimationSet(String modelId) {
        this.modelId = modelId == null ? "" : modelId;
    }

    public String getModelId() {
        return this.modelId;
    }

    public Map<String, Clip> getClips() {
        return Collections.unmodifiableMap(this.clips);
    }

    public Optional<Clip> findClip(String name) {
        return Optional.ofNullable(this.clips.get(name));
    }

    public Set<String> getHiddenByDefaultBones() {
        return Collections.unmodifiableSet(this.hiddenByDefaultBones);
    }

    public void setPreviewAnimationName(String previewAnimationName) {
        this.previewAnimationName = previewAnimationName == null ? "" : previewAnimationName;
    }

    public void registerExplicitAction(String animationName, String displayName, String icon, String description, boolean global) {
        if (animationName == null || animationName.isEmpty()) {
            return;
        }
        String safeName = normalizeClipName(animationName);
        this.explicitWheelActions.add(safeName);
        this.actionMetadata.put(safeName, new ActionMetadata(displayName, icon, description, global));
        Clip clip = this.clips.get(safeName);
        if (clip != null) {
            applyMetadata(clip);
            clip.isExtraAction = true;
        }
    }

    public void addJsonAnimations(String group, String originFile, JsonObject animationFile) {
        if (animationFile == null || !animationFile.has("animations") || !animationFile.get("animations").isJsonObject()) {
            return;
        }

        JsonObject animations = animationFile.getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            String name = normalizeClipName(entry.getKey());
            JsonObject animation = entry.getValue().getAsJsonObject();
            Clip clip = new Clip(name, group, inferSourceType(group, name), originFile,
                    LoopMode.fromJson(animation.get("loop")), getFloat(animation, "animation_length", 0.0F));
            parseJsonBones(clip, animation.getAsJsonObject("bones"));
            if (clip.length <= 0.0F) {
                clip.length = clip.computeLengthFromTracks();
            }
            registerClip(clip);
        }
    }

    public void addRawAnimations(String group, int type, RawYsmModel.RawAnimationFile rawFile) {
        if (rawFile == null || rawFile.animations == null) {
            return;
        }
        String effectiveGroup = group == null || group.isEmpty() ? groupFromRawType(type) : group;
        for (RawYsmModel.RawAnimation animation : rawFile.animations.values()) {
            if (animation == null || animation.name == null || animation.name.isEmpty()) {
                continue;
            }
            String name = normalizeClipName(animation.name);
            Clip clip = new Clip(name, effectiveGroup, inferSourceType(effectiveGroup, name), rawFile.fileHash,
                    LoopMode.fromRaw(animation.loopMode), animation.length);
            parseRawBones(clip, animation.boneAnimations);
            if (clip.length <= 0.0F) {
                clip.length = clip.computeLengthFromTracks();
            }
            registerClip(clip);
        }
    }

    public void addJsonControllerReferences(String originFile, JsonObject controllerFile) {
        if (controllerFile == null || !controllerFile.has("animation_controllers") || !controllerFile.get("animation_controllers").isJsonObject()) {
            return;
        }

        JsonObject controllers = controllerFile.getAsJsonObject("animation_controllers");
        for (Map.Entry<String, JsonElement> controllerEntry : controllers.entrySet()) {
            if (!controllerEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject controller = controllerEntry.getValue().getAsJsonObject();
            try {
                ControllerDefinition definition = parseJsonController(controllerEntry.getKey(), controller);
                if (definition.isUsable()) {
                    this.controllerDefinitions.put(definition.getName(), definition);
                    collectControllerAnimations(definition);
                }
            } catch (RuntimeException exception) {
                if (isDebugEnabled()) {
                    YesSteveModel.LOGGER.info("[DEBUG-animation-state] model={} controller={} parse failed; controller runtime disabled for this controller",
                            this.modelId, controllerEntry.getKey(), exception);
                }
            }
        }
        markControllerReferences();
    }

    public void addRawControllerReferences(Map<String, RawYsmModel.RawAnimationController> controllers) {
        if (controllers == null) {
            return;
        }
        for (RawYsmModel.RawAnimationController controller : controllers.values()) {
            if (controller == null) {
                continue;
            }
            if (controller.animationName != null && !controller.animationName.isEmpty()) {
                this.controllerReferencedNames.add(normalizeClipName(controller.animationName));
            }
            try {
                ControllerDefinition definition = parseRawController(controller);
                if (definition.isUsable()) {
                    this.controllerDefinitions.put(definition.getName(), definition);
                    collectControllerAnimations(definition);
                }
            } catch (RuntimeException exception) {
                if (isDebugEnabled()) {
                    YesSteveModel.LOGGER.info("[DEBUG-animation-state] model={} raw controller={} parse failed; controller runtime disabled for this controller",
                            this.modelId, controller.animationName, exception);
                }
            }
        }
        markControllerReferences();
    }

    public void configureDefaultHidden(Map<String, OpenYsmBone> bones) {
        this.hiddenByDefaultBones.clear();
        if (bones == null || bones.isEmpty()) {
            return;
        }

        Set<String> mainTouched = new LinkedHashSet<>();
        Set<String> conditionalTouched = new LinkedHashSet<>();
        for (Clip clip : this.clips.values()) {
            if (clip.sourceType == AnimationSourceType.MAIN && !clip.isGuiPreviewAction) {
                mainTouched.addAll(clip.touchedBones);
            } else if (clip.sourceType == AnimationSourceType.ARM
                    || clip.sourceType == AnimationSourceType.EXTRA
                    || clip.sourceType == AnimationSourceType.CUSTOM
                    || clip.sourceType == AnimationSourceType.CONTROLLER_REFERENCED
                    || clip.sourceType == AnimationSourceType.GUI_PREVIEW
                    || clip.isControllerReferenced) {
                conditionalTouched.addAll(clip.touchedBones);
            }
        }

        for (OpenYsmBone bone : bones.values()) {
            boolean hidden = conditionalTouched.contains(bone.getName()) && !mainTouched.contains(bone.getName());
            bone.setDefaultHidden(hidden);
            if (hidden) {
                this.hiddenByDefaultBones.add(bone.getName());
            }
        }

        Set<String> wheelActions = new LinkedHashSet<>();
        for (ActionEntry action : listWheelActions()) {
            wheelActions.add(action.getAnimationName());
        }
        OpenYsmAnimationRegistry.registerWheelActions(this.modelId, wheelActions);

        if (isDebugEnabled()) {
            YesSteveModel.LOGGER.info("[DEBUG-animation-state] model={} registered extra actions={} hidden-by-default bones={}",
                    this.modelId, wheelActions, this.hiddenByDefaultBones);
            if (!this.controllerReferencedNames.isEmpty()) {
                YesSteveModel.LOGGER.info("[DEBUG-animation-state] model={} controller referenced animations registered but skipped by default runtime={}",
                        this.modelId, this.controllerReferencedNames);
            }
        }
    }

    public List<ActionEntry> listExtraActions() {
        List<ActionEntry> actions = new ArrayList<>();
        for (Clip clip : this.clips.values()) {
            if (clip.sourceType == AnimationSourceType.EXTRA && isWheelVisible(clip)) {
                actions.add(toActionEntry(clip));
            }
        }
        sortActions(actions);
        return actions;
    }

    public List<ActionEntry> listCustomActions() {
        List<ActionEntry> actions = new ArrayList<>();
        for (Clip clip : this.clips.values()) {
            if ((clip.sourceType == AnimationSourceType.CUSTOM || clip.sourceType == AnimationSourceType.CONTROLLER_REFERENCED)
                    && isWheelVisible(clip)) {
                actions.add(toActionEntry(clip));
            }
        }
        sortActions(actions);
        return actions;
    }

    public List<ActionEntry> listWheelActions() {
        Map<String, ActionEntry> actions = new LinkedHashMap<>();
        for (ActionEntry action : listExtraActions()) {
            actions.put(action.getAnimationName(), action);
        }
        for (ActionEntry action : listCustomActions()) {
            actions.put(action.getAnimationName(), action);
        }
        return new ArrayList<>(actions.values());
    }

    public ActiveAnimationSet resolveActive(PlayerStateSnapshot snapshot, OpenYsmPlayerAnimationState.State extraState,
                                            AnimationRenderContext context) {
        ActiveAnimationSet active = new ActiveAnimationSet();
        if (snapshot == null) {
            return active;
        }

        float elapsedSeconds = snapshot.ageInTicks / 20.0F;
        OpenYsmControllerRuntime.Result controllerResult = OpenYsmControllerRuntime.tick(this.modelId,
                this.controllerDefinitions.values(), this.clips, snapshot);
        Set<String> activeClipNames = new LinkedHashSet<>();
        for (OpenYsmControllerRuntime.ActiveControllerAnimation controllerAnimation : controllerResult.getActiveAnimations()) {
            active.addControllerClip(controllerAnimation.getClip(), controllerAnimation.getLayer(),
                    controllerAnimation.getTimeSeconds(), controllerAnimation.getWeight(),
                    controllerAnimation.getControllerName(), controllerAnimation.getStateName());
            activeClipNames.add(controllerAnimation.getClip().name);
        }
        addBuiltinAlwaysOnClips(active, activeClipNames, elapsedSeconds);

        Clip main = controllerResult.hasMainLayerAnimation() ? null : selectMainState(snapshot);
        if (main != null) {
            active.mainStateClip = main;
            active.setTime(main, elapsedSeconds);
        }

        for (Clip hand : selectHandClips(snapshot)) {
            active.handClips.add(hand);
            active.setTime(hand, elapsedSeconds);
        }

        if (extraState != null && this.modelId.equals(extraState.getModelId())) {
            Clip extra = this.clips.get(extraState.getAnimationName());
            if (extra != null && isWheelVisible(extra)) {
                float elapsed = extraState.elapsedSeconds(snapshot.ageInTicks);
                if (extra.loopMode == LoopMode.PLAY_ONCE && extra.length > 0.0F && elapsed > extra.length) {
                    OpenYsmPlayerAnimationState.stop(snapshot.uuid, this.modelId, extra.name);
                } else {
                    active.extraActionClip = Optional.of(extra);
                    active.actionSource = extraState.getSource();
                    active.setTime(extra, elapsed);
                }
            }
        }

        if (context == AnimationRenderContext.PREVIEW) {
            Clip preview = this.clips.get(this.previewAnimationName);
            if (preview == null) {
                preview = findFirstPreviewClip();
            }
            if (preview != null) {
                active.previewClip = Optional.of(preview);
                active.actionSource = ActionSource.PREVIEW;
                active.setTime(preview, elapsedSeconds);
            }
        }

        if (isDebugEnabled()) {
            YesSteveModel.LOGGER.info("[DEBUG-animation-state] player={} model={} active main animation={} active hand animations={} active extra/custom action={} active controller animations={} action source={} registered extra actions={} hidden-by-default bones={}",
                    snapshot.uuid, this.modelId, clipName(active.mainStateClip), clipNames(active.handClips),
                    active.extraActionClip.map(clip -> clip.name).orElse("none"), clipNames(active.controllerClips), active.actionSource,
                    OpenYsmAnimationRegistry.listWheelActions(this.modelId), this.hiddenByDefaultBones);
        }

        return active;
    }

    private void addBuiltinAlwaysOnClips(ActiveAnimationSet active, Set<String> activeClipNames, float elapsedSeconds) {
        if (!addBuiltinAlwaysOnClip(active, activeClipNames, "pre_parallel0", ControllerLayer.PRE_MAIN,
                elapsedSeconds, "builtin.pre_parallel_0")) {
            addBuiltinAlwaysOnClip(active, activeClipNames, "Hair_Physics", ControllerLayer.PRE_MAIN,
                    elapsedSeconds, "builtin.pre_parallel_0");
        }

        for (int i = 1; i <= 7; i++) {
            addBuiltinAlwaysOnClip(active, activeClipNames, "pre_parallel" + i, ControllerLayer.PRE_MAIN,
                    elapsedSeconds, "builtin.pre_parallel_" + i);
        }
        for (int i = 0; i <= 7; i++) {
            addBuiltinAlwaysOnClip(active, activeClipNames, "parallel" + i, ControllerLayer.PARALLEL,
                    elapsedSeconds, "builtin.parallel_" + i);
        }
    }

    private boolean addBuiltinAlwaysOnClip(ActiveAnimationSet active, Set<String> activeClipNames, String clipName,
                                           ControllerLayer layer, float elapsedSeconds, String controllerName) {
        Clip clip = findClipByName(clipName);
        if (clip == null || activeClipNames.contains(clip.name)) {
            return false;
        }

        active.addControllerClip(clip, layer, elapsedSeconds, 1.0F, controllerName, "default");
        activeClipNames.add(clip.name);
        return true;
    }

    public void apply(Map<String, OpenYsmBone> bones, ActiveAnimationSet active, float partialTicks) {
        if (bones == null || active == null) {
            return;
        }

        for (ActiveAnimationSet.ActiveClip activeClip : active.activeClipsInOrder()) {
            for (String boneName : activeClip.getClip().touchedBones) {
                OpenYsmBone bone = bones.get(boneName);
                if (bone != null) {
                    bone.setVisible(true);
                }
            }
        }

        for (ActiveAnimationSet.ActiveClip activeClip : active.activeClipsInOrder()) {
            applyClip(bones, activeClip.getClip(), activeClip.getTimeSeconds(), activeClip.getWeight());
        }
    }

    private void registerClip(Clip clip) {
        applyMetadata(clip);
        if (this.controllerReferencedNames.contains(clip.name)) {
            clip.isControllerReferenced = true;
        }
        Clip existing = this.clips.get(clip.name);
        if (existing == null || existing.sourceType == AnimationSourceType.UNKNOWN) {
            this.clips.put(clip.name, clip);
        }
    }

    private void markControllerReferences() {
        for (String animationName : this.controllerReferencedNames) {
            Clip clip = this.clips.get(animationName);
            if (clip != null) {
                clip.isControllerReferenced = true;
            } else {
                this.clips.put(animationName, Clip.placeholder(animationName, "controller", AnimationSourceType.CONTROLLER_REFERENCED));
            }
        }
    }

    private void applyMetadata(Clip clip) {
        ActionMetadata metadata = this.actionMetadata.get(clip.name);
        if (metadata != null) {
            clip.displayName = metadata.displayName == null || metadata.displayName.isEmpty() ? clip.name : metadata.displayName;
            clip.icon = parseIcon(metadata.icon);
            clip.description = metadata.description == null ? "" : metadata.description;
            clip.globalAction = metadata.global;
        }
    }

    private void parseJsonBones(Clip clip, JsonObject bones) {
        if (bones == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
            if (!boneEntry.getValue().isJsonObject()) {
                continue;
            }
            BoneTrack track = new BoneTrack();
            JsonObject bone = boneEntry.getValue().getAsJsonObject();
            parseJsonChannel(bone.get("rotation"), track.rotation, 0.0F);
            parseJsonChannel(bone.get("position"), track.position, 0.0F);
            parseJsonChannel(bone.get("scale"), track.scale, 1.0F);
            if (!track.isEmpty()) {
                clip.boneTracks.put(boneEntry.getKey(), track);
            }
            clip.touchedBones.add(boneEntry.getKey());
        }
    }

    private void parseRawBones(Clip clip, List<RawYsmModel.RawBoneAnimation> boneAnimations) {
        if (boneAnimations == null) {
            return;
        }
        for (RawYsmModel.RawBoneAnimation boneAnimation : boneAnimations) {
            if (boneAnimation == null || boneAnimation.boneName == null || boneAnimation.boneName.isEmpty()) {
                continue;
            }
            BoneTrack track = new BoneTrack();
            parseRawChannel(boneAnimation.rotation, track.rotation, 0.0F);
            parseRawChannel(boneAnimation.position, track.position, 0.0F);
            parseRawChannel(boneAnimation.scale, track.scale, 1.0F);
            if (!track.isEmpty()) {
                clip.boneTracks.put(boneAnimation.boneName, track);
            }
            clip.touchedBones.add(boneAnimation.boneName);
        }
    }

    private void parseJsonChannel(JsonElement channel, List<Keyframe> out, float defaultValue) {
        if (channel == null || channel.isJsonNull()) {
            return;
        }
        if (channel.isJsonPrimitive() || channel.isJsonArray() || isSingleKeyframeObject(channel)) {
            float[] vector = vectorFromJson(channel, defaultValue);
            if (vector != null) {
                out.add(new Keyframe(0.0F, vector));
            }
            return;
        }
        if (!channel.isJsonObject()) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : channel.getAsJsonObject().entrySet()) {
            Float timestamp = parseTimestamp(entry.getKey());
            if (timestamp == null) {
                continue;
            }
            float[] vector = vectorFromJson(entry.getValue(), defaultValue);
            if (vector != null) {
                out.add(new Keyframe(timestamp, vector));
            }
        }
        out.sort(Comparator.comparingDouble(frame -> frame.timestamp));
    }

    private void parseRawChannel(List<RawYsmModel.RawKeyframe> channel, List<Keyframe> out, float defaultValue) {
        if (channel == null) {
            return;
        }
        for (RawYsmModel.RawKeyframe frame : channel) {
            float[] vector = vectorFromObjects(frame.postData, defaultValue);
            if (vector == null && frame.hasPreData) {
                vector = vectorFromObjects(frame.preData, defaultValue);
            }
            if (vector != null) {
                out.add(new Keyframe(frame.timestamp, vector));
            }
        }
        out.sort(Comparator.comparingDouble(frame -> frame.timestamp));
    }

    private void collectControllerAnimations(JsonElement animations) {
        if (animations == null || animations.isJsonNull()) {
            return;
        }
        if (animations.isJsonArray()) {
            for (JsonElement element : animations.getAsJsonArray()) {
                collectControllerAnimations(element);
            }
            return;
        }
        if (animations.isJsonPrimitive()) {
            String name = safeString(animations);
            if (!name.isEmpty()) {
                this.controllerReferencedNames.add(normalizeClipName(name));
            }
            return;
        }
        if (animations.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : animations.getAsJsonObject().entrySet()) {
                this.controllerReferencedNames.add(normalizeClipName(entry.getKey()));
            }
        }
    }

    private void collectControllerAnimations(ControllerDefinition definition) {
        for (ControllerStateDefinition state : definition.getStates().values()) {
            for (ControllerAnimationRef animation : state.getAnimations()) {
                if (!animation.getAnimationName().isEmpty()) {
                    this.controllerReferencedNames.add(normalizeClipName(animation.getAnimationName()));
                }
            }
        }
    }

    private ControllerDefinition parseJsonController(String controllerName, JsonObject controller) {
        Map<String, ControllerStateDefinition> states = new LinkedHashMap<>();
        JsonObject statesObject = controller.getAsJsonObject("states");
        if (statesObject != null) {
            for (Map.Entry<String, JsonElement> stateEntry : statesObject.entrySet()) {
                if (!stateEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject stateObject = stateEntry.getValue().getAsJsonObject();
                List<ControllerAnimationRef> animations = new ArrayList<>();
                parseControllerAnimationRefs(stateObject.get("animations"), animations);
                List<ControllerTransition> transitions = new ArrayList<>();
                parseControllerTransitions(stateObject.get("transitions"), transitions);
                states.put(stateEntry.getKey(), new ControllerStateDefinition(stateEntry.getKey(), animations, transitions,
                        getFloat(stateObject, "blend_transition", 0.0F)));
            }
        }
        return new ControllerDefinition(controllerName, getString(controller, "initial_state", ""), states);
    }

    private ControllerDefinition parseRawController(RawYsmModel.RawAnimationController controller) {
        Map<String, ControllerStateDefinition> states = new LinkedHashMap<>();
        for (RawYsmModel.RawControllerState state : controller.states) {
            if (state == null || state.name == null || state.name.isEmpty()) {
                continue;
            }
            List<ControllerAnimationRef> animations = new ArrayList<>();
            if (state.animations != null) {
                for (Map.Entry<String, String> animation : state.animations.entrySet()) {
                    animations.add(new ControllerAnimationRef(animation.getKey(), animation.getValue()));
                }
            }
            List<ControllerTransition> transitions = new ArrayList<>();
            if (state.transitions != null) {
                for (Map.Entry<String, String> transition : state.transitions.entrySet()) {
                    transitions.add(new ControllerTransition(transition.getKey(), transition.getValue()));
                }
            }
            states.put(state.name, new ControllerStateDefinition(state.name, animations, transitions, state.blendTransitionValue));
        }
        return new ControllerDefinition(firstNonEmpty(controller.animationName, controller.name), controller.initialState, states);
    }

    private void parseControllerAnimationRefs(JsonElement animations, List<ControllerAnimationRef> out) {
        if (animations == null || animations.isJsonNull()) {
            return;
        }
        if (animations.isJsonArray()) {
            for (JsonElement element : animations.getAsJsonArray()) {
                parseControllerAnimationRefs(element, out);
            }
            return;
        }
        if (animations.isJsonPrimitive()) {
            String name = safeString(animations);
            if (!name.isEmpty()) {
                out.add(new ControllerAnimationRef(name, "1"));
            }
            return;
        }
        if (!animations.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : animations.getAsJsonObject().entrySet()) {
            String weight = controllerExpressionString(entry.getValue(), "1");
            out.add(new ControllerAnimationRef(entry.getKey(), weight));
        }
    }

    private void parseControllerTransitions(JsonElement transitions, List<ControllerTransition> out) {
        if (transitions == null || transitions.isJsonNull()) {
            return;
        }
        if (transitions.isJsonArray()) {
            for (JsonElement element : transitions.getAsJsonArray()) {
                parseControllerTransitions(element, out);
            }
            return;
        }
        if (!transitions.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : transitions.getAsJsonObject().entrySet()) {
            out.add(new ControllerTransition(entry.getKey(), controllerExpressionString(entry.getValue(), "false")));
        }
    }

    private static String controllerExpressionString(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
        } catch (RuntimeException ignored) {
            return fallback;
        }
        return fallback;
    }

    private Clip selectMainState(PlayerStateSnapshot snapshot) {
        String preferred;
        if (snapshot.sneaking) {
            preferred = snapshot.isMoving() ? "sneak" : "sneaking";
        } else if (snapshot.sprinting && snapshot.isMoving()) {
            preferred = "run";
        } else if (snapshot.isMoving()) {
            preferred = "walk";
        } else {
            preferred = "idle";
        }
        Clip clip = findMainClip(preferred);
        if (clip != null) {
            return clip;
        }
        for (String fallback : MAIN_STATE_PRIORITY) {
            clip = findMainClip(fallback);
            if (clip != null) {
                return clip;
            }
        }
        return null;
    }

    private Clip findMainClip(String name) {
        Clip clip = this.clips.get(name);
        return clip != null && clip.sourceType == AnimationSourceType.MAIN ? clip : null;
    }

    private List<Clip> selectHandClips(PlayerStateSnapshot snapshot) {
        List<Clip> selected = new ArrayList<>();
        addHandClip(selected, selectHandClip(snapshot, Hand.MAIN_HAND));
        addHandClip(selected, selectHandClip(snapshot, Hand.OFF_HAND));
        return selected;
    }

    private Clip selectHandClip(PlayerStateSnapshot snapshot, Hand hand) {
        String suffix = hand == Hand.MAIN_HAND ? "mainhand" : "offhand";
        if (snapshot.usingItem && snapshot.usingHand == hand) {
            Clip clip = findArmClip("use_" + suffix);
            if (clip != null) {
                return clip;
            }
        }
        if (snapshot.swingInProgress && snapshot.swingingHand == hand) {
            String swingName = hand == Hand.MAIN_HAND ? "swing_hand" : "swing_offhand";
            Clip clip = findArmClip(swingName);
            if (clip != null) {
                return clip;
            }
        }
        if (snapshot.isHandEmpty(hand)) {
            Clip clip = findArmClip("hold_" + suffix + ":empty");
            if (clip == null) {
                clip = findArmClip("hold_" + suffix);
            }
            return clip;
        }
        return findArmClip("hold_" + suffix);
    }

    private Clip findArmClip(String name) {
        Clip clip = this.clips.get(name);
        return clip != null && clip.sourceType == AnimationSourceType.ARM ? clip : null;
    }

    private Clip findClipByName(String name) {
        Clip clip = this.clips.get(name);
        if (clip != null) {
            return clip;
        }
        for (Clip value : this.clips.values()) {
            if (value.name.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    private void addHandClip(List<Clip> selected, Clip clip) {
        if (clip != null && !selected.contains(clip)) {
            selected.add(clip);
        }
    }

    private Clip findFirstPreviewClip() {
        for (Clip clip : this.clips.values()) {
            if (clip.isGuiPreviewAction) {
                return clip;
            }
        }
        return null;
    }

    private void applyClip(Map<String, OpenYsmBone> bones, Clip clip, float elapsedSeconds, float weight) {
        if (weight <= 0.0F) {
            return;
        }
        float sampleTime = clip.loopMode.time(elapsedSeconds, clip.length);
        for (Map.Entry<String, BoneTrack> entry : clip.boneTracks.entrySet()) {
            OpenYsmBone bone = bones.get(entry.getKey());
            if (bone == null) {
                continue;
            }
            BoneTrack track = entry.getValue();
            float[] rotation = sample(track.rotation, sampleTime);
            if (rotation != null) {
                bone.addRotation(-rotation[0] * DEG_TO_RAD * weight, -rotation[1] * DEG_TO_RAD * weight,
                        rotation[2] * DEG_TO_RAD * weight);
            }
            float[] position = sample(track.position, sampleTime);
            if (position != null) {
                bone.addPosition(position[0] * weight, -position[1] * weight, position[2] * weight);
            }
            float[] scale = sample(track.scale, sampleTime);
            if (scale != null) {
                bone.setScale(1.0F + (scale[0] - 1.0F) * weight,
                        1.0F + (scale[1] - 1.0F) * weight,
                        1.0F + (scale[2] - 1.0F) * weight);
            }
        }
    }

    private float[] sample(List<Keyframe> frames, float time) {
        if (frames.isEmpty()) {
            return null;
        }
        if (frames.size() == 1 || time <= frames.get(0).timestamp) {
            return frames.get(0).values;
        }
        Keyframe previous = frames.get(0);
        for (int i = 1; i < frames.size(); i++) {
            Keyframe next = frames.get(i);
            if (time <= next.timestamp) {
                float span = Math.max(0.0001F, next.timestamp - previous.timestamp);
                float alpha = Math.max(0.0F, Math.min(1.0F, (time - previous.timestamp) / span));
                return lerp(previous.values, next.values, alpha);
            }
            previous = next;
        }
        return frames.get(frames.size() - 1).values;
    }

    private static float[] lerp(float[] a, float[] b, float alpha) {
        return new float[]{
                a[0] + (b[0] - a[0]) * alpha,
                a[1] + (b[1] - a[1]) * alpha,
                a[2] + (b[2] - a[2]) * alpha
        };
    }

    private boolean isWheelVisible(Clip clip) {
        if (clip == null || clip.isMainState || clip.isHandCondition || clip.isGuiPreviewAction) {
            return false;
        }
        if (clip.sourceType == AnimationSourceType.CONTROLLER_REFERENCED && !this.explicitWheelActions.contains(clip.name)) {
            return false;
        }
        if (clip.sourceType == AnimationSourceType.EXTRA) {
            return true;
        }
        if (clip.sourceType == AnimationSourceType.CUSTOM) {
            return this.explicitWheelActions.contains(clip.name) || isCommonActionName(clip.name);
        }
        return this.explicitWheelActions.contains(clip.name);
    }

    private ActionEntry toActionEntry(Clip clip) {
        boolean global = clip.globalAction || (isCommonActionName(clip.name) && !this.explicitWheelActions.contains(clip.name));
        return new ActionEntry(clip.name, clip.displayName, clip.sourceType, global, this.modelId,
                clip.icon, clip.loopMode, clip.description);
    }

    private static void sortActions(List<ActionEntry> actions) {
        actions.sort(Comparator.comparing(ActionEntry::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ActionEntry::getAnimationName, String.CASE_INSENSITIVE_ORDER));
    }

    private AnimationSourceType inferSourceType(String group, String name) {
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (isPreviewName(lowerName) || (!this.previewAnimationName.isEmpty() && this.previewAnimationName.equals(name))) {
            return AnimationSourceType.GUI_PREVIEW;
        }
        String lowerGroup = group == null ? "" : group.toLowerCase(Locale.ROOT);
        return switch (lowerGroup) {
            case "main" -> AnimationSourceType.MAIN;
            case "arm" -> AnimationSourceType.ARM;
            case "extra" -> AnimationSourceType.EXTRA;
            case "controller", "animation_controller", "animation_controllers" -> AnimationSourceType.CONTROLLER_REFERENCED;
            case "unknown", "" -> AnimationSourceType.UNKNOWN;
            default -> AnimationSourceType.CUSTOM;
        };
    }

    private static String groupFromRawType(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            default -> "custom";
        };
    }

    private static boolean isMainStateName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("idle") || lower.equals("walk") || lower.equals("run")
                || lower.equals("sneak") || lower.equals("sneaking") || lower.equals("swim")
                || lower.equals("swimming") || lower.equals("fly") || lower.equals("flying")
                || lower.equals("jump") || lower.equals("fall") || lower.equals("sleep");
    }

    private static boolean isHandConditionName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("use_") || lower.startsWith("swing_") || lower.startsWith("hold_");
    }

    private static boolean isPreviewName(String lowerName) {
        return lowerName.equals("gui") || lowerName.equals("preview") || lowerName.equals("model_preview")
                || lowerName.contains("model_preview");
    }

    private static boolean isCommonActionName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("wave") || lower.equals("sit") || lower.equals("dance") || lower.equals("clap")
                || lower.equals("yes") || lower.equals("no") || lower.equals("hello") || lower.equals("pose")
                || lower.equals("swing") || lower.equals("bow") || lower.equals("greet");
    }

    private static String normalizeClipName(String name) {
        return name == null ? "" : name.trim();
    }

    private static boolean isSingleKeyframeObject(JsonElement element) {
        if (!element.isJsonObject()) {
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        return object.has("post") || object.has("pre") || object.has("vector");
    }

    private static float[] vectorFromJson(JsonElement element, float defaultValue) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (!primitive.isNumber()) {
                    return null;
                }
                float value = primitive.getAsFloat();
                return new float[]{value, value, value};
            }
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                float[] values = new float[]{defaultValue, defaultValue, defaultValue};
                for (int i = 0; i < 3 && i < array.size(); i++) {
                    JsonElement value = array.get(i);
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                        return null;
                    }
                    values[i] = value.getAsFloat();
                }
                return values;
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("post")) {
                    return vectorFromJson(object.get("post"), defaultValue);
                }
                if (object.has("pre")) {
                    return vectorFromJson(object.get("pre"), defaultValue);
                }
                if (object.has("vector")) {
                    return vectorFromJson(object.get("vector"), defaultValue);
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static float[] vectorFromObjects(Object[] values, float defaultValue) {
        if (values == null || values.length == 0) {
            return null;
        }
        float[] vector = new float[]{defaultValue, defaultValue, defaultValue};
        for (int i = 0; i < 3 && i < values.length; i++) {
            Object value = values[i];
            if (!(value instanceof Number)) {
                return null;
            }
            vector[i] = ((Number) value).floatValue();
        }
        return vector;
    }

    private static Float parseTimestamp(String key) {
        try {
            return Float.parseFloat(key);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String safeString(JsonElement element) {
        try {
            return element.getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static float getFloat(JsonObject json, String key, float fallback) {
        try {
            return json != null && json.has(key) ? json.get(key).getAsFloat() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String getString(JsonObject json, String key, String fallback) {
        try {
            return json != null && json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.isEmpty() ? first : second == null ? "" : second;
    }

    private static ResourceLocation parseIcon(String icon) {
        if (icon == null || icon.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(icon);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean("yes_steve_model.debugAnimationState");
    }

    private static String clipName(Clip clip) {
        return clip == null ? "none" : clip.name;
    }

    private static List<String> clipNames(List<Clip> clips) {
        List<String> names = new ArrayList<>();
        for (Clip clip : clips) {
            names.add(clip.name);
        }
        return names;
    }

    private static final class ActionMetadata {
        private final String displayName;
        private final String icon;
        private final String description;
        private final boolean global;

        private ActionMetadata(String displayName, String icon, String description, boolean global) {
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
            this.global = global;
        }
    }

    public static final class Clip {
        public final String name;
        public final String group;
        public final AnimationSourceType sourceType;
        public final String originFile;
        public final Set<String> touchedBones = new LinkedHashSet<>();
        public final Map<String, BoneTrack> boneTracks = new LinkedHashMap<>();
        public final boolean isMainState;
        public final boolean isHandCondition;
        public boolean isExtraAction;
        public final boolean isGuiPreviewAction;
        public boolean isControllerReferenced;
        public String displayName;
        public ResourceLocation icon;
        public String description = "";
        public boolean globalAction;
        public LoopMode loopMode;
        public float length;

        private Clip(String name, String group, AnimationSourceType sourceType, String originFile, LoopMode loopMode, float length) {
            this.name = name;
            this.group = group == null ? "" : group;
            this.sourceType = sourceType == null ? AnimationSourceType.UNKNOWN : sourceType;
            this.originFile = originFile == null ? "" : originFile;
            this.loopMode = loopMode == null ? LoopMode.PLAY_ONCE : loopMode;
            this.length = Math.max(0.0F, length);
            this.isMainState = this.sourceType == AnimationSourceType.MAIN && isMainStateName(name);
            this.isHandCondition = this.sourceType == AnimationSourceType.ARM && isHandConditionName(name);
            this.isExtraAction = this.sourceType == AnimationSourceType.EXTRA;
            this.isGuiPreviewAction = this.sourceType == AnimationSourceType.GUI_PREVIEW;
            this.isControllerReferenced = this.sourceType == AnimationSourceType.CONTROLLER_REFERENCED;
            this.displayName = name;
        }

        private static Clip placeholder(String name, String group, AnimationSourceType sourceType) {
            return new Clip(name, group, sourceType, "animation_controller", LoopMode.PLAY_ONCE, 0.0F);
        }

        private float computeLengthFromTracks() {
            float max = 0.0F;
            for (BoneTrack track : this.boneTracks.values()) {
                max = Math.max(max, track.maxTimestamp());
            }
            return max;
        }
    }

    public static final class BoneTrack {
        public final List<Keyframe> rotation = new ArrayList<>();
        public final List<Keyframe> position = new ArrayList<>();
        public final List<Keyframe> scale = new ArrayList<>();

        private boolean isEmpty() {
            return this.rotation.isEmpty() && this.position.isEmpty() && this.scale.isEmpty();
        }

        private float maxTimestamp() {
            return Math.max(Math.max(maxTimestamp(this.rotation), maxTimestamp(this.position)), maxTimestamp(this.scale));
        }

        private static float maxTimestamp(List<Keyframe> frames) {
            return frames.isEmpty() ? 0.0F : frames.get(frames.size() - 1).timestamp;
        }
    }

    public static final class Keyframe {
        public final float timestamp;
        public final float[] values;

        private Keyframe(float timestamp, float[] values) {
            this.timestamp = timestamp;
            this.values = values;
        }
    }
}
