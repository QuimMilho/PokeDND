/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.servers;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/** When the API starts nobody is connected, so every server is marked offline until its mod says hello again (chapter 14.7). */
public class ServerStartup {

    private static final Logger LOG = Logger.getLogger(ServerStartup.class);

    @Inject
    ServerService servers;

    void onStart(@Observes StartupEvent event) {
        int n = servers.markAllOffline();
        if (n > 0) {
            LOG.infof("Marked %d servers offline at start", n);
        }
    }
}
