package com.mentalfrostbyte.jello.util.game.sound;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.render.Resources;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雷达告警音效播放器。
 * <p>
 * 播放 {@code assets/minecraft/com/mentalfrostbyte/gui/resources/audio/} 下的
 * WAV 资源（{@link com.mentalfrostbyte.jello.managers.SoundManager} 只支持 mp3，
 * 这里用 javax.sound.sampled 直接解码 PCM WAV，延迟更低）。
 * <p>
 * 自带按音效名的节流（同名音效在 minIntervalMs 内只播一次），
 * 供雷达扫描/锁定告警循环调用而不会叠音。
 */
public final class RadarSoundPlayer {

    private static final String AUDIO_PATH = "com/mentalfrostbyte/gui/resources/audio/";
    private static final Map<String, Long> lastPlayTime = new ConcurrentHashMap<>();

    private RadarSoundPlayer() {
    }

    /**
     * 播放 WAV 音效，同名音效在 minIntervalMs 内的重复调用会被忽略。
     *
     * @param name          资源名（不含扩展名），如 "radar_lock"
     * @param minIntervalMs 同名音效最小播放间隔（毫秒）
     * @return 本次调用是否真正触发了播放
     */
    public static boolean play(String name, long minIntervalMs) {
        long now = System.currentTimeMillis();
        Long last = lastPlayTime.get(name);
        if (last != null && now - last < minIntervalMs) return false;
        lastPlayTime.put(name, now);

        Thread t = new Thread(() -> {
            try {
                InputStream raw = Resources.readInputStream(AUDIO_PATH + name + ".wav");
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(ais);
                    clip.addLineListener(ev -> {
                        if (ev.getType() == LineEvent.Type.STOP) clip.close();
                    });
                    clip.start();
                }
            } catch (Exception e) {
                Client.logger.warn("RadarSoundPlayer: failed to play " + name + ": " + e.getMessage());
            }
        }, "Radar-Sound");
        t.setDaemon(true);
        t.start();
        return true;
    }
}
