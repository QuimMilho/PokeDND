/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Condition;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Handling;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Method;
import net.quimmilho.pokednd.core.importer.EvolutionParser.ParsedRule;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Type;
import org.junit.jupiter.api.Test;

/** The evolution text parser on small strings: one test per kind of condition in chapter 5.4. */
class EvolutionParserTest {

    private static ParsedRule one(String text) {
        EvolutionParser.Result r = EvolutionParser.parse(text);
        assertEquals(List.of(), r.unparseable(), text);
        assertEquals(1, r.rules().size(), text);
        return r.rules().get(0);
    }

    private static Condition only(String text) {
        ParsedRule rule = one(text);
        assertEquals(1, rule.conditions().size(), text);
        return rule.conditions().get(0);
    }

    @Test
    void anEmptyCellHasNoRules() {
        assertEquals(List.of(), EvolutionParser.parse("").rules());
        assertEquals(List.of(), EvolutionParser.parse(null).rules());
    }

    @Test
    void aLevelEvolution() {
        ParsedRule r = one("Ivysaur (Level 16)");
        assertEquals("Ivysaur", r.target());
        assertEquals(Method.LEVEL_UP, r.method());
        assertEquals(Type.LEVEL, r.conditions().get(0).type());
        assertEquals(16, r.conditions().get(0).amount());
        assertEquals(Handling.AUTOMATIC, r.handling());
    }

    @Test
    void aLevelAndTimeOfDayEvolution() {
        ParsedRule r = one("Lycanroc (Level 25, Night)");
        assertEquals(List.of(Type.LEVEL, Type.TIME_OF_DAY), r.conditions().stream().map(Condition::type).toList());
        assertEquals("night", r.conditions().get(1).value());
    }

    @Test
    void anItemEvolution() {
        ParsedRule r = one("Vaporeon (Use Water Stone)");
        assertEquals(Method.USE_ITEM, r.method());
        assertEquals("Water Stone", r.item());
        assertEquals(List.of(), r.conditions());
    }

    @Test
    void tradeAndTradeHoldingAnItem() {
        assertEquals(Method.TRADE, one("Alakazam (Trade)").method());
        ParsedRule r = one("Politoed (Trade, Holding King's Rock)");
        assertEquals(Method.TRADE, r.method());
        assertEquals(Type.HOLDING_ITEM, r.conditions().get(0).type());
        assertEquals("King's Rock", r.conditions().get(0).value());
    }

    @Test
    void friendshipWithTimeOfDayAndWithAFairyMove() {
        ParsedRule day = one("Espeon (Friendship >= 160, Day)");
        assertEquals(160, day.conditions().get(0).amount());
        assertEquals("day", day.conditions().get(1).value());
        Condition fairy = only("Sylveon (Knows a Fairy-type move)");
        assertEquals(Type.KNOWS_MOVE_TYPE, fairy.type());
        assertEquals("fairy", fairy.value());
    }

    @Test
    void aMoveUsedManyTimesIsNotConfusedWithAnItem() {
        ParsedRule rageFist = one("Annihilape (Use Ragefist x20)");
        assertEquals(Method.LEVEL_UP, rageFist.method(), "Rage Fist is a level-up with a condition, not an item");
        assertEquals(Type.USE_MOVE, rageFist.conditions().get(0).type());
        assertEquals("ragefist", rageFist.conditions().get(0).value());
        assertEquals(20, rageFist.conditions().get(0).amount());
    }

    @Test
    void knowsAMoveAndComparesStats() {
        Condition knows = only("Mamoswine (Knows Ancientpower)");
        assertEquals(Type.KNOWS_MOVE, knows.type());
        assertEquals("ancientpower", knows.value());
        Condition stat = only("Hitmonlee (Attack > Defence)");
        assertEquals(Type.STAT_COMPARE, stat.type());
        assertEquals("attack", stat.value());
        assertEquals("defense>", stat.other());
        assertEquals("defense=", only("Hitmontop (Attack = Defence)").other());
    }

