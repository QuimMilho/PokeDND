/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.EntryKind;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.NamedRecord;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.Page;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesFilter;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * The read-only reference data (chapter 18.1): species, moves, items, abilities, types, natures, learnsets and the roster.
 * Lists use {@code limit} and {@code cursor} and answer {@code { "items": [...], "next": "…" }}.
 * Answers carry an ETag (the bundle hash), see {@link ReferenceCacheFilters}.
 */
@Path("/api/v1/data")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.PUBLIC)
public class ReferenceResource {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    @Inject
    ReferenceRepository repository;

    @Inject
    BundleLoader loader;

    @Inject
    ObjectMapper json;

    @GET
    @Path("/roster")
    public JsonNode roster() {
        return document("roster");
    }

    @GET
    @Path("/types")
    public JsonNode types() {
        return document("typechart");
    }

    @GET
    @Path("/natures")
    public JsonNode natures() {
        return document("natures");
    }

    @GET
    @Path("/species")
    public ObjectNode species(@QueryParam("generation") Set<Integer> generations, @QueryParam("type") Set<String> types,
                              @QueryParam("primaryTypeOnly") @DefaultValue("false") boolean primaryTypeOnly, @QueryParam("q") String q,
                              @QueryParam("playable") Boolean playable, @QueryParam("limit") Integer limit,
                              @QueryParam("cursor") String cursor) {
        requireData();
        for (int g : generations) {
            if (g < 1 || g > 9) {
                throw ApiException.badRequest("BAD_GENERATION", "The generation must be 1 to 9, not " + g + ".");
            }
        }
        Set<String> lower = new HashSet<>();
        types.forEach(t -> lower.add(t.toLowerCase(Locale.ROOT)));
        Page<SpeciesRecord> page = page(() -> repository.findSpecies(new SpeciesFilter(generations, lower, primaryTypeOnly, q, playable),
                cursor, limit(limit)));
        ArrayNode items = json.createArrayNode();
        page.items().forEach(s -> items.add(summary(s)));
        return list(items, page.next());
    }

    @GET
    @Path("/species/{id}")
    public ObjectNode oneSpecies(@PathParam("id") String id) {
        requireData();
        SpeciesRecord s = repository.species(id.toLowerCase(Locale.ROOT)).orElseThrow(() -> ApiException.notFound("The species \"" + id + "\""));
        ObjectNode out = s.data().deepCopy();
        out.put("generation", s.generation());
        if (s.derived() != null) {
            out.set("derived", s.derived());
        }
        out.set("evolutions", s.evolutions() == null ? json.createArrayNode() : s.evolutions());
        return out;
    }

