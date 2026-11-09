/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * Stands in for Mojang in the tests, so that no test needs the network: {@code SteveFan} has a classic skin, {@code AlexFan} a slim one,
 * {@code NoSkinFan} has no custom skin, {@code MojangDown} makes Mojang unreachable, and anyone else does not exist.
 */
@Mock
@ApplicationScoped
public class FakeMojangSkins implements MojangSkins {

    @Override
    public Found find(String username) {
        return switch (username.toLowerCase()) {
            case "stevefan" -> new Found(Pngs.skin(11), false, "SteveFan");
            case "alexfan" -> new Found(Pngs.skin(22), true, "AlexFan");
            case "noskinfan" -> throw new ApiException(404, "MINECRAFT_NO_SKIN", "NoSkinFan has no custom skin.");
            case "mojangdown" -> throw new ApiException(502, "MOJANG_UNAVAILABLE", "Mojang could not be reached.");
            // a skin of the wrong size, as a server that misbehaves would send
            case "weirdfan" -> new Found(Pngs.png(32, 32, 1), false, "WeirdFan");
            default -> throw new ApiException(404, "MINECRAFT_USER_NOT_FOUND", "There is no Minecraft account called " + username + ".");
        };
    }
}
