/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.cards.SignatureMoves;
import net.quimmilho.pokednd.core.cards.SignatureMoves.MoveInfo;
import net.quimmilho.pokednd.core.cards.SignatureMoves.StageLevelCaps;
import net.quimmilho.pokednd.core.cards.SignatureOverrides;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.importer.Derived.SpeciesDerived;
import net.quimmilho.pokednd.core.importer.Learnsets.Learnset;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Merged;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.MergedSpecies;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * The signature moves of every card (chapter 9.3, step 9 of the import): the rule of {@link SignatureMoves} for every form in
 * the roster, with the hand-written overrides on top.
 *
 * @param cards    by species id, then form id
 * @param problems overrides that name a card or move that does not exist, in plain words
 */
public record Signature(SortedMap<String, SortedMap<String, Card>> cards, List<String> problems) {

    /**
     * The four moves of one card, and how they were found.
     *
     * @param key        the card key, for example {@code 0006_charizard}
     * @param name       the species name, with the form id if it is not the base form
     * @param moves      the moves printed on the card (the override if there is one, else the rule's pick)
     * @param rule       what the rule picked (also when an override replaced it)
     * @param overridden true if the overrides file decided
     * @param ownLearnset false if the form has no level-up moves of its own and the base form's were used
     * @param types      the types the rule used for STAB
     */
    public record Card(String key, String name, List<String> moves, SignatureMoves.Result rule, boolean overridden,
                       boolean ownLearnset, List<String> types) {
        public Card {
            moves = List.copyOf(moves);
            types = List.copyOf(types);
        }
    }

    /** Works out every card with the default level caps. */
    public static Signature build(Raw raw, Roster roster, Merged merged, Learnsets learnsets, Catalog.Built catalog, Derived derived,
                                  SignatureOverrides overrides) {
        return build(raw, roster, merged, learnsets, catalog, derived, overrides, StageLevelCaps.defaults());
    }

    /** Works out every card. */
    public static Signature build(Raw raw, Roster roster, Merged merged, Learnsets learnsets, Catalog.Built catalog, Derived derived,
                                  SignatureOverrides overrides, StageLevelCaps caps) {
        Map<String, MoveInfo> moves = new HashMap<>();
        Map<String, MoveEntry> entries = new HashMap<>();
        for (MoveEntry m : catalog.moves()) {
            moves.put(m.id(), MoveInfo.from(m));
            entries.put(m.id(), m);
        }
        Map<Integer, MergedSpecies> byDex = new HashMap<>();
        merged.species().forEach(s -> byDex.put(s.dex(), s));

        List<String> problems = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        TreeMap<String, SortedMap<String, Card>> out = new TreeMap<>();
        for (Roster.Entry entry : roster.species().values()) {
            MergedSpecies species = byDex.get(entry.dex());
            if (species == null) {
                continue; // reported by Bundle.validate
            }
            SpeciesDerived d = derived.species().get(species.id());
            Derived.Stage stage = d == null ? Derived.Stage.BASIC : d.stage();
            boolean line = d != null && (!d.evolvesFrom().isEmpty() || !d.evolvesInto().isEmpty());
            int cap = caps.capFor(stage, line);
            String baseId = entry.base().map(Roster.Form::id).orElse(species.id() + "_base");
            Map<String, Learnset> sets = learnsets.bySpecies().getOrDefault(species.id(), new TreeMap<>());

            TreeMap<String, Card> forms = new TreeMap<>();
            for (Roster.Form form : entry.forms()) {
                boolean isBase = form.id().equals(baseId);
                Learnset own = sets.get(form.id());
                boolean hasOwn = own != null && !own.level().isEmpty();
                Learnset used = hasOwn ? own : sets.get(baseId);
                List<Learnsets.LevelMove> level = used == null ? List.of() : used.level();
                Set<PokemonType> types = formTypes(raw, species, form);
                SignatureMoves.Result rule = SignatureMoves.select(level, types, cap, moves);

                String key = SignatureOverrides.cardKey(entry.dex(), species.id(), form.id(), isBase);
                usedKeys.add(key);
                Optional<List<String>> override = overrides.get(key);
                List<String> chosen = rule.moves();
                if (override.isPresent()) {
                    chosen = override.get();
                    for (String m : chosen) {
                        if (!moves.containsKey(m)) {
                            problems.add("signature override " + key + ": the move " + m + " does not exist");
                        }
                    }
                }
                String name = isBase ? species.name() : species.name() + " (" + form.id() + ")";
                forms.put(form.id(), new Card(key, name, chosen, rule, override.isPresent(), hasOwn || isBase,
                        types.stream().map(PokemonType::id).sorted().toList()));
            }
            out.put(species.id(), Collections.unmodifiableSortedMap(forms));
        }
        for (String key : overrides.moves().keySet()) {
            if (!usedKeys.contains(key)) {
                problems.add("signature override " + key + " matches no card of the roster");
            }
        }
        return new Signature(Collections.unmodifiableSortedMap(out), List.copyOf(problems));
    }

