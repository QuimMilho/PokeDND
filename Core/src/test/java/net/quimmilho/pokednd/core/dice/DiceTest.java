/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiceTest {

    @Test
    void parsesTheNotationOfChapter62() {
        assertEquals(Optional.of(new Dice(1, 20)), Dice.parse("d20"));
        assertEquals(Optional.of(new Dice(1, 20)), Dice.parse("1d20"));
        assertEquals(Optional.of(new Dice(2, 8)), Dice.parse("2d8"));
        assertEquals(Optional.of(new Dice(4, 6)), Dice.parse(" 4 D 6 "));
        assertEquals(Optional.of(new Dice(1, 100)), Dice.parse("d100"));
    }

    @Test
    void rejectsWhatIsNotDiceNotation() {
        for (String bad : new String[] {"", "d", "20", "2d", "0d6", "1d1", "1d0", "d20+3", "two d8", "-1d6", null}) {
            assertEquals(Optional.empty(), Dice.parse(bad), String.valueOf(bad));
        }
    }

    @Test
    void writesTheNotationBack() {
        assertEquals("d20", Dice.d(20).toString());
        assertEquals("2d8", new Dice(2, 8).toString());
        assertEquals("d100", Dice.parse("1d100").orElseThrow().toString());
    }

    @Test
    void knowsItsRange() {
        assertEquals(2, new Dice(2, 8).min());
        assertEquals(16, new Dice(2, 8).max());
        assertThrows(IllegalArgumentException.class, () -> new Dice(0, 6));
        assertThrows(IllegalArgumentException.class, () -> new Dice(1, 1));
    }
}
