/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import net.quimmilho.pokednd.api.auth.AuthResource.UserDto;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.error.ApiException;

/** {@code GET /me}: who am I (profile editing and the rest of the account pages come in later phases). */
@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    CurrentUser current;

    @Inject
    AccountRepository accounts;

    @GET
    @Requires(Action.LOGGED_IN)
    public UserDto me() {
        return accounts.findUser(current.id()).map(UserDto::of).orElseThrow(ApiException::unauthenticated);
    }
}
