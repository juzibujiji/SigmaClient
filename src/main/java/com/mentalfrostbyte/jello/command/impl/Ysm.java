package com.mentalfrostbyte.jello.command.impl;

import com.elfmcys.yesstevemodel.OpenYsmModelEntry;
import com.elfmcys.yesstevemodel.OpenYsmTextureOption;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.OpenYsmModelLoader;
import com.elfmcys.yesstevemodel.gui.OpenYsmModelSelectionScreen;
import com.mentalfrostbyte.jello.command.Command;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandArguments;
import com.mentalfrostbyte.jello.managers.util.command.ChatCommandExecutor;
import com.mentalfrostbyte.jello.managers.util.command.CommandException;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class Ysm extends Command {
    private static final String[] SUBCOMMANDS = {
            "on", "off", "enable", "disable", "toggle", "gui",
            "reload", "list", "select", "textures", "render", "current", "folder"
    };

    public Ysm() {
        super("ysm", "Manage OpenYSM models", "openysm", "yesstevemodel");
        this.registerSubCommands(SUBCOMMANDS);
    }

    @Override
    public void run(String message, ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length == 0) {
            usage(executor);
            return;
        }

        String rawAction = value(args[0]);
        String action = rawAction.toLowerCase(Locale.ROOT);
        switch (action) {
            case "on":
            case "enable":
                if (!ensureAvailable(executor) || hasExtraArgs(args, 1, action, executor)) {
                    return;
                }
                setModuleEnabled(true, true, executor);
                break;
            case "off":
            case "disable":
                if (!ensureAvailable(executor) || hasExtraArgs(args, 1, action, executor)) {
                    return;
                }
                setModuleEnabled(false, false, executor);
                break;
            case "toggle":
                if (!ensureAvailable(executor) || hasExtraArgs(args, 1, "toggle", executor)) {
                    return;
                }
                boolean enabled = !YesSteveModel.isEnabled();
                setModuleEnabled(enabled, enabled, executor);
                break;
            case "gui":
                if (!ensureAvailable(executor) || hasExtraArgs(args, 1, "gui", executor)) {
                    return;
                }
                openGui(executor);
                break;
            case "reload":
                if (!ensureAvailable(executor) || hasExtraArgs(args, 1, "reload", executor)) {
                    return;
                }
                YesSteveModel.reload(mc.getResourceManager());
                executor.send("OpenYSM indexed " + YesSteveModel.getModelIndex().getEntries().size() + " model entries.");
                break;
            case "list":
                if (!ensureAvailable(executor) || hasExtraArgs(args, 1, "list", executor)) {
                    return;
                }
                sendModelList(executor);
                break;
            case "select":
                if (!ensureAvailable(executor)) {
                    return;
                }
                selectModel(args, executor);
                break;
            case "textures":
                if (!ensureAvailable(executor)) {
                    return;
                }
                sendTextures(args, executor);
                break;
            case "render":
                if (!ensureAvailable(executor)) {
                    return;
                }
                setRender(args, executor);
                break;
            case "current":
                if (!ensureAvailable(executor) || hasExtraArgs(args, 1, "current", executor)) {
                    return;
                }
                sendCurrent(executor);
                break;
            case "folder":
                if (hasExtraArgs(args, 1, "folder", executor)) {
                    return;
                }
                openFolder(executor);
                break;
            default:
                error(executor, "Unknown subcommand: " + rawAction);
                sendSuggestion(executor, rawAction, SUBCOMMANDS);
                executor.send("Available subcommands: " + String.join(", ", SUBCOMMANDS));
                executor.send("Usage: .ysm <subcommand>");
        }
    }

    private void selectModel(ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length < 2) {
            error(executor, "Missing argument: modelId");
            usageFor("select", executor);
            return;
        }
        if (hasExtraArgs(args, 3, "select", executor)) {
            return;
        }

        String id = value(args[1]);
        Optional<OpenYsmModelEntry> entry = YesSteveModel.getModelIndex().findById(id);
        if (!entry.isPresent()) {
            error(executor, "Model not found: " + id);
            sendModelSuggestion(executor, id);
            executor.send("Use .ysm list to show indexed models.");
            return;
        }

        String textureId = "";
        if (args.length > 2) {
            textureId = value(args[2]);
            try {
                List<OpenYsmTextureOption> textures = OpenYsmModelLoader.listTextures(mc.getResourceManager(), entry.get());
                OpenYsmTextureOption texture = OpenYsmModelLoader.findTexture(textures, textureId);
                if (texture == null) {
                    error(executor, "Texture not found in model " + id + ": " + textureId);
                    sendTextureSuggestion(executor, textureId, textures);
                    executor.send("Use .ysm textures " + id + " to show available textures.");
                    return;
                }
                textureId = texture.getId();
            } catch (Exception exception) {
                error(executor, "Could not read textures for " + id + ": " + shortError(exception));
                return;
            }
        }

        YesSteveModel.selectModel(id, textureId);
        executor.send("Selected OpenYSM model: " + YesSteveModel.getClientConfig().getSelectedModelId());
        if (!textureId.isEmpty()) {
            executor.send("Selected OpenYSM texture: " + textureId);
        }
    }

    private void setRender(ChatCommandArguments[] args, ChatCommandExecutor executor) throws CommandException {
        if (args.length < 2) {
            error(executor, "Missing argument: enabled");
            usageFor("render", executor);
            return;
        }
        if (hasExtraArgs(args, 2, "render", executor)) {
            return;
        }

        Boolean enabled = parseBooleanStrict(value(args[1]));
        if (enabled == null) {
            error(executor, "Invalid render value: " + value(args[1]));
            executor.send("render accepts: on/off/true/false/1/0");
            usageFor("render", executor);
            return;
        }

        if (enabled && !YesSteveModel.isEnabled()) {
            YesSteveModel.setEnabled(true);
        }
        YesSteveModel.setRenderPlayers(enabled);
        executor.send("OpenYSM player rendering: " + (enabled ? "on" : "off"));
    }

    private void setModuleEnabled(boolean enabled, boolean enableRendering, ChatCommandExecutor executor) {
        YesSteveModel.setEnabled(enabled);
        if (!enabled || enableRendering) {
            YesSteveModel.setRenderPlayers(enabled);
        }
        executor.send("OpenYSM module: " + (enabled ? "on" : "off"));
        executor.send("OpenYSM player rendering: " + (YesSteveModel.getClientConfig().isRenderPlayers() ? "on" : "off"));
    }

    private void sendCurrent(ChatCommandExecutor executor) {
        String modelId = YesSteveModel.getClientConfig().getSelectedModelId();
        executor.send("OpenYSM module: " + (YesSteveModel.isEnabled() ? "on" : "off"));
        if (modelId.isEmpty()) {
            executor.send("OpenYSM selected model: none");
            executor.send("Use .ysm select <modelId> [textureId] to choose one.");
        } else {
            executor.send("OpenYSM selected model: " + modelId);
        }
        executor.send("OpenYSM selected texture: " + textureName());
        executor.send("OpenYSM player rendering: " + (YesSteveModel.getClientConfig().isRenderPlayers() ? "on" : "off"));
        YesSteveModel.getSelectedModelEntry().ifPresent(entry ->
                executor.send(entry.getSourceType().name().toLowerCase(Locale.ROOT) + " | " + entry.getId() + " | " + entry.getName()));
    }

    private void sendTextures(ChatCommandArguments[] args, ChatCommandExecutor executor) {
        if (hasExtraArgs(args, 2, "textures", executor)) {
            return;
        }

        Optional<OpenYsmModelEntry> entry = args.length > 1
                ? YesSteveModel.getModelIndex().findById(value(args[1]))
                : YesSteveModel.getSelectedModelEntry();
        if (!entry.isPresent()) {
            if (args.length > 1) {
                String id = value(args[1]);
                error(executor, "Model not found: " + id);
                sendModelSuggestion(executor, id);
                executor.send("Use .ysm list to show indexed models.");
            } else {
                error(executor, "No model is selected.");
                executor.send("Use .ysm select <modelId> [textureId] first, or .ysm textures <modelId>.");
            }
            return;
        }

        try {
            List<OpenYsmTextureOption> textures = OpenYsmModelLoader.listTextures(mc.getResourceManager(), entry.get());
            executor.send("OpenYSM textures for " + entry.get().getId() + ": " + textures.size());
            int limit = Math.min(textures.size(), 12);
            for (int i = 0; i < limit; i++) {
                OpenYsmTextureOption texture = textures.get(i);
                executor.send(texture.getId() + " | " + texture.getPath());
            }
            if (textures.size() > limit) {
                executor.send("...and " + (textures.size() - limit) + " more.");
            }
        } catch (Exception exception) {
            error(executor, "Could not read textures for " + entry.get().getId() + ": " + shortError(exception));
        }
    }

    private void sendModelList(ChatCommandExecutor executor) {
        List<OpenYsmModelEntry> entries = YesSteveModel.getModelIndex().getEntries();
        executor.send("OpenYSM models: " + entries.size());
        int limit = Math.min(entries.size(), 12);
        for (int i = 0; i < limit; i++) {
            OpenYsmModelEntry entry = entries.get(i);
            executor.send(entry.getSourceType().name().toLowerCase(Locale.ROOT) + " | " + entry.getId() + " | " + entry.getName());
        }
        if (entries.size() > limit) {
            executor.send("...and " + (entries.size() - limit) + " more.");
        }
    }

    private void openFolder(ChatCommandExecutor executor) {
        Path path = YesSteveModel.getConfigDirectory();
        if (path == null) {
            error(executor, "OpenYSM is not initialized yet.");
            return;
        }

        Util.getOSType().openFile(path.toFile());
        executor.send("Opened " + path);
    }

    private void openGui(ChatCommandExecutor executor) {
        if (mc == null) {
            error(executor, "Minecraft client is not ready.");
            return;
        }

        mc.displayGuiScreen(new OpenYsmModelSelectionScreen());
        executor.send("Opened OpenYSM model selector.");
    }

    private String textureName() {
        String textureId = YesSteveModel.getClientConfig().getSelectedTextureId();
        return textureId.isEmpty() ? "default" : textureId;
    }

    private boolean ensureAvailable(ChatCommandExecutor executor) {
        if (YesSteveModel.isAvailable()) {
            return true;
        }

        error(executor, "OpenYSM is not initialized yet.");
        return false;
    }

    private boolean hasExtraArgs(ChatCommandArguments[] args, int max, String subcommand, ChatCommandExecutor executor) {
        if (args.length <= max) {
            return false;
        }

        List<String> extras = new ArrayList<>();
        for (int i = max; i < args.length; i++) {
            extras.add(value(args[i]));
        }
        error(executor, "Too many arguments: " + String.join(" ", extras));
        usageFor(subcommand, executor);
        return true;
    }

    private static Boolean parseBooleanStrict(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("on".equals(normalized) || "true".equals(normalized) || "1".equals(normalized)
                || "enable".equals(normalized) || "enabled".equals(normalized)) {
            return true;
        }
        if ("off".equals(normalized) || "false".equals(normalized) || "0".equals(normalized)
                || "disable".equals(normalized) || "disabled".equals(normalized)) {
            return false;
        }
        return null;
    }

    private void usage(ChatCommandExecutor executor) {
        executor.send("OpenYSM commands:");
        usageFor("on", executor);
        usageFor("off", executor);
        usageFor("toggle", executor);
        usageFor("gui", executor);
        usageFor("reload", executor);
        usageFor("list", executor);
        usageFor("textures", executor);
        usageFor("select", executor);
        usageFor("render", executor);
        usageFor("current", executor);
        usageFor("folder", executor);
    }

    private void usageFor(String subcommand, ChatCommandExecutor executor) {
        switch (subcommand) {
            case "on":
            case "enable":
                executor.send("Usage: .ysm on - enable OpenYSM and player rendering");
                break;
            case "off":
            case "disable":
                executor.send("Usage: .ysm off - disable OpenYSM completely");
                break;
            case "toggle":
                executor.send("Usage: .ysm toggle - toggle the OpenYSM module");
                break;
            case "gui":
                executor.send("Usage: .ysm gui - open the model selector");
                break;
            case "reload":
                executor.send("Usage: .ysm reload - rebuild the model index");
                break;
            case "list":
                executor.send("Usage: .ysm list - show indexed models");
                break;
            case "textures":
                executor.send("Usage: .ysm textures [modelId] - show textures");
                break;
            case "select":
                executor.send("Usage: .ysm select <modelId> [textureId] - choose model");
                break;
            case "render":
                executor.send("Usage: .ysm render <on|off|true|false|1|0> - toggle player rendering");
                break;
            case "current":
                executor.send("Usage: .ysm current - show current selection");
                break;
            case "folder":
                executor.send("Usage: .ysm folder - open config/yes_steve_model");
                break;
            default:
                executor.send("Usage: .ysm <subcommand>");
        }
    }

    private void sendModelSuggestion(ChatCommandExecutor executor, String input) {
        List<String> ids = new ArrayList<>();
        for (OpenYsmModelEntry entry : YesSteveModel.getModelIndex().getEntries()) {
            ids.add(entry.getId());
        }
        sendSuggestion(executor, input, ids.toArray(new String[0]));
    }

    private void sendTextureSuggestion(ChatCommandExecutor executor, String input, List<OpenYsmTextureOption> textures) {
        List<String> ids = new ArrayList<>();
        for (OpenYsmTextureOption texture : textures) {
            ids.add(texture.getId());
        }
        sendSuggestion(executor, input, ids.toArray(new String[0]));
    }

    private static void sendSuggestion(ChatCommandExecutor executor, String input, String[] candidates) {
        List<Suggestion> suggestions = suggestClosest(input, candidates);
        if (!suggestions.isEmpty()) {
            List<String> values = new ArrayList<>();
            for (Suggestion suggestion : suggestions) {
                values.add(suggestion.value);
            }
            executor.send("Did you mean: " + String.join(", ", values));
        }
    }

    private static List<Suggestion> suggestClosest(String input, String[] candidates) {
        String normalizedInput = input.toLowerCase(Locale.ROOT);
        int limit = Math.max(2, Math.min(6, normalizedInput.length() / 2 + 2));
        List<Suggestion> suggestions = new ArrayList<>();
        for (String candidate : candidates) {
            String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
            int distance = levenshtein(normalizedInput, normalizedCandidate);
            boolean contains = !normalizedInput.isEmpty()
                    && (normalizedCandidate.contains(normalizedInput) || normalizedInput.contains(normalizedCandidate));
            if (contains || distance <= limit) {
                suggestions.add(new Suggestion(candidate, contains ? 0 : distance));
            }
        }

        suggestions.sort(Comparator.comparingInt((Suggestion suggestion) -> suggestion.distance)
                .thenComparing(suggestion -> suggestion.value));
        if (suggestions.size() > 3) {
            return new ArrayList<>(suggestions.subList(0, 3));
        }
        return suggestions;
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int i = 0; i <= right.length(); i++) {
            previous[i] = i;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }

    private static void error(ChatCommandExecutor executor, String message) {
        executor.send("Error: " + message);
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }

        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        if (message.length() > 160) {
            message = message.substring(0, 157) + "...";
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private static String value(ChatCommandArguments argument) {
        return argument.getArguments() == null ? "" : argument.getArguments();
    }

    private static final class Suggestion {
        private final String value;
        private final int distance;

        private Suggestion(String value, int distance) {
            this.value = value;
            this.distance = distance;
        }
    }
}
