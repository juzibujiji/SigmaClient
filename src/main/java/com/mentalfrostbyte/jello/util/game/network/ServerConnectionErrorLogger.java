package com.mentalfrostbyte.jello.util.game.network;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.ITextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ServerConnectionErrorLogger {
    private static final Logger LOGGER = LogManager.getLogger("ServerConnectionError");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int LOG_CHUNK_SIZE = 1800;
    private static String lastLoggedKey = "";
    private static long lastLoggedAt;

    private ServerConnectionErrorLogger() {
    }

    public static void logDisconnect(String source, ITextComponent title, ITextComponent reason) {
        String titleText = safeString(title);
        String reasonText = safeString(reason);
        String key = source + '\n' + titleText + '\n' + reasonText;
        long now = System.currentTimeMillis();

        if (key.equals(lastLoggedKey) && now - lastLoggedAt < 1000L) {
            return;
        }

        lastLoggedKey = key;
        lastLoggedAt = now;

        String server = describeCurrentServer();
        String reasonJson = safeJson(reason);
        LOGGER.error("[ServerConnectionError] source={} server={} title={} reasonLength={}",
                source, server, titleText, reasonText.length());
        logChunked("[ServerConnectionError] reason", reasonText);
        logChunked("[ServerConnectionError] reasonJson", reasonJson);
        appendToFile(source, server, titleText, reasonText, reasonJson);
    }

    public static void logConnectionException(String source, Throwable throwable) {
        LOGGER.error("[ServerConnectionError] source={} server={} connection exception",
                source, describeCurrentServer(), throwable);
    }

    private static void logChunked(String prefix, String value) {
        if (value.isEmpty()) {
            LOGGER.error("{}[1/1] <empty>", prefix);
            return;
        }

        int total = (value.length() + LOG_CHUNK_SIZE - 1) / LOG_CHUNK_SIZE;
        for (int i = 0; i < total; ++i) {
            int start = i * LOG_CHUNK_SIZE;
            int end = Math.min(value.length(), start + LOG_CHUNK_SIZE);
            LOGGER.error("{}[{}/{}] {}", prefix, i + 1, total, value.substring(start, end));
        }
    }

    private static String describeCurrentServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return "<minecraft-unavailable>";
        }

        ServerData serverData = mc.getCurrentServerData();
        if (serverData == null) {
            return "<none>";
        }

        return serverData.serverName + " (" + serverData.serverIP + ")";
    }

    private static String safeString(ITextComponent component) {
        if (component == null) {
            return "<null>";
        }

        try {
            return component.getString();
        } catch (Exception e) {
            return "<failed to render component: " + e + ">";
        }
    }

    private static String safeJson(ITextComponent component) {
        if (component == null) {
            return "<null>";
        }

        try {
            return ITextComponent.Serializer.toJson(component);
        } catch (Exception e) {
            return "<failed to serialize component: " + e + ">";
        }
    }

    private static void appendToFile(String source, String server, String title, String reason, String reasonJson) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDir == null) {
                return;
            }

            Path logDir = mc.gameDir.toPath().resolve("logs");
            Files.createDirectories(logDir);
            Path path = logDir.resolve("via-connection-errors.log");
            String entry = "==== " + LocalDateTime.now().format(TIMESTAMP_FORMAT) + " ====\n"
                    + "source: " + source + "\n"
                    + "server: " + server + "\n"
                    + "title: " + title + "\n"
                    + "reason:\n" + reason + "\n"
                    + "reasonJson:\n" + reasonJson + "\n\n";
            Files.write(path, entry.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("[ServerConnectionError] Could not append dedicated connection error log: {}", e.getMessage());
        }
    }
}
