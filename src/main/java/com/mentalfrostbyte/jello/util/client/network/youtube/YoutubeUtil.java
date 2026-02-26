package com.mentalfrostbyte.jello.util.client.network.youtube;

import java.io.IOException;
import java.net.URL;

public class YoutubeUtil {
    public static String parseSongTime(int var0) {
        if (var0 < 0) {
            var0 = 0;
        }

        return var0 <= 3600
                ? var0 / 60 + ":" + (var0 % 60 >= 10 ? "" : "0") + var0 % 60
                : var0 / 3600 + ":" + (var0 / 60 % 60 >= 10 ? "" : "0") + var0 / 60 % 60 + ":" + (var0 % 60 >= 10 ? "" : "0") + var0 % 60;
    }

    public static URL getVideoStreamURL(String var0) {
        try {
            return new URL("https://www.youtube.com/watch?v=" + var0);
        } catch (IOException var4) {
            var4.printStackTrace();
            return null;
        }
    }
}