/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.api.settings.SettingsLayers.Layer;
import net.quimmilho.pokednd.api.settings.SettingsLayers.Resolved;
import org.junit.jupiter.api.Test;

/** Chapter 12.2: the highest layer that sets a value wins, and the answer says which layer it was. */
class SettingsLayersTest {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private final SettingsRegistry registry = new SettingsRegistry();

    private Resolved one(String key, Layer... layers) {
        return SettingsLayers.resolve(registry, List.of(layers)).get(key);
    }

    private static Layer ruleSet(String key, JsonNode v) {
        return new Layer(SettingsLayers.RULE_SET, Map.of(key, v));
    }

    private static Layer game(String key, JsonNode v) {
        return new Layer(SettingsLayers.GAME, Map.of(key, v));
    }

    @Test
    void withNoLayerTheDefaultShows() {
        Resolved r = one("combat.ai.level");
        assertEquals("normal", r.value().asText());
        assertEquals(SettingsLayers.DEFAULT, r.source());
        assertFalse(r.overridden());
    }

    @Test
    void aRuleSetValueBeatsTheDefault() {
        Resolved r = one("combat.ai.level", ruleSet("combat.ai.level", F.textNode("easy")));
        assertEquals("easy", r.value().asText());
        assertEquals(SettingsLayers.RULE_SET, r.source());
        assertTrue(r.overridden());
    }

    @Test
    void aGameValueBeatsTheRuleSetValueWhichBeatsTheDefault() {
        Resolved r = one("combat.ai.level", ruleSet("combat.ai.level", F.textNode("easy")), game("combat.ai.level", F.textNode("hard")));
        assertEquals("hard", r.value().asText());
        assertEquals(SettingsLayers.GAME, r.source());
    }

    @Test
    void aLayerOnlyCountsForTheKeysItSets() {
        List<Layer> layers = List.of(ruleSet("combat.ai.level", F.textNode("easy")), game("dice.style", F.textNode("classic")));
        Map<String, Resolved> all = SettingsLayers.resolve(registry, layers);
        assertEquals(234, all.size());
        assertEquals(SettingsLayers.RULE_SET, all.get("combat.ai.level").source());
        assertEquals(SettingsLayers.GAME, all.get("dice.style").source());
        assertEquals(SettingsLayers.DEFAULT, all.get("combat.crit.multiplier").source());
    }

    @Test
    void aHigherLayerNeedNotMentionTheKeyOfALowerOne() {
        Resolved r = one("combat.ai.level", ruleSet("combat.ai.level", F.textNode("easy")), game("dice.style", F.textNode("classic")));
        assertEquals("easy", r.value().asText());
        assertEquals(SettingsLayers.RULE_SET, r.source());
    }

    @Test
    void aSettingWhoseDefaultIsDataShowsNullUntilSomethingSetsIt() {
        assertTrue(one("combat.damage.powerTable").value() == null || one("combat.damage.powerTable").value().isNull());
        Resolved r = one("combat.damage.powerTable", game("combat.damage.powerTable", F.objectNode().put("40", "1d6")));
        assertEquals(SettingsLayers.GAME, r.source());
    }

    @Test
    void keysThatAreNotInTheRegistryAreIgnored() {
        Map<String, Resolved> all = SettingsLayers.resolve(registry, List.of(game("not.a.setting", F.booleanNode(true))));
        assertFalse(all.containsKey("not.a.setting"));
        assertEquals(234, all.size());
    }
}
