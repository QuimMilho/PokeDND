/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ProgressFixtures.MOVES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.RULES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.quimmilho.pokednd.core.game.ProgressFixtures.Scene;
import org.junit.jupiter.api.Test;

/**
 * Phase 49: friendship as documented (chapter 5.8): +5 below 100, +4 from 100, +3 from 200 for each level; +1 for every 10 movement
 * points travelled with the Pokémon as lead; −1 for fainting; at most 255, at least 0. (The level-up and faint cases with a whole battle
 * are in {@link LevelUpTest} and {@link FaintingHealingTest}.)
 */
class FriendshipTest {

    private final ProgressSettings settings = ProgressSettings.defaults();

    @Test
    void travellingAddsOnePointForEveryTenMovementPointsAndRemembersTheRest() {
        assertArrayEquals(new int[] {72, 5}, Friendship.afterTravel(settings, 70, 0, 25));
        assertArrayEquals(new int[] {73, 0}, Friendship.afterTravel(settings, 72, 5, 5));
        assertArrayEquals(new int[] {255, 1}, Friendship.afterTravel(settings, 254, 9, 12), "9 + 12 = 21 points: +2, and 1 left over; never above 255");
    }

    @Test
    void theNumbersAreSettings() {
        ProgressSettings s = settings.withFriendship(220, List.of(10, 8, 6), 3, 5);
        assertEquals(10, Friendship.gainPerLevel(s, 0));
        assertEquals(6, Friendship.gainPerLevel(s, 255));
        assertEquals(97, Friendship.afterFainting(s, 100));
        assertEquals(0, Friendship.afterFainting(s, 2));
        assertArrayEquals(new int[] {72, 0}, Friendship.afterTravel(s, 70, 0, 10), "+1 for every 5 points");
    }

    @Test
    void theLeadPokemonGetsFriendshipFromTheJourneyThroughTheDesk() {
        Scene scene = new Scene();
        scene.give(ProgressFixtures.sheet(scene.book, "pk-1", "ana", "bulbasaur", 10, "tackle"));
        scene.arena.play(ProgressDesk.travelled(scene.arena.state(), scene.book, MOVES, RULES, scene.settings, "pk-1", 25));
        assertEquals(72, scene.pokemon("pk-1").friendship());
        assertEquals(25 * 8, scene.pokemon("pk-1").travelledBlocks(), "world.movement.blocksPerPoint is 8");
        scene.arena.play(ProgressDesk.travelled(scene.arena.state(), scene.book, MOVES, RULES, scene.settings, "pk-1", 5));
        assertEquals(73, scene.pokemon("pk-1").friendship(), "the 5 points left over count");
        assertThrows(BattleRefusedException.class, () -> ProgressDesk.travelled(scene.arena.state(), scene.book, MOVES, RULES, scene.settings, "pk-1", 0));
    }
}
