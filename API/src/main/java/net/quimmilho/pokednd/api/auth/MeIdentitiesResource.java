/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /me/identities · POST /me/identities/{provider}/link · DELETE /me/identities/{provider}} (Suggested paths; chapter 18.1
 * names no path for this): the ways to log in, and linking or unlinking Discord from the profile.
 */
@Path("/api/v1/me/identities")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.LOGGED_IN)
public class MeIdentitiesResource {

    public record MethodDto(String provider, Instant since) {
    }

    /** The address to send the browser to. The state cookie is set by this answer, so the browser must be the one that follows the link. */
    public record LinkStart(String url) {
    }

    @Inject
    OAuthService oauth;

    @Inject
    CurrentUser current;

    @Inject
    RefreshCookies cookies;

    @GET
    public List<MethodDto> list() {
        return oauth.loginMethods(current.id()).stream().map(m -> new MethodDto(m.provider(), m.since())).toList();
    }

    @POST
    @Path("/{provider}/link")
    public Response link(@PathParam("provider") String provider, @Context HttpServerRequest http) {
        OAuthService.Start start = oauth.start(provider, current.id(), AuthResource.clientIp(http));
        return Response.ok(new LinkStart(start.url())).cookie(cookies.oauthState(start.state())).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @DELETE
    @Path("/{provider}")
    public Response unlink(@PathParam("provider") String provider) {
        oauth.unlink(current.id(), provider);
        return Response.noContent().build();
    }
}
