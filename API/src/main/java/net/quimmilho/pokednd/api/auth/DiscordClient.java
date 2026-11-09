/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * A small OAuth 2 client for Discord (chapter 16.4). Discord has no discovery document, so the three addresses are plain
 * settings. It uses the authorization-code flow without PKCE: Discord does not document PKCE for this grant, and this is a
 * confidential client (it holds a secret) whose redirect is protected by the random {@code state} value.
 *
 * <p>Nothing secret is ever logged: not the client secret, the code, nor the access token.
 */
@ApplicationScoped
public class DiscordClient {

    private static final Logger LOG = Logger.getLogger(DiscordClient.class);

    /** What the API needs to know about the Discord account ({@code identify} and {@code email} scopes). */
    public record Profile(String id, String username, String globalName, String email, boolean verified, String locale) {
    }

    /** The code could not be turned into a Discord account (Discord refused, was unreachable, or answered nonsense). */
    public static class DiscordException extends Exception {
        public DiscordException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Inject
    DiscordConfig config;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** The address to send the browser to, where the person agrees to share the Discord name and e-mail. */
    public String authorizeUrl(String state, String redirectUri) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", config.clientId().orElseThrow());
        q.put("response_type", "code");
        q.put("redirect_uri", redirectUri);
        q.put("scope", "identify email");
        q.put("state", state);
        q.put("prompt", "consent");
        return config.authorizeUrl() + (config.authorizeUrl().contains("?") ? "&" : "?") + form(q);
    }

    /** Swaps the code for an access token, then asks Discord who the person is. */
    public Profile profile(String code, String redirectUri) throws DiscordException {
        String accessToken = exchange(code, redirectUri);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.apiUrl())).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken).header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            LOG.debugf("Discord answered the profile request with status %d", res.statusCode());
            if (res.statusCode() != 200) {
                throw new DiscordException("Discord refused the profile request (status " + res.statusCode() + ")", null);
            }
            JsonNode n = json.readTree(res.body());
            String id = text(n, "id");
            if (id == null) {
                throw new DiscordException("The Discord answer has no account id", null);
            }
            return new Profile(id, text(n, "username"), text(n, "global_name"), text(n, "email"), n.path("verified").asBoolean(false), text(n, "locale"));
        } catch (IOException e) {
            throw new DiscordException("Could not read the Discord answer", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiscordException("Interrupted while asking Discord", e);
        }
    }

    private String exchange(String code, String redirectUri) throws DiscordException {
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (config.clientId().orElseThrow() + ":" + config.clientSecret().orElseThrow()).getBytes(StandardCharsets.UTF_8));
            String body = form(Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri));
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.tokenUrl())).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            LOG.debugf("Discord answered the token request with status %d", res.statusCode());
            if (res.statusCode() != 200) {
                throw new DiscordException("Discord refused the code (status " + res.statusCode() + ")", null);
            }
            String token = text(json.readTree(res.body()), "access_token");
            if (token == null) {
                throw new DiscordException("The Discord answer has no access token", null);
            }
            return token;
        } catch (IOException e) {
            throw new DiscordException("Could not reach Discord", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiscordException("Interrupted while asking Discord", e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
