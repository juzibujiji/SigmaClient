package com.elfmcys.yesstevemodel.resource;

public final class YSMFolderDeserializer {
    private YSMFolderDeserializer() {
    }

    public static String getAnimKeyFromType(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            case 4 -> "tac";
            case 5 -> "arrow";
            case 6 -> "carryon";
            case 7 -> "parcool";
            case 8 -> "swem";
            case 9 -> "slashblade";
            case 10 -> "tlm";
            case 11 -> "fp_arm";
            case 12 -> "immersive_melodies";
            case 13 -> "irons_spell_books";
            default -> "unknown";
        };
    }

    public static int getAnimTypeFromKey(String key) {
        return switch (key) {
            case "main" -> 1;
            case "arm" -> 2;
            case "extra" -> 3;
            case "tac" -> 4;
            case "arrow" -> 5;
            case "carryon" -> 6;
            case "parcool" -> 7;
            case "swem" -> 8;
            case "slashblade" -> 9;
            case "tlm" -> 10;
            case "fp_arm" -> 11;
            case "immersive_melodies" -> 12;
            case "irons_spell_books" -> 13;
            default -> 0;
        };
    }
}
