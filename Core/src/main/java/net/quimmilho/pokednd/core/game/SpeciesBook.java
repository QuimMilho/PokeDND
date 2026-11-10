/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.quimmilho.pokednd.core.importer.Bundle;
import net.quimmilho.pokednd.core.importer.Evolutions;
import net.quimmilho.pokednd.core.importer.Learnsets;
import net.quimmilho.pokednd.core.importer.Raw;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.MergedSpecies;
import net.quimmilho.pokednd.core.rules.Growth;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.StatBlock;

/**
 * What the progress rules know about each species (chapters 5.2 to 5.5): base stats, types, how fast it levels, how much XP and which EVs
 * it gives when defeated, its Catch Rate and base friendship, its abilities, the moves it learns by level, and what it evolves into. Like
 * {@link MoveBook} for moves, it is made from the data bundle ({@link #of(Bundle)}) or, in tests, from a few species.
 */
public final class SpeciesBook {

    /**
     * One species.
     *
     * @param baseExperience  the XP value used when it is defeated (chapter 5.3)
     * @param evYield         the EVs it gives when defeated
     * @param baseFriendship  friendship at the start
     * @param abilities       the ordinary abilities, in the order of the data
     * @param hiddenAbility   the hidden ability, or null
     * @param levelMoves      the moves it learns by level, in order
     * @param evolutions      what it evolves into and when
     */
    public record Species(String id, String name, List<PokemonType> types, StatBlock base, Growth growth, int baseExperience, StatBlock evYield,
                          int catchRate, int baseFriendship, List<String> abilities, String hiddenAbility, List<Learnsets.LevelMove> levelMoves,
                          List<Evolutions.Rule> evolutions) {
        public Species {
            types = List.copyOf(types);
            abilities = List.copyOf(abilities);
            levelMoves = List.copyOf(levelMoves);
            evolutions = List.copyOf(evolutions);
        }

        /** A species with only what battles and levelling need; no evolutions, no moves. */
        public static Species simple(String id, String name, List<PokemonType> types, StatBlock base, Growth growth, int baseExperience) {
            return new Species(id, name, types, base, growth, baseExperience, StatBlock.all(0), 45, 50, List.of(), null, List.of(), List.of());
        }

        public Species withEvolutions(List<Evolutions.Rule> value) {
            return new Species(id, name, types, base, growth, baseExperience, evYield, catchRate, baseFriendship, abilities, hiddenAbility, levelMoves, value);
        }

        public Species withLevelMoves(List<Learnsets.LevelMove> value) {
            return new Species(id, name, types, base, growth, baseExperience, evYield, catchRate, baseFriendship, abilities, hiddenAbility, value, evolutions);
        }

        public Species withEvYield(StatBlock value) {
            return new Species(id, name, types, base, growth, baseExperience, value, catchRate, baseFriendship, abilities, hiddenAbility, levelMoves, evolutions);
        }

        public Species withAbilities(List<String> value, String hidden) {
            return new Species(id, name, types, base, growth, baseExperience, evYield, catchRate, baseFriendship, value, hidden, levelMoves, evolutions);
        }
    }

    private final Map<String, Species> byId;
    private final Map<String, Learnsets.Learnset> learnsets;

    private SpeciesBook(Map<String, Species> byId, Map<String, Learnsets.Learnset> learnsets) {
        this.byId = byId;
        this.learnsets = learnsets;
    }

    /** A book of these species. */
    public static SpeciesBook of(Collection<Species> species) {
        Map<String, Species> map = new LinkedHashMap<>();
        for (Species s : species) {
            map.put(s.id(), s);
        }
        return new SpeciesBook(map, Map.of());
    }

    /**
     * The same species with the moves they learn other than by level: TM, tutor, egg and legacy (chapter 5.5). A TM or a tutor teaches only
     * a move that is on the list of the species. Without a list for a species nothing is taught to it.
     */
    public SpeciesBook withLearnsets(Map<String, Learnsets.Learnset> value) {
        return new SpeciesBook(byId, Map.copyOf(value));
    }

    /** The book of the data bundle: every species with its stats, level moves and evolution rules. */
    public static SpeciesBook of(Bundle bundle) {
        Map<String, List<Evolutions.Rule>> evolutions = new HashMap<>();
        for (Evolutions.Rule rule : bundle.evolutions().rules()) {
            evolutions.computeIfAbsent(rule.from(), k -> new ArrayList<>()).add(rule);
        }
        List<Species> all = new ArrayList<>();
        Map<String, Learnsets.Learnset> learnsets = new HashMap<>();
        for (MergedSpecies m : bundle.merged().species()) {
            List<PokemonType> types = new ArrayList<>();
            m.types().forEach(t -> PokemonType.parse(t).ifPresent(types::add));
            var forms = bundle.learnsets().bySpecies().get(m.id());
            Learnsets.Learnset learnset = forms == null || forms.isEmpty() ? null : forms.getOrDefault(m.id() + "_base", forms.get(forms.firstKey()));
            if (learnset != null) {
                learnsets.put(m.id(), withAllForms(learnset, forms.values()));
            }
            all.add(new Species(m.id(), m.name(), types, block(m.baseStats()), Growth.parse(m.growth()).orElse(Growth.MEDIUM_FAST), m.baseExperience(),
                    block(m.evYield()), m.catchRate(), m.baseFriendship(), m.abilities(), m.hiddenAbility().orElse(null),
                    learnset == null ? List.of() : learnset.level(), evolutions.getOrDefault(m.id(), List.of())));
        }
        return of(all).withLearnsets(learnsets);
    }

    /**
     * The learnset of the base form with the TM, tutor, egg, legacy, special and form-change moves of every form added: an owned Pokémon does
     * not keep track of its form, so a move that any form of the species learns that way can be taught to it.
     */
    private static Learnsets.Learnset withAllForms(Learnsets.Learnset base, Collection<Learnsets.Learnset> forms) {
        List<String> tm = new ArrayList<>(), tutor = new ArrayList<>(), egg = new ArrayList<>(), legacy = new ArrayList<>(), special = new ArrayList<>(),
                change = new ArrayList<>();
        for (Learnsets.Learnset l : java.util.stream.Stream.concat(java.util.stream.Stream.of(base), forms.stream()).toList()) {
            l.tm().stream().filter(x -> !tm.contains(x)).forEach(tm::add);
            l.tutor().stream().filter(x -> !tutor.contains(x)).forEach(tutor::add);
            l.egg().stream().filter(x -> !egg.contains(x)).forEach(egg::add);
            l.legacy().stream().filter(x -> !legacy.contains(x)).forEach(legacy::add);
            l.special().stream().filter(x -> !special.contains(x)).forEach(special::add);
            l.formChange().stream().filter(x -> !change.contains(x)).forEach(change::add);
        }
        return new Learnsets.Learnset(base.level(), egg, tm, tutor, legacy, special, change);
    }

    private static StatBlock block(Raw.Stats s) {
        return new StatBlock(s.hp(), s.attack(), s.defense(), s.specialAttack(), s.specialDefense(), s.speed());
    }

    /** The species with this id, if the book has it. */
    public Optional<Species> species(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** The moves the species learns by TM, tutor, egg and legacy, if the book has them. */
    public Optional<Learnsets.Learnset> learnset(String speciesId) {
        return Optional.ofNullable(learnsets.get(speciesId));
    }

    /** Every species. */
    public Collection<Species> all() {
        return byId.values();
    }
}
