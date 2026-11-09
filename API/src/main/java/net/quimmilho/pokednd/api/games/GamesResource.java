/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.games;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.games.GameRepository.GameAdmin;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Membership;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * {@code /games} (chapter 18.1): creating games, moving them through their states, joining with a code, and the people in them.
 *
 * <p>The settings, rule sets, undo and log endpoints of the same table come in later phases. Each endpoint says its action with
 * {@code @Requires}; the actions that depend on the game are checked again, in the game, by {@link GameService}.
 */
@Path("/api/v1/games")
@Produces(MediaType.APPLICATION_JSON)
public class GamesResource {

    /** A person who runs the game. */
    public record AdminDto(String id, String displayName, String role) {
    }

    /**
     * A game. {@code joinCode} is there only for people who run the game, and only while the lobby is open. {@code admins} is left out of
     * the list. {@code myMembership} is the status of the signed-in person's request or membership, when they have one.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GameDto(String id, String serverId, String name, String state, boolean lobbyOpen, String joinCode, String ownerId, List<String> myRoles,
                          String myMembership, List<AdminDto> admins, String ruleSet, Instant createdAt) {
    }

    /** A person in the game or asking to be. */
    public record MemberDto(String id, String userId, String displayName, String role, String status, Instant since) {
    }

    /** The answer to joining: where the request stands. */
    public record JoinedDto(String gameId, String gameName, String role, String status) {
    }

    public record CreateRequest(String serverId, String name, String ruleSetId) {
    }

    public record UpdateRequest(String name) {
    }

    public record JoinRequest(String code, String role) {
    }

    @Inject
    GameService service;

    @Inject
    CurrentUser current;

    // ------------------------------------------------------------------ games

    @GET
    @Requires(Action.LOGGED_IN)
    public List<GameDto> mine() {
        return service.myGames().stream().map(g -> dto(g, false)).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.CREATE_GAME)
    public Response create(CreateRequest body) {
        if (body == null || body.serverId() == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with a serverId and a name.");
        }
        UUID server;
        try {
            server = UUID.fromString(body.serverId());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("This server");
        }
        return Response.status(201).entity(dto(service.create(server, body.name(), body.ruleSetId()), true)).build();
    }

    @GET
    @Path("/{id}")
    @Requires(Action.VIEW_GAME)
    public GameDto get(@PathParam("id") UUID id) {
        return dto(service.get(id), true);
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.RUN_GAME)
    public GameDto update(@PathParam("id") UUID id, UpdateRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        return dto(service.rename(id, body.name()), true);
    }

    /** Archives the game: a draft is discarded, an ended game is put away. It stays in the database but leaves the lists. */
    @DELETE
    @Path("/{id}")
    @Requires(Action.RUN_GAME)
    public GameDto archive(@PathParam("id") UUID id) {
        return dto(service.command(id, GameState.Command.ARCHIVE), true);
    }

    // ------------------------------------------------------------------ the life of a game

    @POST
    @Path("/{id}/lobby/open")
    @Requires(Action.RUN_GAME)
    public GameDto openLobby(@PathParam("id") UUID id) {
        return dto(service.command(id, GameState.Command.OPEN_LOBBY), true);
    }

    @POST
    @Path("/{id}/lobby/close")
    @Requires(Action.RUN_GAME)
    public GameDto closeLobby(@PathParam("id") UUID id) {
        return dto(service.command(id, GameState.Command.CLOSE_LOBBY), true);
    }

    @POST
    @Path("/{id}/start")
    @Requires(Action.RUN_GAME)
    public GameDto start(@PathParam("id") UUID id) {
        return dto(service.command(id, GameState.Command.START), true);
    }

    @POST
    @Path("/{id}/pause")
    @Requires(Action.RUN_GAME)
    public GameDto pause(@PathParam("id") UUID id) {
        return dto(service.command(id, GameState.Command.PAUSE), true);
    }

