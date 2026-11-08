/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.quimmilho.pokednd.core.importer.FileReport.Issue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The importer on small fixture files that contain each known quirk, so no real data is needed. */
class RawReaderFixtureTest {

    private static final String BOM = "﻿";

    @TempDir
    Path dir;

    private void write(String file, String text) throws IOException {
        Path p = dir.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    private static final String POKEDEX_HEADER = "National Dex,Pokémon Name,Type,HP,Attack,Defense,Special Attack,Special Defense,Speed,Total,"
            + "Type I,Type II,Ability I,Ability II,Hidden Ability,Height (ft),Height (m),Weight (lbs),Weight (kg),HP EVs,Attcak EVs,"
            + "Defense EVs,Special Attack EVs,Special Defense EVs,Speed EVs,Experience Value,Catch Rate,Experience Growth,Base Friendship,"
            + "Pokedex Color,Gender Ratio,Egg Cycles,Egg Group I,Egg Group II,Evolution Method\n";

    private static String pokedexRow(String dex, String name, String type, String t1, String t2, String gender, String egg2, String evo) {
        return dex + "," + name + "," + type + ",45,49,49,65,65,45,318," + t1 + "," + t2 + ",Overgrow,,Chlorophyll,2.3,0.7,15.2,6.9,"
                + "0,1,0,0,0,0,64,45,Medium Slow,50,Green," + gender + ",20,Monster," + egg2 + "," + evo + "\n";
    }

    // ---------------------------------------------------------------- pokedex.csv

    @Test
    void pokedexSplitsTypesRepairsTheHeaderAndTurnsBlankAndDashIntoNone() throws IOException {
        write("pokedex.csv", BOM + POKEDEX_HEADER
                + pokedexRow("1", "Bulbasaur", "Grass/Poison", "Grass", "Poison", "87.5% M / 12.5% F", "Grass", "Ivysaur (Level 16)")
                + pokedexRow("4", "Charmander", "Fire", "Fire", "", "87.5% M / 12.5% F", "", "")
                + pokedexRow("132", "Ditto", "Normal/—", "Normal", "—", "Genderless", "—", "—"));
        RawReader.Result<Raw.PokedexRow> r = RawReader.readPokedex(dir);

        assertEquals(3, r.records().size());
        Raw.PokedexRow bulba = r.records().get(0);
        assertEquals("Grass", bulba.type1());
        assertEquals(Optional.of("Poison"), bulba.type2());
        assertEquals(Optional.of("Grass"), bulba.eggGroup2());
        assertEquals(Optional.of("Ivysaur (Level 16)"), bulba.evolutionText());
        assertEquals(1, bulba.evYield().attack(), "the misspelt Attcak EVs column is read as Attack EVs");
        assertEquals(Optional.of(87.5), bulba.malePercent());
        assertEquals(Optional.empty(), bulba.ability2());
        assertEquals(Optional.of("Chlorophyll"), bulba.hiddenAbility());

        Raw.PokedexRow charmander = r.records().get(1);
        assertEquals(Optional.empty(), charmander.type2(), "a blank second type is none");
        assertEquals(Optional.empty(), charmander.evolutionText());

        Raw.PokedexRow ditto = r.records().get(2);
        assertEquals(Optional.empty(), ditto.type2(), "a dash second type is none");
        assertEquals(Optional.empty(), ditto.malePercent(), "Genderless has no male percentage");
        assertEquals(Optional.empty(), ditto.eggGroup2());
        assertEquals(Optional.empty(), ditto.evolutionText());

        FileReport report = r.report();
        assertEquals(3, report.rowsRead());
        assertEquals(3, report.rowsKept());
        assertTrue(report.skipped().isEmpty());
        List<String> fixes = report.fixes().stream().map(Issue::message).toList();
        assertTrue(fixes.stream().anyMatch(m -> m.contains("byte-order mark")), fixes.toString());
        assertTrue(fixes.stream().anyMatch(m -> m.contains("Attcak EVs")), fixes.toString());
    }

    @Test
    void pokedexReportsATypeTextThatDisagreesWithTheTypeColumns() throws IOException {
        write("pokedex.csv", POKEDEX_HEADER + pokedexRow("1", "Bulbasaur", "Grass/Poison", "Grass", "Fire", "50% M / 50% F", "", ""));
        RawReader.Result<Raw.PokedexRow> r = RawReader.readPokedex(dir);
        assertEquals(Optional.of("Poison"), r.records().get(0).type2(), "the combined Type text wins");
        List<String> fixes = r.report().fixes().stream().map(Issue::message).toList();
        assertEquals(1, fixes.stream().filter(m -> m.contains("differs from Type I/II")).count(), fixes.toString());
    }

    @Test
    void pokedexSkipsRowsThatCannotBeUsedAndSaysWhy() throws IOException {
        write("pokedex.csv", POKEDEX_HEADER
                + pokedexRow("1", "Bulbasaur", "Grass", "Grass", "", "50% M / 50% F", "", "")
                + pokedexRow("x", "Broken", "Grass", "Grass", "", "50% M / 50% F", "", "")
                + pokedexRow("3", "NoType", "—", "", "", "50% M / 50% F", "", "")
                + pokedexRow("4", "BadGender", "Fire", "Fire", "", "lots", "", "")
                + "5,TooShort\n");
        RawReader.Result<Raw.PokedexRow> r = RawReader.readPokedex(dir);
        assertEquals(1, r.records().size());
        assertEquals(5, r.report().rowsRead());
        assertEquals(4, r.report().skipped().size());
        List<String> why = r.report().skipped().stream().map(Issue::message).toList();
        assertTrue(why.get(0).contains("National Dex \"x\" is not a whole number"), why.toString());
        assertTrue(why.get(1).contains("one or two types"), why.toString());
        assertTrue(why.get(2).contains("Gender Ratio"), why.toString());
        assertTrue(why.get(3).contains("2 cells"), why.toString());
        assertEquals(List.of(2, 3, 4, 5), r.report().skipped().stream().map(Issue::row).toList());
    }

    @Test
    void aMissingColumnStopsTheImport() throws IOException {
        write("pokedex.csv", "National Dex,Pokémon Name\n1,Bulbasaur\n");
        ImportException e = assertThrows(ImportException.class, () -> RawReader.readPokedex(dir));
        assertTrue(e.getMessage().contains("column \"Type\" is missing"), e.getMessage());
    }

    @Test
    void aMissingFileStopsTheImportAndSaysHowToFixIt() {
        ImportException e = assertThrows(ImportException.class, () -> RawReader.readTms(dir));
        assertTrue(e.getMessage().contains("tm.csv"));
        assertTrue(e.getMessage().contains("node tools/extract.mjs"));
    }

    // ---------------------------------------------------------------- attack.csv

    @Test
    void attacksSkipTheRowWithoutStatsAndReadADashAccuracyAsNeverMisses() throws IOException {
        write("attack.csv", BOM + "move_id,name,type,category,power,accuracy,pp,priority,target,description\n"
                + "\"10000000voltthunderbolt\",\"10,000,000 Volt Thunderbolt\",Electric,Special,195,—,1,0,normal,\"Z-Power, with a comma.\"\n"
                + "absorb,Absorb,Grass,Special,20,100,25,0,normal,Drains HP.\n"
                + "nihillight,Nihil Light,,,,,,,,The user attacks with a powerful light.\n"
                + "\n"
                + "quickattack,Quick Attack,Normal,Physical,40,100,30,1,normal,Always first.\n");
        RawReader.Result<Raw.Move> r = RawReader.readAttacks(dir);

        assertEquals(3, r.records().size());
        assertEquals(Optional.empty(), r.records().get(0).accuracy(), "— means never misses");
        assertEquals("10,000,000 Volt Thunderbolt", r.records().get(0).name());
        assertEquals("Z-Power, with a comma.", r.records().get(0).description());
        assertEquals(Optional.of(100), r.records().get(1).accuracy());
        assertEquals(1, r.records().get(2).priority());

        assertEquals(4, r.report().rowsRead(), "the blank line is not a row");
        assertEquals(1, r.report().skipped().size());
        Issue skipped = r.report().skipped().get(0);
        assertEquals(3, skipped.row());
        assertTrue(skipped.message().startsWith("nihillight: no type, category, power, accuracy, pp, priority, target"), skipped.message());
        assertTrue(r.report().fixes().stream().anyMatch(i -> i.message().contains("1 blank line")));
    }

    // ---------------------------------------------------------------- the other CSV files

    @Test
    void itemsLearnsetsAndTmsReadBlankAsNone() throws IOException {
        write("item.csv", BOM + "item_id,name,category,icon,description\n"
                + "potion,Potion,medicine,icons/items/medicine/potion.png,Restores 20 HP.\n"
                + "mystery,Mystery,candy,icons/items/candy/mystery.png,\n");
        write("pokemon-attacks.csv", BOM + "dex,pokemon,form,move_id,move,method,level,implemented\n"
                + "1,Bulbasaur,,tackle,Tackle,level,1,true\n"
                + "1,Bulbasaur,,curse,Curse,egg,,true\n"
                + "19,Rattata,Alola,tackle,Tackle,level,1,false\n"
                + "19,Rattata,Alola,dig,Dig,tm,x,false\n"
                + "19,Rattata,Alola,dig,Dig,tm,5,maybe\n");
        write("tm.csv", BOM + "move_id,name,type,source,obtain,icon\n"
                + "acidarmor,Acid Armor,poison,generated,,icons/tm/poison.png\n"
                + "acidspray,Acid Spray,poison,cobblemon,unlockable,icons/tm/poison.png\n");

        RawReader.Result<Raw.Item> items = RawReader.readItems(dir);
        assertEquals(Optional.empty(), items.records().get(1).description());
        assertEquals(Optional.of("Restores 20 HP."), items.records().get(0).description());

        RawReader.Result<Raw.LearnsetRow> learnsets = RawReader.readLearnsets(dir);
        assertEquals(3, learnsets.records().size());
        assertEquals(Optional.empty(), learnsets.records().get(0).form());
        assertEquals(Optional.of(1), learnsets.records().get(0).level());
        assertEquals(Optional.empty(), learnsets.records().get(1).level(), "egg moves have no level");
        assertEquals(Optional.of("Alola"), learnsets.records().get(2).form());
        assertFalse(learnsets.records().get(2).implemented());
        assertEquals(2, learnsets.report().skipped().size());
        assertTrue(learnsets.report().skipped().get(0).message().contains("level \"x\" is not a whole number"));
        assertTrue(learnsets.report().skipped().get(1).message().contains("implemented \"maybe\""));

        RawReader.Result<Raw.Tm> tms = RawReader.readTms(dir);
        assertEquals(Optional.empty(), tms.records().get(0).obtain());
        assertEquals(Optional.of("unlockable"), tms.records().get(1).obtain());
    }

    // ---------------------------------------------------------------- species JSON

    private static String speciesJson(int dex, String name, String extra) {
        return "{\"name\":\"" + name + "\",\"nationalPokedexNumber\":" + dex + ",\"primaryType\":\"grass\",\"secondaryType\":\"poison\","
                + "\"implemented\":true,\"baseStats\":{\"hp\":45,\"attack\":49,\"defence\":49,\"special_attack\":65,\"special_defence\":65,\"speed\":45},"
                + "\"evYield\":{\"hp\":0,\"attack\":0,\"defence\":0,\"special_attack\":1,\"special_defence\":0,\"speed\":0},"
                + "\"catchRate\":45,\"baseFriendship\":50,\"baseExperienceYield\":64,\"experienceGroup\":\"medium_slow\",\"eggCycles\":20,"
                + "\"eggGroups\":[\"monster\",\"grass\"],\"maleRatio\":0.875,\"height\":7,\"weight\":69,\"abilities\":[\"overgrow\",\"h:chlorophyll\"],"
                + "\"labels\":[\"gen1\"],\"moves\":[\"1:tackle\",\"tm:cut\"],\"evolutions\":[{\"result\":\"ivysaur\"}]" + extra + "}";
    }

    @Test
    void speciesAreIndexedByDexNumberAndBadFilesAreReported() throws IOException {
        String base = "data/cobblemon/species/";
        write(base + "generation1/bulbasaur.json", speciesJson(1, "Bulbasaur", ""));
        write(base + "generation1/ditto.json", speciesJson(132, "Ditto", "").replace("0.875", "-1").replace(",\"secondaryType\":\"poison\"", ""));
        write(base + "generation9/zz_copy.json", speciesJson(1, "Bulbasaur Again", ""));
        write(base + "generation1/broken.json", "{ not json");
        write(base + "generation1/nodex.json", "{\"name\":\"NoDex\"}");

        RawReader.SpeciesResult r = RawReader.readSpecies(dir);
        assertEquals(List.of(1, 132), List.copyOf(r.byDex().keySet()), "indexed and sorted by Dex");
        Raw.SpeciesFile bulba = r.byDex().get(1);
        assertEquals("Bulbasaur", bulba.name(), "the first file in path order wins a duplicate Dex number");
        assertEquals(Optional.of("poison"), bulba.secondaryType());
        assertEquals(Optional.of(0.875), bulba.maleRatio());
        assertEquals(List.of("monster", "grass"), bulba.eggGroups());
        assertEquals(List.of("1:tackle", "tm:cut"), bulba.moves());
        assertEquals(1, bulba.evolutions().size());
        assertTrue(bulba.implemented());
        assertEquals(1, bulba.evYield().specialAttack());
        assertEquals("data/cobblemon/species/generation1/bulbasaur.json", bulba.source());
        assertEquals("overgrow", bulba.json().get("abilities").get(0).asText(), "the whole file is kept");

        Raw.SpeciesFile ditto = r.byDex().get(132);
        assertEquals(Optional.empty(), ditto.maleRatio(), "-1 means genderless");
        assertEquals(Optional.empty(), ditto.secondaryType());

        assertEquals(5, r.report().rowsRead());
        assertEquals(2, r.report().rowsKept());
        List<String> why = r.report().skipped().stream().map(Issue::message).toList();
        assertEquals(3, why.size(), why.toString());
        assertTrue(why.stream().anyMatch(m -> m.contains("broken.json") && m.contains("not valid JSON")), why.toString());
        assertTrue(why.stream().anyMatch(m -> m.contains("nodex.json") && m.contains("nationalPokedexNumber")), why.toString());
        assertTrue(why.stream().anyMatch(m -> m.contains("zz_copy.json") && m.contains("Dex 1 is already used")), why.toString());
    }

    @Test
    void aSpeciesFileWithoutImplementedIsNotImplemented() throws IOException {
        write("data/cobblemon/species/generation1/a.json", speciesJson(1, "A", "").replace("\"implemented\":true,", ""));
        assertFalse(RawReader.readSpecies(dir).byDex().get(1).implemented());
    }

    // ---------------------------------------------------------------- everything together

    @Test
    void readingTwiceGivesEqualResults() throws IOException {
        write("pokedex.csv", BOM + POKEDEX_HEADER + pokedexRow("1", "Bulbasaur", "Grass/Poison", "Grass", "Poison", "87.5% M / 12.5% F", "Grass", "Ivysaur (Level 16)"));
        write("attack.csv", BOM + "move_id,name,type,category,power,accuracy,pp,priority,target,description\nabsorb,Absorb,Grass,Special,20,100,25,0,normal,x\nnihillight,N,,,,,,,,y\n");
        write("item.csv", BOM + "item_id,name,category,icon,description\npotion,Potion,medicine,p.png,x\n");
        write("pokemon-attacks.csv", BOM + "dex,pokemon,form,move_id,move,method,level,implemented\n1,Bulbasaur,,tackle,Tackle,level,1,true\n");
        write("tm.csv", BOM + "move_id,name,type,source,obtain,icon\nacidspray,Acid Spray,poison,cobblemon,unlockable,i.png\n");
        write("data/cobblemon/species/generation1/bulbasaur.json", speciesJson(1, "Bulbasaur", ""));

        Raw first = RawReader.read(dir);
        Raw second = RawReader.read(dir);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(List.of("attack.csv", "data/cobblemon/species", "item.csv", "pokedex.csv", "pokemon-attacks.csv", "tm.csv"),
                List.copyOf(first.reports().keySet()), "one report per input file");
        assertEquals(1, first.reports().get("attack.csv").skipped().size());
    }
}
