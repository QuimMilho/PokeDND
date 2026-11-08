/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.importer.Bundle;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Hit and damage data read from the real moves. Skipped when {@code resources/} or {@code build/roster.json} are missing,
 * unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class MoveProfileRealDataTest {

    private static final CombatSettings SETTINGS = CombatSettings.defaults();
    private static final Map<String, MoveProfile> PROFILES = new HashMap<>();
    private static int moves;

    @BeforeAll
    static void load() {
        Path resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        Path roster = Path.of(System.getProperty("pokednd.roster", "../build/roster.json"));
        boolean there = Files.isRegularFile(resources.resolve("pokedex.csv")) && Files.isRegularFile(roster);
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ or build/roster.json is missing but POKEDND_REQUIRE_RESOURCES=true");
        }
        assumeTrue(there, "resources/ or build/roster.json is not built");
        Bundle bundle = Bundle.build(resources, roster);
        for (MoveEntry m : bundle.catalog().moves()) {
            PROFILES.put(m.id(), MoveProfile.from(m));
            moves++;
        }
    }

    private static MoveProfile move(String id) {
        MoveProfile p = PROFILES.get(id);
        assertTrue(p != null, "move " + id + " exists");
        return p;
    }

    @Test
    void everyMoveHasAProfileAndEveryDamagingMoveWithPowerGetsDice() {
        assertTrue(moves > 900, "all moves read: " + moves);
        int withDice = 0;
        for (MoveProfile p : PROFILES.values()) {
            if (p.usesDamageDice()) {
                Dice dice = p.damageDice(SETTINGS).orElseThrow();
                assertTrue(dice.count() >= 1);
                withDice++;
            } else {
                assertTrue(p.damageDice(SETTINGS).isEmpty(), p.id());
            }
            // The card line "hit: d20 1–19 · crit 20" can be built for every move.
            assertTrue(p.normalHit(SETTINGS).choice().bands().size() == 3, p.id());
        }
        assertTrue(withDice > 400, "damaging moves with dice: " + withDice);
    }

    @Test
    void knownMovesAreReadCorrectly() {
        MoveProfile thunderbolt = move("thunderbolt");
        assertEquals("3d10", thunderbolt.damageDice(SETTINGS).orElseThrow().toString(), "power 90");
        assertEquals(MoveProfile.Category.SPECIAL, thunderbolt.category());

        MoveProfile fissure = move("fissure");
        assertTrue(fissure.ohko());
        assertFalse(fissure.usesDamageDice());

        MoveProfile seismicToss = move("seismictoss");
        assertEquals(50, seismicToss.fixedDamage().orElseThrow().damage(50));
        assertEquals(40, move("dragonrage").fixedDamage().orElseThrow().damage(3));
        assertEquals(20, move("sonicboom").fixedDamage().orElseThrow().damage(99));
        assertFalse(seismicToss.usesDamageDice());

        assertTrue(move("frostbreath").alwaysCrit());
        assertEquals(1, move("slash").critStage(), "a high-crit move is crit stage +1");
        assertEquals(0, move("tackle").critStage());
        assertTrue(move("splash").damageDice(SETTINGS).isEmpty(), "status move");
        assertEquals(java.util.Optional.empty(), move("swift").accuracy(), "swift never misses");
    }

    @Test
    void theCardLineForTackleIsAD20WithAMissBand() {
        HitRoll.Odds tackle = move("tackle").normalHit(SETTINGS);
        assertEquals(20, tackle.choice().die());
        assertEquals("20", tackle.band(HitRoll.Result.CRIT).range());
    }

    @Test
    void movesThatNeverMissHaveNoMissBand() {
        int checked = 0;
        for (MoveProfile p : PROFILES.values()) {
            if (p.accuracy().isEmpty() && p.damaging() && !p.ohko()) {
                assertFalse(p.normalHit(SETTINGS).canMiss(), p.id());
                checked++;
            }
        }
        assertTrue(checked > 10, "moves with the dash: " + checked);
    }
}
