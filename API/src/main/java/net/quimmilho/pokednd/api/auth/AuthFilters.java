/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.HttpHeaders;
import java.lang.reflect.Method;
import java.util.Optional;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository.Server;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.servers.ServerService;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Reads the access token of every request and applies the endpoint's {@link Requires}.
 *
 * <p>A request without a token is anonymous. A request with a bad or expired token is refused at once with 401, so the app
 * knows to refresh. A method with no {@code @Requires} is refused with 500, never served: the safe way to fail.
 */
public class AuthFilters {

    @Inject
    TokenService tokens;

    @Inject
    CurrentUser current;

    @Inject
    CurrentServer currentServer;

    @Inject
    ServerService servers;

    @ServerRequestFilter(priority = Priorities.AUTHENTICATION)
    public void authenticate(ContainerRequestContext request, ResourceInfo resource) {
        Requires requires = requirement(resource);
        if (requires == null) {
            throw new ApiException(500, "ENDPOINT_WITHOUT_PERMISSION", "This endpoint has no permission rule, so it is closed.");
        }
        Action action = requires.value();
        String header = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (action == Action.SERVER_TOKEN) {
            authenticateServer(header);
            return;
        }
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Optional<TokenService.Claims> claims = tokens.verify(header.substring(7).strip());
            if (claims.isPresent()) {
                current.set(claims.get().userId(), claims.get().siteRole(), claims.get().sessionId());
            } else if (action != Action.PUBLIC) {
                throw new ApiException(401, "INVALID_TOKEN", "The access token is invalid or has expired.");
            }
        }
        if (action.scoped()) {
            // the endpoint checks the role in its server or game; the filter only needs a login
            if (!current.authenticated()) {
                throw ApiException.unauthenticated();
            }
        } else {
            Permissions.require(current.authenticated(), current.siteRoles(), action, false);
        }
    }

    /** The mod's endpoints: the Bearer value must be the token of a registered server. A missing, wrong or replaced token is refused. */
    private void authenticateServer(String header) {
        Optional<Server> server = Optional.empty();
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            server = servers.authenticate(header.substring(7).strip());
        }
        currentServer.set(server.orElseThrow(() -> new ApiException(401, "INVALID_SERVER_TOKEN", "The server token is missing, wrong or no longer valid.")));
    }

    /** The {@code @Requires} of a method, or of its class. */
    public static Requires requirement(ResourceInfo resource) {
        return requirement(resource.getResourceMethod());
    }

    public static Requires requirement(Method method) {
        if (method == null) {
            return null;
        }
        return Optional.ofNullable(method.getAnnotation(Requires.class)).orElseGet(() -> method.getDeclaringClass().getAnnotation(Requires.class));
    }
}
