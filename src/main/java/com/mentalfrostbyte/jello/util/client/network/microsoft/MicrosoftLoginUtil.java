package com.mentalfrostbyte.jello.util.client.network.microsoft;

import com.google.gson.*;
import net.minecraft.util.Session;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class MicrosoftLoginUtil {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static void openWebLink(final URI url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(url);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url.toString()).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url.toString()).start();
                } else {
                    new ProcessBuilder("xdg-open", url.toString()).start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static final RequestConfig REQUEST_CONFIG = RequestConfig
            .custom()
            .setConnectionRequestTimeout(30_000)
            .setConnectTimeout(30_000)
            .setSocketTimeout(30_000)
            .build();
    public static final String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
    public static final int PORT = 25575;

    public static CompletableFuture<String> acquireMSAuthCode(
            final Executor executor
    ) {
        return acquireMSAuthCodeSession(MicrosoftLoginUtil::openWebLink, executor)
                .thenApply(MSAuthCodeSession::authCode);
    }

    public static CompletableFuture<String> acquireMSAuthCode(
            final Consumer<URI> browserAction,
            final Executor executor
    ) {
        return acquireMSAuthCodeSession(browserAction, executor)
                .thenApply(MSAuthCodeSession::authCode);
    }

    public static CompletableFuture<MSAuthCodeSession> acquireMSAuthCodeSession(
            final Executor executor
    ) {
        return acquireMSAuthCodeSession(MicrosoftLoginUtil::openWebLink, executor);
    }

    public static CompletableFuture<MSAuthCodeSession> acquireMSAuthCodeSession(
            final Consumer<URI> browserAction,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final String state = randomUrlSafe(24);
                final String codeVerifier = randomUrlSafe(32);
                final String codeChallenge = createCodeChallenge(codeVerifier);

                try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("localhost"))) {
                    final String redirectUri = String.format("http://localhost:%d/callback", server.getLocalPort());
                    final URI uri = new URIBuilder("https://login.live.com/oauth20_authorize.srf")
                            .addParameter("client_id", CLIENT_ID)
                            .addParameter("response_type", "code")
                            .addParameter("redirect_uri", redirectUri)
                            .addParameter("scope", LOGIN_SCOPE)
                            .addParameter("state", state)
                            .addParameter("code_challenge", codeChallenge)
                            .addParameter("code_challenge_method", "S256")
                            .addParameter("prompt", "select_account")
                            .build();

                    final CompletableFuture<OAuthCallbackResult> callbackFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return waitForOAuthCallback(server, state);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    });

                    browserAction.accept(uri);

                    final OAuthCallbackResult callback = callbackFuture.join();
                    if (!StringUtils.isBlank(callback.errorMessage())) {
                        throw new Exception(callback.errorMessage());
                    }

                    return new MSAuthCodeSession(callback.authCode(), redirectUri, codeVerifier);
                }
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft auth code!", e);
            }
        }, executor);
    }

    private static OAuthCallbackResult waitForOAuthCallback(
            final ServerSocket server,
            final String expectedState
    ) throws Exception {
        server.setSoTimeout((int) TimeUnit.MINUTES.toMillis(10));

        while (true) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5_000);
                final OAuthCallbackResult callback = parseOAuthCallback(readHttpRequestUri(socket), expectedState);
                writeOAuthCallbackResponse(socket, callback);

                if (callback.complete()) {
                    return callback;
                }
            } catch (SocketTimeoutException e) {
                throw new TimeoutException("Timed out waiting for the Microsoft OAuth callback.");
            }
        }
    }

    private static URI readHttpRequestUri(final Socket socket) throws IOException {
        final BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
        );
        final String requestLine = reader.readLine();
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
        }

        if (StringUtils.isBlank(requestLine)) {
            throw new IOException("Missing HTTP request line.");
        }

        final String[] requestParts = requestLine.split(" ", 3);
        if (requestParts.length < 2) {
            throw new IOException("Invalid HTTP request line: " + requestLine);
        }

        return URI.create(requestParts[1]);
    }

    private static OAuthCallbackResult parseOAuthCallback(
            final URI requestUri,
            final String expectedState
    ) {
        if (!"/callback".equals(requestUri.getPath())) {
            return OAuthCallbackResult.pending(404);
        }

        final Map<String, String> query = URLEncodedUtils
                .parse(
                        Optional.ofNullable(requestUri.getRawQuery()).orElse(""),
                        StandardCharsets.UTF_8
                )
                .stream()
                .collect(Collectors.toMap(
                        NameValuePair::getName,
                        NameValuePair::getValue,
                        (first, second) -> first
                ));

        if (!expectedState.equals(query.get("state"))) {
            return OAuthCallbackResult.pending(400);
        }

        if (query.containsKey("code")) {
            return OAuthCallbackResult.success(query.get("code"));
        }

        if (query.containsKey("error")) {
            return OAuthCallbackResult.error(
                    String.format("%s: %s", query.get("error"), query.get("error_description"))
            );
        }

        return OAuthCallbackResult.error("There was no auth code or error description present.");
    }

    private static void writeOAuthCallbackResponse(final Socket socket, final OAuthCallbackResult callback)
            throws IOException {
        final int responseCode = callback.responseCode();
        // Only a completed callback carrying an error message renders the failure
        // page; the auth-code success case (and intermediate probes) use the happy
        // page so the browser never lands on a blank tab.
        final boolean isError = callback.complete() && !StringUtils.isBlank(callback.errorMessage());
        final String pageHtml = isError
                ? renderErrorPage(callback.errorMessage())
                : loadCallbackPage("/callback.html", FALLBACK_SUCCESS_PAGE);
        final byte[] response = pageHtml.getBytes(StandardCharsets.UTF_8);

        final String statusText = responseCode == 200 ? "OK" : responseCode == 404 ? "Not Found" : "Bad Request";
        final String headers = "HTTP/1.1 " + responseCode + " " + statusText + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + response.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        final OutputStream output = socket.getOutputStream();
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(response);
        output.flush();
    }

    private static String renderErrorPage(final String errorMessage) {
        final String safe = escapeHtml(errorMessage);
        return loadCallbackPage("/callback_error.html", FALLBACK_ERROR_PAGE).replace("%ERROR%", safe);
    }

    /**
     * Loads a bundled callback page, falling back to an inline copy if the resource
     * is missing or unreadable so the browser is never served a blank response.
     */
    private static String loadCallbackPage(final String resource, final String fallback) {
        try (InputStream stream = MicrosoftLoginUtil.class.getResourceAsStream(resource)) {
            if (stream != null) {
                final String html = new String(IOUtils.toByteArray(stream), StandardCharsets.UTF_8);
                if (!StringUtils.isBlank(html)) {
                    return html;
                }
            }
        } catch (IOException ignored) {
        }
        return fallback;
    }

    private static String escapeHtml(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final String FALLBACK_SUCCESS_PAGE =
            "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Sigma</title></head>"
                    + "<body style=\"background:#0a1416;color:#e8f6f5;font-family:sans-serif;text-align:center;"
                    + "padding-top:80px\"><h1>You're signed in</h1><p>You can close this tab and return to Sigma.</p>"
                    + "<script>setTimeout(function(){try{window.close();}catch(e){}},1200);</script></body></html>";

    private static final String FALLBACK_ERROR_PAGE =
            "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Sigma</title></head>"
                    + "<body style=\"background:#16100a;color:#f6ece8;font-family:sans-serif;text-align:center;"
                    + "padding-top:80px\"><h1>Login didn't complete</h1><p>%ERROR%</p>"
                    + "<p>You can close this tab and try again from the client.</p></body></html>";

    public static CompletableFuture<String> acquireMSAccessToken(
            final MSAuthCodeSession authCodeSession,
            final Executor executor
    ) {
        return acquireMSAccessToken(
                authCodeSession.authCode(),
                authCodeSession.redirectUri(),
                authCodeSession.codeVerifier(),
                executor
        );
    }

    public static CompletableFuture<String> acquireMSAccessToken(
            final String authCode,
            final Executor executor
    ) {
        return acquireMSAccessToken(
                authCode,
                String.format("http://localhost:%d/callback", PORT),
                null,
                executor
        );
    }

    public static CompletableFuture<String> acquireMSAccessToken(
            final String authCode,
            final String redirectUri,
            final String codeVerifier,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                final HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
                final java.util.List<NameValuePair> params = new java.util.ArrayList<>(Arrays.asList(
                        new BasicNameValuePair("client_id", CLIENT_ID),
                        new BasicNameValuePair("grant_type", "authorization_code"),
                        new BasicNameValuePair("code", authCode),
                        new BasicNameValuePair("redirect_uri", redirectUri)
                ));
                if (!StringUtils.isBlank(codeVerifier)) {
                    params.add(new BasicNameValuePair("code_verifier", codeVerifier));
                }
                request.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

                final org.apache.http.HttpResponse res = client.execute(request);

                final JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                return Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(
                                json.has("error") ? String.format(
                                        "%s: %s",
                                        json.get("error").getAsString(),
                                        json.get("error_description").getAsString()
                                ) : "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft access token!", e);
            }
        }, executor);
    }

    /**
     * The OAuth scope requested for both the initial web login and later refreshes.
     * {@code XboxLive.offline_access} is what makes Microsoft hand back a long-lived
     * refresh token, so it must stay identical across the two flows.
     */
    public static final String LOGIN_SCOPE = "XboxLive.signin XboxLive.offline_access";

    /**
     * The pair of tokens returned by the Microsoft token endpoint: a short-lived
     * access token and (when {@code offline_access} was requested) a long-lived,
     * possibly rotated, refresh token.
     */
    public record MSTokenResult(String accessToken, String refreshToken) {
    }

    /**
     * A completed Microsoft login: the resulting Minecraft {@link Session} together
     * with the refresh token that can later re-mint it without user interaction.
     */
    public record MicrosoftSession(Session session, String refreshToken) {
    }

    /**
     * Exchanges a freshly acquired auth code for a Microsoft access token
     * <b>and</b> refresh token. Unlike {@link #acquireMSAccessToken}, this keeps
     * the refresh token so the account can be silently re-logged in later.
     */
    public static CompletableFuture<MSTokenResult> acquireMSTokens(
            final MSAuthCodeSession authCodeSession,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            final java.util.List<NameValuePair> params = new java.util.ArrayList<>(Arrays.asList(
                    new BasicNameValuePair("client_id", CLIENT_ID),
                    new BasicNameValuePair("grant_type", "authorization_code"),
                    new BasicNameValuePair("code", authCodeSession.authCode()),
                    new BasicNameValuePair("redirect_uri", authCodeSession.redirectUri())
            ));
            if (!StringUtils.isBlank(authCodeSession.codeVerifier())) {
                params.add(new BasicNameValuePair("code_verifier", authCodeSession.codeVerifier()));
            }
            return requestMSTokens(params);
        }, executor);
    }

    /**
     * Redeems a stored refresh token for a new access token (and, since Microsoft
     * rotates them, usually a new refresh token). The refresh token is bound to the
     * issuing {@link #CLIENT_ID}, so this must run against the same client id and
     * scope used for the original login.
     */
    public static CompletableFuture<MSTokenResult> refreshMSTokens(
            final String refreshToken,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> requestMSTokens(Arrays.asList(
                new BasicNameValuePair("client_id", CLIENT_ID),
                new BasicNameValuePair("grant_type", "refresh_token"),
                new BasicNameValuePair("refresh_token", refreshToken),
                new BasicNameValuePair("scope", LOGIN_SCOPE)
        )), executor);
    }

    private static MSTokenResult requestMSTokens(final java.util.List<NameValuePair> params) {
        try (CloseableHttpClient client = HttpClients.createMinimal()) {
            final HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
            request.setConfig(REQUEST_CONFIG);
            request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            request.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            final org.apache.http.HttpResponse res = client.execute(request);

            final JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
            final String accessToken = Optional.ofNullable(json.get("access_token"))
                    .map(JsonElement::getAsString)
                    .filter(token -> !StringUtils.isBlank(token))
                    .orElseThrow(() -> new Exception(
                            json.has("error") ? String.format(
                                    "%s: %s",
                                    json.get("error").getAsString(),
                                    json.has("error_description") ? json.get("error_description").getAsString() : ""
                            ) : "There was no access token or error description present."
                    ));
            final String refreshToken = Optional.ofNullable(json.get("refresh_token"))
                    .map(JsonElement::getAsString)
                    .filter(token -> !StringUtils.isBlank(token))
                    .orElse(null);
            return new MSTokenResult(accessToken, refreshToken);
        } catch (InterruptedException e) {
            throw new CancellationException("Microsoft token acquisition was cancelled!");
        } catch (Exception e) {
            throw new CompletionException("Unable to acquire Microsoft tokens!", e);
        }
    }

    /**
     * Runs the full Xbox Live -&gt; XSTS -&gt; Minecraft chain from a Microsoft access
     * token and resolves the resulting Minecraft {@link Session}.
     */
    public static CompletableFuture<Session> mcSessionFromMSAccessToken(
            final String msAccessToken,
            final Executor executor
    ) {
        return acquireXboxAccessToken(msAccessToken, executor)
                .thenComposeAsync(xboxAccessToken -> acquireXboxXstsToken(xboxAccessToken, executor), executor)
                .thenComposeAsync(xboxXstsData -> acquireMCAccessToken(
                        xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor), executor)
                .thenComposeAsync(mcToken -> login(mcToken, executor), executor);
    }

    /**
     * Completes a browser login end-to-end: auth code session -&gt; MS tokens -&gt;
     * Minecraft session, preserving the refresh token for future silent re-logins.
     */
    public static CompletableFuture<MicrosoftSession> loginWithAuthCodeSession(
            final MSAuthCodeSession authCodeSession,
            final Executor executor
    ) {
        return acquireMSTokens(authCodeSession, executor)
                .thenComposeAsync(msTokens -> mcSessionFromMSAccessToken(msTokens.accessToken(), executor)
                        .thenApply(session -> new MicrosoftSession(session, msTokens.refreshToken())), executor);
    }

    /**
     * Silently re-mints a Minecraft session from a stored refresh token, returning
     * the new session together with the (possibly rotated) refresh token to persist.
     */
    public static CompletableFuture<MicrosoftSession> loginWithRefreshToken(
            final String refreshToken,
            final Executor executor
    ) {
        return refreshMSTokens(refreshToken, executor)
                .thenComposeAsync(msTokens -> mcSessionFromMSAccessToken(msTokens.accessToken(), executor)
                        .thenApply(session -> new MicrosoftSession(
                                session,
                                msTokens.refreshToken() != null ? msTokens.refreshToken() : refreshToken)), executor);
    }

    private static String randomUrlSafe(final int byteLength) {
        final byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String createCodeChallenge(final String codeVerifier) throws Exception {
        final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        final byte[] digest = sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private record OAuthCallbackResult(
            boolean complete,
            int responseCode,
            String authCode,
            String errorMessage
    ) {
        private static OAuthCallbackResult pending(final int responseCode) {
            return new OAuthCallbackResult(false, responseCode, null, null);
        }

        private static OAuthCallbackResult success(final String authCode) {
            return new OAuthCallbackResult(true, 200, authCode, null);
        }

        private static OAuthCallbackResult error(final String errorMessage) {
            return new OAuthCallbackResult(true, 200, null, errorMessage);
        }
    }

    public record MSAuthCodeSession(String authCode, String redirectUri, String codeVerifier) {
    }

    public static CompletableFuture<String> acquireXboxAccessToken(
            final String accessToken,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                final HttpPost request = new HttpPost(URI.create("https://user.auth.xboxlive.com/user/authenticate"));
                final JsonObject entity = new JsonObject();
                final JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", String.format("d=%s", accessToken));
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(entity.toString()));

                final org.apache.http.HttpResponse res = client.execute(request);

                final JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();
                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(
                                json.has("XErr") ? String.format(
                                        "%s: %s", json.get("XErr").getAsString(), json.get("Message").getAsString()
                                ) : "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireXboxXstsToken(
            final String accessToken,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                // Build a new HTTP request
                final HttpPost request = new HttpPost("https://xsts.auth.xboxlive.com/xsts/authorize");
                final JsonObject entity = new JsonObject();
                final JsonObject properties = new JsonObject();
                final JsonArray userTokens = new JsonArray();
                userTokens.add(new JsonPrimitive(accessToken));
                properties.addProperty("SandboxId", "RETAIL");
                properties.add("UserTokens", userTokens);
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                entity.addProperty("TokenType", "JWT");
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(entity.toString()));

                final org.apache.http.HttpResponse res = client.execute(request);

                final JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();
                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .map(token -> {
                            final String uhs = json.get("DisplayClaims").getAsJsonObject()
                                    .get("xui").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("uhs").getAsString();

                            Map<String, String> result = new HashMap<>();
                            result.put("Token", token);
                            result.put("uhs", uhs);
                            return result;
                        })
                        .orElseThrow(() -> new Exception(
                                json.has("XErr") ? String.format(
                                        "%s: %s", json.get("XErr").getAsString(), json.get("Message").getAsString()
                                ) : "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live XSTS token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live XSTS token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireMCAccessToken(
            final String xstsToken,
            final String userHash,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                final HttpPost request = new HttpPost(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(
                        String.format("{\"identityToken\": \"XBL3.0 x=%s;%s\"}", userHash, xstsToken)
                ));

                final org.apache.http.HttpResponse res = client.execute(request);

                final JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();

                return Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(
                                json.has("error") ? String.format(
                                        "%s: %s", json.get("error").getAsString(), json.get("errorMessage").getAsString()
                                ) : "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Minecraft access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Session> login(
            final String mcToken,
            final Executor executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                final HttpGet request = new HttpGet(URI.create("https://api.minecraftservices.com/minecraft/profile"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Authorization", "Bearer " + mcToken);

                final org.apache.http.HttpResponse res = client.execute(request);

                final JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                final String uuid = getJsonString(json, "id");
                final String name = getJsonString(json, "name");

                if (!StringUtils.isBlank(uuid) && !StringUtils.isBlank(name)) {
                    return new Session(
                            name,
                            uuid,
                            mcToken,
                            Session.Type.MOJANG.toString()
                    );
                }

                throw new Exception(
                        json.has("error") ? String.format(
                                "%s: %s",
                                getJsonString(json, "error"),
                                getJsonString(json, "errorMessage")
                        ) : "Minecraft profile response did not include a player name and UUID."
                );
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft profile fetching was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to fetch Minecraft profile!", e);
            }
        }, executor);
    }

    private static String getJsonString(final JsonObject json, final String key) {
        final JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : "";
    }
}
