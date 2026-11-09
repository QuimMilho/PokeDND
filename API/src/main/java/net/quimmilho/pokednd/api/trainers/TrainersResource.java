/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.StoredTrainer;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.games.GameService;
import net.quimmilho.pokednd.api.trainers.GameTrainersResource.ChoicesBody;
import net.quimmilho.pokednd.api.trainers.TrainerViews.TrainerDto;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * One trainer and its skin (chapter 18.1): {@code GET/PATCH/DELETE /trainers/{id}} and {@code PUT/DELETE/GET /trainers/{id}/skin}.
 *
 * <p>Each endpoint says its action with {@code @Requires}; the checks that depend on the game are made again in {@link TrainerService}.
 */
@Path("/api/v1/trainers/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class TrainersResource {

    /** The body of a skin copy: {@code {"username": "Steve"}}. */
    public record UsernameBody(String username) {
    }

    @Inject
    TrainerService service;

    @Inject
    TrainerViews views;

    @Inject
    GameService games;

    @ConfigProperty(name = "pokednd.skins.max-bytes", defaultValue = "65536")
    int skinMaxBytes;

    @GET
    @Requires(Action.VIEW_GAME)
    public TrainerDto get(@PathParam("id") UUID id) {
        return dto(service.get(id));
    }

    /** Remakes the trainer from new choices (the wizard again); the skin stays. */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.PLAY_TRAINER)
    public TrainerDto change(@PathParam("id") UUID id, ChoicesBody body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with the trainer's choices.");
        }
        return dto(service.change(id, body.toChoices()));
    }

    @DELETE
    @Requires(Action.PLAY_TRAINER)
    public Response remove(@PathParam("id") UUID id) {
        service.remove(id);
        return Response.noContent().build();
    }

    /** Uploads a PNG: a multipart form with {@code file} and, optionally, {@code model} (classic or slim). */
    @PUT
    @Path("/skin")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Requires(Action.PLAY_TRAINER)
    public TrainerDto upload(@PathParam("id") UUID id, @RestForm("file") FileUpload file, @RestForm("model") String model) {
        if (file == null) {
            throw ApiException.badRequest("BAD_REQUEST", "Send the skin as a form field called file.");
        }
        if (file.size() > skinMaxBytes) {
            throw new ApiException(422, "SKIN_TOO_LARGE", "The skin is " + file.size() + " bytes, but the limit is " + skinMaxBytes + " bytes.");
        }
        try {
            return dto(service.uploadSkin(id, Files.readAllBytes(file.uploadedFile()), model));
        } catch (IOException e) {
            throw new ApiException(422, "SKIN_UNREADABLE", "The skin could not be read.");
        }
    }

    /** Copies the skin of a Minecraft account: {@code {"username": "Steve"}}. */
    @PUT
    @Path("/skin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.PLAY_TRAINER)
    public TrainerDto copy(@PathParam("id") UUID id, UsernameBody body) {
        if (body == null || body.username() == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with a username.");
        }
        return dto(service.copySkin(id, body.username()));
    }

    @DELETE
    @Path("/skin")
    @Requires(Action.PLAY_TRAINER)
    public TrainerDto clearSkin(@PathParam("id") UUID id) {
        return dto(service.clearSkin(id));
    }

    /** The picture itself, for any member of the game. */
    @GET
    @Path("/skin")
    @Produces("image/png")
    @Requires(Action.VIEW_GAME)
    public Response skin(@PathParam("id") UUID id) {
        StoredTrainer t = service.get(id);
        byte[] png = service.skinPng(id);
        return Response.ok(png).type("image/png").header("ETag", "\"" + t.skinSha256() + "\"").header("Cache-Control", "private, max-age=300").build();
    }

    private TrainerDto dto(StoredTrainer t) {
        Game game = games.get(t.gameId());
        return views.dto(game, t);
    }
}
