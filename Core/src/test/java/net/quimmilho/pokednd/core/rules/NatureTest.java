/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NatureTest {

    /**
     * The well-known 5 x 5 table of the games, typed in separately: the row is the stat that goes up, the column the stat
     * that goes down. Stat order of both: Attack, Defense, Speed, Sp. Atk, Sp. Def. The diagonal is the five neutral natures.
     */
    private static final String[][] TABLE = {
        /* +Atk */ {"Hardy", "Lonely", "Brave", "Adamant", "Naughty"},
        /* +Def */ {"Bold", "Docile", "Relaxed", "Impish", "Lax"},
        /* +Spe */ {"Timid", "Hasty", "Serious", "Jolly", "Naive"},
        /* +SpA */ {"Modest", "Mild", "Quiet", "Bashful", "Rash"},
        /* +SpD */ {"Calm", "Gentle", "Sassy", "Careful", "Quirky"},
    };
    private static final Stat[] ORDER = {Stat.ATTACK, Stat.DEFENSE, Stat.SPEED, Stat.SPECIAL_ATTACK, Stat.SPECIAL_DEFENSE};

    @Test
    void thereAreTwentyFiveNaturesFiveOfThemNeutral() {
        assertEquals(25, Nature.all().size());
        assertEquals(25, new HashSet<>(Nature.all().stream().map(Nature::id).toList()).size(), "ids are unique");
        assertEquals(5, Nature.all().stream().filter(Nature::neutral).count());
        assertEquals(Set.of("hardy", "docile", "serious", "bashful", "quirky"),
                new HashSet<>(Nature.all().stream().filter(Nature::neutral).map(Nature::id).toList()));
    }

    @Test
    void everyOtherNatureRaisesOneStatByTenPercentAndLowersAnotherByTenPercent() {
        for (Nature n : Nature.all()) {
            if (n.neutral()) {
                for (Stat s : Stat.values()) {
                    assertEquals(1.0, n.multiplier(s), n.id());
                }
                continue;
            }
            assertTrue(n.plus().isPresent() && n.minus().isPresent(), n.id());
            assertTrue(n.plus().get() != n.minus().get(), n.id());
            int up = 0;
            int down = 0;
            for (Stat s : Stat.values()) {
                double m = n.multiplier(s);
                up += m == 1.1 ? 1 : 0;
                down += m == 0.9 ? 1 : 0;
                assertTrue(m == 1.0 || m == 1.1 || m == 0.9, n.id() + " " + s + " " + m);
            }
            assertEquals(1, up, n.id());
            assertEquals(1, down, n.id());
            assertEquals(1.0, n.multiplier(Stat.HP), n.id() + ": HP is never changed");
        }
    }

    @Test
    void theNaturesEqualTheIndependentFiveByFiveTable() {
        for (int up = 0; up < 5; up++) {
            for (int down = 0; down < 5; down++) {
                Nature n = Nature.find(TABLE[up][down]).orElseThrow(() -> new AssertionError("missing nature"));
                if (up == down) {
                    assertTrue(n.neutral(), n.id());
                } else {
                    assertEquals(Optional.of(ORDER[up]), n.plus(), n.id());
                    assertEquals(Optional.of(ORDER[down]), n.minus(), n.id());
                }
            }
        }
    }

    @Test
    void examplesFromTheSpecification() {
        Nature adamant = Nature.find("Adamant").orElseThrow();
        assertEquals(1.1, adamant.multiplier(Stat.ATTACK));
        assertEquals(0.9, adamant.multiplier(Stat.SPECIAL_ATTACK));
        assertEquals(1.0, adamant.multiplier(Stat.SPEED));
        Nature timid = Nature.find(" timid ").orElseThrow();
        assertEquals(1.1, timid.multiplier(Stat.SPEED));
        assertEquals(0.9, timid.multiplier(Stat.ATTACK));
        assertTrue(Nature.find("grumpy").isEmpty());
    }

    private static Optional<Stat> stat(Map<String, Stat> names, JsonNode nature, String key) {
        return nature.has(key) ? Optional.of(names.get(nature.get(key).asText())) : Optional.empty();
    }

    @Test
    void agreesWithTheShowdownSnapshot() throws IOException {
        JsonNode sd;
        try (InputStream in = NatureTest.class.getResourceAsStream("/showdown/natures.json")) {
            sd = new ObjectMapper().readTree(in);
        }
        Map<String, Stat> short2stat = Map.of("atk", Stat.ATTACK, "def", Stat.DEFENSE, "spa", Stat.SPECIAL_ATTACK,
                "spd", Stat.SPECIAL_DEFENSE, "spe", Stat.SPEED);
        assertEquals(25, sd.size());
        for (Nature n : Nature.all()) {
            JsonNode s = sd.get(n.id());
            assertEquals(n.plus(), stat(short2stat, s, "plus"), n.id());
            assertEquals(n.minus(), stat(short2stat, s, "minus"), n.id());
        }
    }
}
