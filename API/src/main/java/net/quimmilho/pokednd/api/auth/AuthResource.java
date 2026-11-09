/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User;
import net.quimmilho.pokednd.api.error.ApiException;

/** {@code POST /auth/register · /auth/login · /auth/refresh · /auth/logout} (chapter 18.1). */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.PUBLIC)
public class AuthResource {

    /** Name of the refresh cookie. */
    public static final String REFRESH_COOKIE = RefreshCookies.NAME;

    public record RegisterRequest(String email, String password, String displayName, String locale) {
    }

    public record LoginRequest(String email, String password) {
    }

    /** What the app gets from login and refresh. The refresh token is only in the HTTP-only cookie. */
    public record TokenResponse(String accessToken, String tokenType, int expiresIn, UserDto user) {
    }

    public record UserDto(String id, String email, boolean emailVerified, String displayName, String locale, String theme, String siteRole) {
        public static UserDto of(User u) {
            return new UserDto(u.id().toString(), u.email(), u.emailVerified(), u.displayName(), u.locale(), u.theme(), u.siteRole());
        }
    }

    @Inject
    AuthService auth;

    @Inject
    RefreshCookies cookies;

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(RegisterRequest body, @Context HttpServerRequest http) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        User user = auth.register(body.email(), body.password(), body.displayName(), body.locale(), clientIp(http));
        return Response.status(201).entity(UserDto.of(user)).build();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(LoginRequest body, @Context HttpServerRequest http, @HeaderParam(HttpHeaders.USER_AGENT) String userAgent) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        return answer(auth.login(body.email(), body.password(), clientIp(http), userAgent));
    }

    @POST
    @Path("/refresh")
    public Response refresh(@CookieParam(REFRESH_COOKIE) String refreshToken, @HeaderParam(HttpHeaders.USER_AGENT) String userAgent) {
        try {
            return answer(auth.refresh(refreshToken, userAgent));
        } catch (ApiException e) {
            // a refresh token that is not good any more must not stay in the browser
            throw new ApiExceptionWithCookie(e, cookies.clear());
        }
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(REFRESH_COOKIE) String refreshToken) {
        auth.logout(refreshToken);
        return Response.noContent().cookie(cookies.clear()).build();
    }

    // ------------------------------------------------------------------ helpers

    private Response answer(AuthService.Session s) {
        TokenResponse body = new TokenResponse(s.accessToken(), "Bearer", s.expiresInSeconds(), UserDto.of(s.user()));
        return Response.ok(body).cookie(cookies.issue(s)).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    static String clientIp(HttpServerRequest http) {
        return http.remoteAddress() == null ? "unknown" : http.remoteAddress().host();
    }

    /** An API error that also clears the refresh cookie. */
    static final class ApiExceptionWithCookie extends jakarta.ws.rs.WebApplicationException {
        ApiExceptionWithCookie(ApiException e, NewCookie clear) {
            super(Response.status(e.status()).entity(new net.quimmilho.pokednd.api.error.ApiError(e.code(), e.getMessage(), e.details()))
                    .type(MediaType.APPLICATION_JSON).cookie(clear).build());
        }
    }
}
