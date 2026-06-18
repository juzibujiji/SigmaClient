package com.mentalfrostbyte.jello.gui.impl.irc;

import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import org.newdawn.slick.TrueTypeFont;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IRCChatGuiUtil {
    private IRCChatGuiUtil() {
    }

    public static List<String> wrapForWidth(String text, TrueTypeFont font, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return Collections.singletonList("");
        }

        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            String candidate = line.length() == 0 ? word : line + " " + word;
            if (RenderUtil.getHybridStringWidth(font, candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }

            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }

            if (RenderUtil.getHybridStringWidth(font, word) <= maxWidth) {
                line.append(word);
            } else {
                lines.addAll(splitLongWord(word, font, maxWidth));
            }
        }

        if (line.length() > 0) {
            lines.add(line.toString());
        }

        return lines.isEmpty() ? Collections.singletonList(text) : lines;
    }

    public static String ellipsize(String text, TrueTypeFont font, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (RenderUtil.getHybridStringWidth(font, text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        StringBuilder builder = new StringBuilder(text);
        while (builder.length() > 0 && RenderUtil.getHybridStringWidth(font, builder + ellipsis) > maxWidth) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder + ellipsis;
    }

    private static List<String> splitLongWord(String word, TrueTypeFont font, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            String candidate = line.toString() + word.charAt(i);
            if (line.length() > 0 && RenderUtil.getHybridStringWidth(font, candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(word.charAt(i));
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
