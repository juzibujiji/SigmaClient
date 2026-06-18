package com.mentalfrostbyte.jello.module.impl.gui.jello.irc;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public final class IRCChatHistory {
    private static final int MAX_MESSAGES = 120;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.ROOT);
    private static final LinkedList<Entry> MESSAGES = new LinkedList<>();

    private IRCChatHistory() {
    }

    public static Entry addChat(String username, String message, long timestamp) {
        Entry entry = new Entry(Type.CHAT, sanitizeUsername(username), sanitizeMessage(message), timestamp);
        add(entry);
        return entry;
    }

    public static Entry addSystem(String message) {
        Entry entry = new Entry(Type.SYSTEM, "IRC", sanitizeMessage(message), System.currentTimeMillis());
        add(entry);
        return entry;
    }

    public static List<Entry> snapshot() {
        synchronized (MESSAGES) {
            return new ArrayList<>(MESSAGES);
        }
    }

    public static String sanitizeUsername(String raw) {
        String username = sanitize(raw, MAX_USERNAME_LENGTH);
        return username.isEmpty() ? "Unknown" : username;
    }

    public static String sanitizeMessage(String raw) {
        return sanitize(raw, MAX_MESSAGE_LENGTH);
    }

    private static void add(Entry entry) {
        synchronized (MESSAGES) {
            MESSAGES.add(entry);
            while (MESSAGES.size() > MAX_MESSAGES) {
                MESSAGES.removeFirst();
            }
        }
    }

    private static String sanitize(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(Math.min(raw.length(), maxLength));
        boolean skipFormattingCode = false;
        for (int i = 0; i < raw.length() && builder.length() < maxLength; i++) {
            char chr = raw.charAt(i);
            if (skipFormattingCode) {
                skipFormattingCode = false;
                continue;
            }

            if (chr == '\u00A7') {
                skipFormattingCode = true;
                continue;
            }

            if (chr == '\r' || chr == '\n' || chr == '\t') {
                builder.append(' ');
            } else if (!Character.isISOControl(chr)) {
                builder.append(chr);
            }
        }

        return builder.toString().trim().replaceAll(" {2,}", " ");
    }

    public enum Type {
        CHAT,
        SYSTEM
    }

    public static final class Entry {
        private final Type type;
        private final String username;
        private final String message;
        private final long timestamp;
        private final String timeText;

        private Entry(Type type, String username, String message, long timestamp) {
            this.type = type;
            this.username = username;
            this.message = message;
            this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
            synchronized (TIME_FORMAT) {
                this.timeText = TIME_FORMAT.format(new Date(this.timestamp));
            }
        }

        public Type getType() {
            return type;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getTimeText() {
            return timeText;
        }

        public String getDisplayText() {
            if (type == Type.CHAT) {
                return "[" + timeText + "] " + username + ": " + message;
            }

            return "[" + timeText + "] " + message;
        }
    }
}
