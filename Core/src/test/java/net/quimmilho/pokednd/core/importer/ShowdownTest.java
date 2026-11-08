/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The committed Showdown snapshot is packaged with Core, so no jar and no network are needed. */
class ShowdownTest {

    @Test
    void thePackagedSnapshotLoadsWithoutAnyInput() {
        Showdown sd = Showdown.load();
        assertEquals(952, sd.moves().size());
        assertEquals(314, sd.abilities().size());
        assertEquals(537, sd.items().size());
        assertEquals(25, sd.natures().size());
        assertEquals(19, sd.typeChart().size());
    }

    @Test
    void entriesKeepPlainDataAndOnlyTheNamesOfCodeHandlers() {
        Showdown sd = Showdown.load();
        var flamethrower = sd.moves().get("flamethrower");
        assertEquals(90, flamethrower.get("basePower").asInt());
        assertEquals(10, flamethrower.get("secondary").get("chance").asInt());
        assertEquals("brn", flamethrower.get("secondary").get("status").asText());
        assertTrue(flamethrower.path("handlers").isMissingNode(), "a plain-data move has no handlers");
        var leftovers = sd.items().get("leftovers");
        assertTrue(leftovers.get("handlers").toString().contains("onResidual"));
        assertTrue(sd.abilities().get("overgrow").get("handlers").size() > 0);
    }
}
