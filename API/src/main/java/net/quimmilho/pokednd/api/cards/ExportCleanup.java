/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** Deletes finished card exports once {@code cards.export.fileTtlHours} have passed (chapter 9.6). */
@ApplicationScoped
public class ExportCleanup {

    private static final Logger LOG = Logger.getLogger(ExportCleanup.class);

    @Inject
    ExportService exports;

    @Scheduled(every = "{pokednd.cards.export.cleanup-every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "card-export-cleanup")
    void cleanup() {
        int n = exports.cleanupExpired();
        if (n > 0) {
            LOG.infof("%d expired card export(s) deleted", n);
        }
    }
}
