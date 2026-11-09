/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.servers;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.auth.Role;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository.Server;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * {@code /servers} (chapter 18.1): registering Minecraft servers, their tokens and their admins. Everything is for the site admin
 * except {@code GET /servers/{id}/status}, which the admins of that server may also read.
 */
@Path("/api/v1/servers")
@Produces(MediaType.APPLICATION_JSON)
public class ServersResource {

    /** A server's admin as the lists show it. */
    public record AdminDto(String id, String displayName) {
        static AdminDto of(User u) {
            return new AdminDto(u.id().toString(), u.displayName());
        }
    }

    /** A server. The token is never part of it: it is shown once, in {@link CreatedDto}. */
    public record ServerDto(String id, String name, String bluemapUrl, String status, Instant lastSeenAt, String modVersion, String mcVersion,
                            String tokenPrefix, List<AdminDto> admins, Instant createdAt) {
        static ServerDto of(Server s, List<User> admins) {
            return new ServerDto(s.id().toString(), s.name(), s.bluemapUrl(), s.status(), s.lastSeenAt(), s.modVersion(), s.mcVersion(), s.tokenPrefix(),
                    admins.stream().map(AdminDto::of).toList(), s.createdAt());
        }
    }

    /** The answer to registering a server or rotating its token: the only time the token is shown. */
    public record CreatedDto(ServerDto server, String token) {
    }

    public record StatusDto(String id, String name, String status, Instant lastSeenAt, String modVersion, String mcVersion) {
    }

    public record CreateRequest(String name, String bluemapUrl) {
    }

    public record UpdateRequest(String name, String bluemapUrl) {
    }

    @Inject
    ServerService service;

    @Inject
    CurrentUser current;

    @GET
    @Requires(Action.MANAGE_SERVERS)
    public List<ServerDto> list() {
        return service.all().stream().map(s -> ServerDto.of(s, service.adminsOf(s.id()))).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.MANAGE_SERVERS)
    public Response register(CreateRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with a name.");
        }
        ServerService.Created created = service.register(body.name(), body.bluemapUrl());
        return Response.status(201).entity(created(created)).build();
    }

    @GET
    @Path("/{id}")
    @Requires(Action.MANAGE_SERVERS)
    public ServerDto get(@PathParam("id") UUID id) {
        return dto(service.get(id));
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.MANAGE_SERVERS)
    public ServerDto update(@PathParam("id") UUID id, UpdateRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        return dto(service.update(id, body.name(), body.bluemapUrl()));
    }

    @DELETE
    @Path("/{id}")
    @Requires(Action.MANAGE_SERVERS)
    public Response delete(@PathParam("id") UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }

    /** A new token; the old one stops working at once. */
    @POST
    @Path("/{id}/token/rotate")
    @Requires(Action.MANAGE_SERVERS)
    public CreatedDto rotate(@PathParam("id") UUID id) {
        return created(service.rotateToken(id));
    }

    @PUT
    @Path("/{id}/admins/{userId}")
    @Requires(Action.ASSIGN_SERVER_ADMINS)
    public Response addAdmin(@PathParam("id") UUID id, @PathParam("userId") UUID userId) {
        service.addAdmin(id, userId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}/admins/{userId}")
    @Requires(Action.ASSIGN_SERVER_ADMINS)
    public Response removeAdmin(@PathParam("id") UUID id, @PathParam("userId") UUID userId) {
        service.removeAdmin(id, userId);
        return Response.noContent().build();
    }

    /** Online state and versions: for the site admin and the admins of this server only. */
    @GET
    @Path("/{id}/status")
    @Requires(Action.VIEW_SERVER)
    public StatusDto status(@PathParam("id") UUID id) {
        Set<Role> roles = service.isAdmin(id, current.id()) ? EnumSet.of(Role.SERVER_ADMIN) : EnumSet.noneOf(Role.class);
        // checked before the server is looked up, so a server admin learns nothing about servers that are not theirs
        current.require(Action.VIEW_SERVER, roles, false);
        Server s = service.get(id);
        return new StatusDto(s.id().toString(), s.name(), s.status(), s.lastSeenAt(), s.modVersion(), s.mcVersion());
    }

    private ServerDto dto(Server s) {
        return ServerDto.of(s, service.adminsOf(s.id()));
    }

    private CreatedDto created(ServerService.Created c) {
        return new CreatedDto(dto(c.server()), c.token());
    }
}
