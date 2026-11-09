/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.quimmilho.pokednd.api.error.ApiException;

/** {@code POST /auth/verify-email · /auth/resend-verification · /auth/forgot · /auth/reset} (chapter 18.1). */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Requires(Action.PUBLIC)
public class EmailAuthResource {

    public record TokenRequest(String token) {
    }

    public record EmailRequest(String email) {
    }

    public record ResetRequest(String token, String password) {
    }

    @Inject
    EmailFlowService flows;

    @POST
    @Path("/verify-email")
    public Response verifyEmail(TokenRequest body, @Context HttpServerRequest http) {
        flows.verifyEmail(required(body).token(), AuthResource.clientIp(http));
        return Response.noContent().build();
    }

    /** Suggested: a person who cannot log in until the address is verified needs a way to get the mail again. */
    @POST
    @Path("/resend-verification")
    public Response resendVerification(EmailRequest body, @Context HttpServerRequest http) {
        flows.resendVerification(required(body).email(), AuthResource.clientIp(http));
        return Response.noContent().build();
    }

    @POST
    @Path("/forgot")
    public Response forgot(EmailRequest body, @Context HttpServerRequest http) {
        flows.forgotPassword(required(body).email(), AuthResource.clientIp(http));
        return Response.noContent().build();
    }

    @POST
    @Path("/reset")
    public Response reset(ResetRequest body, @Context HttpServerRequest http) {
        flows.resetPassword(required(body).token(), body.password(), AuthResource.clientIp(http));
        return Response.noContent().build();
    }

    private static <T> T required(T body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        return body;
    }
}
