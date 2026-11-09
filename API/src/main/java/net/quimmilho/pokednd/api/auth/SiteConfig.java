/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The public address of the site ({@code POKEDND_BASE_URL}), used in e-mail links, OAuth callbacks and redirects. */
@ApplicationScoped
public class SiteConfig {

    @ConfigProperty(name = "pokednd.base-url")
    String baseUrl;

    /** The public URL of the app without a trailing slash, e.g. {@code https://pokednd.example.org}. */
    public String baseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
