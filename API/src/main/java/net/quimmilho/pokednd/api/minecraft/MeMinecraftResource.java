/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.minecraft;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository.McLink;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * {@code GET /me/minecraft · POST /me/minecraft/link · POST /me/minecraft/link-code · DELETE /me/minecraft/{uuid}} (chapter 18.1;
 * {@code link-code} is Suggested): the Minecraft accounts of the signed-in account.
 */
@Path("/api/v1/me/minecraft")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.LOGGED_IN)
public class MeMinecraftResource {

    public record LinkRequest(String code) {
    }

    public record LinkDto(String mcUuid, String mcName, Instant linkedAt) {
        static LinkDto of(McLink l) {
            return new LinkDto(l.mcUuid().toString(), l.mcName(), l.linkedAt());
        }
    }

    public record CodeDto(String code, Instant expiresAt) {
    }

    @Inject
    MinecraftLinkService service;

    @Inject
    CurrentUser current;

    @GET
    public List<LinkDto> list() {
        return service.linksOf(current.id()).stream().map(LinkDto::of).toList();
    }

    /** The player typed the code that the mod printed. */
    @POST
    @Path("/link")
    @Consumes(MediaType.APPLICATION_JSON)
    public LinkDto link(LinkRequest body) {
        if (body == null || body.code() == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with a code.");
        }
        return LinkDto.of(service.redeemInApp(current.id(), body.code()));
    }

    /** The other direction: the app shows a code and the player types it in Minecraft. */
    @POST
    @Path("/link-code")
    public CodeDto linkCode() {
        MinecraftLinkService.IssuedCode c = service.codeForUser(current.id());
        return new CodeDto(c.code(), c.expiresAt());
    }

    @DELETE
    @Path("/{uuid}")
    public Response unlink(@PathParam("uuid") String uuid) {
        UUID mc;
        try {
            mc = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("This Minecraft link");
        }
        service.unlink(current.id(), mc);
        return Response.noContent().build();
    }
}
