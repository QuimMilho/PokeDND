/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.quimmilho.pokednd.api.error.ApiException;

/** {@code GET /me/sessions · DELETE /me/sessions/{id}} (chapter 18.1): see and end the logins of the account. */
@Path("/api/v1/me/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.LOGGED_IN)
public class MeSessionsResource {

    /** {@code current} is true for the session of the access token that made this call. */
    public record SessionDto(String id, String userAgent, Instant createdAt, Instant lastUsedAt, Instant expiresAt, boolean current) {
    }

    @Inject
    SessionService sessions;

    @Inject
    CurrentUser current;

    @GET
    public List<SessionDto> list() {
        UUID mine = current.sessionId();
        return sessions.activeSessions(current.id()).stream().map(s -> new SessionDto(s.id().toString(), s.userAgent(), s.createdAt(),
                s.lastUsedAt(), s.expiresAt(), s.id().equals(mine))).toList();
    }

    @DELETE
    @Path("/{id}")
    public Response end(@PathParam("id") String id) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("This session");
        }
        sessions.end(current.id(), sessionId);
        return Response.noContent().build();
    }
}
