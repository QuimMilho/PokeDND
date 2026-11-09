/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Locale;
import java.util.Set;
import net.quimmilho.pokednd.api.auth.AuthResource.UserDto;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.error.ApiException;

/** {@code GET /me} and {@code PATCH /me} (chapter 18.1): who am I, and the profile and preferences the person may change. */
@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    private static final Set<String> LOCALES = Set.of("en", "pt");
    private static final Set<String> THEMES = Set.of("system", "light", "dark");

    /** Every field is optional: what the request leaves out stays as it was. */
    public record ProfileRequest(String displayName, String locale, String theme) {
    }

    @Inject
    CurrentUser current;

    @Inject
    AccountRepository accounts;

    @GET
    @Requires(Action.LOGGED_IN)
    public UserDto me() {
        return accounts.findUser(current.id()).map(UserDto::of).orElseThrow(ApiException::unauthenticated);
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.LOGGED_IN)
    public UserDto update(ProfileRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        String name = null;
        if (body.displayName() != null) {
            name = body.displayName().strip();
            if (name.isEmpty() || name.length() > 40) {
                throw ApiException.badRequest("INVALID_NAME", "The display name can have 1 to 40 characters.");
            }
        }
        String locale = body.locale() == null ? null : body.locale().strip().toLowerCase(Locale.ROOT);
        if (locale != null && !LOCALES.contains(locale)) {
            throw ApiException.badRequest("INVALID_LOCALE", "The language must be en or pt.");
        }
        String theme = body.theme() == null ? null : body.theme().strip().toLowerCase(Locale.ROOT);
        if (theme != null && !THEMES.contains(theme)) {
            throw ApiException.badRequest("INVALID_THEME", "The theme must be system, light or dark.");
        }
        accounts.updateProfile(current.id(), name, locale, theme);
        return me();
    }
}
