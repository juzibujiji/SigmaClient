package com.mentalfrostbyte.jello.managers.util.account.microsoft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.network.microsoft.MicrosoftLoginUtil;
import com.mentalfrostbyte.jello.util.system.network.ImageUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.util.BufferedImageUtil;
import fr.litarvan.openauth.microsoft.MicrosoftAuthResult;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticationException;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticator;
import net.minecraft.util.Session;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class Account {
    private String knownName = "Unknown name";
    private String uuid = "8667ba71-b85a-4004-af54-457a9734eed7";
    private String email;
    private String password;
    private ArrayList<Ban> bans = new ArrayList<>();
    private long lastUsed;
    private final long dateAdded;
    private int useCount;
    private BufferedImage skin;
    private Texture head;
    private volatile boolean headLoading = false;
    private volatile boolean headLoadFailed = false;
    private volatile BufferedImage pendingHeadImage = null;
    private Thread skinUpdateThread;

    private String token = "";
    // Long-lived Microsoft OAuth refresh token (web login only). Used to silently
    // re-mint the short-lived Minecraft access token once it expires.
    private String refreshToken = "";

    public Account(String email, String password, ArrayList<Ban> bans, String knownName) {
        this.email = email;
        this.password = password;
        this.dateAdded = System.currentTimeMillis();
        this.lastUsed = 0L;
        this.useCount = 0;
        if (bans != null) {
            this.bans = bans;
        }

        if (knownName != null) {
            this.knownName = knownName;
        }
    }

    public Account(String username, String playerID, String token) {
        this(username, playerID, null, null);
        this.token = token;
    }

    public Account(String email, String password, ArrayList<Ban> bans) {
        this(email, password, bans, null);
    }

    public Account(String email, String password) {
        this(email, password, null, null);
    }

    public Account(JsonObject json) throws IOException {
        if (json.has("email")) {
            this.email = json.get("email").getAsString();
        }

        if (json.has("password")) {
            this.password = decodeBase64(json.get("password").getAsString());
        }

        if (json.has("token")) {
            this.token = decodeBase64(json.get("token").getAsString());
        }

        if (json.has("refreshToken")) {
            this.refreshToken = decodeBase64(json.get("refreshToken").getAsString());
        }

        if (json.has("bans")) {
            for (var ban : json.getAsJsonArray("bans")) {
                this.bans.add(new Ban(ban.getAsJsonObject()));
            }
        }

        if (json.has("knownName")) {
            this.knownName = json.get("knownName").getAsString();
        }

        if (json.has("knownUUID")) {
            this.uuid = json.get("knownUUID").getAsString();
        }

        if (json.has("dateAdded")) {
            this.dateAdded = json.get("dateAdded").getAsLong();
        } else {
            this.dateAdded = System.currentTimeMillis();
        }

        if (json.has("lastUsed")) {
            this.lastUsed = json.get("lastUsed").getAsLong();
        }

        if (json.has("useCount")) {
            this.useCount = json.get("useCount").getAsInt();
        }

        if (json.has("skin")) {
            byte[] var7 = parseBase64Binary(json.get("skin").getAsString());

            try {
                this.skin = ImageIO.read(new ByteArrayInputStream(var7));
            } catch (IOException var6) {
                throw new IOException(var6);
            }
        }
    }

    public static String encodeBase64(String s) {
        byte[] bytes = Base64.encodeBase64(s.getBytes());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String decodeBase64(String s) {
        byte[] bytes = Base64.decodeBase64(s.getBytes());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void updateUsedCount() {
        this.useCount++;
    }

    public ArrayList<Ban> getBans() {
        return this.bans;
    }

    public String getEmail() {
        return this.email;
    }

    public String getKnownName() {
        return this.knownName;
    }

    public String getName() {
        return !this.knownName.equals("Unknown name") ? this.knownName : this.email;
    }

    public String getUUID() {
        return this.uuid;
    }

    public String getFormattedUUID() {
        return this.uuid.replaceAll("-", "");
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String var1) {
        this.password = var1;
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token != null ? token : "";
    }

    public String getRefreshToken() {
        return this.refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken != null ? refreshToken : "";
    }

    public boolean hasRefreshToken() {
        return this.refreshToken != null && !this.refreshToken.isEmpty();
    }

    public void setEmail(String var1) {
        this.email = var1;
    }

    public void registerBan(Ban ban) {
        this.unbanFromServerIP(ban.getServerIP());
        this.bans.add(ban);
    }

    public void unbanFromServerIP(String serverIP) {
        this.bans.removeIf(ban -> ban.getServerIP().equals(serverIP));
    }

    public void setName(String name) {
        this.knownName = name;
        this.skinUpdateThread = null;
    }

    public void setUuid(String var1) {
        this.uuid = var1;
    }

    public Texture setHeadTexture() {
        // If we have a pending image from background thread, create texture now (on
        // render thread)
        if (this.pendingHeadImage != null) {
            try {
                this.head = BufferedImageUtil.getTexture("head_" + getFormattedUUID(), this.pendingHeadImage);
            } catch (Exception e) {
                System.err.println("[Account] Failed to create head texture: " + e.getMessage());
            }
            this.pendingHeadImage = null;
        }

        // Start background download if not already loading, not failed, and head not
        // loaded
        if (this.head == null && !this.headLoading && !this.headLoadFailed) {
            this.headLoading = true;
            new Thread(() -> {
                // Try multiple API endpoints in case some are blocked
                String[] apiUrls = {
                        "https://minotar.net/avatar/" + getFormattedUUID(),
                        "https://mc-heads.net/avatar/" + getFormattedUUID()
                };

                BufferedImage image = null;
                for (String apiUrl : apiUrls) {
                    try {
                        URL url = new URL(apiUrl);
                        image = ImageIO.read(url);
                        if (image != null) {
                            System.out.println("[Account] Successfully loaded head from: " + apiUrl);
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println("[Account] Failed to download from " + apiUrl + ": " + e.getMessage());
                    }
                }

                if (image != null) {
                    this.pendingHeadImage = image;
                } else {
                    this.headLoadFailed = true;
                    System.err.println("[Account] All avatar APIs failed, using default head");
                }
                this.headLoading = false;
            }, "HeadTextureLoader-" + getFormattedUUID()).start();
        }

        return this.head != null ? this.head : Resources.head;
    }

    public void updateSkin() {
        if (!this.getUUID().contains("8667ba71-b85a-4004-af54-457a9734eed7") && this.skinUpdateThread == null) {
            this.skinUpdateThread = new Thread(() -> {
                try {
                    this.skin = ImageIO.read(new URL(ImageUtil.getSkinUrlByID(getFormattedUUID())));
                } catch (Exception ignored) {
                }
            });
            this.skinUpdateThread.start();
        }
    }

    public Session.Type getAccountType() {
        return this.email.contains("@") ? Session.Type.MOJANG : Session.Type.LEGACY;
    }

    public Session login() throws MicrosoftAuthenticationException {
        // Accounts added via Web / Cookie / Token login carry a Minecraft access
        // token. These expire in ~24h, so we must confirm the token still works and,
        // if not, refresh it. Handing back a dead token would look "successful" here
        // but fail on the actual server join with "Invalid session".
        if (this.token != null && !this.token.isEmpty()) {
            ProfileProbe probe = probeToken(this.token);
            switch (probe.state) {
                case VALID -> {
                    this.knownName = probe.profile.get("name").getAsString();
                    this.uuid = fixUUID(probe.profile.get("id").getAsString());
                    this.updateSkin();
                    this.lastUsed = System.currentTimeMillis();
                    return new Session(this.knownName, this.uuid.replace("-", ""), this.token, "mojang");
                }
                case EXPIRED -> {
                    // Token is genuinely dead. Try the stored Microsoft refresh token.
                    if (this.hasRefreshToken()) {
                        Session refreshed = loginWithStoredRefreshToken();
                        if (refreshed != null) {
                            return refreshed;
                        }
                    }
                    // No working refresh path. For a real Microsoft email+password
                    // account we can still fall through to a credential login below;
                    // otherwise this account cannot be logged in, so fail loudly
                    // instead of returning a dead session.
                    if (this.isEmailAValidEmailFormat()) {
                        throw new MicrosoftAuthenticationException(
                                "Stored session for " + this.getName()
                                        + " expired and could not be refreshed. Re-add it via Web login.");
                    }
                }
                case UNKNOWN -> {
                    // Couldn't reach the profile API (network/region/5xx). That does
                    // not mean the token is dead - the join uses a different host - so
                    // optimistically reuse the stored token rather than failing.
                    this.lastUsed = System.currentTimeMillis();
                    return new Session(this.getName(), this.getFormattedUUID(), this.token, "mojang");
                }
            }
        }

        if (!this.isEmailAValidEmailFormat()) {
            MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
            MicrosoftAuthResult result = authenticator.loginWithCredentials(email, password);
            System.out.printf("Logged in with '%s'%n", result.getProfile().getName());
            this.setName(result.getProfile().getName());
            this.setUuid(fixUUID(result.getProfile().getId()));
            // Persist the tokens so subsequent launches can silently refresh instead
            // of prompting for credentials (and re-hitting Microsoft rate limits).
            this.setToken(result.getAccessToken());
            this.setRefreshToken(result.getRefreshToken());
            this.updateSkin();
            this.lastUsed = System.currentTimeMillis();
            try {
                Client.getInstance().accountManager.saveAlts();
            } catch (Exception ignored) {
            }
            return new Session(
                    result.getProfile().getName(), result.getProfile().getId(), result.getAccessToken(),
                    getAccountType().name());
        } else {
            // Offline / cracked account: no token, name only.
            this.setName(this.getEmail());
            this.lastUsed = System.currentTimeMillis();
            return new Session(this.getEmail(), "", "", "mojang");
        }
    }

    /**
     * Redeems the stored Microsoft refresh token for a fresh Minecraft session and
     * persists the rotated tokens. Returns {@code null} if the refresh fails (e.g.
     * the refresh token was revoked or expired), so the caller can fall back.
     */
    private Session loginWithStoredRefreshToken() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MicrosoftLoginUtil.MicrosoftSession result =
                    MicrosoftLoginUtil.loginWithRefreshToken(this.refreshToken, executor).join();

            Session session = result.session();
            this.token = session.getToken();
            this.refreshToken = result.refreshToken();
            this.setName(session.getUsername());
            this.setUuid(fixUUID(session.getPlayerID()));
            this.updateSkin();
            this.lastUsed = System.currentTimeMillis();

            // The account list is keyed on email; persist the rotated tokens so the
            // next launch starts from the refreshed refresh token, not a stale one.
            try {
                Client.getInstance().accountManager.saveAlts();
            } catch (Exception ignored) {
            }

            return new Session(session.getUsername(), fixUUID(session.getPlayerID()).replace("-", ""),
                    session.getToken(), "mojang");
        } catch (Exception e) {
            Client.logger.error("Failed to refresh Microsoft session for " + this.getName(), e);
            return null;
        } finally {
            executor.shutdown();
        }
    }

    public boolean isPossibleRefreshToken(String token) {
        if (token.length() > 100) {
            return true;
        }

        return token.matches("^[A-Za-z0-9+/=]+$");
    }

    /**
     * I made this to fix autism caused by OpenAuth
     */
    public static String fixUUID(String uuidString) {
        return uuidString.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5");
    }

    public JsonObject toJSON() {
        JsonObject obj = new JsonObject();
        obj.add("bans", this.makeBanJSONArray());
        obj.addProperty("email", this.email);
        obj.addProperty("password", encodeBase64(this.password));
        obj.addProperty("token", encodeBase64(this.token));
        obj.addProperty("refreshToken", encodeBase64(this.refreshToken != null ? this.refreshToken : ""));
        obj.addProperty("knownName", this.knownName);
        obj.addProperty("knownUUID", this.uuid);
        obj.addProperty("useCount", this.useCount);
        obj.addProperty("lastUsed", this.lastUsed);
        obj.addProperty("dateAdded", this.dateAdded);
        if (this.skin != null) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Base64OutputStream base64OutputStream = new Base64OutputStream(outputStream);
            String skinBase64 = "";

            try {
                ImageIO.write(this.skin, "png", base64OutputStream);
                skinBase64 = outputStream.toString("UTF-8");
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            obj.addProperty("skin", skinBase64);
        }

        return obj;
    }

    public static byte[] parseBase64Binary(String base64String) {
        return Base64.decodeBase64(base64String);
    }

    public JsonArray makeBanJSONArray() {
        JsonArray jsonArray = new JsonArray();

        for (Ban ban : this.bans) {
            jsonArray.add(ban.asJSONObject());
        }

        return jsonArray;
    }

    public int getUseCount() {
        return this.useCount;
    }

    public long getLastUsed() {
        return this.lastUsed;
    }

    public long getDateAdded() {
        return this.dateAdded;
    }

    public Ban getBanInfo(String serverIP) {
        for (Ban ban : this.getBans()) {
            if (ban.getServerIP().equals(serverIP)) {
                return ban;
            }
        }

        return null;
    }

    public boolean isEmailAValidEmailFormat() {
        if (this.getPassword().isEmpty())
            return true;

        Pattern var3 = Pattern.compile("[a-zA-Z0-9_]{2,16}");
        return var3.matcher(this.getEmail()).matches();
    }

    private enum TokenState {
        /** The Minecraft profile endpoint accepted the token (HTTP 200). */
        VALID,
        /** The endpoint rejected the token as unauthorized (HTTP 401/403). */
        EXPIRED,
        /** The endpoint could not be reached or returned an inconclusive status. */
        UNKNOWN
    }

    private static final class ProfileProbe {
        final TokenState state;
        final JsonObject profile;

        ProfileProbe(TokenState state, JsonObject profile) {
            this.state = state;
            this.profile = profile;
        }
    }

    /**
     * Probes a Minecraft access token against the profile endpoint. Distinguishes a
     * genuinely rejected token (401/403) from a transient reachability problem, so
     * the caller doesn't discard a still-valid token just because the profile API
     * was momentarily unreachable.
     */
    private ProfileProbe probeToken(String accessToken) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.minecraftservices.com/minecraft/profile");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);

            int code = connection.getResponseCode();
            if (code == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    return new ProfileProbe(TokenState.VALID,
                            JsonParser.parseString(response.toString()).getAsJsonObject());
                }
            }

            if (code == 401 || code == 403) {
                return new ProfileProbe(TokenState.EXPIRED, null);
            }

            // Any other status (404 no-profile, 5xx, etc.) is inconclusive.
            return new ProfileProbe(TokenState.UNKNOWN, null);
        } catch (Exception e) {
            // Network failure / timeout / DNS: cannot conclude the token is dead.
            return new ProfileProbe(TokenState.UNKNOWN, null);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
