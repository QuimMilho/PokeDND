/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository;
import org.jboss.logging.Logger;

/** Deletes e-mail tokens and linking codes that expired a while ago, so the tables do not grow for ever. */
@ApplicationScoped
public class AccountCleanup {

    private static final Logger LOG = Logger.getLogger(AccountCleanup.class);

    @Inject
    AccountRepository accounts;

    @Inject
    MinecraftLinkRepository links;

    @Scheduled(every = "1h", delayed = "5m")
    void run() {
        Instant before = Instant.now().minus(Duration.ofDays(1));
        int tokens = accounts.deleteEmailTokensExpiredBefore(before);
        int codes = links.deleteCodesExpiredBefore(before);
        if (tokens + codes > 0) {
            LOG.debugf("Removed %d old e-mail tokens and %d old linking codes", tokens, codes);
        }
    }
}
