/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Loads the data bundle when the API starts (the manifest hash decides whether anything changes). */
@ApplicationScoped
public class ReferenceStartup {

    private static final Logger LOG = Logger.getLogger(ReferenceStartup.class);

    @Inject
    BundleLoader loader;

    @ConfigProperty(name = "pokednd.reference.load-on-start", defaultValue = "true")
    boolean loadOnStart;

    void onStart(@Observes StartupEvent event) {
        if (!loadOnStart) {
            return;
        }
        if (!loader.bundleExists()) {
            LOG.warn("No data bundle found (pokednd.bundle.dir). The reference-data endpoints will answer 503 until one is loaded. "
                    + "Build it with: node tools/extract.mjs, then Core's ImporterCli.");
            return;
        }
        try {
            loader.load();
        } catch (RuntimeException e) {
            // A broken bundle must not stop the whole API from starting; the endpoints say what is wrong.
            LOG.error("The data bundle could not be loaded: " + e.getMessage(), e);
        }
    }
}
