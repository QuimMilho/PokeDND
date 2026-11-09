/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.smallrye.config.ConfigMapping;
import java.util.Optional;

/** The Discord application ({@code POKEDND_OAUTH_DISCORD_*}). The three addresses can be changed so a test can point at a stub. */
@ConfigMapping(prefix = "pokednd.oauth.discord")
public interface DiscordConfig {

    /** Empty switches Discord sign-in off. */
    Optional<String> clientId();

    Optional<String> clientSecret();

    String authorizeUrl();

    String tokenUrl();

    String apiUrl();

    default boolean enabled() {
        return clientId().isPresent() && clientSecret().isPresent();
    }
}
