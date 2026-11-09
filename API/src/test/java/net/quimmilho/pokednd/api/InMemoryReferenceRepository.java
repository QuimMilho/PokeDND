/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;

/** The reference data in memory, for tests that need species and moves but no database. */
public class InMemoryReferenceRepository implements ReferenceRepository {

    private LoadedBundle info;
    private final Map<String, SpeciesRecord> species = new LinkedHashMap<>();
    private final Map<String, NamedRecord> entries = new LinkedHashMap<>();
    private final Map<String, JsonNode> documents = new LinkedHashMap<>();

    public InMemoryReferenceRepository(ReferenceBundle bundle) {
        replaceAll(bundle);
    }

    @Override
    public Optional<LoadedBundle> loadedBundle() {
        return Optional.ofNullable(info);
    }

    @Override
    public void replaceAll(ReferenceBundle bundle) {
        info = bundle.info();
        species.clear();
        entries.clear();
        documents.clear();
        bundle.species().forEach(s -> species.put(s.id(), s));
        bundle.entries().forEach(e -> entries.put(e.kind().id() + ":" + e.id(), e));
        documents.putAll(bundle.documents());
    }

    @Override
    public Page<SpeciesRecord> findSpecies(SpeciesFilter f, String cursor, int limit) {
        List<SpeciesRecord> all = species.values().stream().filter(s -> f.generations().isEmpty() || f.generations().contains(s.generation()))
                .filter(s -> f.types().isEmpty() || (f.primaryTypeOnly() ? f.types().contains(s.types().get(0))
                        : s.types().stream().anyMatch(f.types()::contains)))
                .filter(s -> f.query() == null || s.name().toLowerCase(Locale.ROOT).contains(f.query().toLowerCase(Locale.ROOT)))
                .filter(s -> f.playable() == null || s.playable() == f.playable())
                .sorted(Comparator.comparingInt(SpeciesRecord::dex).thenComparing(SpeciesRecord::id)).toList();
        List<SpeciesRecord> items = all.stream().limit(limit).toList();
        return new Page<>(items, all.size() > limit ? "more" : null);
    }

    @Override
    public Optional<SpeciesRecord> species(String id) {
        return Optional.ofNullable(species.get(id));
    }

    @Override
    public List<SpeciesRecord> speciesByDex(Collection<Integer> dexNumbers) {
        return species.values().stream().filter(s -> dexNumbers.contains(s.dex()))
                .sorted(Comparator.comparingInt(SpeciesRecord::dex).thenComparing(SpeciesRecord::id)).toList();
    }

    @Override
    public List<SpeciesRecord> speciesByIds(Collection<String> ids) {
        List<SpeciesRecord> out = new ArrayList<>();
        ids.forEach(i -> {
            if (species.containsKey(i)) {
                out.add(species.get(i));
            }
        });
        out.sort(Comparator.comparingInt(SpeciesRecord::dex).thenComparing(SpeciesRecord::id));
        return out;
    }

    @Override
    public Page<NamedRecord> findEntries(EntryKind kind, String group, String query, String cursor, int limit) {
        List<NamedRecord> all = entries.values().stream().filter(e -> e.kind() == kind).filter(e -> group == null || group.equalsIgnoreCase(e.group()))
                .sorted(Comparator.comparing(NamedRecord::id)).toList();
        return new Page<>(all.stream().limit(limit).toList(), all.size() > limit ? "more" : null);
    }

    @Override
    public Optional<NamedRecord> entry(EntryKind kind, String id) {
        return Optional.ofNullable(entries.get(kind.id() + ":" + id));
    }

    @Override
    public List<NamedRecord> entries(EntryKind kind, Collection<String> ids) {
        List<NamedRecord> out = new ArrayList<>();
        ids.forEach(i -> entry(kind, i).ifPresent(out::add));
        return out;
    }

    @Override
    public Optional<JsonNode> document(String name) {
        return Optional.ofNullable(documents.get(name));
    }

    @Override
    public Counts counts() {
        return new Counts(species.size(), entries.values().stream().filter(e -> e.kind() == EntryKind.MOVE).count(),
                entries.values().stream().filter(e -> e.kind() == EntryKind.ITEM).count(),
                entries.values().stream().filter(e -> e.kind() == EntryKind.ABILITY).count(), documents.size());
    }
}
