/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Optional;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * ETags for the reference data (chapter 18.1: "cached with ETags"). The data only changes when a new bundle is loaded, so the
 * ETag is the bundle hash: a browser that sends {@code If-None-Match} with it gets {@code 304 Not Modified}.
 */
public class ReferenceCacheFilters {

    private static final String PREFIX = "/api/v1/data/";

    @Inject
    BundleLoader loader;

    @ServerRequestFilter
    public Optional<Response> notModified(ContainerRequestContext request) {
        if (!applies(request) || !"GET".equals(request.getMethod())) {
            return Optional.empty();
        }
        Optional<String> hash = loader.currentHash();
        String header = request.getHeaderString(HttpHeaders.IF_NONE_MATCH);
        if (hash.isPresent() && header != null && matches(header, etag(hash.get()))) {
            return Optional.of(Response.notModified(etag(hash.get())).header(HttpHeaders.CACHE_CONTROL, "public, max-age=0, must-revalidate").build());
        }
        return Optional.empty();
    }

    @ServerResponseFilter
    public void addEtag(ContainerRequestContext request, ContainerResponseContext response) {
        if (!applies(request) || response.getStatus() != 200) {
            return;
        }
        loader.currentHash().ifPresent(h -> {
            response.getHeaders().putSingle(HttpHeaders.ETAG, etag(h));
            response.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "public, max-age=0, must-revalidate");
        });
    }

    private static boolean applies(ContainerRequestContext request) {
        return request.getUriInfo().getRequestUri().getPath().startsWith(PREFIX);
    }

    private static String etag(String hash) {
        return "\"" + hash + "\"";
    }

    private static boolean matches(String header, String etag) {
        return "*".equals(header.strip()) || Arrays.stream(header.split(",")).map(String::strip).map(v -> v.startsWith("W/") ? v.substring(2) : v)
                .anyMatch(etag::equals);
    }
}
