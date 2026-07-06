package com.mentalfrostbyte.jello.util.game.sound;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 雷达告警音效播放器。
 * <p>
 * 播放 {@code assets/minecraft/com/mentalfrostbyte/gui/resources/audio/} 下的 MP3 资源。
 * <p>
 * 自带按音效名的节流（同名音效在 minIntervalMs 内只播一次），
 * 供雷达扫描/锁定告警循环调用而不会叠音。
 */
public final class RadarSoundPlayer {

    private static final String AUDIO_PATH = "com/mentalfrostbyte/gui/resources/audio/";
    private static final String AUDIO_TYPE = ".mp3";
    private static final Map<String, Long> lastPlayTime = new ConcurrentHashMap<>();
    private static final AtomicBoolean playing = new AtomicBoolean(false);
    private static final Object playbackLock = new Object();
    private static AdvancedPlayer currentPlayer;
    private static String currentName;
    private static long playbackId;

    private RadarSoundPlayer() {
    }

    /**
     * 播放 MP3 音效，同名音效在 minIntervalMs 内的重复调用会被忽略。
     *
     * @param name          资源名（不含扩展名），如 "radar_lock"
     * @param minIntervalMs 同名音效最小播放间隔（毫秒）
     * @return 本次调用是否真正触发了播放
     */
    public static boolean play(String name, long minIntervalMs) {
        long now = System.currentTimeMillis();
        Long last = lastPlayTime.get(name);
        if (last != null && now - last < minIntervalMs) return false;
        if (!playing.compareAndSet(false, true)) return false;
        lastPlayTime.put(name, now);

        long id;
        synchronized (playbackLock) {
            id = ++playbackId;
            currentName = name;
            currentPlayer = null;
        }

        Thread t = new Thread(() -> {
            AdvancedPlayer player = null;
            try (InputStream raw = Resources.readInputStream(AUDIO_PATH + name + AUDIO_TYPE)) {
                player = new AdvancedPlayer(raw);
                synchronized (playbackLock) {
                    if (id != playbackId || !playing.get()) {
                        player.close();
                        return;
                    }
                    currentPlayer = player;
                }
                player.play();
            } catch (JavaLayerException e) {
                if (isCurrentPlayback(id)) {
                    Client.logger.warn("RadarSoundPlayer: unsupported MP3 " + name + ": " + e.getMessage());
                }
            } catch (Exception e) {
                if (isCurrentPlayback(id)) {
                    Client.logger.warn("RadarSoundPlayer: failed to play " + name + ": " + e.getMessage());
                }
            } finally {
                synchronized (playbackLock) {
                    if (id == playbackId) {
                        currentPlayer = null;
                        currentName = null;
                        playing.set(false);
                    }
                }
            }
        }, "Radar-Sound");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private static boolean isCurrentPlayback(long id) {
        synchronized (playbackLock) {
            return id == playbackId;
        }
    }

    public static boolean stop() {
        return stop(null);
    }

    /**
     * 立即停止当前播放。
     *
     * @param name 只在当前播放的是该音效时才停止；null 表示无条件停止
     * @return 是否真的停止了一段正在进行的播放
     */
    public static boolean stop(String name) {
        AdvancedPlayer player;
        synchronized (playbackLock) {
            if (!playing.get()) return false;
            if (name != null && !name.equals(currentName)) return false;

            playbackId++;
            player = currentPlayer;
            currentPlayer = null;
            currentName = null;
            playing.set(false);
        }

        // close() 会让播放线程里的 play() 解码循环立即退出
        if (player != null) {
            player.close();
        }
        return true;
    }
}
