/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.reference;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Where the reference data (chapter 19.2) is kept. The API talks to this interface; the SQL adapter implements it with
 * Hibernate and Panache, and a MongoDB adapter follows in phase 34 (chapter 16.3).
 */
public interface ReferenceRepository {

    /** The kinds of entry that share one table. */
    enum EntryKind {
        MOVE, ITEM, ABILITY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One page of a list; {@code next} is the cursor of the following page, or null at the end. */
    record Page<T>(List<T> items, String next) {
    }

    /** Which data bundle the store holds ({@code hash} is the {@code outputHash} of MANIFEST.json). */
    record LoadedBundle(String hash, int format, String cobblemon, Instant loadedAt) {
    }

    /**
     * One species with all its forms.
     *
     * @param data       the merged species (stats, abilities, forms…) from {@code species.json}
     * @param derived    stage badge, weaknesses, signature moves (may be null)
     * @param evolutions the evolution rules that start from this species (may be null)
     * @param learnsets  moves by form and method (may be null)
     */
    record SpeciesRecord(String id, int dex, String name, int generation, List<String> types, boolean playable, JsonNode data,
                         JsonNode derived, JsonNode evolutions, JsonNode learnsets) {
    }

    /** A move, item or ability. {@code group} is the move's type or the item's category (null for abilities). */
    record NamedRecord(EntryKind kind, String id, String name, String group, JsonNode data) {
    }

    /**
     * Which species to list. Empty sets mean "no filter".
     *
     * @param generations     National Dex generations (1 to 9)
     * @param types           lower-case type ids
     * @param primaryTypeOnly match the first type only (default: either type)
     * @param query           part of the name or id
     * @param playable        only species with complete assets (null: all)
     */
    record SpeciesFilter(Set<Integer> generations, Set<String> types, boolean primaryTypeOnly, String query, Boolean playable) {

        public SpeciesFilter {
            generations = generations == null ? Set.of() : Set.copyOf(generations);
            types = types == null ? Set.of() : Set.copyOf(types);
        }

        public static SpeciesFilter all() {
            return new SpeciesFilter(Set.of(), Set.of(), false, null, null);
        }
    }

    /** Everything the loader read from a bundle, ready to store. */
    record ReferenceBundle(LoadedBundle info, List<SpeciesRecord> species, List<NamedRecord> entries, Map<String, JsonNode> documents) {
    }

    /** Row counts of the reference tables. */
    record Counts(long species, long moves, long items, long abilities, long documents) {
    }

    /** Which bundle is in the store now, if any. */
    Optional<LoadedBundle> loadedBundle();

    /** Replaces everything with this bundle, all or nothing. */
    void replaceAll(ReferenceBundle bundle);

    /** Species ordered by Dex number, then id. The cursor is the {@code next} of the previous page. */
    Page<SpeciesRecord> findSpecies(SpeciesFilter filter, String cursor, int limit);

    Optional<SpeciesRecord> species(String id);

    /** The species with these National Dex numbers, ordered by Dex number. */
    List<SpeciesRecord> speciesByDex(Collection<Integer> dexNumbers);

    /** The species with these ids (unknown ids are left out), ordered by Dex number. */
    List<SpeciesRecord> speciesByIds(Collection<String> ids);

    /** Moves, items or abilities ordered by id. {@code group} is the move type or the item category; {@code query} searches the name. */
    Page<NamedRecord> findEntries(EntryKind kind, String group, String query, String cursor, int limit);

    Optional<NamedRecord> entry(EntryKind kind, String id);

    /** The entries with these ids (unknown ids are left out). */
    List<NamedRecord> entries(EntryKind kind, Collection<String> ids);

    Optional<JsonNode> document(String name);

    /** How many rows each table holds. */
    Counts counts();
}
