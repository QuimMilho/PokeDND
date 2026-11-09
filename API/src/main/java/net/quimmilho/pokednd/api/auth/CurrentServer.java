/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.RequestScoped;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository.Server;
import net.quimmilho.pokednd.api.error.ApiException;

/** The Minecraft server making the current request, found by {@link AuthFilters} from its server token. */
@RequestScoped
public class CurrentServer {

    private Server server;

    void set(Server server) {
        this.server = server;
    }

    /**
     * The server the token belongs to.
     *
     * @throws ApiException 401 if the request had no valid server token
     */
    public Server server() {
        if (server == null) {
            throw new ApiException(401, "INVALID_SERVER_TOKEN", "A valid server token is needed.");
        }
        return server;
    }
}
