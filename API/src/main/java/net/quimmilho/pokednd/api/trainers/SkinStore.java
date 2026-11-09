/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Where the skin pictures live: {@code <data dir>/skins/<trainer id>.png}. The database keeps who wears which skin; the file system keeps
 * the pixels, the same on every database flavour. Only pictures cleaned by {@link SkinImages} are written here.
 */
@ApplicationScoped
public class SkinStore {

    @ConfigProperty(name = "pokednd.data.dir")
    String dataDir;

    private Path folder() {
        return Path.of(dataDir).resolve("skins");
    }

    private Path file(UUID trainerId) {
        return folder().resolve(trainerId + ".png");
    }

    /** Saves the picture, replacing the old one. The file appears whole or not at all. */
    public void write(UUID trainerId, byte[] png) {
        try {
            Files.createDirectories(folder());
            Path temp = Files.createTempFile(folder(), trainerId + "-", ".tmp");
            Files.write(temp, png);
            Files.move(temp, file(trainerId), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the skin of trainer " + trainerId, e);
        }
    }

    public Optional<byte[]> read(UUID trainerId) {
        try {
            Path f = file(trainerId);
            return Files.isRegularFile(f) ? Optional.of(Files.readAllBytes(f)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(UUID trainerId) {
        try {
            Files.deleteIfExists(file(trainerId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
