/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;
import java.nio.file.Files;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.Requires;
import net.quimmilho.pokednd.api.error.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * {@code GET /api/v1/art/{folder}/{file}}: the card art of a species, so that the app can show a card preview (chapter 9.9).
 * <b>Suggested</b> (Appendix D): the high-resolution card art when it exists, otherwise the 128 px icon. The folder and file names are
 * those of {@code resources/card-art}, for example {@code 0105_marowak/marowak_alolan.png}. {@code ?size=icon} gives the small icon.
 */
@Path("/api/v1/art")
@Requires(Action.PUBLIC)
public class ArtResource {

    private static final Pattern FOLDER = Pattern.compile("^\\d{4}_[a-z0-9_.\\-]+$");
    private static final Pattern FILE = Pattern.compile("^[a-z0-9_.\\-]+\\.png$");

    @ConfigProperty(name = "pokednd.resources.dir")
    String resourcesDir;

    @GET
    @Path("/{folder}/{file}")
    @Produces("image/png")
    public Response art(@PathParam("folder") String folder, @PathParam("file") String file, @QueryParam("size") @DefaultValue("card") String size) {
        if (!FOLDER.matcher(folder).matches() || !FILE.matcher(file).matches() || folder.contains("..") || file.contains("..")) {
            throw ApiException.notFound("The picture");
        }
        java.nio.file.Path root = java.nio.file.Path.of(resourcesDir);
        // ?size=icon asks for the small 128 px picture (lists show many at once); otherwise the card art comes first
        String[] kinds = "icon".equals(size) ? new String[] {"icons"} : new String[] {"card-art", "icons"};
        for (String kind : kinds) {
            java.nio.file.Path candidate = root.resolve(kind).resolve(folder).resolve(file).normalize();
            if (candidate.startsWith(root.resolve(kind).normalize()) && Files.isRegularFile(candidate)) {
                CacheControl cache = new CacheControl();
                cache.setMaxAge(86400);
                cache.setPrivate(false);
                return Response.ok(candidate.toFile()).cacheControl(cache).build();
            }
        }
        throw ApiException.notFound("The picture");
    }
}
