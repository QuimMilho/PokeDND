/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

/** {@code GET /auth/oauth/providers · /auth/oauth/{provider}/start · /auth/oauth/{provider}/callback} (chapter 18.1): Discord sign-in. */
@Path("/api/v1/auth/oauth")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.PUBLIC)
public class OAuthResource {

    /** The cookie that ties the {@code state} value to the browser that started the redirect. */
    public static final String STATE_COOKIE = RefreshCookies.STATE_NAME;

    public record Providers(List<String> providers) {
    }

    @Inject
    OAuthService oauth;

    @Inject
    RefreshCookies refreshCookies;

    /** Suggested: which sign-in buttons the login screen shows. */
    @GET
    @Path("/providers")
    public Providers providers() {
        return new Providers(oauth.providers());
    }

    @GET
    @Path("/{provider}/start")
    public Response start(@PathParam("provider") String provider, @Context HttpServerRequest http) {
        OAuthService.Start start = oauth.start(provider, null, AuthResource.clientIp(http));
        return Response.status(302).location(URI.create(start.url())).cookie(refreshCookies.oauthState(start.state())).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GET
    @Path("/{provider}/callback")
    public Response callback(@PathParam("provider") String provider, @QueryParam("code") String code, @QueryParam("state") String state,
            @QueryParam("error") String error, @CookieParam(STATE_COOKIE) String stateCookie, @HeaderParam(HttpHeaders.USER_AGENT) String userAgent,
            @Context HttpServerRequest http) {
        OAuthService.Outcome outcome = oauth.callback(provider, code, state, error, stateCookie, userAgent, AuthResource.clientIp(http));
        Response.ResponseBuilder r = Response.status(302).location(URI.create(outcome.redirectTo())).cookie(refreshCookies.clearOauthState())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (outcome.session() != null) {
            r.cookie(refreshCookies.issue(outcome.session()));
        }
        return r.build();
    }
}
