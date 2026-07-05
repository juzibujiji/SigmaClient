package net.minecraft.util;

import com.mojang.authlib.GameProfile;
import com.mojang.util.UUIDTypeAdapter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class Session
{
    private static final String DEFAULT_USERNAME = "Player";
    public String username;
    public String playerID;
    public String token;
    private final Session.Type sessionType;

    public Session(String usernameIn, String playerIDIn, String tokenIn, String sessionTypeIn)
    {
        this.username = usernameIn;
        this.playerID = playerIDIn;
        this.token = tokenIn;
        this.sessionType = Session.Type.setSessionType(sessionTypeIn);
    }

    public String getSessionID()
    {
        return "token:" + this.token + ":" + this.playerID;
    }

    public String getPlayerID()
    {
        return this.playerID;
    }

    public String getUsername()
    {
        return this.username;
    }

    public String getToken()
    {
        return this.token;
    }

    public GameProfile getProfile()
    {
        String profileName = this.getProfileName();
        String profileId = this.getPlayerID();

        if (profileId == null || profileId.trim().isEmpty())
        {
            return new GameProfile((UUID)null, profileName);
        }

        try
        {
            UUID uuid = UUIDTypeAdapter.fromString(profileId);
            return new GameProfile(uuid, profileName);
        }
        catch (IllegalArgumentException illegalargumentexception)
        {
            return new GameProfile((UUID)null, profileName);
        }
    }

    private String getProfileName()
    {
        return this.username != null && !this.username.trim().isEmpty() ? this.username : DEFAULT_USERNAME;
    }

    public static enum Type
    {
        LEGACY("legacy"),
        MOJANG("mojang");

        private static final Map<String, Session.Type> SESSION_TYPES = Arrays.stream(values()).collect(Collectors.toMap((type) -> {
            return type.sessionType;
        }, Function.identity()));
        private final String sessionType;

        private Type(String sessionTypeIn)
        {
            this.sessionType = sessionTypeIn;
        }

        @Nullable
        public static Session.Type setSessionType(String sessionTypeIn)
        {
            return SESSION_TYPES.get(sessionTypeIn.toLowerCase(Locale.ROOT));
        }
    }
}
