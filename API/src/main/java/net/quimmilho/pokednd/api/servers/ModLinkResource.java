/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.servers;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.minecraft.MinecraftLinkService;

/**
 * The mod's two linking endpoints (chapter 18.1, "Mod channel REST helpers"). They are called by the Minecraft mod, so the only way in is
 * the server token; no person's access token opens them.
 *
 * <ul>
 *   <li>{@code POST /mod/link-code}: a player typed {@code /pokednd link}; the answer is the code to type in the app.
 *   <li>{@code POST /mod/link-complete} (<b>Suggested</b>): a player typed an app-made code in Minecraft; the account is linked.
 * </ul>
 */
@Path("/api/v1/mod")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Requires(Action.SERVER_TOKEN)
public class ModLinkResource {

    public record CodeRequest(String mcUuid, String mcName) {
    }

    public record CodeDto(String code, Instant expiresAt) {
    }

    public record CompleteRequest(String code, String mcUuid, String mcName) {
    }

    public record CompletedDto(String userId, String displayName, String mcUuid, String mcName) {
    }

    @Inject
    MinecraftLinkService links;

    @Inject
    AccountRepository accounts;

    @POST
    @Path("/link-code")
    public CodeDto linkCode(CodeRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with the Minecraft id and name.");
        }
        MinecraftLinkService.IssuedCode c = links.codeForMinecraft(parseUuid(body.mcUuid()), body.mcName());
        return new CodeDto(c.code(), c.expiresAt());
    }

    @POST
    @Path("/link-complete")
    public CompletedDto linkComplete(CompleteRequest body) {
        if (body == null || body.code() == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with the code and the Minecraft id and name.");
        }
        MinecraftLinkService.Completed done = links.completeInMinecraft(body.code(), parseUuid(body.mcUuid()), body.mcName());
        String name = accounts.findUser(done.userId()).map(AccountRepository.User::displayName).orElse("");
        return new CompletedDto(done.userId().toString(), name, done.link().mcUuid().toString(), done.link().mcName());
    }

    private static UUID parseUuid(String text) {
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_MINECRAFT_ACCOUNT", "The Minecraft id must be a UUID.");
        }
    }
}
