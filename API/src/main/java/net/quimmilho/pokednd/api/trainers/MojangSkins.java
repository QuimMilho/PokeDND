/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

/**
 * Looks up the skin of a Minecraft account by its username (chapter 3.6: "copy the skin of any Minecraft account by username"). The
 * real client talks to Mojang ({@link HttpMojangSkins}); the tests replace this interface with a fake, so no test needs the network.
 */
public interface MojangSkins {

    /**
     * A skin found at Mojang.
     *
     * @param png      the bytes of the picture, as Mojang serves it (it is cleaned before it is kept)
     * @param slim     true for the slim (Alex) arms
     * @param username the account's name with the capital letters Mojang gives it
     */
    record Found(byte[] png, boolean slim, String username) {
    }

    /**
     * @throws net.quimmilho.pokednd.api.error.ApiException 404 {@code MINECRAFT_USER_NOT_FOUND} or {@code MINECRAFT_NO_SKIN}; 502
     *                                                      {@code MOJANG_UNAVAILABLE} when Mojang cannot be reached
     */
    Found find(String username);
}
