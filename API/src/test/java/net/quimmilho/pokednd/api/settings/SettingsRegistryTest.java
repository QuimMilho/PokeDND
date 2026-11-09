/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.api.settings.SettingDef.Type;
import org.junit.jupiter.api.Test;

/** The registry against the document: the keys and defaults are those of Appendix A, and every value is checked as its setting demands. */
class SettingsRegistryTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private final SettingsRegistry registry = new SettingsRegistry();
    private final BuiltinRuleSets builtins = new BuiltinRuleSets(registry);

    /** Appendix A of project_v2.md read here, on its own, without the generator: key to the default as the document writes it. */
    static Map<String, String> appendixA() throws IOException {
        Path spec = Path.of("..", "project_v2.md");
        assertTrue(Files.isRegularFile(spec), "project_v2.md must be next to the API folder: " + spec.toAbsolutePath());
        String text = Files.readString(spec).replace("\r\n", "\n");
        int start = text.indexOf("### Appendix A");
        int end = text.indexOf("### Appendix B");
        assertTrue(start > 0 && end > start, "Appendix A not found");
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : text.substring(start, end).split("\n")) {
            if (!line.startsWith("| `")) {
                continue;
            }
            String[] cells = line.split("\\|");
            // cells[0] is empty (before the first bar): key, default, description, chapter follow
            List<String> keys = new ArrayList<>(List.of(cells[1].trim().split(" / ")));
            String defaultCell = cells[2].trim();
            List<String> defaults = keys.size() > 1 ? List.of(defaultCell.split(" / ")) : List.of(defaultCell);
            assertEquals(keys.size(), defaults.size(), "keys and defaults of " + line);
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i).trim().replace("`", "");
                String def = defaults.get(i).trim();
                if (def.startsWith("`")) {
                    def = def.substring(1, def.length() - 1);
                }
                assertTrue(rows.put(key, def) == null, "Appendix A lists " + key + " twice");
            }
        }
        return rows;
    }

    @Test
    void theRegistryHasExactlyTheKeysAndDefaultsOfAppendixA() throws IOException {
        Map<String, String> doc = appendixA();
        assertEquals(234, doc.size(), "Appendix A says it has 234 keys");
        assertEquals(doc.keySet(), registry.keys(), "the registry must have exactly the keys of Appendix A (run node tools/gen-settings.mjs)");
        for (Map.Entry<String, String> e : doc.entrySet()) {
            SettingDef def = registry.get(e.getKey());
            assertEquals(e.getValue(), def.defaultText(), "the default text of " + e.getKey());
            JsonNode v = def.defaultValue();
            switch (def.type()) {
                case BOOL -> assertEquals(Boolean.parseBoolean(e.getValue()), v.asBoolean(), e.getKey());
                case INT, DECIMAL -> assertEquals(Double.parseDouble(e.getValue()), v.asDouble(), 0.0, e.getKey());
                case ENUM, STRING -> assertEquals(e.getValue(), v.asText(), e.getKey());
                case CSV -> assertEquals(e.getValue().replace(" ", ""), v.asText(), e.getKey());
                case INT_LIST -> {
                    List<String> want = List.of(e.getValue().replace(" ", "").split(","));
                    List<String> got = new ArrayList<>();
                    v.forEach(n -> got.add(n.asText()));
                    assertEquals(want, got, e.getKey());
                }
                case JSON -> assertEquals("{}", e.getValue(), e.getKey());
                case DATA -> assertTrue(v.isNull() || "official".equals(v.asText()), e.getKey() + " has data that ships with the game, not in the document");
            }
        }
    }

    @Test
    void everySettingKeepsItsChapterGroupAndDescription() {
        for (SettingDef d : registry.all()) {
            assertTrue(d.chapter() >= 3 && d.chapter() <= 15, d.key() + " chapter " + d.chapter());
            assertFalse(d.group().isBlank(), d.key());
            assertFalse(d.description().isBlank(), d.key());
        }
    }

    @Test
    void everyDefaultPassesItsOwnRules() {
        for (SettingDef d : registry.all()) {
            if (d.type() == Type.DATA && (d.defaultValue() == null || d.defaultValue().isNull())) {
                continue;
            }
            SettingsRegistry.Checked c = registry.check(d.key(), d.defaultValue());
            assertTrue(c.ok(), d.key() + ": " + c.error());
            assertTrue(SettingsRegistry.sameValue(d.defaultValue(), c.value()), d.key() + " changes when it is checked");
        }
    }

    private String refused(String key, JsonNode value) {
        SettingsRegistry.Checked c = registry.check(key, value);
        assertFalse(c.ok(), key + " = " + value + " should be refused");
        return c.error();
    }

    private JsonNode accepted(String key, JsonNode value) {
        SettingsRegistry.Checked c = registry.check(key, value);
        assertTrue(c.ok(), key + " = " + value + ": " + c.error());
        return c.value();
    }

    @Test
    void anUnknownKeyIsRefused() {
        assertTrue(refused("combat.nothing", F.booleanNode(true)).contains("no such setting"));
    }

    @Test
    void booleansAreOnlyTrueOrFalse() {
        accepted("game.admin.plays", F.booleanNode(true));
        assertTrue(refused("game.admin.plays", F.textNode("true")).contains("true or false"));
        refused("game.admin.plays", F.numberNode(1));
    }

    @Test
    void wholeNumbersMustBeWholeAndWithinTheirLimits() {
        assertEquals(6, accepted("game.lobby.codeLength", F.numberNode(6)).asInt());
        assertEquals(8, accepted("game.lobby.codeLength", F.numberNode(8.0)).asInt());
        assertTrue(refused("game.lobby.codeLength", F.numberNode(3)).contains("at least 4"));
        assertTrue(refused("game.lobby.codeLength", F.numberNode(13)).contains("at most 12"));
        assertTrue(refused("game.lobby.codeLength", F.numberNode(6.5)).contains("whole number"));
        refused("game.lobby.codeLength", F.textNode("6"));
        assertTrue(refused("combat.whiteout.moneyLossPct", F.numberNode(101)).contains("at most 100"));
        assertTrue(refused("trainer.start.money", F.numberNode(-1)).contains("at least 0"));
        assertEquals(31, accepted("pokemon.ivs.fixedValue", F.numberNode(31)).asInt());
        refused("pokemon.ivs.fixedValue", F.numberNode(32));
    }

    @Test
    void decimalsAreNumbersWithinTheirLimits() {
        assertEquals(1.5, accepted("combat.crit.multiplier", F.numberNode(1.5)).asDouble());
        assertEquals(2.0, accepted("combat.crit.multiplier", F.numberNode(2)).asDouble());
        assertTrue(refused("combat.crit.multiplier", F.textNode("1.5")).contains("must be a number"));
        refused("economy.sellRatio", F.numberNode(1.5));
        refused("combat.crit.multiplier", F.numberNode(-0.5));
    }

    @Test
    void anEnumIsOneOfItsValuesAndTheMessageNamesThem() {
        accepted("combat.ai.level", F.textNode("hard"));
        String why = refused("combat.ai.level", F.textNode("extreme"));
        assertTrue(why.contains("easy, normal, hard") && why.contains("extreme"), why);
        refused("combat.ai.level", F.numberNode(3));
        refused("combat.ai.level", F.textNode("HARD"));
    }

    @Test
    void textSettingsHaveALengthAndSometimesAForm() {
        accepted("world.movement.formula", F.textNode("2d6+STR"));
        assertTrue(refused("world.movement.formula", F.textNode("roll a die")).contains("dice formula"));
        accepted("cards.export.defaultSizeMm", F.textNode("70x120"));
        refused("cards.export.defaultSizeMm", F.textNode("63 by 88"));
        refused("economy.currency.symbol", F.textNode(""));
        refused("economy.currency.symbol", F.textNode("too long"));
        refused("game.ruleset.default", F.textNode("casual"));
        accepted("game.ruleset.default", F.textNode("builtin:casual"));
        accepted("game.ruleset.default", F.textNode("0192f2a0-1c2d-7abc-8def-0123456789ab"));
    }

    @Test
    void aCsvListIsCleanedAndItsEntriesAreChecked() {
        assertEquals("playerSubmit,adminEnter", accepted("dice.input.modes", F.textNode(" playerSubmit , adminEnter ")).asText());
        assertEquals("digital", accepted("dice.input.modes", F.arrayNode().add("digital")).asText());
        assertTrue(refused("dice.input.modes", F.textNode("playerSubmit,telepathy")).contains("telepathy"));
        assertTrue(refused("dice.input.modes", F.textNode("")).contains("at least 1"));
        assertTrue(refused("dice.input.modes", F.textNode("digital,digital")).contains("twice"));
        assertTrue(refused("dice.input.modes", F.textNode("digital,,adminEnter")).contains("empty"));
        accepted("trainer.start.items", F.textNode("pokeBall×5, potion×3, repel"));
        refused("trainer.start.items", F.textNode("poke ball"));
        accepted("app.language.gameTextFallback", F.textNode("pt_br,en_us"));
        refused("app.language.gameTextFallback", F.textNode("portuguese"));
    }

    @Test
    void aNumberListIsAnArrayOfWholeNumbers() {
        assertEquals("[5,10,15,25]", accepted("combat.crit.chancesPct", F.textNode("5, 10, 15, 25")).toString());
        assertEquals("[1,2,3,4]", accepted("combat.crit.chancesPct", JSON.valueToTree(List.of(1, 2, 3, 4))).toString());
        assertTrue(refused("combat.crit.chancesPct", JSON.valueToTree(List.of(5, 10, 15))).contains("exactly 4"));
        assertTrue(refused("combat.crit.chancesPct", JSON.valueToTree(List.of(5, 10, 15, 250))).contains("250"));
        refused("combat.crit.chancesPct", F.textNode("five,ten"));
        accepted("dice.available", JSON.valueToTree(List.of(6, 20)));
        assertTrue(refused("dice.available", JSON.valueToTree(List.of(6, 6))).contains("twice"));
        refused("dice.available", JSON.valueToTree(List.of(1)));
    }

    @Test
    void forceDieIsAnObjectOfDiceSizes() throws IOException {
        accepted("dice.forceDie", JSON.readTree("{\"catch\": 20}"));
        accepted("dice.forceDie", JSON.readTree("{}"));
        assertTrue(refused("dice.forceDie", JSON.readTree("{\"catch\": 1}")).contains("die size"));
        refused("dice.forceDie", JSON.readTree("{\"catch\": \"d20\"}"));
        refused("dice.forceDie", JSON.readTree("[20]"));
    }

    @Test
    void dataIsATableAListOrAName() throws IOException {
        accepted("combat.damage.powerTable", JSON.readTree("{\"40\": \"1d6\"}"));
        accepted("trainer.classes.set", JSON.readTree("[{\"id\": \"ranger\"}]"));
        accepted("combat.typeChart", F.textNode("official"));
        refused("trainer.classes.set", F.numberNode(3));
        refused("trainer.classes.set", F.booleanNode(true));
        StringBuilder big = new StringBuilder("[");
        for (int i = 0; i < 40000; i++) {
            big.append("\"0123456789\",");
        }
        big.append("1]");
        assertTrue(refused("trainer.classes.set", JSON.readTree(big.toString())).contains("too large"));
    }

    @Test
    void anEmptyValueIsRefused() {
        refused("game.admin.plays", null);
        refused("combat.ai.level", JsonNodeFactory.instance.nullNode());
    }

    @Test
    void checkAllCollectsEveryError() {
        Map<String, JsonNode> raw = new LinkedHashMap<>();
        raw.put("combat.ai.level", F.textNode("hard"));
        raw.put("combat.crit.multiplier", F.textNode("lots"));
        raw.put("nothing.here", F.booleanNode(true));
        List<Map<String, String>> errors = new ArrayList<>();
        Map<String, JsonNode> kept = registry.checkAll(raw, errors);
        assertEquals(1, kept.size());
        assertEquals(2, errors.size());
        assertEquals("combat.crit.multiplier", errors.get(0).get("key"));
    }

    @Test
    void withoutDefaultsKeepsOnlyWhatDiffers() {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put("combat.crit.multiplier", F.numberNode(1.5));
        values.put("combat.ai.level", F.textNode("hard"));
        values.put("combat.crit.chancesPct", JSON.valueToTree(List.of(5, 10, 15, 25)));
        Map<String, JsonNode> diff = registry.withoutDefaults(values);
        assertEquals(List.of("combat.ai.level"), List.copyOf(diff.keySet()));
    }

    // ---------------------------------------------------------------- the built-in rule sets

    @Test
    void theFivePresetsExistAndEachSettingIsValid() {
        assertEquals(List.of("builtin:defaults", "builtin:casual", "builtin:hardcore", "builtin:atTheTable", "builtin:remote"),
                builtins.all().stream().map(BuiltinRuleSets.Builtin::id).toList());
        assertTrue(builtins.find("builtin:defaults").orElseThrow().settings().isEmpty());
        assertNotNull(builtins.find("builtin:hardcore").orElseThrow().settings().get("trainer.ko.mode"));
    }

    @Test
    void theRowsOfChapter12_1AreWhatThePresetsDo() {
        Map<String, JsonNode> casual = builtins.find("builtin:casual").orElseThrow().settings();
        assertEquals("digital", casual.get("dice.input.modes").asText());
        assertFalse(casual.get("dice.approval.required").asBoolean());
        assertEquals("easy", casual.get("combat.ai.level").asText());
        assertEquals(0, casual.get("combat.whiteout.moneyLossPct").asInt());
        assertFalse(casual.get("world.fogOfWar").asBoolean());

        Map<String, JsonNode> hardcore = builtins.find("builtin:hardcore").orElseThrow().settings();
        assertEquals("playerSubmit,adminEnter", hardcore.get("dice.input.modes").asText());
        assertTrue(hardcore.get("dice.approval.required").asBoolean());
        assertEquals(60, hardcore.get("combat.turnTimer.seconds").asInt());
        assertTrue(hardcore.get("combat.turnTimer.enabled").asBoolean());
        assertEquals("deathSaves", hardcore.get("trainer.ko.mode").asText());
        assertEquals(50, hardcore.get("combat.whiteout.moneyLossPct").asInt());
        assertEquals("hard", hardcore.get("combat.ai.level").asText());
        assertEquals(30, hardcore.get("world.boss.oneIn").asInt());
        assertEquals(100, hardcore.get("economy.healer.price").asInt());

        Map<String, JsonNode> table = builtins.find("builtin:atTheTable").orElseThrow().settings();
        assertEquals("physical", table.get("cards.mode").asText());
        assertEquals("playerSubmit,adminEnter", table.get("dice.input.modes").asText());
        assertTrue(table.get("dice.approval.required").asBoolean());

        Map<String, JsonNode> remote = builtins.find("builtin:remote").orElseThrow().settings();
        assertEquals(120, remote.get("combat.turnTimer.seconds").asInt());
        assertTrue(remote.get("combat.turnTimer.enabled").asBoolean());
        assertTrue(remote.get("app.push.enabled").asBoolean());
        assertTrue(remote.get("dice.input.modes").asText().contains("digital"));
    }
}
