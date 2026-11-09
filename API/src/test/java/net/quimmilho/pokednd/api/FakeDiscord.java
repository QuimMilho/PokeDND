/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for Discord for the tests: the token endpoint and the {@code users/@me} endpoint. A test registers which profile a
 * code gives; any other code is refused like Discord refuses a bad one.
 */
public final class FakeDiscord implements AutoCloseable {

    public static final String CLIENT_ID = "test-client-id";
    public static final String CLIENT_SECRET = "test-client-secret-5f3a9c7e";

    private final HttpServer server;
    private final Map<String, String> profileByCode = new ConcurrentHashMap<>();
    private final Map<String, String> profileByToken = new ConcurrentHashMap<>();
    private final AtomicInteger tokenRequests = new AtomicInteger();
    /** The last redirect_uri that the API sent to the token endpoint. */
    private volatile String lastRedirectUri;

    public FakeDiscord() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/oauth2/token", this::token);
        server.createContext("/api/users/@me", this::me);
        server.start();
    }

    public String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String authorizeUrl() {
        return base() + "/oauth2/authorize";
    }

    public String tokenUrl() {
        return base() + "/api/oauth2/token";
    }

    public String apiUrl() {
        return base() + "/api/users/@me";
    }

    public int tokenRequests() {
        return tokenRequests.get();
    }

    public String lastRedirectUri() {
        return lastRedirectUri;
    }

    /** Makes {@code code} give this Discord account. */
    public void account(String code, String id, String username, String email, boolean verified) {
        String email2 = email == null ? "null" : "\"" + email + "\"";
        profileByCode.put(code, "{\"id\":\"" + id + "\",\"username\":\"" + username + "\",\"global_name\":\"" + username + " G\",\"email\":" + email2
                + ",\"verified\":" + verified + ",\"locale\":\"pt-BR\"}");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void token(HttpExchange x) throws IOException {
        tokenRequests.incrementAndGet();
        String expected = "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(x.getRequestHeaders().getFirst("Authorization"))) {
            send(x, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        Map<String, String> form = form(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        lastRedirectUri = form.get("redirect_uri");
        String profile = profileByCode.remove(form.getOrDefault("code", ""));
        if (!"authorization_code".equals(form.get("grant_type")) || profile == null) {
            send(x, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        String accessToken = "at-" + Integer.toHexString(profile.hashCode()) + "-" + System.nanoTime();
        profileByToken.put(accessToken, profile);
        send(x, 200, "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":604800,\"scope\":\"identify email\"}");
    }

    private void me(HttpExchange x) throws IOException {
        String auth = x.getRequestHeaders().getFirst("Authorization");
        String profile = auth != null && auth.startsWith("Bearer ") ? profileByToken.get(auth.substring(7)) : null;
        if (profile == null) {
            send(x, 401, "{\"message\":\"401: Unauthorized\"}");
        } else {
            send(x, 200, profile);
        }
    }

    private static void send(HttpExchange x, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }

    private static Map<String, String> form(String body) {
        Map<String, String> m = new HashMap<>();
        for (String pair : body.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }
}
