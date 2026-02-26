package com.mentalfrostbyte.jello.managers;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.managers.data.Manager;
import com.mentalfrostbyte.jello.util.client.render.Resources;

import java.io.InputStream;
import java.util.*;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;

public class SoundManager extends Manager {
    private static final String fileType = ".mp3";
    private static final List<String> VALID_SOUNDS = new ArrayList<>(
            Arrays.asList("activate", "deactivate", "click", "error", "pop", "connect", "switch", "clicksound")
    );

    @Override
    public void init() {
    }

    public void play(String url) {
        if (!VALID_SOUNDS.contains(url)) {
            Client.logger.warn("Invalid audio file attempted to be played: {}", url);
        } else {
            try {
                InputStream audioStream = Resources.readInputStream("com/mentalfrostbyte/gui/resources/audio/" + url + fileType);

                AdvancedPlayer player = new AdvancedPlayer(audioStream);
                new Thread(() -> {
                    try {
                        player.play();
                    } catch (JavaLayerException e) {
                        Client.logger.error("Error playing audio file: {}", url, e);
                    }
                }).start();
            } catch (JavaLayerException e) {
                Client.logger.error("Unsupported audio file: {}", url, e);
            }
        }
    }
}
