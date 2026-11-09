/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.games.GameService;
import net.quimmilho.pokednd.api.settings.SettingsLayers.Resolved;

/**
 * The settings of a game (chapters 12.1 to 12.3 and 18.1): the value of every key and where it came from, changing, resetting, loading
 * a rule set or a preset, and taking the last change back.
 */
@Path("/api/v1/games/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class GameSettingsResource {

    /** A rule set, as far as the settings screen needs it. */
    public record RuleSetRef(String id, String name) {
    }

    /**
     * A setting with its value in this game. {@code source} is {@code default}, {@code ruleSet} or {@code game}; {@code overridden} is true
     * unless it is the default; {@code defaultValue} is what it would be without a rule set and without the game's own change.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SettingValueDto(String key, String group, String type, JsonNode value, JsonNode defaultValue, String source, boolean overridden, int chapter,
                                  String description, List<String> allowed, Double min, Double max) {
        static SettingValueDto of(Resolved r) {
            SettingDef d = r.def();
            return new SettingValueDto(d.key(), d.group(), d.type().id(), r.value(), d.defaultValue(), r.source(), r.overridden(), d.chapter(), d.description(),
                    d.allowed(), d.min(), d.max());
        }
    }

    public record SettingsDto(String gameId, RuleSetRef ruleSet, List<SettingValueDto> settings) {
    }

    /** The settings that a call changed, with their values now. */
    public record ChangedDto(List<SettingValueDto> changed) {
    }

    public record ChangeRequest(Map<String, JsonNode> settings) {
    }

    public record ResetRequest(List<String> keys, Boolean all) {
    }

    public record ApplyRequest(String ruleSetId, Boolean dryRun, Boolean resetOverrides) {
    }

    public record ChangeDto(String key, JsonNode from, JsonNode to, String fromSource, String toSource) {
    }

    public record AppliedDto(RuleSetRef ruleSet, boolean applied, List<ChangeDto> changes) {
    }

    public record PresetDto(String id, String name, String description, Map<String, JsonNode> settings) {
    }

    public record PresetsDto(boolean enabled, List<PresetDto> presets) {
    }

    @Inject
    SettingsService service;

    @Inject
    BuiltinRuleSets builtins;

    @Inject
    GameService games;

    @GET
    @Path("/settings")
    @Requires(Action.VIEW_GAME)
    public SettingsDto get(@PathParam("id") UUID id) {
        SettingsService.View v = service.view(id);
        return new SettingsDto(id.toString(), new RuleSetRef(v.ruleSetId(), v.ruleSetName()), v.values().values().stream().map(SettingValueDto::of).toList());
    }

    /** Changes the game's own value of some settings: {@code {"settings": {"combat.ai.level": "hard", "dice.style": null}}}; null removes the game's value. */
    @PATCH
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.RUN_GAME)
    public ChangedDto change(@PathParam("id") UUID id, ChangeRequest body) {
        if (body == null || body.settings() == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with a \"settings\" object.");
        }
        return changed(service.change(id, body.settings()));
    }

    @POST
    @Path("/settings/reset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.RUN_GAME)
    public ChangedDto reset(@PathParam("id") UUID id, ResetRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with \"keys\" or \"all\": true.");
        }
        return changed(service.reset(id, body.keys(), Boolean.TRUE.equals(body.all())));
    }

    @POST
    @Path("/settings/undo")
    @Requires(Action.RUN_GAME)
    public ChangedDto undo(@PathParam("id") UUID id) {
        return changed(service.undo(id));
    }

    /** The presets of chapter 12.1. They are built-in rule sets; load one with {@code POST …/rulesets/apply}. */
    @GET
    @Path("/presets")
    @Requires(Action.RUN_GAME)
    public PresetsDto presets(@PathParam("id") UUID id) {
        Game game = games.authorize(id, Action.RUN_GAME);
        boolean enabled = service.bool(game, "game.presets.enabled");
        return new PresetsDto(enabled, builtins.all().stream().map(b -> new PresetDto(b.id(), b.name(), b.description(), b.settings())).toList());
    }

    /** Loads a rule set or a preset into the game. With {@code "dryRun": true} it only lists what would change. */
    @POST
    @Path("/rulesets/apply")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.RUN_GAME)
    public AppliedDto apply(@PathParam("id") UUID id, ApplyRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body with a ruleSetId.");
        }
        SettingsService.Applied a = service.applyRuleSet(id, body.ruleSetId(), Boolean.TRUE.equals(body.dryRun()), Boolean.TRUE.equals(body.resetOverrides()));
        return new AppliedDto(new RuleSetRef(a.ruleSetId(), a.ruleSetName()), a.applied(),
                a.changes().stream().map(c -> new ChangeDto(c.key(), c.from(), c.to(), c.fromSource(), c.toSource())).toList());
    }

    private static ChangedDto changed(List<Resolved> list) {
        return new ChangedDto(list.stream().map(SettingValueDto::of).toList());
    }
}
