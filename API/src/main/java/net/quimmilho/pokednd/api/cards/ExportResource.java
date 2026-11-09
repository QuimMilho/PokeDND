/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository.ExportJob;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * Card exports (chapter 18.1): {@code POST /cards/exports} starts a job, {@code GET /cards/exports}, {@code GET /cards/exports/{id}}
 * show jobs and progress, {@code /download} gives the PDF and {@code /manifest} the CSV of where every card is. Any logged-in person may
 * export species cards (chapter 16.2); each sees only their own jobs.
 */
@Path("/api/v1/cards/exports")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.EXPORT_CARDS)
public class ExportResource {

    /** What the app shows about a job. */
    public record JobDto(String id, String state, int progress, int total, String message, List<String> warnings, Integer pages, Instant createdAt,
                         Instant expiresAt, String downloadUrl, String manifestUrl, JsonNode request, JsonNode options) {

        static JobDto of(ExportJob j) {
            boolean done = ExportJobRepository.DONE.equals(j.state());
            String base = "/api/v1/cards/exports/" + j.id();
            return new JobDto(j.id().toString(), j.state(), j.progress(), j.total(), j.message(), j.warnings(), j.pages(), j.createdAt(), j.expiresAt(),
                    done ? base + "/download" : null, done ? base + "/manifest" : null, j.request().path("request"), j.request().path("options"));
        }
    }

    @Inject
    ExportService exports;

    @Inject
    CurrentUser current;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(ExportRequest request) {
        if (request == null) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a JSON body.");
        }
        ExportJob job = exports.start(current.id(), request);
        return Response.accepted(JobDto.of(job)).location(URI.create("/api/v1/cards/exports/" + job.id())).build();
    }

    @GET
    public List<JobDto> list(@QueryParam("limit") @DefaultValue("25") int limit) {
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("BAD_LIMIT", "The limit must be 1 to 100.");
        }
        return exports.list(current.id(), limit).stream().map(JobDto::of).toList();
    }

    @GET
    @Path("/{id}")
    public JobDto one(@PathParam("id") String id) {
        return JobDto.of(exports.get(current.id(), uuid(id)));
    }

    @GET
    @Path("/{id}/download")
    @Produces("application/pdf")
    public Response download(@PathParam("id") String id) {
        java.nio.file.Path file = exports.file(current.id(), uuid(id), false);
        return Response.ok(file.toFile()).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pokednd-cards-" + id.substring(0, 8) + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }

    @GET
    @Path("/{id}/manifest")
    @Produces("text/csv")
    public Response manifest(@PathParam("id") String id) {
        java.nio.file.Path file = exports.file(current.id(), uuid(id), true);
        return Response.ok(file.toFile()).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pokednd-cards-" + id.substring(0, 8) + "-manifest.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }

    private static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("The export");
        }
    }
}