    @GET
    @Path("/learnsets/{species}")
    public ObjectNode learnsets(@PathParam("species") String species, @QueryParam("form") String form, @QueryParam("method") String method) {
        requireData();
        SpeciesRecord s = repository.species(species.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("The species \"" + species + "\""));
        ObjectNode forms = json.createObjectNode();
        if (s.learnsets() != null) {
            Iterator<Map.Entry<String, JsonNode>> it = s.learnsets().fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (form != null && !form.isBlank() && !e.getKey().equals(form)) {
                    continue;
                }
                JsonNode byMethod = e.getValue();
                if (method != null && !method.isBlank()) {
                    ObjectNode only = json.createObjectNode();
                    if (byMethod.has(method)) {
                        only.set(method, byMethod.get(method));
                    }
                    byMethod = only;
                }
                forms.set(e.getKey(), byMethod);
            }
        }
        if (form != null && !form.isBlank() && forms.isEmpty()) {
            throw ApiException.notFound("The form \"" + form + "\" of " + s.id());
        }
        ObjectNode out = json.createObjectNode();
        out.put("species", s.id());
        out.set("forms", forms);
        return out;
    }

    @GET
    @Path("/moves")
    public ObjectNode moves(@QueryParam("type") String type, @QueryParam("q") String q, @QueryParam("limit") Integer limit,
                            @QueryParam("cursor") String cursor) {
        return entries(EntryKind.MOVE, type, q, limit, cursor);
    }

    @GET
    @Path("/moves/{id}")
    public JsonNode move(@PathParam("id") String id) {
        return entry(EntryKind.MOVE, id);
    }

    @GET
    @Path("/items")
    public ObjectNode items(@QueryParam("category") String category, @QueryParam("q") String q, @QueryParam("limit") Integer limit,
                            @QueryParam("cursor") String cursor) {
        return entries(EntryKind.ITEM, category, q, limit, cursor);
    }

    @GET
    @Path("/items/{id}")
    public JsonNode item(@PathParam("id") String id) {
        return entry(EntryKind.ITEM, id);
    }

    @GET
    @Path("/abilities")
    public ObjectNode abilities(@QueryParam("q") String q, @QueryParam("limit") Integer limit, @QueryParam("cursor") String cursor) {
        return entries(EntryKind.ABILITY, null, q, limit, cursor);
    }

    @GET
    @Path("/abilities/{id}")
    public JsonNode ability(@PathParam("id") String id) {
        return entry(EntryKind.ABILITY, id);
    }

    // ------------------------------------------------------------------ helpers

    private ObjectNode entries(EntryKind kind, String group, String q, Integer limit, String cursor) {
        requireData();
        Page<NamedRecord> page = page(() -> repository.findEntries(kind, group, q, cursor, limit(limit)));
        ArrayNode items = json.createArrayNode();
        page.items().forEach(n -> items.add(n.data()));
        return list(items, page.next());
    }

    private JsonNode entry(EntryKind kind, String id) {
        requireData();
        return repository.entry(kind, id.toLowerCase(Locale.ROOT)).map(NamedRecord::data)
                .orElseThrow(() -> ApiException.notFound("The " + kind.id() + " \"" + id + "\""));
    }

    private JsonNode document(String name) {
        requireData();
        return repository.document(name).orElseThrow(() -> noData());
    }

    /** A short version of a species for lists. */
    private ObjectNode summary(SpeciesRecord s) {
        ObjectNode o = json.createObjectNode();
        JsonNode d = s.data();
        o.put("dex", s.dex());
        o.put("id", s.id());
        o.put("name", s.name());
        o.put("generation", s.generation());
        o.set("types", d.path("types"));
        o.set("baseStats", d.path("baseStats"));
        o.put("playable", s.playable());
        if (s.derived() != null && s.derived().has("stage")) {
            o.set("stage", s.derived().get("stage"));
        }
        ArrayNode forms = json.createArrayNode();
        for (JsonNode f : d.path("forms")) {
            ObjectNode fo = json.createObjectNode();
            fo.set("id", f.path("id"));
            fo.set("slug", f.path("slug"));
            fo.set("kind", f.path("kind"));
            fo.set("playable", f.path("playable"));
            forms.add(fo);
        }
        o.set("forms", forms);
        return o;
    }

    private ObjectNode list(ArrayNode items, String next) {
        ObjectNode out = json.createObjectNode();
        out.set("items", items);
        if (next == null) {
            out.putNull("next");
        } else {
            out.put("next", next);
        }
        return out;
    }

    private static int limit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiException.badRequest("BAD_LIMIT", "The limit must be 1 to " + MAX_LIMIT + ".");
        }
        return limit;
    }

    private static <T> T page(java.util.function.Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException || e.getCause() instanceof NumberFormatException) {
                throw ApiException.badRequest("BAD_CURSOR", "The cursor is not valid.");
            }
            throw e;
        }
    }

    private void requireData() {
        if (loader.currentHash().isEmpty()) {
            throw noData();
        }
    }

    private static ApiException noData() {
        return new ApiException(503, "REFERENCE_DATA_MISSING", "The reference data is not loaded yet. Ask the administrator to load the data bundle.");
    }
}
