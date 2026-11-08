/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.Forms.FormRef;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Conflict;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Merged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The merge on tiny hand-made files: the CSV wins, gaps are filled from the JSON, forms are mapped. */
class SpeciesMergerFixtureTest {

    @TempDir
    Path dir;

    private static final String HEADER = "National Dex,Pokémon Name,Type,HP,Attack,Defense,Special Attack,Special Defense,Speed,Total,"
            + "Type I,Type II,Ability I,Ability II,Hidden Ability,Height (ft),Height (m),Weight (lbs),Weight (kg),HP EVs,Attack EVs,"
            + "Defense EVs,Special Attack EVs,Special Defense EVs,Speed EVs,Experience Value,Catch Rate,Experience Growth,Base Friendship,"
            + "Pokedex Color,Gender Ratio,Egg Cycles,Egg Group I,Egg Group II,Evolution Method\n";

    /** Bulbasaur-like row; {@code catchRate} is the only value the tests change. */
    private static String row(int dex, String name, int catchRate) {
        return dex + "," + name + ",Grass/Poison,45,49,49,65,65,45,318,Grass,Poison,Overgrow,,Chlorophyll,2.3,0.7,15.2,6.9,"
                + "0,0,0,1,0,0,64," + catchRate + ",Medium Slow,50,Green,87.5% M / 12.5% F,20,Monster,Grass,\n";
    }

    private static String json(int dex, String name, int catchRate, String extra) {
        return "{\"name\":\"" + name + "\",\"nationalPokedexNumber\":" + dex + ",\"implemented\":true,\"primaryType\":\"grass\","
                + "\"secondaryType\":\"poison\",\"baseStats\":{\"hp\":45,\"attack\":49,\"defence\":49,\"special_attack\":65,"
                + "\"special_defence\":65,\"speed\":45},\"evYield\":{\"hp\":0,\"attack\":0,\"defence\":0,\"special_attack\":1,"
                + "\"special_defence\":0,\"speed\":0},\"catchRate\":" + catchRate + ",\"baseFriendship\":50,\"baseExperienceYield\":64,"
                + "\"experienceGroup\":\"medium_slow\",\"eggCycles\":20,\"eggGroups\":[\"monster\",\"grass\"],\"maleRatio\":0.875,"
                + "\"height\":7,\"weight\":69,\"abilities\":[\"overgrow\",\"h:chlorophyll\"],\"labels\":[\"gen1\"],\"moves\":[]"
                + extra + "}";
    }

