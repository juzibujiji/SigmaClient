package com.elfmcys.yesstevemodel.client.animation;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.OpenYsmBakedPlayerModel;
import com.elfmcys.yesstevemodel.client.animation.controller.OpenYsmControllerRuntime;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangBindings;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangContext;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangEvaluator;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangExpression;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class OpenYsmAnimationEventDispatcher {
    private static final int MAX_TRACKED_EVENTS = 8192;
    private static final int MAX_EVENT_FUNCTIONS_PER_TICK = 16;
    private static final int MAX_ASSIGNMENTS_PER_FUNCTION = 64;
    private static final Map<String, Float> LAST_ELAPSED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> INITIALIZED_EVENTS = new ConcurrentHashMap<>();
    private static final Map<String, ISound> MANAGED_SOUNDS = new ConcurrentHashMap<>();
    private static final AtomicLong SOUND_SEQUENCE = new AtomicLong();

    private OpenYsmAnimationEventDispatcher() {
    }

    public static void dispatch(OpenYsmBakedPlayerModel model, Entity entity, ActiveAnimationSet active) {
        PlayerStateSnapshot snapshot = entity == null ? null
                : PlayerStateSnapshot.captureEntity(entity, 0.0F, entity.ticksExisted);
        dispatch(model, entity, active, snapshot);
    }

    public static void dispatch(OpenYsmBakedPlayerModel model, Entity entity, ActiveAnimationSet active,
                                PlayerStateSnapshot snapshot) {
        if (model == null || entity == null || active == null) {
            return;
        }
        if (LAST_ELAPSED.size() > MAX_TRACKED_EVENTS) {
            LAST_ELAPSED.clear();
        }
        dispatchResourceEvents(model, entity, snapshot, "player_init", true);
        dispatchResourceEvents(model, entity, snapshot, "player_update", false);
        for (OpenYsmControllerRuntime.ControllerEvent event : active.controllerEvents) {
            dispatchControllerEvent(model, entity, event);
        }
        for (ActiveAnimationSet.ActiveClip activeClip : active.activeClipsInOrder()) {
            OpenYsmAnimationSet.Clip clip = activeClip.getClip();
            if (clip.soundEffects.isEmpty() && clip.timelineEvents.isEmpty()) {
                continue;
            }
            String key = entity.getUniqueID() + "|" + model.getId() + "|" + clip.name;
            float currentElapsed = Math.max(0.0F, activeClip.getTimeSeconds());
            Float previousElapsed = LAST_ELAPSED.put(key, currentElapsed);
            for (OpenYsmAnimationSet.SoundEffectKeyframe soundEffect : clip.soundEffects) {
                if (crossed(clip, previousElapsed, currentElapsed, soundEffect.timestamp)) {
                    dispatchSound(model, entity, soundEffect.effectName);
                }
            }
            for (OpenYsmAnimationSet.TimelineEventKeyframe timelineEvent : clip.timelineEvents) {
                if (crossed(clip, previousElapsed, currentElapsed, timelineEvent.timestamp)) {
                    for (String event : timelineEvent.events) {
                        dispatchTimelineEvent(model, entity, event);
                    }
                }
            }
        }
    }

    private static void dispatchControllerEvent(OpenYsmBakedPlayerModel model, Entity entity,
                                                OpenYsmControllerRuntime.ControllerEvent event) {
        if (event == null || event.getValue().isEmpty()) {
            return;
        }
        if (event.getKind() == OpenYsmControllerRuntime.ControllerEvent.Kind.SOUND) {
            dispatchSound(model, entity, event.getValue());
            return;
        }
        dispatchTimelineEvent(model, entity, event.getValue());
    }

    public static void clearModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        String needle = "|" + modelId + "|";
        LAST_ELAPSED.keySet().removeIf(key -> key.contains(needle));
        INITIALIZED_EVENTS.keySet().removeIf(key -> key.contains(needle));
        stopManagedSounds(key -> key.contains(needle));
    }

    private static void dispatchResourceEvents(OpenYsmBakedPlayerModel model, Entity entity,
                                               PlayerStateSnapshot snapshot, String eventName, boolean onlyOnce) {
        Map<String, String> functions = model.getExtraResources().getFunctions();
        if (functions.isEmpty()) {
            return;
        }
        String eventKey = entity.getUniqueID() + "|" + model.getId() + "|" + eventName;
        if (onlyOnce && INITIALIZED_EVENTS.putIfAbsent(eventKey, Boolean.TRUE) != null) {
            return;
        }
        if (snapshot == null) {
            snapshot = PlayerStateSnapshot.captureEntity(entity, 0.0F, entity.ticksExisted);
        }
        if (snapshot == null) {
            return;
        }
        int executed = 0;
        for (Map.Entry<String, String> entry : functions.entrySet()) {
            if (executed >= MAX_EVENT_FUNCTIONS_PER_TICK) {
                break;
            }
            if (matchesResourceEvent(entry.getKey(), eventName)) {
                executeVariableAssignments(model, snapshot, entry.getValue(), eventName, entry.getKey());
                executed++;
            }
        }
    }

    private static boolean matchesResourceEvent(String functionName, String eventName) {
        if (functionName == null || eventName == null) {
            return false;
        }
        int at = functionName.indexOf('@');
        if (at < 0 || at + 1 >= functionName.length()) {
            return false;
        }
        String suffix = functionName.substring(at + 1).toLowerCase(Locale.ROOT);
        if (suffix.endsWith(".molang")) {
            suffix = suffix.substring(0, suffix.length() - ".molang".length());
        }
        return suffix.equals(eventName.toLowerCase(Locale.ROOT));
    }

    private static void executeVariableAssignments(OpenYsmBakedPlayerModel model, PlayerStateSnapshot snapshot,
                                                   String script, String eventName, String functionName) {
        if (script == null || script.isEmpty()) {
            return;
        }
        Map<String, Double> variables = OpenYsmPlayerAnimationState.getGuiVariables(snapshot.uuid, model.getId());
        int count = 0;
        for (String statement : splitStatements(script)) {
            if (count >= MAX_ASSIGNMENTS_PER_FUNCTION) {
                break;
            }
            AssignmentExpression assignment = parseVariableAssignment(statement);
            if (assignment == null) {
                continue;
            }
            try {
                MolangExpression expression = MolangParser.parse(assignment.expression);
                MolangBindings bindings = new MolangBindings(variables, java.util.Collections.emptyMap());
                MolangContext context = MolangContext.controller(snapshot, model.getId(), eventName, bindings, false, false);
                double value = MolangEvaluator.evaluate(expression, context).asDouble();
                if (Double.isFinite(value)) {
                    value = Math.max(-1000000.0D, Math.min(1000000.0D, value));
                    OpenYsmPlayerAnimationState.setGuiVariable(snapshot.uuid, model.getId(), assignment.variableName, value);
                    variables.put(assignment.variableName, value);
                    count++;
                }
            } catch (MolangParser.ParseException exception) {
                debug("resource event parse failure model={} event={} function={} variable={} expression={} reason={}",
                        model.getId(), eventName, functionName, assignment.variableName, assignment.expression,
                        exception.getMessage());
            }
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (quoted && c == '\\') {
                escaped = true;
            } else if (quoted) {
                if (c == quote) {
                    quoted = false;
                }
                current.append(c);
            } else if (c == '\'' || c == '"') {
                quoted = true;
                quote = c;
                current.append(c);
            } else if (c == ';' || c == '\n' || c == '\r') {
                addStatement(statements, current);
            } else {
                current.append(c);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (!statement.isEmpty() && statement.length() <= 256) {
            statements.add(statement);
        }
    }

    private static AssignmentExpression parseVariableAssignment(String statement) {
        int equals = statement == null ? -1 : statement.indexOf('=');
        if (equals <= 0 || statement.indexOf('=', equals + 1) >= 0) {
            return null;
        }
        String left = statement.substring(0, equals).trim().toLowerCase(Locale.ROOT);
        String right = statement.substring(equals + 1).trim();
        if (!(left.startsWith("variable.") || left.startsWith("v.")) || right.isEmpty() || right.length() > 192) {
            return null;
        }
        String variableName = left.startsWith("variable.")
                ? left.substring("variable.".length())
                : left.substring("v.".length());
        if (!isSafeVariableName(variableName)) {
            return null;
        }
        return new AssignmentExpression(variableName, right);
    }

    private static boolean isSafeVariableName(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean crossed(OpenYsmAnimationSet.Clip clip, Float previousElapsed, float currentElapsed,
                                   float eventTimestamp) {
        if (eventTimestamp < 0.0F) {
            return false;
        }
        if (previousElapsed == null || currentElapsed < previousElapsed) {
            return eventTimestamp <= 0.001F || eventTimestamp <= currentSample(clip, currentElapsed);
        }
        if (clip.loopMode == LoopMode.LOOP && clip.length > 0.0F) {
            float previousSample = previousElapsed % clip.length;
            float currentSample = currentElapsed % clip.length;
            if (currentSample < previousSample) {
                return eventTimestamp > previousSample || eventTimestamp <= currentSample;
            }
            return eventTimestamp > previousSample && eventTimestamp <= currentSample;
        }
        return eventTimestamp > previousElapsed && eventTimestamp <= currentElapsed;
    }

    private static float currentSample(OpenYsmAnimationSet.Clip clip, float currentElapsed) {
        return clip.loopMode.time(currentElapsed, clip.length);
    }

    private static void dispatchSound(OpenYsmBakedPlayerModel model, Entity entity, String effectName) {
        String safeName = normalizeSoundName(effectName);
        if (safeName.isEmpty()) {
            return;
        }

        byte[] modelSound = getModelSound(model, safeName);
        if (modelSound != null) {
            debug("model sound event model={} entity={} sound={}", model.getId(), entity.getEntityId(), safeName);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getSoundHandler() != null) {
                minecraft.getSoundHandler().playYsmOgg(modelSound, SoundCategory.PLAYERS, 1.0F, 1.0F, entity);
            }
            return;
        }

        ResourceLocation soundId = parseSoundId(safeName);
        if (soundId == null || !Registry.SOUND_EVENT.containsKey(soundId)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundHandler().getAccessor(soundId) == null) {
            return;
        }
        minecraft.getSoundHandler().play(new SimpleSound(soundId, SoundCategory.PLAYERS, 1.0F, 1.0F,
                false, 0, ISound.AttenuationType.LINEAR, entity.getPosX(), entity.getPosY(), entity.getPosZ(), false));
    }

    private static void dispatchTimelineEvent(OpenYsmBakedPlayerModel model, Entity entity, String event) {
        if (event == null) {
            return;
        }
        String safe = event.replace('\0', ' ').trim();
        if (safe.length() > 512) {
            safe = safe.substring(0, 512);
        }
        String lower = safe.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ysm.play_sound")) {
            List<String> args = parseCallArguments(safe);
            if (args.size() < 2 || args.size() > 5) {
                return;
            }
            int id = parseSoundHandleId(args.get(0));
            String soundName = stripQuotes(args.get(1)).trim();
            if (soundName.isEmpty()) {
                return;
            }
            int flags = args.size() >= 3 ? parseInt(args.get(2), 0) : 0;
            if (flags < 0 || flags > 7) {
                return;
            }
            float volume = args.size() >= 4 ? clamp(parseFloat(args.get(3), 1.0F), 0.001F, 1000.0F) : 1.0F;
            float pitch = args.size() >= 5 ? clamp(parseFloat(args.get(4), 1.0F), 0.001F, 1000.0F) : 1.0F;
            playManagedSound(model, entity, id, soundName, (flags & 1) == 1, (flags & 2) == 2,
                    (flags & 4) == 4, volume, pitch);
        } else if (lower.startsWith("ysm.stop_sound")) {
            List<String> args = parseCallArguments(safe);
            if (args.size() == 1 || args.size() == 2) {
                int id = parseSoundHandleId(args.get(0));
                boolean global = args.size() == 2 && parseBoolean(args.get(1));
                stopManagedSound(model, entity, id, global);
            }
        } else if (lower.startsWith("ysm.stop_all_sounds")) {
            List<String> args = parseCallArguments(safe);
            if (args.size() <= 1) {
                boolean global = !args.isEmpty() && parseBoolean(args.get(0));
                stopManagedSounds(model, entity, global);
            }
        }
    }

    private static void playManagedSound(OpenYsmBakedPlayerModel model, Entity entity, int id, String soundName,
                                         boolean forceReplace, boolean global, boolean looping, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundHandler() == null) {
            return;
        }
        pruneManagedSounds(minecraft);
        String handleKey = managedSoundKey(model, entity, id, global);
        if (id != 0) {
            ISound previous = MANAGED_SOUNDS.get(handleKey);
            if (previous != null && minecraft.getSoundHandler().isPlaying(previous)) {
                if (!forceReplace) {
                    return;
                }
                minecraft.getSoundHandler().stop(previous);
            }
        }

        ISound sound = createManagedSound(model, entity, soundName, global, looping, volume, pitch);
        if (sound != null) {
            MANAGED_SOUNDS.put(id == 0 ? autoManagedSoundKey(model, entity, global) : handleKey, sound);
        }
    }

    private static ISound createManagedSound(OpenYsmBakedPlayerModel model, Entity entity, String soundName,
                                             boolean global, boolean looping, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundHandler() == null) {
            return null;
        }
        String safeName = normalizeSoundName(soundName);
        byte[] modelSound = getModelSound(model, safeName);
        if (modelSound != null) {
            return minecraft.getSoundHandler().playYsmOgg(modelSound, SoundCategory.PLAYERS, volume, pitch, entity,
                    looping, global);
        }

        ResourceLocation soundId = parseSoundId(soundName.trim().toLowerCase(Locale.ROOT));
        if (soundId == null || !Registry.SOUND_EVENT.containsKey(soundId)
                || minecraft.getSoundHandler().getAccessor(soundId) == null) {
            return null;
        }
        SimpleSound sound = new SimpleSound(soundId, SoundCategory.PLAYERS, volume, pitch,
                looping, 0, global ? ISound.AttenuationType.NONE : ISound.AttenuationType.LINEAR,
                entity.getPosX(), entity.getPosY(), entity.getPosZ(), global);
        minecraft.getSoundHandler().play(sound);
        return sound;
    }

    private static void stopManagedSound(OpenYsmBakedPlayerModel model, Entity entity, int id, boolean global) {
        if (id == 0) {
            return;
        }
        String handleKey = managedSoundKey(model, entity, id, global);
        ISound sound = MANAGED_SOUNDS.remove(handleKey);
        Minecraft minecraft = Minecraft.getInstance();
        if (sound != null && minecraft != null && minecraft.getSoundHandler() != null) {
            minecraft.getSoundHandler().stop(sound);
        }
    }

    private static void stopManagedSounds(OpenYsmBakedPlayerModel model, Entity entity, boolean global) {
        String prefix = managedSoundPrefix(model, entity, global);
        stopManagedSounds(key -> key.startsWith(prefix));
    }

    private static void stopManagedSounds(java.util.function.Predicate<String> predicate) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundHandler() == null) {
            MANAGED_SOUNDS.keySet().removeIf(predicate);
            return;
        }
        List<String> keys = new ArrayList<>();
        for (String key : MANAGED_SOUNDS.keySet()) {
            if (predicate.test(key)) {
                keys.add(key);
            }
        }
        for (String key : keys) {
            ISound sound = MANAGED_SOUNDS.remove(key);
            if (sound != null) {
                minecraft.getSoundHandler().stop(sound);
            }
        }
    }

    private static String managedSoundKey(OpenYsmBakedPlayerModel model, Entity entity, int id, boolean global) {
        return managedSoundPrefix(model, entity, global) + "|" + id;
    }

    private static String autoManagedSoundKey(OpenYsmBakedPlayerModel model, Entity entity, boolean global) {
        return managedSoundPrefix(model, entity, global) + "|auto|" + SOUND_SEQUENCE.incrementAndGet();
    }

    private static String managedSoundPrefix(OpenYsmBakedPlayerModel model, Entity entity, boolean global) {
        return (global ? "global" : entity.getUniqueID().toString()) + "|" + model.getId();
    }

    private static void pruneManagedSounds(Minecraft minecraft) {
        if (minecraft == null || minecraft.getSoundHandler() == null || MANAGED_SOUNDS.isEmpty()) {
            return;
        }
        MANAGED_SOUNDS.entrySet().removeIf(entry -> entry.getValue() == null
                || !minecraft.getSoundHandler().isPlaying(entry.getValue()));
    }

    private static ResourceLocation parseSoundId(String soundName) {
        try {
            return soundName.indexOf(':') >= 0 ? new ResourceLocation(soundName) : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static byte[] getModelSound(OpenYsmBakedPlayerModel model, String safeName) {
        byte[] direct = model.getExtraResources().getSounds().get(safeName);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, byte[]> entry : model.getExtraResources().getSounds().entrySet()) {
            String soundName = entry.getKey();
            if (soundName != null && soundName.equalsIgnoreCase(safeName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static List<String> parseCallArguments(String call) {
        List<String> args = new ArrayList<>();
        int open = call.indexOf('(');
        int close = call.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return args;
        }
        String body = call.substring(open + 1, close);
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && quoted) {
                escaped = true;
            } else if (quoted) {
                if (c == quote) {
                    quoted = false;
                }
                current.append(c);
            } else if (c == '\'' || c == '"') {
                quoted = true;
                quote = c;
                current.append(c);
            } else if (c == ',') {
                args.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 || body.endsWith(",")) {
            args.add(current.toString().trim());
        }
        return args;
    }

    private static String stripQuotes(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() >= 2) {
            char first = safe.charAt(0);
            char last = safe.charAt(safe.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return safe.substring(1, safe.length() - 1);
            }
        }
        return safe;
    }

    private static int parseSoundHandleId(String value) {
        String safe = stripQuotes(value).trim();
        try {
            return -Integer.parseInt(safe);
        } catch (NumberFormatException ignored) {
            return safe.toLowerCase(Locale.ROOT).hashCode();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(stripQuotes(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(stripQuotes(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value) {
        String safe = stripQuotes(value).trim().toLowerCase(Locale.ROOT);
        return "1".equals(safe) || "true".equals(safe);
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeSoundName(String soundName) {
        if (soundName == null) {
            return "";
        }
        String safe = soundName.replace('\0', ' ').trim();
        if (safe.endsWith(".ogg")) {
            safe = safe.substring(0, safe.length() - ".ogg".length());
        }
        int slash = safe.replace('\\', '/').lastIndexOf('/');
        if (slash >= 0) {
            safe = safe.substring(slash + 1);
        }
        return safe.length() > 128 ? safe.substring(0, 128).toLowerCase(Locale.ROOT) : safe.toLowerCase(Locale.ROOT);
    }

    private static void debug(String message, Object... args) {
        if (Boolean.getBoolean("yes_steve_model.debugAnimationEvents")) {
            YesSteveModel.LOGGER.info("[DEBUG-ysm-events] " + message, args);
        }
    }

    private static final class AssignmentExpression {
        private final String variableName;
        private final String expression;

        private AssignmentExpression(String variableName, String expression) {
            this.variableName = variableName;
            this.expression = expression;
        }
    }
}
