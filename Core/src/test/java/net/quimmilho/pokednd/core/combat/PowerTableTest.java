/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.quimmilho.pokednd.core.combat.PowerTable.Row;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.Test;

class PowerTableTest {

    /** The table of chapter 7.6, typed in separately: lowest power, highest power (0 = no end), dice, effective power. */
    private static final Object[][] SPEC = {
        {1, 15, "1d4", 12.5}, {16, 20, "1d6", 17.5}, {21, 25, "1d8", 22.5}, {26, 30, "1d10", 27.5},
        {31, 38, "1d12", 32.5}, {39, 50, "2d8", 45.0}, {51, 60, "2d10", 55.0}, {61, 66, "2d12", 65.0},
        {67, 75, "3d8", 67.5}, {76, 90, "3d10", 82.5}, {91, 103, "3d12", 97.5}, {104, 120, "4d10", 110.0},
        {121, 146, "4d12", 130.0}, {147, 178, "5d12", 162.5}, {179, 227, "6d12", 195.0}, {228, 0, "8d12", 260.0},
    };

    private static Dice dice(String text) {
        return Dice.parse(text).orElseThrow();
    }

    @Test
    void theDefaultTableIsTheOneOfChapter7_6() {
        PowerTable table = PowerTable.defaults();
        assertEquals(SPEC.length, table.rows().size());
        for (int i = 0; i < SPEC.length; i++) {
            int from = (Integer) SPEC[i][0];
            int to = (Integer) SPEC[i][1];
            Dice expected = dice((String) SPEC[i][2]);
            assertEquals(from, table.rows().get(i).fromPower());
            assertEquals(expected, table.rows().get(i).dice());
            assertEquals(to == 0 ? java.util.Optional.empty() : java.util.Optional.of(to), table.upTo(i), "row " + i);
            assertEquals((Double) SPEC[i][3], PowerTable.effectivePower(expected, 5), "effective power of " + expected);
            // Every power in the range maps to the same dice.
            int last = to == 0 ? 300 : to;
            for (int power = from; power <= last; power++) {
                assertEquals(expected, table.diceFor(power).orElseThrow(), "power " + power);
            }
        }
    }

    @Test
    void powerBelowOneHasNoDiceAndHugePowerUsesTheLastRow() {
        PowerTable table = PowerTable.defaults();
        assertTrue(table.diceFor(0).isEmpty());
        assertTrue(table.diceFor(-5).isEmpty());
        assertEquals(dice("8d12"), table.diceFor(250).orElseThrow());
        assertEquals(dice("8d12"), table.diceFor(9_999).orElseThrow());
    }

    @Test
    void aTableThatIsEditedGivesOtherDiceAndTheDefaultsStayTheSame() {
        PowerTable defaults = PowerTable.defaults();
        PowerTable edited = defaults.withRow(39, dice("3d8"));
        assertEquals(dice("2d8"), defaults.diceFor(40).orElseThrow(), "the original is not changed");
        assertEquals(dice("3d8"), edited.diceFor(40).orElseThrow());
        assertEquals(dice("1d12"), edited.diceFor(38).orElseThrow(), "neighbouring rows are untouched");
        PowerTable added = defaults.withRow(45, dice("2d10"));
        assertEquals(dice("2d8"), added.diceFor(44).orElseThrow());
        assertEquals(dice("2d10"), added.diceFor(45).orElseThrow());
        assertEquals(dice("2d10"), added.diceFor(50).orElseThrow());
        assertEquals(SPEC.length + 1, added.rows().size());
    }

    @Test
    void changingTheTableChangesTheDamage() {
        // Ember (power 40) with the default table is 2d8: the best roll is 16, giving 54 damage in the worked example.
        CombatSettings normal = CombatSettings.defaults();
        CombatSettings strong = normal.withPowerTable(normal.powerTable().withRow(39, dice("4d10")));
        Dice dice = strong.powerTable().diceFor(40).orElseThrow();
        assertEquals(dice("4d10"), dice);
        Damage.Input best = Damage.Input.plain(20, dice.max(), 32, 31).withStab(true).withTypeEffectiveness(2.0);
        Damage.Input bestBefore = Damage.Input.plain(20, 16, 32, 31).withStab(true).withTypeEffectiveness(2.0);
        assertEquals(54, Damage.compute(bestBefore, normal).damage());
        assertTrue(Damage.compute(best, strong).damage() > 54, "4d10 can roll up to 40");
        assertEquals(Damage.compute(best, strong).damage(), Damage.compute(best, normal.withPowerTable(strong.powerTable())).damage());
    }

    @Test
    void theTableSurvivesJson() {
        PowerTable table = PowerTable.defaults().withRow(39, dice("3d8"));
        assertEquals(table, PowerTable.fromJson(table.toJson()));
        assertEquals(PowerTable.defaults(), PowerTable.fromJson(PowerTable.defaults().toJson()));
        PowerTable small = PowerTable.fromJson("{\"rows\":[{\"from\":1,\"dice\":\"d6\"},{\"from\":50,\"dice\":\"2d6\"}]}");
        assertEquals(dice("1d6"), small.diceFor(30).orElseThrow());
        assertEquals(dice("2d6"), small.diceFor(80).orElseThrow());
    }

    @Test
    void aBrokenTableIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PowerTable.of(List.of()), "empty");
        assertThrows(IllegalArgumentException.class, () -> PowerTable.of(List.of(new Row(5, dice("1d4")))), "does not start at 1");
        assertThrows(IllegalArgumentException.class,
                () -> PowerTable.of(List.of(new Row(1, dice("1d4")), new Row(1, dice("1d6")))), "same start twice");
        assertThrows(IllegalArgumentException.class, () -> PowerTable.fromJson("{\"rows\":[{\"from\":1,\"dice\":\"banana\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> PowerTable.fromJson("not json"));
    }

    @Test
    void theAverageTimesFiveIsRoughlyThePower() {
        PowerTable table = PowerTable.defaults();
        for (int i = 0; i < table.rows().size(); i++) {
            Row row = table.rows().get(i);
            double effective = PowerTable.effectivePower(row.dice(), 5);
            int low = row.fromPower();
            int high = table.upTo(i).orElse(row.fromPower() + 30);
            // The effective power sits inside the band of powers it stands for, give or take 12 %.
            assertTrue(effective >= low * 0.88 && effective <= high * 1.12 + 0.5,
                    row.dice() + " = " + effective + " for powers " + low + "-" + high);
        }
    }

    @Test
    void settingsDefaultsAreTheOnesOfChapter7_17() {
        CombatSettings s = CombatSettings.defaults();
        assertEquals(List.of(5, 10, 15, 25), s.critChancesPct());
        assertEquals(1.5, s.critMultiplier());
        assertEquals(1.5, s.stabMultiplier());
        assertTrue(s.accuracyStageEffects());
        assertEquals(5, s.dicePowerFactor());
        assertEquals(PowerTable.defaults(), s.powerTable());
        assertEquals(CombatSettings.MultihitDamageDice.ONCE, s.multihitDamageDice());
        assertEquals(PokemonType.FIRE, PokemonType.parse("Fire").orElseThrow());
    }
}
