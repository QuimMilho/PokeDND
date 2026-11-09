/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

/**
 * Everything an endpoint can ask permission for. The rows of the table in chapter 16.2 come first; {@link #PUBLIC},
 * {@link #LOGGED_IN} and {@link #SITE_ADMINISTRATION} are the ones that table does not list.
 */
public enum Action {
    /** No login needed (sign-up, login, reference data). */
    PUBLIC(false),
    /** Any logged-in account. */
    LOGGED_IN(false),
    /** Site administration that 16.2 has no row for (reloading the data bundle, system info). <b>Suggested</b>: site admin only. */
    SITE_ADMINISTRATION(false),

    MANAGE_USERS(false),
    /** Register or delete servers, rotate tokens. */
    MANAGE_SERVERS(false),
    ASSIGN_SERVER_ADMINS(false),
    /** Create games on a server. */
    CREATE_GAME(true),
    /** Run the game, approve, override, undo. */
    RUN_GAME(true),
    /** Edit the world (regions, paths, NPCs). */
    EDIT_WORLD(true),
    /** Own and play a trainer. */
    PLAY_TRAINER(true),
    /** Roll, command, trade and use items (own trainer). */
    PLAYER_ACTIONS(true),
    /** View the map, battles, teams and cards. */
    VIEW_GAME(true),
    /** Export species cards: any logged-in user. */
    EXPORT_CARDS(false),
    /** See a server's status: the site admin and the admins of that server (18.1, {@code GET /servers/{id}/status}). */
    VIEW_SERVER(true),
    /**
     * Called by the Minecraft mod with its server token instead of a person's access token (the {@code server} rows of 18.1). The
     * filter checks the token; no person and no role is involved.
     */
    SERVER_TOKEN(false);

    private final boolean scoped;

    Action(boolean scoped) {
        this.scoped = scoped;
    }

    /** True if the answer depends on a server or a game, so the endpoint's own code must check it (the filter only checks the login). */
    public boolean scoped() {
        return scoped;
    }
}
