/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.quimmilho.pokednd.api.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The permission table of chapter 16.2, written out here a second time, row by row, and checked for every role against every
 * action. If the table in the specification changes, this test and {@link Permissions} change together.
 */
class PermissionsTest {

    private static final Role S = Role.SITE_ADMIN;
    private static final Role V = Role.SERVER_ADMIN;
    private static final Role G = Role.GAME_ADMIN;
    private static final Role P = Role.PLAYER;
    private static final Role W = Role.SPECTATOR;

    /** Action, then which of (site admin, server admin, game admin, player, spectator) may do it when they hold only that role. */
    private static final List<Object[]> TABLE = List.of(
            row(Action.MANAGE_USERS, true, false, false, false, false),
            row(Action.MANAGE_SERVERS, true, false, false, false, false),
            row(Action.ASSIGN_SERVER_ADMINS, true, false, false, false, false),
            row(Action.CREATE_GAME, true, true, false, false, false),
            // "server admin (if game admin)": alone a server admin may not run the game (see the next test)
            row(Action.RUN_GAME, true, false, true, false, false),
            row(Action.EDIT_WORLD, true, true, true, false, false),
            // a game admin plays only if game.admin.plays (see adminPlaysDecidesForTheGameAdmin)
            row(Action.PLAY_TRAINER, false, false, false, true, false),
            row(Action.PLAYER_ACTIONS, false, false, false, true, false),
            row(Action.VIEW_GAME, true, true, true, true, true),
            row(Action.EXPORT_CARDS, true, true, true, true, true),
            // not a row of 16.2, but 18.1 gives GET /servers/{id}/status to the site admin and the admins of that server
            row(Action.VIEW_SERVER, true, true, false, false, false));

    private static Object[] row(Action a, boolean s, boolean v, boolean g, boolean p, boolean w) {
        return new Object[] {a, s, v, g, p, w};
    }

    static Stream<Object[]> everyRoleAndAction() {
        Role[] roles = {S, V, G, P, W};
        return TABLE.stream().flatMap(r -> {
            Stream.Builder<Object[]> b = Stream.builder();
            for (int i = 0; i < roles.length; i++) {
                b.add(new Object[] {r[0], roles[i], r[i + 1]});
            }
            return b.build();
        });
    }

    @ParameterizedTest(name = "{1} on {0}: {2}")
    @MethodSource("everyRoleAndAction")
    void theTableOf16_2(Action action, Role role, boolean allowed) {
        assertEquals(allowed, Permissions.allows(true, EnumSet.of(role), action, false));
    }

    @Test
    void everyRowOfTheTableIsCoveredAndNoActionIsForgotten() {
        Set<Action> inTable = EnumSet.noneOf(Action.class);
        TABLE.forEach(r -> inTable.add((Action) r[0]));
        Set<Action> rest = EnumSet.allOf(Action.class);
        rest.removeAll(inTable);
        assertEquals(EnumSet.of(Action.PUBLIC, Action.LOGGED_IN, Action.SITE_ADMINISTRATION, Action.SERVER_TOKEN), rest);
    }

    @Test
    void aServerTokenEndpointIsNeverOpenedByARole() {
        // AuthFilters checks the token itself; no person, whatever their roles, is let in through Permissions
        assertFalse(Permissions.allows(true, EnumSet.allOf(Role.class), Action.SERVER_TOKEN, true));
    }

    @Test
    void aServerAdminRunsAGameOnlyAsItsGameAdmin() {
        assertFalse(Permissions.allows(true, EnumSet.of(V), Action.RUN_GAME, false));
        assertTrue(Permissions.allows(true, EnumSet.of(V, G), Action.RUN_GAME, false));
    }

    @Test
    void adminPlaysDecidesForTheGameAdmin() {
        assertFalse(Permissions.allows(true, EnumSet.of(G), Action.PLAY_TRAINER, false));
        assertTrue(Permissions.allows(true, EnumSet.of(G), Action.PLAY_TRAINER, true));
        // the setting does not give the right to anybody else
        assertFalse(Permissions.allows(true, EnumSet.of(S), Action.PLAY_TRAINER, true));
        assertFalse(Permissions.allows(true, EnumSet.of(W), Action.PLAY_TRAINER, true));
    }

    @Test
    void aSiteAdminWhoAlsoPlaysCanDoBoth() {
        assertTrue(Permissions.allows(true, EnumSet.of(S, P), Action.PLAYER_ACTIONS, false));
        assertTrue(Permissions.allows(true, EnumSet.of(S, P), Action.MANAGE_USERS, false));
    }

    @Test
    void anonymousPeopleDoNothingExceptPublicThings() {
        for (Action a : Action.values()) {
            assertEquals(a == Action.PUBLIC, Permissions.allows(false, EnumSet.allOf(Role.class), a, true), a.name());
        }
    }

    @Test
    void anyLoggedInAccountMayExportCardsAndSeeItself() {
        assertTrue(Permissions.allows(true, EnumSet.noneOf(Role.class), Action.EXPORT_CARDS, false));
        assertTrue(Permissions.allows(true, EnumSet.noneOf(Role.class), Action.LOGGED_IN, false));
        assertFalse(Permissions.allows(true, EnumSet.noneOf(Role.class), Action.VIEW_GAME, false));
    }

    @Test
    void requireAnswers401ForNobodyAnd403ForTheWrongRole() {
        ApiException none = assertThrows(ApiException.class, () -> Permissions.require(false, Set.of(), Action.EXPORT_CARDS, false));
        assertEquals(401, none.status());
        ApiException wrong = assertThrows(ApiException.class, () -> Permissions.require(true, Set.of(P), Action.MANAGE_USERS, false));
        assertEquals(403, wrong.status());
        Permissions.require(false, Set.of(), Action.PUBLIC, false);
        Permissions.require(true, Set.of(S), Action.MANAGE_USERS, false);
    }
}