    @POST
    @Path("/{id}/resume")
    @Requires(Action.RUN_GAME)
    public GameDto resume(@PathParam("id") UUID id) {
        return dto(service.command(id, GameState.Command.RESUME), true);
    }

    @POST
    @Path("/{id}/end")
    @Requires(Action.RUN_GAME)
    public GameDto end(@PathParam("id") UUID id) {
        return dto(service.command(id, GameState.Command.END), true);
    }

    // ------------------------------------------------------------------ joining

    /** Asks to join the game whose lobby shows this code. The admin answers. */
    @POST
    @Path("/join")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.LOGGED_IN)
    public JoinedDto join(JoinRequest body) {
        if (body == null || body.code() == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with a code.");
        }
        GameService.Joined joined = service.join(body.code(), body.role());
        return new JoinedDto(joined.game().id().toString(), joined.game().name(), joined.membership().role(), joined.membership().status());
    }

    @GET
    @Path("/{id}/requests")
    @Requires(Action.RUN_GAME)
    public List<MemberDto> requests(@PathParam("id") UUID id) {
        return service.requests(id).stream().map(this::member).toList();
    }

    @POST
    @Path("/{id}/requests/{rid}/accept")
    @Requires(Action.RUN_GAME)
    public MemberDto accept(@PathParam("id") UUID id, @PathParam("rid") UUID rid) {
        return member(service.accept(id, rid));
    }

    @POST
    @Path("/{id}/requests/{rid}/reject")
    @Requires(Action.RUN_GAME)
    public MemberDto reject(@PathParam("id") UUID id, @PathParam("rid") UUID rid) {
        return member(service.reject(id, rid));
    }

    // ------------------------------------------------------------------ members and admins

    @GET
    @Path("/{id}/members")
    @Requires(Action.VIEW_GAME)
    public List<MemberDto> members(@PathParam("id") UUID id) {
        return service.members(id).stream().map(this::member).toList();
    }

    @DELETE
    @Path("/{id}/members/{userId}")
    @Requires(Action.RUN_GAME)
    public Response kick(@PathParam("id") UUID id, @PathParam("userId") UUID userId) {
        service.kick(id, userId);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/admins/{userId}")
    @Requires(Action.RUN_GAME)
    public Response addCoAdmin(@PathParam("id") UUID id, @PathParam("userId") UUID userId) {
        service.addCoAdmin(id, userId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}/admins/{userId}")
    @Requires(Action.RUN_GAME)
    public Response removeCoAdmin(@PathParam("id") UUID id, @PathParam("userId") UUID userId) {
        service.removeCoAdmin(id, userId);
        return Response.noContent().build();
    }

    // ------------------------------------------------------------------ DTOs

    private GameDto dto(Game g, boolean withAdmins) {
        UUID me = current.id();
        boolean runs = service.runs(g, me);
        List<AdminDto> admins = null;
        if (withAdmins) {
            admins = service.adminsOf(g.id()).stream().map(this::admin).toList();
        }
        String membership = service.membershipOf(g.id(), me).map(Membership::status).orElse(null);
        return new GameDto(g.id().toString(), g.serverId().toString(), g.name(), g.state().id(), g.state().lobbyOpen(), runs ? g.joinCode() : null,
                g.ownerId().toString(), service.roleNames(g, me), membership, admins,
                g.ruleSet() == null ? "builtin:defaults" : g.ruleSet(), g.createdAt());
    }

    private AdminDto admin(GameAdmin a) {
        return new AdminDto(a.userId().toString(), name(a.userId()), a.role());
    }

    private MemberDto member(Membership m) {
        return new MemberDto(m.id().toString(), m.userId().toString(), name(m.userId()), m.role(), m.status(), m.createdAt());
    }

    private String name(UUID userId) {
        Optional<net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User> u = service.user(userId);
        return u.map(net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User::displayName).orElse("");
    }
}