    /**
     * The types of a form. A regional or typed form has its own types in the species file; other forms use the species' types.
     * The species file's form is found by its aspects.
     */
    static Set<PokemonType> formTypes(Raw raw, MergedSpecies species, Roster.Form form) {
        List<String> names = new ArrayList<>(species.types());
        Raw.SpeciesFile file = raw.species().get(species.dex());
        if (file != null && !form.aspects().isEmpty()) {
            for (JsonNode f : file.json().path("forms")) {
                List<String> aspects = new ArrayList<>();
                f.path("aspects").forEach(a -> aspects.add(a.asText()));
                if (new TreeSet<>(aspects).equals(new TreeSet<>(form.aspects())) && f.has("primaryType")) {
                    names.clear();
                    names.add(f.get("primaryType").asText().toLowerCase(java.util.Locale.ROOT));
                    if (f.has("secondaryType")) {
                        names.add(f.get("secondaryType").asText().toLowerCase(java.util.Locale.ROOT));
                    }
                    break;
                }
            }
        }
        Set<PokemonType> types = new java.util.LinkedHashSet<>();
        for (String n : names) {
            PokemonType.parse(n).ifPresent(types::add);
        }
        return types;
    }

    /** The moves of every form, for {@code derived.json}: species id, then form id. */
    public SortedMap<String, SortedMap<String, List<String>>> movesByForm() {
        TreeMap<String, SortedMap<String, List<String>>> out = new TreeMap<>();
        cards.forEach((species, forms) -> {
            TreeMap<String, List<String>> m = new TreeMap<>();
            forms.forEach((formId, card) -> m.put(formId, card.moves()));
            out.put(species, m);
        });
        return out;
    }

    /** All cards in order of card key. */
    public List<Card> all() {
        List<Card> list = new ArrayList<>();
        cards.values().forEach(forms -> list.addAll(forms.values()));
        list.sort(java.util.Comparator.comparing(Card::key));
        return list;
    }

    /** The Markdown report {@code reports/signature.md}. */
    public String report(SignatureOverrides overrides) {
        List<Card> all = all();
        long four = all.stream().filter(c -> c.moves().size() == 4).count();
        List<Card> fewer = all.stream().filter(c -> c.moves().size() < 4).toList();
        long lifted = all.stream().filter(c -> c.rule().capLifted()).count();
        List<Card> replaced = all.stream().filter(Card::overridden).toList();
        StringBuilder sb = new StringBuilder("# Signature moves\n\n");
        sb.append("The four moves printed on each species card (chapter 9.3). The rule picks them; `cards/signature-overrides.json` ")
                .append("can fix them by hand.\n\n");
        sb.append("| What | Count |\n|---|---:|\n");
        sb.append("| Cards (species and forms in the roster) | ").append(all.size()).append(" |\n");
        sb.append("| Cards with four moves | ").append(four).append(" |\n");
        sb.append("| Cards with fewer than four (the form has fewer usable level-up moves) | ").append(fewer.size()).append(" |\n");
        sb.append("| Cards where the level cap was lifted (too few moves under it) | ").append(lifted).append(" |\n");
        sb.append("| Cards decided by an override | ").append(replaced.size()).append(" |\n\n");

        sb.append("## Overrides\n\n");
        if (replaced.isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("| Card | Override | The rule would pick |\n|---|---|---|\n");
            for (Card c : replaced) {
                sb.append("| ").append(c.key()).append(" | ").append(String.join(", ", c.moves())).append(" | ")
                        .append(String.join(", ", c.rule().moves())).append(" |\n");
            }
            sb.append('\n');
        }
        sb.append("## Cards with fewer than four moves\n\n");
        if (fewer.isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("| Card | Moves | Candidates |\n|---|---|---:|\n");
            for (Card c : fewer) {
                sb.append("| ").append(c.key()).append(" | ").append(String.join(", ", c.moves())).append(" | ")
                        .append(c.rule().candidates()).append(" |\n");
            }
            sb.append('\n');
        }
        sb.append("## All cards\n\n| Card | Moves |\n|---|---|\n");
        for (Card c : all) {
            sb.append("| ").append(c.key()).append(c.overridden() ? " (override)" : "").append(" | ")
                    .append(String.join(", ", c.moves())).append(" |\n");
        }
        return sb.toString();
    }
}
