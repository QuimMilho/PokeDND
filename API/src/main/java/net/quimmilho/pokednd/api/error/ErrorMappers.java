/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Turns every failure into the one error shape: {@code { "code": "…", "message": "…", "details": {…} }}. */
public class ErrorMappers {

    private static final Logger LOG = Logger.getLogger(ErrorMappers.class);

    @ServerExceptionMapper
    public Response api(ApiException e) {
        return Response.status(e.status()).entity(new ApiError(e.code(), e.getMessage(), e.details())).type(MediaType.APPLICATION_JSON).build();
    }

    @ServerExceptionMapper
    public Response web(WebApplicationException e) {
        Response r = e.getResponse();
        int status = r.getStatus();
        if (r.hasEntity() || (status >= 300 && status < 400)) {
            return r;
        }
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "ERROR";
        };
        String message = e.getMessage() == null || e.getMessage().startsWith("HTTP ") ? "The request could not be handled." : e.getMessage();
        return Response.status(status).entity(new ApiError(code, message, Map.of())).type(MediaType.APPLICATION_JSON).build();
    }

    @ServerExceptionMapper
    public Response unexpected(Exception e) {
        LOG.error("Unexpected error", e);
        return Response.status(500).entity(new ApiError("INTERNAL_ERROR", "Something went wrong on the server.", Map.of()))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
