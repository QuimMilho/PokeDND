/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Refuses the upgrade to {@code /ws/mod} with 401 unless the {@code Authorization} header holds the current token of a registered server
 * (chapter 14.6). The check reads the database, so it runs on a worker thread.
 */
@ApplicationScoped
public class ModUpgradeCheck implements HttpUpgradeCheck {

    @Inject
    ModProtocol protocol;

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        String header = context.httpRequest().getHeader("Authorization");
        return Uni.createFrom().item(() -> protocol.authenticate(header).isPresent() ? CheckResult.permitUpgradeSync() : CheckResult.rejectUpgradeSync(401))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public boolean appliesTo(String endpointId) {
        return ModSocket.ENDPOINT.equals(endpointId);
    }
}
