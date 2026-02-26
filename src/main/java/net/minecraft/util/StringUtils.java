package net.minecraft.util;

import org.newdawn.slick.TrueTypeFont;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class StringUtils
{
    private static final Pattern PATTERN_CONTROL_CODE = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

    /**
     * Returns the time elapsed for the given number of ticks, in "mm:ss" format.
     */
    public static String ticksToElapsedTime(int ticks)
    {
        int i = ticks / 20;
        int j = i / 60;
        i = i % 60;
        return i < 10 ? j + ":0" + i : j + ":" + i;
    }

    public static String stripControlCodes(String text)
    {
        return PATTERN_CONTROL_CODE.matcher(text).replaceAll("");
    }

    /**
     * Returns a value indicating whether the given string is null or empty.
     */
    public static boolean isNullOrEmpty(@Nullable String string)
    {
        return org.apache.commons.lang3.StringUtils.isEmpty(string);
    }

    public static String[] wrapText(String text, int maxWidth, TrueTypeFont font) {
        String[] words = text.split(" ");
        Map<Integer, String> lines = new HashMap<>();
        int lineIndex = 0;

        for (String word : words) {
            String currentLine = lines.getOrDefault(lineIndex, "");
            boolean isNewLine = !lines.containsKey(lineIndex);
            boolean fitsInLine = font.getWidth(currentLine) + font.getWidth(word) <= maxWidth;
            boolean wordExceedsWidth = font.getWidth(word) >= maxWidth;

            if (!fitsInLine && !wordExceedsWidth) {
                lineIndex++;
                currentLine = lines.getOrDefault(lineIndex, "");
                isNewLine = !lines.containsKey(lineIndex);
                fitsInLine = font.getWidth(currentLine) + font.getWidth(word) <= maxWidth;
                wordExceedsWidth = font.getWidth(word) >= maxWidth;
            }

            if (fitsInLine) {
                lines.put(lineIndex, isNewLine ? word : currentLine + " " + word);
            } else if (wordExceedsWidth) {
                while (wordExceedsWidth && !fitsInLine) {
                    int trimIndex = 0;

                    while (true) {
                        if (trimIndex <= word.length()) {
                            String trimmedWord = word.substring(0, word.length() - trimIndex);
                            if (font.getWidth(trimmedWord) > maxWidth) {
                                trimIndex++;
                                continue;
                            }

                            lines.put(++lineIndex, trimmedWord);
                            word = word.substring(word.length() - trimIndex);
                        }

                        currentLine = lines.getOrDefault(lineIndex, "");
                        fitsInLine = font.getWidth(currentLine) + font.getWidth(word) <= maxWidth;
                        wordExceedsWidth = font.getWidth(word) >= maxWidth;
                        isNewLine = !lines.containsKey(lineIndex);
                        break;
                    }
                }

                if (!fitsInLine) {
                    lineIndex++;
                }

                lines.put(lineIndex, word);
            }
        }

        return lines.values().toArray(new String[0]);
    }
}
