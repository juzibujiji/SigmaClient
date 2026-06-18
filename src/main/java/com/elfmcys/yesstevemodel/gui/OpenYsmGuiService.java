package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.OpenYsmModelEntry;
import com.elfmcys.yesstevemodel.OpenYsmTextureOption;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.OpenYsmBakedPlayerModel;
import com.elfmcys.yesstevemodel.client.OpenYsmModelLoader;
import com.elfmcys.yesstevemodel.client.animation.ActionEntry;
import com.elfmcys.yesstevemodel.client.animation.ActionSource;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import com.elfmcys.yesstevemodel.network.OpenYsmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class OpenYsmGuiService {
    public static final OpenYsmGuiService INSTANCE = new OpenYsmGuiService();
    private static final int PAGE_SIZE = 8;

    private OpenYsmGuiService() {
    }

    public List<OpenYsmModelEntry> listModels(Minecraft minecraft, String filter) {
        if (!YesSteveModel.isAvailable()) {
            return Collections.emptyList();
        }
        List<OpenYsmModelEntry> entries = new ArrayList<>(YesSteveModel.getModelIndex().getEntries());
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        if (!needle.isEmpty()) {
            entries.removeIf(entry -> !contains(entry.getId(), needle) && !contains(entry.getName(), needle)
                    && !contains(entry.getSourceType().name(), needle));
        }
        entries.sort(Comparator.comparing((OpenYsmModelEntry entry) -> safe(entry.getName()).toLowerCase(Locale.ROOT))
                .thenComparing(OpenYsmModelEntry::getId));
        return entries;
    }

    public List<OpenYsmTextureOption> listTextures(Minecraft minecraft, OpenYsmModelEntry entry) {
        if (minecraft == null || entry == null) {
            return Collections.emptyList();
        }
        try {
            return OpenYsmModelLoader.listTextures(minecraft.getResourceManager(), entry);
        } catch (Exception exception) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to list textures for GUI model='{}'", entry.getId(), exception);
            return Collections.emptyList();
        }
    }

    public OpenYsmBakedPlayerModel selectedModel(Minecraft minecraft) {
        if (minecraft == null || !YesSteveModel.isAvailable()) {
            return null;
        }
        return YesSteveModel.getSelectedPlayerModel(minecraft.getResourceManager());
    }

    public Optional<OpenYsmModelEntry> selectedEntry() {
        return YesSteveModel.isAvailable() ? YesSteveModel.getSelectedModelEntry() : Optional.empty();
    }

    public List<OpenYsmWheelNode> wheelPage(OpenYsmBakedPlayerModel model, Deque<String> navigation, int page) {
        List<OpenYsmWheelNode> nodes = wheelNodes(model, currentKey(navigation));
        int from = Math.max(0, page) * PAGE_SIZE;
        if (from >= nodes.size()) {
            from = Math.max(0, (pageCount(nodes) - 1) * PAGE_SIZE);
        }
        int to = Math.min(nodes.size(), from + PAGE_SIZE);
        return new ArrayList<>(nodes.subList(from, to));
    }

    public int pageCount(OpenYsmBakedPlayerModel model, Deque<String> navigation) {
        return pageCount(wheelNodes(model, currentKey(navigation)));
    }

    public void play(Minecraft minecraft, String modelId, ActionEntry action) {
        if (minecraft == null || minecraft.player == null || action == null) {
            return;
        }
        OpenYsmPlayerAnimationState.play(minecraft.player, modelId, action.getAnimationName(), ActionSource.GUI_ACTION);
        OpenYsmNetwork.sendPlayExtraAnimation(modelId, action.getAnimationName());
    }

    public void stop(Minecraft minecraft, String modelId) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        OpenYsmPlayerAnimationState.stop(minecraft.player);
        OpenYsmNetwork.sendStopExtraAnimation(modelId);
    }

    public boolean applyForm(PlayerEntity player, String modelId, OpenYsmAnimationSet.ActionForm form, String selectedExpression) {
        if (player == null || form == null) {
            return false;
        }
        Assignment assignment = parseAssignment(selectedExpression);
        if (assignment == null) {
            assignment = parseAssignment(form.getDefaultValue());
        }
        if (assignment == null || !isDeclared(form, assignment)) {
            return false;
        }
        OpenYsmPlayerAnimationState.setGuiVariable(player.getUniqueID(), modelId, assignment.name, assignment.value);
        OpenYsmNetwork.sendGuiVariable(modelId, assignment.name, assignment.value);
        return true;
    }

    private List<OpenYsmWheelNode> wheelNodes(OpenYsmBakedPlayerModel model, String groupKey) {
        if (model == null) {
            return Collections.emptyList();
        }
        OpenYsmAnimationSet animations = model.getAnimations();
        Map<String, ActionEntry> actions = new LinkedHashMap<>();
        for (ActionEntry action : animations.listWheelActions()) {
            actions.put(action.getAnimationName(), action);
        }

        List<OpenYsmWheelNode> nodes = new ArrayList<>();
        boolean root = groupKey.isEmpty();
        if (!root) {
            nodes.add(OpenYsmWheelNode.back());
        }
        Map<String, String> entries = root ? rootEntries(animations) : groupEntries(animations, groupKey);
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            OpenYsmAnimationSet.ExtraActionButton directButton = animations.getActionButtons().get(normalizeGroupKey(key));
            if (directButton != null) {
                nodes.add(OpenYsmWheelNode.config(directButton));
                continue;
            }
            if (key.startsWith("#")) {
                nodes.add(OpenYsmWheelNode.category(key, stripHash(key)));
                continue;
            }
            if (value.startsWith("#")) {
                OpenYsmAnimationSet.ExtraActionButton button = animations.getActionButtons().get(normalizeGroupKey(value));
                if (button != null) {
                    nodes.add(OpenYsmWheelNode.config(button));
                } else if (animations.getWheelGroups().containsKey(normalizeGroupKey(value))) {
                    nodes.add(OpenYsmWheelNode.category(normalizeGroupKey(value), stripHash(value)));
                }
                continue;
            }
            ActionEntry action = actions.get(key);
            if (action != null) {
                nodes.add(OpenYsmWheelNode.action(action, value));
            }
        }
        if (root && nodes.isEmpty()) {
            for (ActionEntry action : actions.values()) {
                nodes.add(OpenYsmWheelNode.action(action, action.getDescription()));
            }
        }
        return nodes;
    }

    private Map<String, String> rootEntries(OpenYsmAnimationSet animations) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!animations.getRootWheelEntries().isEmpty()) {
            entries.putAll(animations.getRootWheelEntries());
        } else {
            for (ActionEntry action : animations.listWheelActions()) {
                entries.put(action.getAnimationName(), action.getDescription());
            }
        }
        for (String group : animations.getWheelGroups().keySet()) {
            entries.putIfAbsent(group, group);
        }
        for (OpenYsmAnimationSet.ExtraActionButton button : animations.getActionButtons().values()) {
            entries.putIfAbsent(button.getId(), button.getId());
        }
        return entries;
    }

    private Map<String, String> groupEntries(OpenYsmAnimationSet animations, String groupKey) {
        OpenYsmAnimationSet.WheelActionGroup group = animations.getWheelGroups().get(normalizeGroupKey(groupKey));
        return group == null ? Collections.emptyMap() : group.getActions();
    }

    private static boolean isDeclared(OpenYsmAnimationSet.ActionForm form, Assignment assignment) {
        Assignment declared = parseAssignment(form.getDefaultValue());
        if (declared != null && declared.name.equals(assignment.name)) {
            return true;
        }
        for (String expression : form.getLabels().keySet()) {
            declared = parseAssignment(expression);
            if (declared != null && declared.name.equals(assignment.name)) {
                return true;
            }
        }
        return false;
    }

    public static Assignment parseAssignment(String expression) {
        if (expression == null || expression.length() > 160) {
            return null;
        }
        String value = expression.trim();
        if (value.endsWith(";")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        int equals = value.indexOf('=');
        if (equals <= 0 || value.indexOf('=', equals + 1) >= 0) {
            return null;
        }
        String left = value.substring(0, equals).trim().toLowerCase(Locale.ROOT);
        String right = value.substring(equals + 1).trim().toLowerCase(Locale.ROOT);
        if (!left.startsWith("variable.")) {
            return null;
        }
        String name = left.substring("variable.".length());
        if (!isSafeVariableName(name)) {
            return null;
        }
        double numeric;
        if ("true".equals(right)) {
            numeric = 1.0D;
        } else if ("false".equals(right)) {
            numeric = 0.0D;
        } else {
            try {
                numeric = Double.parseDouble(right);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        if (!Double.isFinite(numeric)) {
            return null;
        }
        return new Assignment(name, Math.max(-1000000.0D, Math.min(1000000.0D, numeric)));
    }

    private static int pageCount(List<OpenYsmWheelNode> nodes) {
        return Math.max(1, (nodes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static String currentKey(Deque<String> navigation) {
        return navigation == null || navigation.isEmpty() ? "" : navigation.peekLast();
    }

    public static String normalizeGroupKey(String key) {
        if (key == null) {
            return "";
        }
        String value = key.trim();
        return value.startsWith("#") ? value : "#" + value;
    }

    private static String stripHash(String value) {
        String safe = safe(value);
        return safe.startsWith("#") ? safe.substring(1) : safe;
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

    private static boolean contains(String value, String needle) {
        return safe(value).toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    public static Deque<String> newNavigation() {
        return new LinkedList<>();
    }

    public static final class Assignment {
        private final String name;
        private final double value;

        private Assignment(String name, double value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return this.name;
        }

        public double getValue() {
            return this.value;
        }
    }
}
