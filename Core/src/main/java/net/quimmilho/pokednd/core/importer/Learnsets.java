/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.importer.Forms.FormRef;
import net.quimmilho.pokednd.core.importer.Raw.LearnsetRow;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.FormUse;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Merged;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.MergedSpecies;

/**
 * The learnsets (chapter 5.5): for every species and form, the moves it learns by each method. The form text of each
 * row is turned into a form id with the table of {@link SpeciesMerger}. Rows of forms the game does not support (the
 * Gigantamax ones) are left out and counted.
 *
 * @param bySpecies species id, then form id, then the moves
 * @param skipped   rows left out because their form text is not mapped
 * @param rows      rows read in total
 */
public record Learnsets(SortedMap<String, SortedMap<String, Learnset>> bySpecies, int skipped, int rows) {

    /**
     * A move learnt at a level.
     *
     * @param level the level
     * @param move  move id
     */
    public record LevelMove(int level, String move) {
    }

    /**
     * What one form can learn. Every list is sorted and has no repeats.
     *
     * @param level      by level, then in the order of the data
     * @param egg        egg moves
     * @param tm         moves taught by a TM
     * @param tutor      moves taught by a tutor
     * @param legacy     moves from older games
     * @param special    special and event moves
     * @param formChange moves linked to a form change
     */
    public record Learnset(List<LevelMove> level, List<String> egg, List<String> tm, List<String> tutor, List<String> legacy,
                           List<String> special, List<String> formChange) {
        public Learnset {
            level = List.copyOf(level);
            egg = List.copyOf(egg);
            tm = List.copyOf(tm);
            tutor = List.copyOf(tutor);
            legacy = List.copyOf(legacy);
            special = List.copyOf(special);
            formChange = List.copyOf(formChange);
        }
    }

    /** Every move id mentioned anywhere, sorted. */
    public TreeSet<String> allMoves() {
        TreeSet<String> out = new TreeSet<>();
        for (SortedMap<String, Learnset> forms : bySpecies.values()) {
            for (Learnset l : forms.values()) {
                l.level().forEach(m -> out.add(m.move()));
                out.addAll(l.egg());
                out.addAll(l.tm());
                out.addAll(l.tutor());
                out.addAll(l.legacy());
                out.addAll(l.special());
                out.addAll(l.formChange());
            }
        }
        return out;
    }

    /** Builds the learnsets from the CSV rows and the merged form table. */
    public static Learnsets build(Raw raw, Merged merged) {
        Map<Integer, String> idByDex = new HashMap<>();
        for (MergedSpecies s : merged.species()) {
            idByDex.put(s.dex(), s.id());
        }
        Map<String, FormRef> formByKey = new HashMap<>();
        for (FormUse u : merged.formUses()) {
            u.form().ifPresent(f -> formByKey.put(u.dex() + "|" + u.text(), f));
        }

        record Acc(List<LevelMove> level, TreeSet<String> egg, TreeSet<String> tm, TreeSet<String> tutor, TreeSet<String> legacy,
                   TreeSet<String> special, TreeSet<String> formChange) {
            Acc() {
                this(new ArrayList<>(), new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), new TreeSet<>(),
                        new TreeSet<>());
            }
        }
        TreeMap<String, TreeMap<String, Acc>> acc = new TreeMap<>();
        int skipped = 0;
        for (LearnsetRow row : raw.learnsets()) {
            FormRef form = formByKey.get(row.dex() + "|" + row.form().orElse(""));
            String species = idByDex.get(row.dex());
            if (form == null || species == null) {
                skipped++;
                continue;
            }
            Acc a = acc.computeIfAbsent(species, k -> new TreeMap<>()).computeIfAbsent(form.id(), k -> new Acc());
            switch (row.method()) {
                case "level" -> {
                    LevelMove lm = new LevelMove(row.level().orElse(0), row.moveId());
                    if (!a.level().contains(lm)) {
                        a.level().add(lm);
                    }
                }
                case "egg" -> a.egg().add(row.moveId());
                case "tm" -> a.tm().add(row.moveId());
                case "tutor" -> a.tutor().add(row.moveId());
                case "legacy" -> a.legacy().add(row.moveId());
                case "special" -> a.special().add(row.moveId());
                case "form_change" -> a.formChange().add(row.moveId());
                default -> throw new ImportException("pokemon-attacks.csv: unknown learn method \"" + row.method() + "\" for "
                        + row.moveId() + " of Dex " + row.dex());
            }
        }
        TreeMap<String, SortedMap<String, Learnset>> out = new TreeMap<>();
        acc.forEach((species, forms) -> {
            TreeMap<String, Learnset> byForm = new TreeMap<>();
            forms.forEach((formId, a) -> {
                List<LevelMove> level = new ArrayList<>(a.level());
                level.sort(java.util.Comparator.comparingInt(LevelMove::level)); // stable: the data's order within a level
                byForm.put(formId, new Learnset(level, List.copyOf(a.egg()), List.copyOf(a.tm()), List.copyOf(a.tutor()),
                        List.copyOf(a.legacy()), List.copyOf(a.special()), List.copyOf(a.formChange())));
            });
            out.put(species, byForm);
        });
        return new Learnsets(java.util.Collections.unmodifiableSortedMap(out), skipped, raw.learnsets().size());
    }
}
