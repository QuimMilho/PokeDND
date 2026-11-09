/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import java.time.Duration;
import java.time.Instant;

/** The HTTP-only cookie that holds the refresh token (chapter 14.6). */
@ApplicationScoped
public class RefreshCookies {

    /** Name of the refresh cookie. */
    public static final String NAME = "pokednd_refresh";
    /** The cookie is sent only to the {@code /auth} endpoints, where the refresh and logout calls live. */
    public static final String PATH = "/api/v1/auth";
    /** The OAuth state cookie, sent only to the OAuth endpoints. */
    public static final String STATE_NAME = "pokednd_oauth_state";
    public static final String STATE_PATH = "/api/v1/auth/oauth";

    @Inject
    AuthConfig config;

    /** The cookie that carries the refresh token of a new session. */
    public NewCookie issue(AuthService.Session session) {
        return make(session.refreshToken(), Duration.between(Instant.now(), session.refreshExpiresAt()));
    }

    /** A cookie that makes the browser forget the refresh token. */
    public NewCookie clear() {
        return make("", Duration.ZERO);
    }

    /** The cookie that ties the OAuth state value to the browser that started the redirect. */
    public NewCookie oauthState(String state) {
        return oauthCookie(state, Duration.ofMinutes(10));
    }

    public NewCookie clearOauthState() {
        return oauthCookie("", Duration.ZERO);
    }

    /** It must be sent on the top-level redirect back from the provider, so it is {@code Lax}, not {@code Strict}. */
    private NewCookie oauthCookie(String value, Duration maxAge) {
        return new NewCookie.Builder(STATE_NAME).value(value).path(STATE_PATH).httpOnly(true).secure(config.cookieSecure())
                .sameSite(NewCookie.SameSite.LAX).maxAge((int) maxAge.getSeconds()).build();
    }

    private NewCookie make(String value, Duration maxAge) {
        return new NewCookie.Builder(NAME).value(value).path(PATH).httpOnly(true).secure(config.cookieSecure())
                .sameSite(NewCookie.SameSite.STRICT).maxAge((int) maxAge.getSeconds()).build();
    }
}
