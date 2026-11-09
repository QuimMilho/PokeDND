/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

/** The roles of chapter 16.2. A person can hold several: a site admin may also play, a server admin may also run a game. */
public enum Role {
    /** Site-wide; comes from the account ({@code User.site_role}). */
    SITE_ADMIN,
    /** For one server (phase 31). */
    SERVER_ADMIN,
    /** For one game: the owner or a co-admin (phase 32). */
    GAME_ADMIN,
    /** A member of a game who plays a trainer (phase 32). */
    PLAYER,
    /** A member of a game who only watches (phase 32). */
    SPECTATOR
}
