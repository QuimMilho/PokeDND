/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.error.ApiException;

/** {@code /rulesets} and {@code /settings/registry} (chapters 12.3 and 18.1): saved rule sets, sharing them as files, and the list of settings. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class RuleSetsResource {

    /** What a person sends to make or change a rule set (all fields but the name are optional). */
    public record RuleSetRequest(String name, String scope, UUID serverId, String basedOn, JsonNode settings, JsonNode data, UUID fromGameId) {
    }

    /** One setting as Appendix A describes it, for the settings screen. */
    public record SettingInfoDto(String key, String group, String type, JsonNode defaultValue, int chapter, String description, List<String> allowed, Double min,
                                 Double max) {
    }

    @Inject
    RuleSetService service;

    @Inject
    SettingsRegistry registry;

    /** Every setting with its default and what it accepts (the whole of Appendix A). */
    @GET
    @Path("/settings/registry")
    @Requires(Action.LOGGED_IN)
    public List<SettingInfoDto> settingsRegistry() {
        return registry.all().stream().map(d -> new SettingInfoDto(d.key(), d.group(), d.type().id(), d.defaultValue(), d.chapter(), d.description(), d.allowed(),
                d.min(), d.max())).toList();
    }

    /** The rule sets the signed-in person may use: built-in, the site's, those of their servers, and their own. */
    @GET
    @Path("/rulesets")
    @Requires(Action.LOGGED_IN)
    public List<RuleSetService.View> list() {
        return service.list();
    }

    @POST
    @Path("/rulesets")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.LOGGED_IN)
    public Response create(RuleSetRequest body) {
        return Response.status(201).entity(service.create(input(body))).build();
    }

    @GET
    @Path("/rulesets/{id}")
    @Requires(Action.LOGGED_IN)
    public RuleSetService.View get(@PathParam("id") String id) {
        return service.get(id);
    }

    /** Changes a saved rule set; what the request leaves out stays as it was. */
    @PUT
    @Path("/rulesets/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.LOGGED_IN)
    public RuleSetService.View update(@PathParam("id") String id, RuleSetRequest body) {
        return service.update(id, input(body));
    }

    @DELETE
    @Path("/rulesets/{id}")
    @Requires(Action.LOGGED_IN)
    public Response delete(@PathParam("id") String id) {
        service.delete(id);
        return Response.noContent().build();
    }

    /** Saves a rule set file (the JSON of chapter 12.3) as a rule set of the signed-in person. */
    @POST
    @Path("/rulesets/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Requires(Action.LOGGED_IN)
    public Response importFile(JsonNode file) {
        return Response.status(201).entity(service.importFile(file)).build();
    }

    /** The rule set as a JSON file to save and share. */
    @GET
    @Path("/rulesets/{id}/export")
    @Requires(Action.LOGGED_IN)
    public Response export(@PathParam("id") String id) {
        ObjectNode file = service.export(id);
        String name = file.get("name").asText().replaceAll("[^A-Za-z0-9._-]+", "_");
        return Response.ok(file).header("Content-Disposition", "attachment; filename=\"" + (name.isBlank() ? "ruleset" : name) + ".json\"").build();
    }

    private static RuleSetService.Input input(RuleSetRequest body) {
        if (body == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        return new RuleSetService.Input(body.name(), body.scope(), body.serverId(), body.basedOn(), body.settings(), body.data(), body.fromGameId());
    }
}
