/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.StoredTrainer;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.trainers.TrainerViews.OptionsDto;
import net.quimmilho.pokednd.api.trainers.TrainerViews.TrainerDto;
import net.quimmilho.pokednd.core.trainer.TrainerClass;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;

/**
 * The trainer endpoints that belong to a game (chapter 18.1, "Trainers and parties"): {@code GET /games/{id}/classes},
 * {@code GET/POST /games/{id}/trainers}, and {@code GET /games/{id}/trainer-options} (<em>Suggested</em>, Appendix D #82) with everything the
 * wizard of the app needs to draw its steps. The endpoints of one trainer are in {@link TrainersResource}.
 */
@Path("/api/v1/games/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class GameTrainersResource {

    /** What the wizard sends (see {@link TrainerService.Choices}). */
    public record ChoicesBody(String name, String classId, String method, Map<String, Integer> scores, List<String> skills,
                              Map<String, Integer> improvements, String starter) {
        TrainerService.Choices toChoices() {
            return new TrainerService.Choices(name, classId, method, scores, skills, improvements, starter);
        }
    }

    @Inject
    TrainerService service;

    @Inject
    TrainerRules rules;

    @Inject
    TrainerViews views;

    @Inject
    CurrentUser current;

    /** The classes of the game, with their features. */
    @GET
    @Path("/classes")
    @Requires(Action.VIEW_GAME)
    public List<TrainerClass> classes(@PathParam("id") UUID gameId) {
        Game game = service.gameForMember(gameId);
        return rules.rulebook(game).classes().classes();
    }

    @GET
    @Path("/trainer-options")
    @Requires(Action.VIEW_GAME)
    public OptionsDto options(@PathParam("id") UUID gameId) {
        Game game = service.gameForMember(gameId);
        boolean has = service.ofGame(gameId).stream().anyMatch(t -> t.userId().equals(current.id()));
        return views.options(game, has);
    }

    @GET
    @Path("/trainers")
    @Requires(Action.VIEW_GAME)
    public List<TrainerDto> list(@PathParam("id") UUID gameId) {
        Game game = service.gameForMember(gameId);
        TrainerRulebook rulebook = rules.rulebook(game);
        return service.ofGame(gameId).stream().map(t -> views.dto(t, rulebook)).toList();
    }

    @POST
    @Path("/trainers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.PLAY_TRAINER)
    public Response create(@PathParam("id") UUID gameId, ChoicesBody body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with the trainer's choices.");
        }
        StoredTrainer t = service.create(gameId, body.toChoices());
        return Response.status(201).entity(views.dto(service.gameForMember(gameId), t)).build();
    }
}
