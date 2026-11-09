/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.cards;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where the card export jobs are kept (chapter 17.2: ExportJob). */
public interface ExportJobRepository {

    String QUEUED = "queued";
    String RUNNING = "running";
    String DONE = "done";
    String FAILED = "failed";
    String EXPIRED = "expired";

    /**
     * One export job.
     *
     * @param request      what was asked, and the cards it resolved to
     * @param progress     cards prepared so far
     * @param total        cards to prepare
     * @param message      why it failed (null otherwise)
     * @param filePath     the PDF, once done and until it expires
     * @param manifestPath the manifest CSV next to it
     * @param pages        pages of the PDF
     * @param expiresAt    when the finished file stops being available
     */
    record ExportJob(UUID id, UUID userId, JsonNode request, String state, int progress, int total, String message, List<String> warnings,
                     String filePath, String manifestPath, Integer pages, Instant expiresAt, Instant createdAt, Instant updatedAt) {

        public ExportJob withState(String newState) {
            return new ExportJob(id, userId, request, newState, progress, total, message, warnings, filePath, manifestPath, pages, expiresAt,
                    createdAt, Instant.now());
        }

        public ExportJob withProgress(int done) {
            return new ExportJob(id, userId, request, state, done, total, message, warnings, filePath, manifestPath, pages, expiresAt, createdAt,
                    Instant.now());
        }

        public ExportJob failed(String why) {
            return new ExportJob(id, userId, request, FAILED, progress, total, why, warnings, null, null, null, null, createdAt, Instant.now());
        }

        public ExportJob done(List<String> warningList, String file, String manifest, int pageCount, Instant expires) {
            return new ExportJob(id, userId, request, DONE, total, total, null, warningList, file, manifest, pageCount, expires, createdAt,
                    Instant.now());
        }

        public ExportJob expired() {
            return new ExportJob(id, userId, request, EXPIRED, progress, total, message, warnings, null, null, pages, expiresAt, createdAt, Instant.now());
        }

        public ExportJob withExpiry(Instant when) {
            return new ExportJob(id, userId, request, state, progress, total, message, warnings, filePath, manifestPath, pages, when, createdAt,
                    Instant.now());
        }
    }

    void create(ExportJob job);

    /** Saves every field of an existing job. */
    void update(ExportJob job);

    Optional<ExportJob> find(UUID id);

    /** A person's jobs, newest first. */
    List<ExportJob> ofUser(UUID userId, int limit);

    /** Finished jobs whose files are past their time. */
    List<ExportJob> doneAndExpiredBefore(Instant now);

    /** Jobs that were queued or running (a restart left them behind). */
    List<ExportJob> unfinished();
}
