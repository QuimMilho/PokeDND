/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.core.CoreVersion;

/** Reports which Core library the API is running with. Proves the API really uses Core. */
@Path("/api/core-version")
@Requires(Action.PUBLIC)
public class CoreInfoResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String version() {
        return CoreVersion.get();
    }
}
