/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.quimmilho.pokednd.core.combat.Damage.Input;
import net.quimmilho.pokednd.core.combat.MoveProfile.FixedDamage;
import net.quimmilho.pokednd.core.dice.Dice;
import org.junit.jupiter.api.Test;

class DamageTest {

    private static final CombatSettings SETTINGS = CombatSettings.defaults();

    /** Level-20 Charmander (Sp. Atk 32) uses Ember on a level-18 Bulbasaur (Sp. Def 31): STAB and Fire against Grass. */
    private static Input ember(int diceTotal) {
        return Input.plain(20, diceTotal, 32, 31).withStab(true).withTypeEffectiveness(2.0);
    }

    @Test
    void theWorkedExampleOfChapter7_6() {
        // Ember has power 40, which is 2d8; the player rolls 4 + 5 = 9.
        assertEquals(Dice.parse("2d8").orElseThrow(), SETTINGS.powerTable().diceFor(40).orElseThrow());
        Damage.Result r = Damage.compute(ember(9), SETTINGS);
        assertEquals(45, r.effectivePower());
        assertEquals(11, r.base());
        assertEquals(33, r.damage(), "11 x 1.5 x 2");
        assertFalse(r.immune());
    }

    @Test
    void theSameMoveDealsFrom12To54() {
        assertEquals(12, Damage.compute(ember(2), SETTINGS).damage(), "a roll of 2");
        assertEquals(54, Damage.compute(ember(16), SETTINGS).damage(), "a roll of 16");
        double sum = 0;
        int outcomes = 0;
        for (int a = 1; a <= 8; a++) {
            for (int b = 1; b <= 8; b++) {
                sum += Damage.compute(ember(a + b), SETTINGS).damage();
                outcomes++;
            }
        }
        assertEquals(33.0, sum / outcomes, 1.5, "averaging about 33");
    }

    @Test
    void aCriticalHitMultipliesByOnePointFive() {
        Input plain = Input.plain(20, 9, 32, 31);
        assertEquals(11, Damage.compute(plain, SETTINGS).damage());
        assertEquals(16, Damage.compute(plain.withCrit(true), SETTINGS).damage(), "floor(11 x 1.5)");
    }

    @Test
    void aCriticalHitIgnoresTheTargetsPositiveDefenseStagesAndTheAttackersNegativeAttackStages() {
        Input base = Input.plain(50, 10, 100, 100).withCrit(true);
        int clean = Damage.compute(base, SETTINGS).damage();
        assertEquals(clean, Damage.compute(base.withStages(0, 3), SETTINGS).damage(), "target Defense +3 ignored");
        assertEquals(clean, Damage.compute(base.withStages(-2, 0), SETTINGS).damage(), "attacker Attack -2 ignored");
        assertTrue(Damage.compute(base.withStages(2, 0), SETTINGS).damage() > clean, "positive Attack still counts");
        assertTrue(Damage.compute(base.withStages(0, -2), SETTINGS).damage() > clean, "negative Defense still counts");
        // Without a crit the same stages do matter.
        Input noCrit = base.withCrit(false);
        assertTrue(Damage.compute(noCrit.withStages(0, 3), SETTINGS).damage() < Damage.compute(noCrit, SETTINGS).damage());
        assertTrue(Damage.compute(noCrit.withStages(-2, 0), SETTINGS).damage() < Damage.compute(noCrit, SETTINGS).damage());
    }

    @Test
    void burnHalvesPhysicalDamage() {
        Input plain = Input.plain(50, 10, 100, 100);
        int normal = Damage.compute(plain, SETTINGS).damage();
        int burned = Damage.compute(plain.withBurn(true), SETTINGS).damage();
        assertEquals(normal / 2, burned);
    }