    private void write(String file, String text) throws IOException {
        Path p = dir.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    private Raw raw(List<Raw.LearnsetRow> learnsets) {
        RawReader.Result<Raw.PokedexRow> pokedex = RawReader.readPokedex(dir);
        RawReader.SpeciesResult species = RawReader.readSpecies(dir);
        assertTrue(pokedex.report().skipped().isEmpty(), pokedex.report().toString());
        assertTrue(species.report().skipped().isEmpty(), species.report().toString());
        return new Raw(pokedex.records(), List.of(), List.of(), learnsets, List.of(), species.byDex(), new TreeMap<>());
    }

    private static Raw.LearnsetRow learns(int dex, String name, String form) {
        return new Raw.LearnsetRow(dex, name, form.isEmpty() ? Optional.empty() : Optional.of(form), "tackle", "Tackle", "level",
                Optional.of(1), true);
    }

    private static Roster roster(String id, int dex, List<Roster.Form> forms) {
        TreeMap<Integer, Roster.Entry> map = new TreeMap<>();
        map.put(dex, new Roster.Entry(dex, id, id, forms));
        return new Roster("test", map);
    }

    @Test
    void whenCsvAndJsonDisagreeTheCsvWinsAndTheDifferenceIsReported() throws IOException {
        write("pokedex.csv", HEADER + row(1, "Bulbasaur", 45));
        write("data/cobblemon/species/generation1/bulbasaur.json", json(1, "Bulbasaur", 255, ""));
        Merged m = SpeciesMerger.merge(raw(List.of()), new Roster("test", new TreeMap<>()));

        assertEquals(45, m.species().get(0).catchRate(), "the CSV value is kept");
        assertEquals(List.of(new Conflict(1, "bulbasaur", "catchRate", "45", "255", "json")), m.conflicts());
        assertTrue(Reports.conflicts(m.conflicts()).contains("| 1 | bulbasaur | catchRate | 45 | 255 | json |"));
    }

    @Test
    void agreeingSourcesGiveNoConflict() throws IOException {
        write("pokedex.csv", HEADER + row(1, "Bulbasaur", 45));
        write("data/cobblemon/species/generation1/bulbasaur.json", json(1, "Bulbasaur", 45, ""));
        Merged m = SpeciesMerger.merge(raw(List.of()), new Roster("test", new TreeMap<>()));
        assertEquals(List.of(), m.conflicts());
    }

    @Test
    void gapsAreFilledFromTheJson() throws IOException {
        write("pokedex.csv", HEADER + row(1, "Bulbasaur", 45));
        write("data/cobblemon/species/generation1/bulbasaur.json", json(1, "Bulbasaur", 45,
                ",\"baseScale\":0.5,\"hitbox\":{\"width\":0.7,\"height\":0.9,\"fixed\":false},\"preEvolution\":\"x\","
                        + "\"behaviour\":{\"resting\":{\"canSleep\":true}},\"features\":[\"alolan\"],\"shoulderMountable\":true"));
        SpeciesMerger.MergedSpecies s = SpeciesMerger.merge(raw(List.of()), new Roster("test", new TreeMap<>())).species().get(0);

        assertEquals("bulbasaur", s.id());
        assertEquals(List.of("grass", "poison"), s.types());
        assertEquals(List.of("overgrow"), s.abilities());
        assertEquals(Optional.of("chlorophyll"), s.hiddenAbility());
        assertEquals("medium_slow", s.growth());
        assertEquals(Optional.of(0.5), s.baseScale());
        assertEquals(Optional.of(new SpeciesMerger.Hitbox(0.7, 0.9, false)), s.hitbox());
        assertEquals(Optional.of("x"), s.preEvolution());
        assertEquals(List.of("alolan"), s.features());
        assertTrue(s.shoulderMountable());
        assertTrue(s.behaviour().path("resting").path("canSleep").asBoolean());
        assertEquals(List.of("gen1"), s.labels());
        assertFalse(s.playable(), "not in the roster");
    }

    @Test
    void formTextsBecomeFormIdsAndOnlyRosterFormsArePlayable() throws IOException {
        write("pokedex.csv", HEADER + row(19, "Rattata", 255));
        write("data/cobblemon/species/generation1/rattata.json", json(19, "Rattata", 255, ""));
        Roster roster = roster("rattata", 19, List.of(new Roster.Form("rattata_base", List.of()),
                new Roster.Form("rattata_alolan", List.of("alolan"))));
        Merged m = SpeciesMerger.merge(raw(List.of(learns(19, "Rattata", ""), learns(19, "Rattata", "Alola"),
                learns(19, "Rattata", "Galar"), learns(19, "Rattata", "Alola"), learns(19, "Rattata", "Nonsense"),
                learns(19, "Rattata", "Low-Key-Gmax"))), roster);

        List<FormRef> forms = m.species().get(0).forms();
        assertEquals(List.of("rattata_base", "rattata_alolan", "rattata_galarian"), forms.stream().map(FormRef::id).toList());
        assertEquals(List.of(true, true, false), forms.stream().map(FormRef::playable).toList());
        assertTrue(m.species().get(0).playable());
        assertEquals(new TreeMap<>(java.util.Map.of("Nonsense", 1, "Low-Key-Gmax", 1)), m.unmapped(),
                "an unknown value and a Gigantamax one are reported, never guessed");
        assertEquals(2, m.formUses().stream().filter(u -> u.text().equals("Alola")).mapToInt(SpeciesMerger.FormUse::rows).sum());
    }

    @Test
    void theReportsAreTheSameTextEveryTime() throws IOException {
        write("pokedex.csv", HEADER + row(1, "Bulbasaur", 45));
        write("data/cobblemon/species/generation1/bulbasaur.json", json(1, "Bulbasaur", 255, ""));
        Raw raw = raw(List.of(learns(1, "Bulbasaur", "Hisui")));
        Roster none = new Roster("test", new TreeMap<>());
        Merged a = SpeciesMerger.merge(raw, none);
        Merged b = SpeciesMerger.merge(raw, none);
        assertEquals(Reports.conflicts(a.conflicts()), Reports.conflicts(b.conflicts()));
        assertEquals(Reports.forms(a.formUses(), a.unmapped()), Reports.forms(b.formUses(), b.unmapped()));
    }
}
