/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import net.quimmilho.pokednd.api.error.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Asks Mojang for a skin: the profile service turns a username into an id, the session server gives that profile's textures, and the
 * texture is downloaded. Only a picture on {@code textures.minecraft.net} is downloaded, whatever the answer says, and never more than
 * the size limit.
 */
@ApplicationScoped
@DefaultBean
public class HttpMojangSkins implements MojangSkins {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final String TEXTURE_HOST = "textures.minecraft.net";

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "pokednd.skins.mojang.profile-url", defaultValue = "https://api.mojang.com/users/profiles/minecraft/")
    String profileUrl;

    @ConfigProperty(name = "pokednd.skins.mojang.session-url", defaultValue = "https://sessionserver.mojang.com/session/minecraft/profile/")
    String sessionUrl;

    @ConfigProperty(name = "pokednd.skins.max-bytes", defaultValue = "65536")
    int maxBytes;

    @Override
    public Found find(String username) {
        JsonNode profile = getJson(profileUrl + encode(username), true);
        if (profile == null || !profile.hasNonNull("id")) {
            throw new ApiException(404, "MINECRAFT_USER_NOT_FOUND", "There is no Minecraft account called " + username + ".");
        }
        String id = profile.get("id").asText();
        String name = profile.path("name").asText(username);
        JsonNode session = getJson(sessionUrl + encode(id), true);
        JsonNode textures = null;
        if (session != null) {
            for (JsonNode p : session.path("properties")) {
                if ("textures".equals(p.path("name").asText())) {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(p.path("value").asText());
                        textures = json.readTree(new String(decoded, StandardCharsets.UTF_8));
                    } catch (IOException | IllegalArgumentException e) {
                        throw unavailable();
                    }
                }
            }
        }
        JsonNode skin = textures == null ? null : textures.path("textures").path("SKIN");
        if (skin == null || !skin.hasNonNull("url")) {
            throw new ApiException(404, "MINECRAFT_NO_SKIN", name + " has no custom skin.");
        }
        URI uri = URI.create(skin.get("url").asText());
        if (!TEXTURE_HOST.equals(uri.getHost())) {
            throw unavailable();
        }
        boolean slim = "slim".equals(skin.path("metadata").path("model").asText());
        // the texture host serves the same picture over https
        byte[] png = getBytes(URI.create("https://" + TEXTURE_HOST + uri.getRawPath()));
        return new Found(png, slim, name);
    }

    private JsonNode getJson(String url, boolean noContentMeansMissing) {
        try {
            HttpResponse<byte[]> r = CLIENT.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() == 404 || (noContentMeansMissing && r.statusCode() == 204)) {
                return null;
            }
            if (r.statusCode() != 200) {
                throw unavailable();
            }
            return json.readTree(r.body());
        } catch (IOException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private byte[] getBytes(URI uri) {
        try {
            HttpResponse<byte[]> r = CLIENT.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200 || r.body().length > maxBytes) {
                throw unavailable();
            }
            return r.body();
        } catch (IOException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static String encode(String text) {
        return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static ApiException unavailable() {
        return new ApiException(502, "MOJANG_UNAVAILABLE", "Mojang could not be reached. Try again later, or upload a skin.");
    }
}
