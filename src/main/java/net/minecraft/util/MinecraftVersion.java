package net.minecraft.util;

import com.google.gson.JsonParseException;
import com.mojang.bridge.game.GameVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

public class MinecraftVersion implements GameVersion {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final GameVersion GAME_VERSION = new MinecraftVersion();
    private final String id;
    private final String name;
    private final boolean stable;
    private final int worldVersion;
    private final int protocolVersion;
    private final int packVersion;
    private final Date buildTime;
    private final String releaseTarget;

    private MinecraftVersion() {
        this.id = UUID.randomUUID().toString().replaceAll("-", "");
        this.name = "1.16.4";
        this.stable = true;
        this.worldVersion = 2584;
        this.protocolVersion = SharedConstants.getNativeVersion();
        this.packVersion = 6;
        this.buildTime = new Date();
        this.releaseTarget = "1.16.4";
    }

    /**
     * Creates a new instance containing game version data from version.json (or fallback data if necessary).
     * <p>
     * For getting data, use {@link SharedConstants#getVersion} instead, as that is cached.
     */
    public static GameVersion load() {
        return GAME_VERSION;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getReleaseTarget() {
        return this.releaseTarget;
    }

    public int getWorldVersion() {
        return this.worldVersion;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public int getPackVersion() {
        return this.packVersion;
    }

    public Date getBuildTime() {
        return this.buildTime;
    }

    public boolean isStable() {
        return this.stable;
    }
}