    @Test
    void travelWeatherMoonPartyAndGender() {
        assertEquals(1000, only("Pawmot (Travel 1000 blocks)").amount());
        assertEquals("raining", only("Goodra (Raining)").value());
        Condition moon = only("Ursaluna (Moon FULL MOON)");
        assertEquals(Type.MOON_PHASE, moon.type());
        assertEquals(Handling.ADMIN, moon.handling(), "the moon is not automatic: the admin confirms");
        Condition party = only("Mantine (With Remoraid in party)");
        assertEquals(Type.PARTY_MEMBER, party.type());
        assertEquals("remoraid", party.value());
        assertEquals(Type.GENDER, only("Salazzle (Gender Female)").type());
    }

    @Test
    void biomesAndStructuresNeedARegionTagAndNotKeepsItsMeaning() {
        Condition biome = only("Raichu (Biome Evolution/Regional/Pikachu Alolabiome)");
        assertEquals(Type.BIOME, biome.type());
        assertEquals("evolution/regional/pikachu_alolabiome", biome.value());
        assertEquals(Handling.REGION_TAG, biome.handling());
        Condition not = only("Vivillon (Not (Biome Is Magical))");
        assertTrue(not.negated());
        assertEquals("magical", not.value());
        Condition structure = only("Vivillon (Not (Structure Village))");
        assertEquals(Type.STRUCTURE, structure.type());
        assertTrue(structure.negated());
    }

    @Test
    void bracketsInsideAConditionDoNotSplitIt() {
        ParsedRule r = one("Vivillon (Level 12, Biome Evolution/Vivillon/Polar, Not (Biome Is Magical), Not (Structure Village))");
        assertEquals(4, r.conditions().size());
        assertEquals(Handling.REGION_TAG, r.handling());
    }

    @Test
    void hiddenValuesAndAdminOnlyOnes() {
        Condition cocoon = only("Silcoon (Wurmple Cocoon Species Silcoon)");
        assertEquals("cocoon_species", cocoon.value());
        assertEquals("silcoon", cocoon.other());
        Condition coins = only("Gholdengo (Gimmighoul Gimmighoul Coins 999)");
        assertEquals(999, coins.amount());
        assertEquals(Handling.ADMIN, coins.handling());
        Condition netherite = only("Gholdengo (Gimmighoul Netherite 32-63)");
        assertEquals(32, netherite.amount());
        assertEquals(63, netherite.max());
        assertEquals("hardy", only("Toxtricity (Toxel Nature Hardy)").other());
        assertEquals(Type.ADVANCEMENT, only("Vivillon (Advancement Catching/Collect All Vivillon)").type());
    }

    @Test
    void defeatAndNickname() {
        Condition defeat = only("Kingambit (Defeat 3x bisharp held_item=cobblemon:kings_rock)");
        assertEquals(Type.DEFEAT, defeat.type());
        assertEquals(3, defeat.amount());
        assertEquals("bisharp", defeat.value());
        assertEquals("cobblemon:kings_rock", defeat.other());
        assertEquals("Dinnerbone", only("Malamar (Nickname Dinnerbone)").value());
    }

    @Test
    void severalRulesAreSeparatedBySemicolons() {
        EvolutionParser.Result r = EvolutionParser.parse("Vileplume (Use Leaf Stone); Bellossom (Use Sun Stone)");
        assertEquals(List.of("Vileplume", "Bellossom"), r.rules().stream().map(ParsedRule::target).toList());
    }

    @Test
    void whatIsNotUnderstoodIsKeptAsUnknownAndHandledAsAskTheAdmin() {
        ParsedRule r = one("Foo (Level 5, Dance wildly)");
        assertEquals(Type.UNKNOWN, r.conditions().get(1).type());
        assertEquals("Dance wildly", r.conditions().get(1).value());
        assertEquals(Handling.UNKNOWN, r.handling());
        assertNull(r.item());
    }

    @Test
    void aTextWithoutTheTargetShapeIsReportedNotGuessed() {
        EvolutionParser.Result r = EvolutionParser.parse("something odd");
        assertEquals(List.of(), r.rules());
        assertEquals(1, r.unparseable().size());
    }
}
