/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.nio.file.Path;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.error.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** {@code POST /admin/data/reimport}: reloads the data bundle (chapter 18.1, site admin only). */
@jakarta.ws.rs.Path("/api/v1/admin/data")
@Produces(MediaType.APPLICATION_JSON)
@Requires(Action.SITE_ADMINISTRATION)
public class AdminDataResource {

    public record ReimportResult(String result, String hash, long species, long moves, long items, long abilities) {
    }

    @Inject
    BundleLoader loader;

    @ConfigProperty(name = "pokednd.bundle.dir")
    String bundleDir;

    /** Reloads the bundle folder. The hash decides: an unchanged bundle is reported as {@code unchanged}. */
    @POST
    @jakarta.ws.rs.Path("/reimport")
    public ReimportResult reimport() {
        if (!loader.bundleExists()) {
            throw new ApiException(404, "BUNDLE_NOT_FOUND", "There is no data bundle in " + bundleDir + ".");
        }
        BundleLoader.Result r = loader.load(Path.of(bundleDir), false);
        return new ReimportResult(r.outcome().name().toLowerCase(), r.hash(), r.counts().species(), r.counts().moves(), r.counts().items(),
                r.counts().abilities());
    }
}
