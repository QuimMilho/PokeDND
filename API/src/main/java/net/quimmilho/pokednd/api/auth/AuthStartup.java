/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/** At start: creates the first site admin if the site has none (chapter 13.5). */
@ApplicationScoped
public class AuthStartup {

    @Inject
    AuthService auth;

    void onStart(@Observes StartupEvent event) {
        auth.bootstrapAdmin();
    }
}