    @Test
    void typeEffectivenessScalesTheDamage() {
        Input plain = Input.plain(50, 10, 100, 100); // base = floor(floor(22 x 50 x 100 / 100) / 50) + 2 = 24
        assertEquals(24, Damage.compute(plain, SETTINGS).base());
        assertEquals(96, Damage.compute(plain.withTypeEffectiveness(4.0), SETTINGS).damage());
        assertEquals(48, Damage.compute(plain.withTypeEffectiveness(2.0), SETTINGS).damage());
        assertEquals(12, Damage.compute(plain.withTypeEffectiveness(0.5), SETTINGS).damage());
        assertEquals(6, Damage.compute(plain.withTypeEffectiveness(0.25), SETTINGS).damage());
    }

    @Test
    void aMoveThatIsNotImmuneDoesAtLeastOneDamage() {
        Input weak = Input.plain(1, 1, 1, 255).withTypeEffectiveness(0.25).withBurn(true);
        Damage.Result r = Damage.compute(weak, SETTINGS);
        assertEquals(1, r.damage());
        assertFalse(r.immune());
    }

    @Test
    void anImmuneTargetTakesNothing() {
        Damage.Result r = Damage.compute(Input.plain(50, 20, 100, 100).withTypeEffectiveness(0.0), SETTINGS);
        assertEquals(0, r.damage());
        assertTrue(r.immune());
    }

    @Test
    void theOtherFactorFromAbilitiesAndItemsApplies() {
        Input plain = Input.plain(50, 10, 100, 100);
        assertEquals(36, Damage.compute(plain.withOther(1.5), SETTINGS).damage(), "24 x 1.5");
    }

    @Test
    void styleDiceAreAddedToTheDiceTotalBeforeTheFactor() {
        // Ember rolled 9, plus 1d6 style dice that showed 3: effective power 12 x 5 = 60.
        Damage.Result r = Damage.compute(ember(9 + 3), SETTINGS);
        assertEquals(60, r.effectivePower());
        assertTrue(r.damage() > 33);
    }

    @Test
    void theScaleSettingsChangeTheResult() {
        Input plain = Input.plain(50, 10, 100, 100);
        assertEquals(24, Damage.compute(plain, SETTINGS).damage());
        assertEquals(46, Damage.compute(plain, SETTINGS.withDicePowerFactor(10)).base(), "double power");
        assertEquals(36, Damage.compute(plain.withStab(true), SETTINGS).damage(), "STAB 1.5");
        assertEquals(48, Damage.compute(plain.withStab(true), SETTINGS.withStabMultiplier(2.0)).damage());
        assertEquals(48, Damage.compute(plain.withCrit(true), SETTINGS.withCritMultiplier(2.0)).damage());
        assertEquals(24, Damage.compute(plain.withCrit(false), SETTINGS.withCritMultiplier(2.0)).damage(), "no crit, no effect");
    }

    @Test
    void multiHitMovesRollTheDamageDiceOnceByDefaultOrPerHit() {
        assertEquals(1, SETTINGS.damageRolls(5));
        CombatSettings perHit = SETTINGS.withMultihitDamageDice(CombatSettings.MultihitDamageDice.PER_HIT);
        assertEquals(5, perHit.damageRolls(5));
        assertEquals(1, perHit.damageRolls(0));
        assertEquals(CombatSettings.MultihitDamageDice.PER_HIT, CombatSettings.MultihitDamageDice.parse("perHit"));
    }

    @Test
    void fixedDamageMovesSkipTheDice() {
        FixedDamage seismicToss = new FixedDamage(0, true);
        FixedDamage dragonRage = new FixedDamage(40, false);
        assertEquals(37, Damage.fixed(seismicToss, 37, 1.0), "the user's level");
        assertEquals(40, Damage.fixed(dragonRage, 5, 1.0));
        assertEquals(20, Damage.fixed(new FixedDamage(20, false), 80, 1.0), "Sonic Boom");
        assertEquals(0, Damage.fixed(seismicToss, 37, 0.0), "a Ghost is immune to Fighting");
    }

    @Test
    void badInputIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Damage.compute(Input.plain(0, 1, 10, 10), SETTINGS));
        assertThrows(IllegalArgumentException.class, () -> Damage.compute(Input.plain(10, -1, 10, 10), SETTINGS));
    }
}
