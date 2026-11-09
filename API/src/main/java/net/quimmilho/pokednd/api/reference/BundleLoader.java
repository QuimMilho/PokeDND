/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.LoadedBundle;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.ReferenceBundle;
import net.quimmilho.pokednd.api.error.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Fills the reference tables from the data bundle written by the importer (chapter 19.2). The manifest hash decides: the same
 * hash does nothing, a new hash replaces everything in one transaction. Reading the files is {@link BundleReader}'s job.
 */
@ApplicationScoped
public class BundleLoader {

    private static final Logger LOG = Logger.getLogger(BundleLoader.class);

    /** What a call to {@link #load} did. */
    public enum Outcome { LOADED, UNCHANGED }

    public record Result(Outcome outcome, String hash, ReferenceRepository.Counts counts) {
    }

    @Inject
    ReferenceRepository repository;

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "pokednd.bundle.dir")
    String bundleDir;

    private volatile String currentHash;

    /** The hash of the bundle in the store, or empty when none is loaded (cached; used for ETags). */
    public Optional<String> currentHash() {
        String h = currentHash;
        if (h == null) {
            h = repository.loadedBundle().map(LoadedBundle::hash).orElse(null);
            currentHash = h;
        }
        return Optional.ofNullable(h);
    }

    /** True if the configured folder holds a bundle. */
    public boolean bundleExists() {
        return Files.isRegularFile(Path.of(bundleDir).resolve("MANIFEST.json"));
    }

    /** Loads the configured bundle folder. */
    public Result load() {
        return load(Path.of(bundleDir), false);
    }

    /**
     * Loads a bundle folder.
     *
     * @param force load even when the hash is the one already stored
     * @throws ApiException 500 BUNDLE_INVALID if the folder is not a usable bundle
     */
    public synchronized Result load(Path dir, boolean force) {
        BundleReader reader = new BundleReader(json);
        String hash = reader.hash(dir);
        Optional<LoadedBundle> stored = repository.loadedBundle();
        if (!force && stored.isPresent() && stored.get().hash().equals(hash)) {
            currentHash = hash;
            return new Result(Outcome.UNCHANGED, hash, repository.counts());
        }
        ReferenceBundle bundle = reader.read(dir);
        repository.replaceAll(bundle);
        currentHash = hash;
        ReferenceRepository.Counts counts = repository.counts();
        LOG.infof("Reference data loaded from %s (hash %s): %d species, %d moves, %d items, %d abilities", dir, hash.substring(0, 12),
                counts.species(), counts.moves(), counts.items(), counts.abilities());
        return new Result(Outcome.LOADED, hash, counts);
    }
}
