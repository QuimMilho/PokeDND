/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import java.util.Set;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * The permission table of chapter 16.2 in one place. Every endpoint is checked against it: {@code @Requires} on the method
 * for site-wide actions, and {@link #require} in the code for actions that depend on a server or a game.
 */
public final class Permissions {

    private Permissions() {
    }

    /**
     * May a person with these roles do this?
     *
     * @param authenticated is the person logged in?
     * @param roles         the roles they hold in the server or game the action is about (empty for site-wide actions)
     * @param adminPlays    the game's setting {@code game.admin.plays}: may a game admin own and play a trainer?
     */
    public static boolean allows(boolean authenticated, Set<Role> roles, Action action, boolean adminPlays) {
        return switch (action) {
            case PUBLIC -> true;
            case LOGGED_IN, EXPORT_CARDS -> authenticated;
            case SITE_ADMINISTRATION, MANAGE_USERS, MANAGE_SERVERS, ASSIGN_SERVER_ADMINS -> authenticated && roles.contains(Role.SITE_ADMIN);
            case CREATE_GAME -> authenticated && (roles.contains(Role.SITE_ADMIN) || roles.contains(Role.SERVER_ADMIN));
            // a server admin runs a game only when they are also its game admin
            case RUN_GAME -> authenticated && (roles.contains(Role.SITE_ADMIN) || roles.contains(Role.GAME_ADMIN));
            case EDIT_WORLD -> authenticated && (roles.contains(Role.SITE_ADMIN) || roles.contains(Role.SERVER_ADMIN)
                    || roles.contains(Role.GAME_ADMIN));
            case PLAY_TRAINER -> authenticated && (roles.contains(Role.PLAYER) || (adminPlays && roles.contains(Role.GAME_ADMIN)));
            case PLAYER_ACTIONS -> authenticated && roles.contains(Role.PLAYER);
            case VIEW_GAME -> authenticated && !roles.isEmpty();
            case VIEW_SERVER -> authenticated && (roles.contains(Role.SITE_ADMIN) || roles.contains(Role.SERVER_ADMIN));
            // a server token is not a person: AuthFilters checks it, and no role ever opens such an endpoint
            case SERVER_TOKEN -> false;
        };
    }

    /**
     * Throws 401 when nobody is logged in and 403 when the roles are not enough.
     *
     * @throws ApiException UNAUTHENTICATED or FORBIDDEN
     */
    public static void require(boolean authenticated, Set<Role> roles, Action action, boolean adminPlays) {
        if (action != Action.PUBLIC && !authenticated) {
            throw ApiException.unauthenticated();
        }
        if (!allows(authenticated, roles, action, adminPlays)) {
            throw ApiException.forbidden();
        }
    }
}
