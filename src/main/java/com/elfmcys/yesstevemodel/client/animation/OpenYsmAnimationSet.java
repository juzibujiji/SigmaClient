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
import com.elfmcys.yesstevemodel.client.animation.molang.MolangBindings;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangContext;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangEvaluator;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangExpression;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangParser;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenYsmAnimationSet {
    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;
    private static final String[] MAIN_STATE_PRIORITY = new String[]{"idle", "walk", "run", "sneak", "sneaking"};
    private static final int MAX_EVENTS_PER_CLIP = 256;
    private static final int MAX_EVENT_TEXT_LENGTH = 512;
    private static final int MAX_BLEND_WEIGHT_EXPRESSION_LENGTH = 256;
    private static final int MAX_KEYFRAME_EXPRESSION_LENGTH = 512;
    private static final int MAX_FUNCTION_CONTROLLER_SCRIPT_LENGTH = 8192;
    private static final int MAX_FUNCTION_CONTROLLER_ANIMATIONS = 64;
    private static final Pattern SET_ANIMATION_PATTERN = Pattern.compile(
            "ctrl\\s*\\.\\s*set_animation\\s*\\(\\s*(['\"])([^'\"]{1,128})\\1",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CTRL_HAND_FUNCTION_PATTERN = Pattern.compile(
            "ctrl\\s*\\.\\s*(use|swing|hold)\\s*\\(\\s*(['\"])(mainhand|offhand)\\2\\s*(?:,\\s*(['\"])([^'\"]{0,128})\\4\\s*)?\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YSM_STRING_FUNCTION_PATTERN = Pattern.compile(
            "ysm\\s*\\.\\s*(mod_version|keyboard)\\s*\\([^)]{0,128}\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_BLOCK_TAG_PATTERN = Pattern.compile(
            "query\\s*\\.\\s*relative_block_has_any_tag\\s*\\(\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*(['\"])([^'\"]{1,128})\\4\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private final String modelId;
    private final Map<String, Clip> clips = new LinkedHashMap<>();
    private final Map<String, ActionMetadata> actionMetadata = new LinkedHashMap<>();
    private final Map<String, String> rootWheelEntries = new LinkedHashMap<>();
    private final Map<String, WheelActionGroup> wheelGroups = new LinkedHashMap<>();
    private final Map<String, ExtraActionButton> actionButtons = new LinkedHashMap<>();
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

    public void registerWheelGroup(String id, Map<String, String> extras) {
        String safeId = normalizeWheelKey(id);
        if (safeId.isEmpty() || extras == null || extras.isEmpty()) {
            return;
        }
        WheelActionGroup group = new WheelActionGroup(safeId);
        for (Map.Entry<String, String> entry : extras.entrySet()) {
            String actionName = normalizeClipName(entry.getKey());
            if (actionName.isEmpty()) {
                continue;
            }
            group.actions.put(actionName, entry.getValue() == null ? "" : entry.getValue());
            String value = entry.getValue();
            if (value != null && value.startsWith("#")) {
                registerExplicitAction(actionName, actionName, "", "", false);
            } else {
                registerExplicitAction(actionName, actionName, "", value, false);
            }
        }
        this.wheelGroups.put(safeId, group);
    }

    public void registerRootWheelEntry(String key, String value) {
        String safeKey = key == null ? "" : key.trim();
        if (!safeKey.isEmpty()) {
            this.rootWheelEntries.put(safeKey, value == null ? "" : value);
        }
    }

    public void registerActionButton(ExtraActionButton button) {
        if (button == null || button.getId().isEmpty()) {
            return;
        }
        this.actionButtons.put(button.getId(), button);
    }

    public Map<String, WheelActionGroup> getWheelGroups() {
        return Collections.unmodifiableMap(this.wheelGroups);
    }

    public Map<String, String> getRootWheelEntries() {
        return Collections.unmodifiableMap(this.rootWheelEntries);
    }

    public Map<String, ExtraActionButton> getActionButtons() {
        return Collections.unmodifiableMap(this.actionButtons);
    }

    public Collection<ControllerDefinition> getControllerDefinitions() {
        return Collections.unmodifiableCollection(this.controllerDefinitions.values());
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
            clip.blendWeightExpression = blendWeightExpression(animation.get("blend_weight"));
            parseJsonBones(clip, animation.getAsJsonObject("bones"));
            parseJsonSoundEffects(clip, animation.get("sound_effects"));
            parseJsonTimelineEvents(clip, animation.get("timeline"));
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
            clip.blendWeightExpression = blendWeightExpression(animation.blendWeight);
            parseRawBones(clip, animation.boneAnimations);
            parseRawSoundEffects(clip, animation.soundEffects);
            parseRawTimelineEvents(clip, animation.timelineEvents);
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

    public void addFunctionControllerReferences(Map<String, String> functions) {
        if (functions == null || functions.isEmpty()) {
            return;
        }
        int registered = 0;
        for (Map.Entry<String, String> entry : functions.entrySet()) {
            FunctionControllerDefinition parsed = parseFunctionController(entry.getKey(), entry.getValue());
            if (parsed == null || parsed.animationRefs.isEmpty()) {
                continue;
            }
            Map<String, ControllerStateDefinition> states = new LinkedHashMap<>();
            states.put("default", new ControllerStateDefinition("default", parsed.animationRefs,
                    Collections.emptyList(), 0.0F));
            ControllerDefinition definition = new ControllerDefinition(parsed.controllerName, "default", states);
            if (definition.isUsable()) {
                this.controllerDefinitions.put(definition.getName(), definition);
                collectControllerAnimations(definition);
                registered++;
            }
        }
        if (registered > 0) {
            markControllerReferences();
        }
    }

    public void configureDefaultHidden(Map<String, OpenYsmBone> bones) {
        this.hiddenByDefaultBones.clear();
        if (bones == null || bones.isEmpty()) {
            return;
        }

        Set<String> mainTouched = new LinkedHashSet<>();
        Set<String> conditionalTouched = new LinkedHashSet<>();
        for (Clip clip : this.clips.values()) {
            if (clip.isMainState && !clip.isGuiPreviewAction) {
                // Only real selectable main states (idle/walk/run/sneak/...) prove a bone is
                // part of the always-visible body. The main animation file also carries
                // author config/organizational animations ('gui', part-toggle or art-frame
                // clips) that never auto-play; bones only they touch must stay hidden.
                mainTouched.addAll(clip.touchedBones);
            } else if (isBuiltinParallelName(clip.name)) {
                // Always-on parallel clips run every frame with weight 1; treating them as
                // visibility proof keeps e.g. physics-driven hair bones visible, matching
                // OpenYSM where bones are visible unless actively hidden.
                mainTouched.addAll(clip.touchedBones);
            } else {
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
        boolean playingExtraAnimation = isExtraActionPlaying(snapshot, extraState);
        OpenYsmControllerRuntime.Result controllerResult = OpenYsmControllerRuntime.tick(this.modelId,
                this.controllerDefinitions.values(), this.clips, snapshot, null, playingExtraAnimation);
        Set<String> activeClipNames = new LinkedHashSet<>();
        for (OpenYsmControllerRuntime.ActiveControllerAnimation controllerAnimation : controllerResult.getActiveAnimations()) {
            float clipWeight = evaluateClipBlendWeight(controllerAnimation.getClip(), snapshot);
            active.addControllerClip(controllerAnimation.getClip(), controllerAnimation.getLayer(),
                    controllerAnimation.getTimeSeconds(), controllerAnimation.getWeight() * clipWeight,
                    controllerAnimation.getControllerName(), controllerAnimation.getStateName());
            activeClipNames.add(controllerAnimation.getClip().name);
        }
        active.controllerEvents.addAll(controllerResult.getControllerEvents());
        addBuiltinAlwaysOnClips(active, activeClipNames, elapsedSeconds, snapshot);

        Clip main = context == AnimationRenderContext.FIRST_PERSON_ARM || controllerResult.hasMainLayerAnimation()
                ? null
                : selectMainState(snapshot);
        if (main != null) {
            active.mainStateClip = main;
            active.setTime(main, elapsedSeconds);
            active.setWeight(main, evaluateClipBlendWeight(main, snapshot));
        }

        for (Clip hand : selectHandClips(snapshot)) {
            active.handClips.add(hand);
            active.setTime(hand, elapsedSeconds);
            active.setWeight(hand, evaluateClipBlendWeight(hand, snapshot));
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
                    active.setWeight(extra, evaluateClipBlendWeight(extra, snapshot));
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
                active.setWeight(preview, evaluateClipBlendWeight(preview, snapshot));
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

    private boolean isExtraActionPlaying(PlayerStateSnapshot snapshot, OpenYsmPlayerAnimationState.State extraState) {
        if (snapshot == null || extraState == null || !this.modelId.equals(extraState.getModelId())) {
            return false;
        }
        Clip extra = this.clips.get(extraState.getAnimationName());
        if (extra == null || !isWheelVisible(extra)) {
            return false;
        }
        float elapsed = extraState.elapsedSeconds(snapshot.ageInTicks);
        return extra.loopMode != LoopMode.PLAY_ONCE || extra.length <= 0.0F || elapsed <= extra.length;
    }

    private void addBuiltinAlwaysOnClips(ActiveAnimationSet active, Set<String> activeClipNames, float elapsedSeconds,
                                         PlayerStateSnapshot snapshot) {
        if (!addBuiltinAlwaysOnClip(active, activeClipNames, "pre_parallel0", ControllerLayer.PRE_MAIN,
                elapsedSeconds, "builtin.pre_parallel_0", snapshot)) {
            addBuiltinAlwaysOnClip(active, activeClipNames, "Hair_Physics", ControllerLayer.PRE_MAIN,
                    elapsedSeconds, "builtin.pre_parallel_0", snapshot);
        }

        for (int i = 1; i <= 7; i++) {
            addBuiltinAlwaysOnClip(active, activeClipNames, "pre_parallel" + i, ControllerLayer.PRE_MAIN,
                    elapsedSeconds, "builtin.pre_parallel_" + i, snapshot);
        }
        for (int i = 0; i <= 7; i++) {
            addBuiltinAlwaysOnClip(active, activeClipNames, "parallel" + i, ControllerLayer.PARALLEL,
                    elapsedSeconds, "builtin.parallel_" + i, snapshot);
        }
    }

    private boolean addBuiltinAlwaysOnClip(ActiveAnimationSet active, Set<String> activeClipNames, String clipName,
                                           ControllerLayer layer, float elapsedSeconds, String controllerName,
                                           PlayerStateSnapshot snapshot) {
        Clip clip = findClipByName(clipName);
        if (clip == null || activeClipNames.contains(clip.name)) {
            return false;
        }

        active.addControllerClip(clip, layer, elapsedSeconds, evaluateClipBlendWeight(clip, snapshot),
                controllerName, "default");
        activeClipNames.add(clip.name);
        return true;
    }

    public void apply(Map<String, OpenYsmBone> bones, ActiveAnimationSet active, float partialTicks) {
        apply(bones, active, partialTicks, null);
    }

    public void apply(Map<String, OpenYsmBone> bones, ActiveAnimationSet active, float partialTicks,
                      PlayerStateSnapshot snapshot) {
        if (bones == null || active == null) {
            return;
        }

        try (AutoCloseable ignored = MolangContext.pushBones(bones)) {
            for (ActiveAnimationSet.ActiveClip activeClip : active.activeClipsInOrder()) {
                // A clip whose blend/controller weight evaluated to 0 is inactive this frame:
                // it must not un-hide condition-driven (default-hidden) bones it touches,
                // otherwise sub-models appear before their condition is met.
                if (activeClip.getWeight() <= 0.0F) {
                    continue;
                }
                for (String boneName : activeClip.getClip().touchedBones) {
                    OpenYsmBone bone = bones.get(boneName);
                    if (bone != null) {
                        bone.setVisible(true);
                    }
                }
            }

            for (ActiveAnimationSet.ActiveClip activeClip : active.activeClipsInOrder()) {
                applyClip(bones, activeClip.getClip(), activeClip.getTimeSeconds(), activeClip.getWeight(), snapshot);
            }
        } catch (Exception exception) {
            if (isDebugEnabled()) {
                YesSteveModel.LOGGER.info("[DEBUG-animation-state] model={} bone scoped animation apply failed", this.modelId,
                        exception);
            }
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

    private void parseJsonSoundEffects(Clip clip, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (clip.soundEffects.size() >= MAX_EVENTS_PER_CLIP) {
                return;
            }
            Float timestamp = parseTimestamp(entry.getKey());
            if (timestamp == null) {
                continue;
            }
            String effect = "";
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                effect = safeString(value);
            } else if (value.isJsonObject()) {
                effect = getString(value.getAsJsonObject(), "effect", "");
            }
            effect = safeEventText(effect);
            if (!effect.isEmpty()) {
                clip.soundEffects.add(new SoundEffectKeyframe(timestamp, effect));
            }
        }
        clip.soundEffects.sort(Comparator.comparingDouble(frame -> frame.timestamp));
    }

    private void parseRawSoundEffects(Clip clip, List<RawYsmModel.RawSoundEffect> soundEffects) {
        if (soundEffects == null) {
            return;
        }
        for (RawYsmModel.RawSoundEffect soundEffect : soundEffects) {
            if (clip.soundEffects.size() >= MAX_EVENTS_PER_CLIP) {
                return;
            }
            if (soundEffect == null) {
                continue;
            }
            String effect = safeEventText(soundEffect.effectName);
            if (!effect.isEmpty()) {
                clip.soundEffects.add(new SoundEffectKeyframe(Math.max(0.0F, soundEffect.timestamp), effect));
            }
        }
        clip.soundEffects.sort(Comparator.comparingDouble(frame -> frame.timestamp));
    }

    private void parseJsonTimelineEvents(Clip clip, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (clip.timelineEvents.size() >= MAX_EVENTS_PER_CLIP) {
                return;
            }
            Float timestamp = parseTimestamp(entry.getKey());
            if (timestamp == null) {
                continue;
            }
            List<String> events = new ArrayList<>();
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                for (JsonElement child : value.getAsJsonArray()) {
                    addSafeTimelineEvent(events, child);
                }
            } else {
                addSafeTimelineEvent(events, value);
            }
            if (!events.isEmpty()) {
                clip.timelineEvents.add(new TimelineEventKeyframe(timestamp, events));
            }
        }
        clip.timelineEvents.sort(Comparator.comparingDouble(frame -> frame.timestamp));
    }

    private void parseRawTimelineEvents(Clip clip, List<RawYsmModel.RawTimelineEvent> timelineEvents) {
        if (timelineEvents == null) {
            return;
        }
        for (RawYsmModel.RawTimelineEvent timelineEvent : timelineEvents) {
            if (clip.timelineEvents.size() >= MAX_EVENTS_PER_CLIP) {
                return;
            }
            if (timelineEvent == null || timelineEvent.events == null) {
                continue;
            }
            List<String> events = new ArrayList<>();
            for (String event : timelineEvent.events) {
                String safe = safeEventText(event);
                if (!safe.isEmpty()) {
                    events.add(safe);
                }
            }
            if (!events.isEmpty()) {
                clip.timelineEvents.add(new TimelineEventKeyframe(Math.max(0.0F, timelineEvent.timestamp), events));
            }
        }
        clip.timelineEvents.sort(Comparator.comparingDouble(frame -> frame.timestamp));
    }

    private static void addSafeTimelineEvent(List<String> events, JsonElement element) {
        if (events.size() >= MAX_EVENTS_PER_CLIP || element == null || !element.isJsonPrimitive()) {
            return;
        }
        String safe = safeEventText(safeString(element));
        if (!safe.isEmpty()) {
            events.add(safe);
        }
    }

    private void parseJsonChannel(JsonElement channel, List<Keyframe> out, float defaultValue) {
        if (channel == null || channel.isJsonNull()) {
            return;
        }
        if (channel.isJsonPrimitive() || channel.isJsonArray() || isSingleKeyframeObject(channel)) {
            ChannelVector vector = channelVectorFromJson(channel, defaultValue);
            if (vector != null) {
                out.add(new Keyframe(0.0F, vector.values, vector.expressions, vector.values, vector.expressions, 0));
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
            ChannelVector vector = channelVectorFromJson(entry.getValue(), defaultValue);
            if (vector != null) {
                out.add(new Keyframe(timestamp, vector.values, vector.expressions, vector.values, vector.expressions, 0));
            }
        }
        out.sort(Comparator.comparingDouble(frame -> frame.timestamp));
    }

    private void parseRawChannel(List<RawYsmModel.RawKeyframe> channel, List<Keyframe> out, float defaultValue) {
        if (channel == null) {
            return;
        }
        for (RawYsmModel.RawKeyframe frame : channel) {
            ChannelVector post = channelVectorFromObjects(frame.postData, defaultValue);
            ChannelVector pre = frame.hasPreData ? channelVectorFromObjects(frame.preData, defaultValue) : post;
            if (post == null) {
                post = pre;
            }
            if (pre == null) {
                pre = post;
            }
            if (post != null) {
                out.add(new Keyframe(frame.timestamp, pre.values, pre.expressions,
                        post.values, post.expressions, frame.interpolationMode));
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
                        getFloat(stateObject, "blend_transition", 0.0F),
                        parseControllerBlendTransitions(stateObject.get("blend_transition")),
                        parseControllerEventList(stateObject.get("on_entry")),
                        parseControllerEventList(stateObject.get("on_exit")),
                        parseControllerEventList(firstElement(stateObject, "sound_effects", "sound_effect"))));
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
            states.put(state.name, new ControllerStateDefinition(state.name, animations, transitions, state.blendTransitionValue,
                    state.blendTransitions,
                    sanitizeControllerEvents(state.onEntry),
                    sanitizeControllerEvents(state.onExit),
                    sanitizeControllerEvents(state.soundEffects)));
        }
        return new ControllerDefinition(firstNonEmpty(controller.animationName, controller.name), controller.initialState, states);
    }

    private static JsonElement firstElement(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && object.has(key)) {
                return object.get(key);
            }
        }
        return null;
    }

    private static List<String> parseControllerEventList(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Collections.emptyList();
        }
        List<String> events = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                addControllerEvent(events, controllerEventText(child));
                if (events.size() >= MAX_EVENTS_PER_CLIP) {
                    break;
                }
            }
        } else {
            addControllerEvent(events, controllerEventText(element));
        }
        return events;
    }

    private static String controllerEventText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return safeString(firstElement(object, "effect", "event", "sound", "name"));
        }
        return safeString(element);
    }

    private static List<String> sanitizeControllerEvents(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> events = new ArrayList<>();
        for (String value : values) {
            addControllerEvent(events, value);
            if (events.size() >= MAX_EVENTS_PER_CLIP) {
                break;
            }
        }
        return events;
    }

    private static void addControllerEvent(List<String> events, String value) {
        String safe = safeEventText(value);
        if (!safe.isEmpty()) {
            events.add(safe);
        }
    }

    private static String blendWeightExpression(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "1";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("expression")) {
                return blendWeightExpression(object.get("expression"));
            }
            if (object.has("value")) {
                return blendWeightExpression(object.get("value"));
            }
            return "1";
        }
        return blendWeightExpression(safeString(element));
    }

    private static String blendWeightExpression(Object value) {
        if (value == null) {
            return "1";
        }
        if (value instanceof Number) {
            return Float.toString(((Number)value).floatValue());
        }
        return blendWeightExpression(String.valueOf(value));
    }

    private static String blendWeightExpression(String value) {
        String expression = value == null ? "" : value.trim();
        if (expression.isEmpty() || expression.length() > MAX_BLEND_WEIGHT_EXPRESSION_LENGTH) {
            return "1";
        }
        return expression;
    }

    private FunctionControllerDefinition parseFunctionController(String functionName, String script) {
        String controllerName = functionControllerName(functionName);
        if (controllerName.isEmpty() || script == null || script.isEmpty()
                || script.length() > MAX_FUNCTION_CONTROLLER_SCRIPT_LENGTH) {
            return null;
        }
        String cleanScript = stripMolangComments(script);
        Matcher allSetAnimations = SET_ANIMATION_PATTERN.matcher(cleanScript);
        int totalSetAnimationCalls = 0;
        while (allSetAnimations.find()) {
            totalSetAnimationCalls++;
            if (totalSetAnimationCalls > MAX_FUNCTION_CONTROLLER_ANIMATIONS) {
                return null;
            }
        }
        if (totalSetAnimationCalls <= 0) {
            return null;
        }

        List<ControllerAnimationRef> refs = new ArrayList<>();
        int matchedConditionalCalls = 0;
        for (ConditionalBlock block : findConditionalBlocks(cleanScript)) {
            if (refs.size() >= MAX_FUNCTION_CONTROLLER_ANIMATIONS) {
                return null;
            }
            Matcher matcher = SET_ANIMATION_PATTERN.matcher(maskNestedConditionalBodies(block.body));
            while (matcher.find()) {
                String animation = normalizeClipName(matcher.group(2));
                String expression = sanitizeFunctionControllerExpression(block.condition);
                if (!animation.isEmpty() && !expression.isEmpty()) {
                    refs.add(new ControllerAnimationRef(animation, expression));
                    matchedConditionalCalls++;
                }
            }
        }

        if (matchedConditionalCalls == 0 && totalSetAnimationCalls == 1) {
            Matcher matcher = SET_ANIMATION_PATTERN.matcher(cleanScript);
            if (matcher.find()) {
                String animation = normalizeClipName(matcher.group(2));
                if (!animation.isEmpty()) {
                    refs.add(new ControllerAnimationRef(animation, "1"));
                }
            }
        } else if (matchedConditionalCalls != totalSetAnimationCalls) {
            return null;
        }

        return refs.isEmpty() ? null : new FunctionControllerDefinition(controllerName, refs);
    }

    private static String functionControllerName(String functionName) {
        if (functionName == null) {
            return "";
        }
        int at = functionName.indexOf('@');
        if (at < 0 || at + 1 >= functionName.length()) {
            return "";
        }
        String suffix = functionName.substring(at + 1).trim();
        if (suffix.toLowerCase(Locale.ROOT).endsWith(".molang")) {
            suffix = suffix.substring(0, suffix.length() - ".molang".length());
        }
        if (!suffix.toLowerCase(Locale.ROOT).startsWith("player_ctrl_")) {
            return "";
        }
        return "function." + suffix;
    }

    private static String stripMolangComments(String script) {
        StringBuilder out = new StringBuilder(script.length());
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : 0;
            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                    out.append(c);
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (escaped) {
                out.append(c);
                escaped = false;
                continue;
            }
            if (quoted && c == '\\') {
                out.append(c);
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) {
                    quoted = false;
                }
                out.append(c);
                continue;
            }
            if (c == '\'' || c == '"') {
                quoted = true;
                quote = c;
                out.append(c);
                continue;
            }
            if (c == '/' && next == '/') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static List<ConditionalBlock> findConditionalBlocks(String script) {
        List<ConditionalBlock> blocks = new ArrayList<>();
        findConditionalBlocks(script, 0, script.length(), "", blocks);
        return blocks;
    }

    private static String maskNestedConditionalBodies(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        char[] chars = body.toCharArray();
        for (ConditionalBlock child : findConditionalBlocks(body)) {
            int start = Math.max(0, Math.min(chars.length, child.bodyStart));
            int end = Math.max(start, Math.min(chars.length, child.bodyEnd));
            for (int i = start; i < end; i++) {
                chars[i] = ' ';
            }
        }
        return new String(chars);
    }

    private static void findConditionalBlocks(String script, int start, int end, String parentCondition,
                                              List<ConditionalBlock> blocks) {
        String lower = script.toLowerCase(Locale.ROOT);
        int index = start;
        while (index < end && blocks.size() < MAX_FUNCTION_CONTROLLER_ANIMATIONS) {
            ConditionalBlock block = findNextConditionalBlock(script, lower, index, end);
            if (block == null) {
                break;
            }
            String combinedCondition = combineConditions(parentCondition, block.condition);
            ConditionalBlock combined = new ConditionalBlock(combinedCondition, block.body, block.bodyStart, block.bodyEnd,
                    block.closeIndex);
            blocks.add(combined);
            findConditionalBlocks(script, block.bodyStart, block.bodyEnd, combinedCondition, blocks);
            index = block.closeIndex + 1;
        }
    }

    private static ConditionalBlock findNextConditionalBlock(String script, String lower, int start, int end) {
        int index = start;
        while (index < end) {
            int nextIf = findNextIf(script, lower, index, end);
            int nextTernary = findNextTernaryQuestion(script, index, end);
            if (nextIf < 0 && nextTernary < 0) {
                return null;
            }
            if (nextIf >= 0 && (nextTernary < 0 || nextIf < nextTernary)) {
                ConditionalBlock block = parseIfBlock(script, nextIf, end);
                if (block != null) {
                    return block;
                }
                index = nextIf + 2;
            } else {
                ConditionalBlock block = parseTernaryBlock(script, nextTernary, end);
                if (block != null) {
                    return block;
                }
                index = nextTernary + 1;
            }
        }
        return null;
    }

    private static int findNextIf(String script, String lower, int start, int end) {
        int index = start;
        while (index < end) {
            int ifIndex = lower.indexOf("if", index);
            if (ifIndex < 0 || ifIndex >= end) {
                return -1;
            }
            if (isWordBoundary(lower, ifIndex - 1) && isWordBoundary(lower, ifIndex + 2)
                    && !isQuoted(script, start, ifIndex)) {
                return ifIndex;
            }
            index = ifIndex + 2;
        }
        return -1;
    }

    private static ConditionalBlock parseIfBlock(String script, int ifIndex, int end) {
        int openParen = skipWhitespace(script, ifIndex + 2);
        if (openParen >= end || script.charAt(openParen) != '(') {
            return null;
        }
        int closeParen = findMatching(script, openParen, '(', ')');
        if (closeParen < 0 || closeParen >= end) {
            return null;
        }
        int openBrace = skipWhitespace(script, closeParen + 1);
        if (openBrace >= end || script.charAt(openBrace) != '{') {
            return null;
        }
        int closeBrace = findMatching(script, openBrace, '{', '}');
        if (closeBrace < 0 || closeBrace >= end) {
            return null;
        }
        return new ConditionalBlock(script.substring(openParen + 1, closeParen),
                script.substring(openBrace + 1, closeBrace), openBrace + 1, closeBrace, closeBrace);
    }

    private static int findNextTernaryQuestion(String script, int start, int end) {
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        int parenDepth = 0;
        for (int i = start; i < end; i++) {
            char c = script.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && c == '\\') {
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) {
                    quoted = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quoted = true;
                quote = c;
                continue;
            }
            if (c == '(') {
                parenDepth++;
            } else if (c == ')' && parenDepth > 0) {
                parenDepth--;
            } else if (c == '?' && parenDepth == 0) {
                return i;
            } else if (c == '?' && i + 1 < end && script.charAt(i + 1) == '{') {
                return i;
            }
        }
        return -1;
    }

    private static ConditionalBlock parseTernaryBlock(String script, int questionIndex, int end) {
        int openBrace = skipWhitespace(script, questionIndex + 1);
        if (openBrace >= end || script.charAt(openBrace) != '{') {
            return null;
        }
        int closeBrace = findMatching(script, openBrace, '{', '}');
        if (closeBrace < 0 || closeBrace >= end) {
            return null;
        }
        int conditionStart = findConditionStart(script, questionIndex);
        String condition = script.substring(conditionStart, questionIndex).trim();
        if (condition.startsWith(":")) {
            condition = condition.substring(1).trim();
        }
        return new ConditionalBlock(condition, script.substring(openBrace + 1, closeBrace),
                openBrace + 1, closeBrace, closeBrace);
    }

    private static int findConditionStart(String script, int questionIndex) {
        int parenDepth = 0;
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = questionIndex - 1; i >= 0; i--) {
            char c = script.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && c == '\\') {
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) {
                    quoted = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quoted = true;
                quote = c;
                continue;
            }
            if (c == ')') {
                parenDepth++;
            } else if (c == '(' && parenDepth > 0) {
                parenDepth--;
            } else if (parenDepth == 0 && (c == ';' || c == '{' || c == '}' || c == '\n' || c == '\r')) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String combineConditions(String parent, String child) {
        String safeParent = parent == null ? "" : parent.trim();
        String safeChild = child == null ? "" : child.trim();
        if (safeParent.isEmpty()) {
            return safeChild;
        }
        if (safeChild.isEmpty()) {
            return safeParent;
        }
        return "(" + safeParent + ") && (" + safeChild + ")";
    }

    private static String sanitizeFunctionControllerExpression(String expression) {
        if (expression == null) {
            return "";
        }
        String safe = normalizeFunctionControllerExpression(expression).trim();
        if (safe.isEmpty() || safe.length() > 384 || safe.indexOf(';') >= 0 || safe.indexOf('{') >= 0
                || safe.indexOf('}') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\'') >= 0) {
            return "";
        }
        try {
            MolangParser.parse(safe);
            return safe;
        } catch (MolangParser.ParseException ignored) {
            return "";
        }
    }

    private static String normalizeFunctionControllerExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return "";
        }
        String normalized = replaceCtrlHandFunctions(expression);
        normalized = replaceRelativeBlockTagQueries(normalized);
        normalized = YSM_STRING_FUNCTION_PATTERN.matcher(normalized).replaceAll("0");
        return normalized;
    }

    private static String replaceRelativeBlockTagQueries(String expression) {
        Matcher matcher = RELATIVE_BLOCK_TAG_PATTERN.matcher(expression);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String replacement = "query.relative_block_has_any_tag_x" + safeNumberToken(matcher.group(1))
                    + "_y" + safeNumberToken(matcher.group(2))
                    + "_z" + safeNumberToken(matcher.group(3))
                    + "_tag_" + safeResourceToken(matcher.group(5));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String safeNumberToken(String value) {
        if (value == null || value.isEmpty()) {
            return "0";
        }
        String trimmed = value.trim();
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < trimmed.length() && safe.length() < 16; i++) {
            char c = trimmed.charAt(i);
            if (c == '-') {
                safe.append('m');
            } else if (c == '+') {
                safe.append('p');
            } else if (c == '.') {
                safe.append('d');
            } else if (c >= '0' && c <= '9') {
                safe.append(c);
            }
        }
        return safe.length() == 0 ? "0" : safe.toString();
    }

    private static String safeResourceToken(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String raw = value.trim().toLowerCase(Locale.ROOT).replace(':', '_');
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < raw.length() && safe.length() < 96; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                safe.append(c);
            } else if (c == '_' || c == '-' || c == '/' || c == '.') {
                safe.append('_');
            }
        }
        return safe.toString();
    }

    private static String replaceCtrlHandFunctions(String expression) {
        Matcher matcher = CTRL_HAND_FUNCTION_PATTERN.matcher(expression);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String function = matcher.group(1).toLowerCase(Locale.ROOT);
            String hand = matcher.group(3).toLowerCase(Locale.ROOT);
            String filter = matcher.group(5);
            String replacement = filteredCtrlIdentifier(function, hand, filter);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String filteredCtrlIdentifier(String function, String hand, String filter) {
        String base = switch (function) {
            case "swing" -> "mainhand".equals(hand) ? "ctrl.swing_mainhand" : "ctrl.swing_offhand";
            case "hold" -> "mainhand".equals(hand) ? "ctrl.hold_mainhand" : "ctrl.hold_offhand";
            default -> "mainhand".equals(hand) ? "ctrl.using_mainhand" : "ctrl.using_offhand";
        };
        String safeFilter = safeItemFilterIdentifier(filter);
        return safeFilter.isEmpty() ? base : base + "_" + safeFilter;
    }

    private static String safeItemFilterIdentifier(String filter) {
        if (filter == null) {
            return "";
        }
        String value = filter.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        String type;
        String id;
        if (value.startsWith("#")) {
            type = "tag";
            id = value.substring(1);
        } else if (value.startsWith(":")) {
            type = "suffix";
            id = value.substring(1);
        } else {
            type = value.contains(":") ? "item" : "suffix";
            id = value;
        }
        id = id.replace(':', '.');
        StringBuilder safe = new StringBuilder(type);
        safe.append('_');
        for (int i = 0; i < id.length() && safe.length() < 80; i++) {
            char c = id.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                safe.append(c);
            } else if (c == '_' || c == '.' || c == '-' || c == '/') {
                safe.append('_');
            }
        }
        return safe.length() <= type.length() + 1 ? "" : safe.toString();
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int findMatching(String value, int openIndex, char open, char close) {
        int depth = 0;
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = openIndex; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && c == '\\') {
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) {
                    quoted = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quoted = true;
                quote = c;
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isWordBoundary(String value, int index) {
        if (index < 0 || index >= value.length()) {
            return true;
        }
        char c = value.charAt(index);
        return !((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_');
    }

    private static boolean isQuoted(String value, int start, int index) {
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = start; i < index && i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (quoted && c == '\\') {
                escaped = true;
            } else if (quoted && c == quote) {
                quoted = false;
            } else if (!quoted && (c == '\'' || c == '"')) {
                quoted = true;
                quote = c;
            }
        }
        return quoted;
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

    private Map<Float, Float> parseControllerBlendTransitions(JsonElement blendTransition) {
        if (blendTransition == null || blendTransition.isJsonNull() || !blendTransition.isJsonObject()) {
            return Collections.emptyMap();
        }
        Map<Float, Float> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : blendTransition.getAsJsonObject().entrySet()) {
            try {
                result.put(Float.parseFloat(entry.getKey()), entry.getValue().getAsFloat());
            } catch (RuntimeException ignored) {
            }
        }
        return result;
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
        for (String preferred : preferredMainStates(snapshot)) {
            Clip clip = findMainClip(preferred);
            if (clip != null) {
                return clip;
            }
        }
        for (String fallback : MAIN_STATE_PRIORITY) {
            Clip clip = findMainClip(fallback);
            if (clip != null) {
                return clip;
            }
        }
        return null;
    }

    /**
     * Candidate main-state names in priority order, mirroring real YSM's controller layering:
     * death > sleep > riding (boat/pig/mount/seat) > elytra > swim > tread water > climb >
     * creative flight > jump > sneak > run > walk > idle. Each candidate falls through to the
     * next when the model does not ship that animation.
     */
    private static List<String> preferredMainStates(PlayerStateSnapshot snapshot) {
        List<String> preferred = new ArrayList<>(4);
        if (snapshot.dead) {
            preferred.add("death");
        }
        if (snapshot.sleeping) {
            preferred.add("sleep");
        }
        if (snapshot.riding) {
            if (snapshot.ridingBoat) {
                preferred.add("boat");
            } else if (snapshot.ridingPig) {
                preferred.add("ride_pig");
            }
            preferred.add(snapshot.ridingLiving ? "ride" : "sit");
            preferred.add(snapshot.ridingLiving ? "sit" : "ride");
        }
        if (snapshot.elytraFlying) {
            preferred.add("elytra_fly");
        }
        if (snapshot.swimming) {
            preferred.add("swim");
        } else if (snapshot.inWater && !snapshot.onGround) {
            preferred.add("swim_stand");
            preferred.add("swim");
        }
        if (snapshot.climbing && !snapshot.onGround) {
            preferred.add("climbing");
            preferred.add("climb");
        }
        if (snapshot.creativeFlying && !snapshot.onGround) {
            preferred.add("fly");
        }
        if (snapshot.sneaking) {
            preferred.add(snapshot.isMoving() ? "sneak" : "sneaking");
        } else if (!snapshot.onGround && !snapshot.inWater && snapshot.deltaY > 0.01D) {
            preferred.add("jump");
        }
        if (snapshot.sprinting && snapshot.isMoving()) {
            preferred.add("run");
        }
        if (snapshot.isMoving()) {
            preferred.add("walk");
        }
        preferred.add("idle");
        return preferred;
    }

    private Clip findMainClip(String name) {
        Clip clip = this.clips.get(name);
        return clip != null && clip.sourceType == AnimationSourceType.MAIN ? clip : null;
    }

    /**
     * Whether this model drives its main body/limb motion itself, either through
     * main-state clips (idle/walk/run/...) or through a MAIN-layer animation controller.
     * YSM models drive limbs entirely through these; the vanilla biped pose copy
     * is only a fallback for models without them.
     */
    public boolean hasMainStateAnimations() {
        for (Clip clip : this.clips.values()) {
            if (clip.isMainState) {
                return true;
            }
        }
        for (ControllerDefinition definition : this.controllerDefinitions.values()) {
            if (definition.getLayer().replacesHardcodedMain()) {
                return true;
            }
        }
        return false;
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

    public float evaluateClipBlendWeight(Clip clip, PlayerStateSnapshot snapshot) {
        if (clip == null) {
            return 0.0F;
        }
        String expression = clip.blendWeightExpression;
        if (expression == null || expression.trim().isEmpty() || "1".equals(expression.trim())) {
            return 1.0F;
        }
        try {
            MolangExpression parsed = MolangParser.parse(expression);
            Map<String, Double> variables = snapshot == null
                    ? Collections.emptyMap()
                    : OpenYsmPlayerAnimationState.getGuiVariables(snapshot.uuid, this.modelId);
            MolangBindings bindings = new MolangBindings(variables, Collections.emptyMap());
            MolangContext context = MolangContext.controller(snapshot, this.modelId, "blend_weight", bindings,
                    false, false);
            double value = MolangEvaluator.evaluate(parsed, context).asDouble();
            return Double.isFinite(value) ? Math.max(0.0F, (float)value) : 0.0F;
        } catch (Exception exception) {
            debugBlendWeightFailure(clip, expression, exception);
            return 0.0F;
        }
    }

    private static void debugBlendWeightFailure(Clip clip, String expression, Exception exception) {
        if (!isDebugEnabled()) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] blend_weight failed clip={} expression={} reason={}",
                clip == null ? "" : clip.name, expression, exception.getMessage());
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

    private void applyClip(Map<String, OpenYsmBone> bones, Clip clip, float elapsedSeconds, float weight,
                           PlayerStateSnapshot snapshot) {
        if (weight <= 0.0F) {
            return;
        }
        float sampleTime = clip.loopMode.time(elapsedSeconds, clip.length);
        MolangContext keyframeContext = keyframeContext(clip, snapshot, sampleTime);
        for (Map.Entry<String, BoneTrack> entry : clip.boneTracks.entrySet()) {
            OpenYsmBone bone = bones.get(entry.getKey());
            if (bone == null) {
                continue;
            }
            BoneTrack track = entry.getValue();
            float[] rotation = sample(track.rotation, sampleTime, keyframeContext);
            if (rotation != null) {
                // Bedrock keyframe rotations apply unmodified in vanilla model space
                // (GeckoLib's (-x,-y,+z) storage and the geckolib->vanilla space conversion
                // both negate X and Y, cancelling out). See loader base-rotation handling.
                bone.addRotation(rotation[0] * DEG_TO_RAD * weight, rotation[1] * DEG_TO_RAD * weight,
                        rotation[2] * DEG_TO_RAD * weight);
            }
            float[] position = sample(track.position, sampleTime, keyframeContext);
            if (position != null) {
                bone.addPosition(position[0] * weight, -position[1] * weight, position[2] * weight);
            }
            float[] scale = sample(track.scale, sampleTime, keyframeContext);
            if (scale != null) {
                bone.setScale(1.0F + (scale[0] - 1.0F) * weight,
                        1.0F + (scale[1] - 1.0F) * weight,
                        1.0F + (scale[2] - 1.0F) * weight);
            }
        }
    }

    /**
     * Evaluation context for molang expression keyframes: exposes query.anim_time as the clip's
     * looped sample time plus the player snapshot queries and this player's GUI variables
     * (part-toggle / expression config forms write those).
     */
    private MolangContext keyframeContext(Clip clip, PlayerStateSnapshot snapshot, float sampleTime) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Double> variables = OpenYsmPlayerAnimationState.getGuiVariables(snapshot.uuid, this.modelId);
        MolangBindings bindings = new MolangBindings(variables, Collections.emptyMap());
        return MolangContext.controller(snapshot, this.modelId, clip.name, bindings, false, false, false, sampleTime);
    }

    private float[] sample(List<Keyframe> frames, float time, MolangContext context) {
        if (frames.isEmpty()) {
            return null;
        }
        if (frames.size() == 1 || time <= frames.get(0).timestamp) {
            Keyframe first = frames.get(0);
            return resolveKeyframeValues(first.postValues, first.postExpressions, context);
        }
        Keyframe previous = frames.get(0);
        for (int i = 1; i < frames.size(); i++) {
            Keyframe next = frames.get(i);
            if (time <= next.timestamp) {
                float span = Math.max(0.0001F, next.timestamp - previous.timestamp);
                float alpha = Math.max(0.0F, Math.min(1.0F, (time - previous.timestamp) / span));
                if (previous.interpolationMode == 1 || next.interpolationMode == 1) {
                    Keyframe before = i > 1 ? frames.get(i - 2) : previous;
                    Keyframe after = i + 1 < frames.size() ? frames.get(i + 1) : next;
                    return catmullRom(resolveKeyframeValues(before.postValues, before.postExpressions, context),
                            resolveKeyframeValues(previous.postValues, previous.postExpressions, context),
                            resolveKeyframeValues(next.preValues, next.preExpressions, context),
                            resolveKeyframeValues(after.preValues, after.preExpressions, context), alpha);
                }
                return lerp(resolveKeyframeValues(previous.postValues, previous.postExpressions, context),
                        resolveKeyframeValues(next.preValues, next.preExpressions, context), alpha);
            }
            previous = next;
        }
        Keyframe last = frames.get(frames.size() - 1);
        return resolveKeyframeValues(last.postValues, last.postExpressions, context);
    }

    private static float[] resolveKeyframeValues(float[] values, MolangExpression[] expressions, MolangContext context) {
        if (expressions == null || context == null) {
            return values;
        }
        float[] resolved = new float[]{values[0], values[1], values[2]};
        for (int i = 0; i < 3; i++) {
            MolangExpression expression = expressions[i];
            if (expression == null) {
                continue;
            }
            try {
                double value = MolangEvaluator.evaluate(expression, context).asDouble();
                if (Double.isFinite(value)) {
                    resolved[i] = (float) value;
                }
            } catch (Exception ignored) {
                // keep the numeric fallback for this component
            }
        }
        return resolved;
    }

    private static float[] lerp(float[] a, float[] b, float alpha) {
        return new float[]{
                a[0] + (b[0] - a[0]) * alpha,
                a[1] + (b[1] - a[1]) * alpha,
                a[2] + (b[2] - a[2]) * alpha
        };
    }

    private static float[] catmullRom(float[] p0, float[] p1, float[] p2, float[] p3, float alpha) {
        float t2 = alpha * alpha;
        float t3 = t2 * alpha;
        return new float[]{
                catmullRom(p0[0], p1[0], p2[0], p3[0], alpha, t2, t3),
                catmullRom(p0[1], p1[1], p2[1], p3[1], alpha, t2, t3),
                catmullRom(p0[2], p1[2], p2[2], p3[2], alpha, t2, t3)
        };
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t, float t2, float t3) {
        return 0.5F * ((2.0F * p1)
                + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
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
            case "arm", "fp_arm", "animation-arm", "animation-fp_arm", "first_person_arm" -> AnimationSourceType.ARM;
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
            case 11 -> "fp_arm";
            default -> "custom";
        };
    }

    private static boolean isMainStateName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("idle") || lower.equals("walk") || lower.equals("run")
                || lower.equals("sneak") || lower.equals("sneaking") || lower.equals("swim")
                || lower.equals("swimming") || lower.equals("swim_stand") || lower.equals("fly")
                || lower.equals("flying") || lower.equals("jump") || lower.equals("fall")
                || lower.equals("sleep") || lower.equals("death") || lower.equals("elytra_fly")
                || lower.equals("climb") || lower.equals("climbing") || lower.equals("boat")
                || lower.equals("ride") || lower.equals("ride_pig") || lower.equals("sit");
    }

    /**
     * Names of the always-on clips scheduled by {@code addBuiltinAlwaysOnClips}:
     * pre_parallel0-7 / parallel0-7 and the legacy Hair_Physics alias.
     */
    private static boolean isBuiltinParallelName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.equals("hair_physics")) {
            return true;
        }
        String digits = lower.startsWith("pre_parallel") ? lower.substring("pre_parallel".length())
                : lower.startsWith("parallel") ? lower.substring("parallel".length())
                : null;
        if (digits == null || digits.isEmpty()) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
        }
        return true;
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

    private static String normalizeWheelKey(String key) {
        if (key == null) {
            return "";
        }
        String value = key.trim();
        return value.startsWith("#") ? value : "#" + value;
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

    /** Numeric values plus optional per-component molang expressions for one keyframe vector. */
    private static final class ChannelVector {
        final float[] values;
        final MolangExpression[] expressions;

        ChannelVector(float[] values, MolangExpression[] expressions) {
            this.values = values;
            this.expressions = expressions;
        }
    }

    private static ChannelVector channelVectorFromObjects(Object[] values, float defaultValue) {
        if (values == null || values.length == 0) {
            return null;
        }
        float[] vector = new float[]{defaultValue, defaultValue, defaultValue};
        MolangExpression[] expressions = null;
        boolean anyComponent = false;
        for (int i = 0; i < 3 && i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Number) {
                vector[i] = ((Number) value).floatValue();
                anyComponent = true;
            } else if (value instanceof String) {
                MolangExpression parsed = parseKeyframeExpression((String) value, vector, i);
                if (parsed != null) {
                    if (expressions == null) {
                        expressions = new MolangExpression[3];
                    }
                    expressions[i] = parsed;
                }
                anyComponent = true;
            }
        }
        return anyComponent ? new ChannelVector(vector, expressions) : null;
    }

    private static ChannelVector channelVectorFromJson(JsonElement element, float defaultValue) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    float value = primitive.getAsFloat();
                    return new ChannelVector(new float[]{value, value, value}, null);
                }
                if (primitive.isString()) {
                    float[] vector = new float[]{defaultValue, defaultValue, defaultValue};
                    MolangExpression parsed = parseKeyframeExpression(primitive.getAsString(), vector, 0);
                    if (parsed == null) {
                        // Numeric string was folded into vector[0]; broadcast it like a plain number.
                        return new ChannelVector(new float[]{vector[0], vector[0], vector[0]}, null);
                    }
                    return new ChannelVector(vector, new MolangExpression[]{parsed, parsed, parsed});
                }
                return null;
            }
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                float[] vector = new float[]{defaultValue, defaultValue, defaultValue};
                MolangExpression[] expressions = null;
                for (int i = 0; i < 3 && i < array.size(); i++) {
                    JsonElement value = array.get(i);
                    if (!value.isJsonPrimitive()) {
                        return null;
                    }
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (primitive.isNumber()) {
                        vector[i] = primitive.getAsFloat();
                    } else if (primitive.isString()) {
                        MolangExpression parsed = parseKeyframeExpression(primitive.getAsString(), vector, i);
                        if (parsed != null) {
                            if (expressions == null) {
                                expressions = new MolangExpression[3];
                            }
                            expressions[i] = parsed;
                        }
                    } else {
                        return null;
                    }
                }
                return new ChannelVector(vector, expressions);
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("post")) {
                    return channelVectorFromJson(object.get("post"), defaultValue);
                }
                if (object.has("pre")) {
                    return channelVectorFromJson(object.get("pre"), defaultValue);
                }
                if (object.has("vector")) {
                    return channelVectorFromJson(object.get("vector"), defaultValue);
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /**
     * Parses one keyframe component. Plain numeric strings are folded into {@code vector[index]}
     * and null is returned; molang strings return the parsed expression (vector keeps the default
     * as fallback for evaluation failures). Unparseable strings keep the default silently — this
     * matches real YSM where a broken expression contributes nothing.
     */
    private static MolangExpression parseKeyframeExpression(String text, float[] vector, int index) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_KEYFRAME_EXPRESSION_LENGTH) {
            return null;
        }
        try {
            vector[index] = Float.parseFloat(trimmed);
            return null;
        } catch (NumberFormatException ignored) {
            // fall through to molang parsing
        }
        try {
            return MolangParser.parse(trimmed);
        } catch (MolangParser.ParseException exception) {
            if (isDebugEnabled()) {
                YesSteveModel.LOGGER.info("[DEBUG-animation-state] keyframe expression parse failed expression={} reason={}",
                        trimmed, exception.getMessage());
            }
            return null;
        }
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

    private static String safeEventText(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace('\0', ' ').trim();
        return safe.length() > MAX_EVENT_TEXT_LENGTH ? safe.substring(0, MAX_EVENT_TEXT_LENGTH) : safe;
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

    private static final class FunctionControllerDefinition {
        private final String controllerName;
        private final List<ControllerAnimationRef> animationRefs;

        private FunctionControllerDefinition(String controllerName, List<ControllerAnimationRef> animationRefs) {
            this.controllerName = controllerName;
            this.animationRefs = animationRefs;
        }
    }

    private static final class ConditionalBlock {
        private final String condition;
        private final String body;
        private final int bodyStart;
        private final int bodyEnd;
        private final int closeIndex;

        private ConditionalBlock(String condition, String body, int bodyStart, int bodyEnd, int closeIndex) {
            this.condition = condition == null ? "" : condition;
            this.body = body == null ? "" : body;
            this.bodyStart = bodyStart;
            this.bodyEnd = bodyEnd;
            this.closeIndex = closeIndex;
        }
    }

    public static final class WheelActionGroup {
        private final String id;
        private final Map<String, String> actions = new LinkedHashMap<>();

        private WheelActionGroup(String id) {
            this.id = id;
        }

        public String getId() {
            return this.id;
        }

        public String getDisplayName() {
            return this.id.startsWith("#") ? this.id.substring(1) : this.id;
        }

        public Map<String, String> getActions() {
            return Collections.unmodifiableMap(this.actions);
        }
    }

    public static final class ExtraActionButton {
        private final String id;
        private final String name;
        private final String description;
        private final List<ActionForm> forms;

        public ExtraActionButton(String id, String name, String description, List<ActionForm> forms) {
            this.id = normalizeWheelKey(id);
            this.name = name == null || name.isEmpty() ? this.id : name;
            this.description = description == null ? "" : description;
            this.forms = new ArrayList<>(forms == null ? Collections.emptyList() : forms);
        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public String getDescription() {
            return this.description;
        }

        public List<ActionForm> getForms() {
            return Collections.unmodifiableList(this.forms);
        }
    }

    public static final class ActionForm {
        private final String type;
        private final String title;
        private final String description;
        private final String defaultValue;
        private final float step;
        private final float min;
        private final float max;
        private final Map<String, String> labels;

        public ActionForm(String type, String title, String description, String defaultValue,
                          float step, float min, float max, Map<String, String> labels) {
            this.type = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.step = step;
            this.min = min;
            this.max = max;
            this.labels = new LinkedHashMap<>(labels == null ? Collections.emptyMap() : labels);
        }

        public String getType() {
            return this.type;
        }

        public String getTitle() {
            return this.title;
        }

        public String getDescription() {
            return this.description;
        }

        public String getDefaultValue() {
            return this.defaultValue;
        }

        public float getStep() {
            return this.step;
        }

        public float getMin() {
            return this.min;
        }

        public float getMax() {
            return this.max;
        }

        public Map<String, String> getLabels() {
            return Collections.unmodifiableMap(this.labels);
        }
    }

    public static final class Clip {
        public final String name;
        public final String group;
        public final AnimationSourceType sourceType;
        public final String originFile;
        public final Set<String> touchedBones = new LinkedHashSet<>();
        public final Map<String, BoneTrack> boneTracks = new LinkedHashMap<>();
        public final List<SoundEffectKeyframe> soundEffects = new ArrayList<>();
        public final List<TimelineEventKeyframe> timelineEvents = new ArrayList<>();
        public final boolean isMainState;
        public final boolean isHandCondition;
        public boolean isExtraAction;
        public final boolean isGuiPreviewAction;
        public boolean isControllerReferenced;
        public String displayName;
        public ResourceLocation icon;
        public String description = "";
        public boolean globalAction;
        public String blendWeightExpression = "1";
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
        public final float[] preValues;
        public final float[] postValues;
        /** Per-component molang expressions; null array or null entry = use the numeric value. */
        public final MolangExpression[] preExpressions;
        public final MolangExpression[] postExpressions;
        public final int interpolationMode;

        private Keyframe(float timestamp, float[] values) {
            this(timestamp, values, null, values, null, 0);
        }

        private Keyframe(float timestamp, float[] preValues, float[] postValues, int interpolationMode) {
            this(timestamp, preValues, null, postValues, null, interpolationMode);
        }

        private Keyframe(float timestamp, float[] preValues, MolangExpression[] preExpressions,
                         float[] postValues, MolangExpression[] postExpressions, int interpolationMode) {
            this.timestamp = timestamp;
            this.preValues = preValues == null ? postValues : preValues;
            this.postValues = postValues == null ? preValues : postValues;
            this.preExpressions = preValues == null ? postExpressions : preExpressions;
            this.postExpressions = postValues == null ? preExpressions : postExpressions;
            this.interpolationMode = interpolationMode;
        }
    }

    public static final class SoundEffectKeyframe {
        public final float timestamp;
        public final String effectName;

        private SoundEffectKeyframe(float timestamp, String effectName) {
            this.timestamp = Math.max(0.0F, timestamp);
            this.effectName = effectName == null ? "" : effectName;
        }
    }

    public static final class TimelineEventKeyframe {
        public final float timestamp;
        public final List<String> events;

        private TimelineEventKeyframe(float timestamp, List<String> events) {
            this.timestamp = Math.max(0.0F, timestamp);
            this.events = Collections.unmodifiableList(new ArrayList<>(events == null ? Collections.emptyList() : events));
        }
    }
}
