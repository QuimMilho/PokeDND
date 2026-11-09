/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.servers;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.servers.ServersResource.ServerDto;

/**
 * {@code GET /me/servers} (<b>Suggested</b>; chapter 18.1 lists {@code GET /servers} for the site admin only): the servers the signed-in
 * person administers. The app needs it so a server admin can pick a server for a new game. The list is empty for everyone else.
 */
@Path("/api/v1/me/servers")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.LOGGED_IN)
public class MeServersResource {

    @Inject
    ServerService service;

    @Inject
    CurrentUser current;

    @GET
    public List<ServerDto> mine() {
        return service.administeredBy(current.id()).stream().map(s -> ServerDto.of(s, service.adminsOf(s.id()))).toList();
    }
}
