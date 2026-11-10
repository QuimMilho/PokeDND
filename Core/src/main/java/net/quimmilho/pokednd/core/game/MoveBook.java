/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.quimmilho.pokednd.core.combat.CombatSettings;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.dice.DiceSettings;
import net.quimmilho.pokednd.core.importer.Catalog.AbilityEntry;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.TypeChart;

/**
 * What the battle engine knows about moves (chapters 7.5 to 7.8 and 19.6): every move of the data bundle with its hit, damage and effect
 * data, the berries Natural Gift reads, the combat settings, the type chart, and which moves Core runs by itself.
 *
 * <p>The battle state does not carry move data; the resolver ({@link BattleDesk#resolveNext}) looks a move up here by its id. A move
 * that is not here, or whose effect Core cannot run, becomes a task for the admin and never an error.
 */
public final class MoveBook {

    /** The file with the moves and conditions that Core runs by hand. */
    public static final String IMPLEMENTED_FILE = "/rules/implemented-effects.json";

    private static volatile Implemented implemented;

    /**
     * The moves, abilities and items Core scripts, and the conditions it runs for every move that sets them.
     *
     * @param moves             the ids of the scripted moves that are implemented
     * @param conditions        conditions such as {@code volatileStatus:mustrecharge}
     * @param abilities         the ids of the scripted abilities that are implemented (Showdown ids: {@code sheerforce})
     * @param abilitiesNoEffect abilities that only matter in weather or terrain, which the game does not have: known, and they do nothing
     * @param items             the ids of the held items and berries that are implemented (the game's ids: {@code choice_band})
     */
    public record Implemented(Set<String> moves, Set<String> conditions, Set<String> abilities, Set<String> abilitiesNoEffect, Set<String> items) {
        public Implemented {
            moves = Set.copyOf(moves);
            conditions = Set.copyOf(conditions);
            abilities = Set.copyOf(abilities);
            abilitiesNoEffect = Set.copyOf(abilitiesNoEffect);
            items = Set.copyOf(items);
        }

        /** Moves and conditions only (the shape before phase 47). */
        public Implemented(Set<String> moves, Set<String> conditions) {
            this(moves, conditions, Set.of(), Set.of(), Set.of());
        }
    }

    /** Who makes a move work (chapter 19.6). */
    public enum Handling {
        /** Plain data: it works without code. */
        AUTOMATIC,
        /** Written in Java inside Core. */
        IMPLEMENTED,
        /** Scripted and not written yet: the plain parts (hit, damage, status chances) run and the admin applies the rest. */
        PARTIAL,
        /** Nothing runs: the app shows the text and the admin applies it. */
        MANUAL
    }

    /**
     * What Natural Gift gets from a berry.
     *
     * @param power the move's base power
     * @param type  the move's type
     */
    public record Gift(int power, PokemonType type) {
    }

    private final Map<String, MoveEntry> entries;
    private final Map<String, MoveProfile> profiles = new HashMap<>();
    private final Map<String, Gift> gifts;
    private final Map<String, AbilityEntry> abilities;
    private final Map<String, ItemEntry> items;
    private final CombatSettings combat;
    private final TypeChart chart;
    private final DiceSettings dice;
    private final Implemented effects;

    private MoveBook(Map<String, MoveEntry> entries, Map<String, Gift> gifts, Map<String, AbilityEntry> abilities, Map<String, ItemEntry> items,
                     CombatSettings combat, TypeChart chart, DiceSettings dice) {
        this.entries = entries;
        this.gifts = gifts;
        this.abilities = abilities;
        this.items = items;
        this.combat = combat;
        this.chart = chart;
        this.dice = dice;
        this.effects = implementedEffects();
        if (!entries.containsKey(Battle.MoveSlot.STRUGGLE)) {
            // Struggle is a rule of the game (chapter 7.4), so it exists even when the data does not have it
            this.entries.put(Battle.MoveSlot.STRUGGLE, new MoveEntry(Battle.MoveSlot.STRUGGLE, "Struggle", "Normal", "Physical", 50, Optional.empty(), 1, 0,
                    "randomNormal", "Used when no move has PP left. It also damages the user a little.", EffectClass.SCRIPTED, List.of("onModifyType"),
                    List.of("onModifyType"), null));
        }
        for (MoveEntry m : entries.values()) {
            try {
                profiles.put(m.id(), MoveProfile.from(m));
            } catch (IllegalArgumentException e) {
                // a move with a type the chart does not know stays without a profile: the admin applies it
            }
        }
    }

    /** A book of these moves and items, with the default combat settings, the official type chart and the default dice. */
    public static MoveBook of(Collection<MoveEntry> moves, Collection<ItemEntry> items) {
        return of(moves, items, List.of());
    }

    /** A book of these moves, items and abilities (chapter 7.9): a Pokémon with a scripted ability or item that Core does not run gets a task. */
    public static MoveBook of(Collection<MoveEntry> moves, Collection<ItemEntry> items, Collection<AbilityEntry> abilities) {
        Map<String, MoveEntry> entries = new HashMap<>();
        for (MoveEntry m : moves) {
            entries.put(m.id(), m);
        }
        Map<String, Gift> gifts = new HashMap<>();
        for (ItemEntry item : items) {
            JsonNode gift = item.effect() == null ? null : item.effect().get("naturalGift");
            if (gift != null && gift.has("basePower")) {
                PokemonType.parse(gift.path("type").asText()).ifPresent(t -> gifts.put(item.id(), new Gift(gift.get("basePower").asInt(), t)));
            }
        }
        Map<String, ItemEntry> itemsById = new HashMap<>();
        for (ItemEntry item : items) {
            itemsById.put(Battle.squash(item.id()), item);
        }
        Map<String, AbilityEntry> abilitiesById = new HashMap<>();
        for (AbilityEntry a : abilities) {
            abilitiesById.put(Battle.squash(a.id()), a);
        }
        return new MoveBook(entries, gifts, abilitiesById, itemsById, CombatSettings.defaults(), TypeChart.standard(), DiceSettings.defaults());
    }

    /** A book of these moves with the default settings and no items. */
    public static MoveBook of(Collection<MoveEntry> moves) {
        return of(moves, List.of());
    }

    /** The same moves with other settings (a game can edit the power table, the crit chances, the type chart and the dice). */
    public MoveBook with(CombatSettings newCombat, TypeChart newChart, DiceSettings newDice) {
        return new MoveBook(entries, gifts, abilities, items, newCombat, newChart, newDice);
    }

    /** The ability data for an id (any spelling), if the book has it. */
    public Optional<AbilityEntry> ability(String id) {
        return Optional.ofNullable(abilities.get(Battle.squash(id)));
    }

    /** The item data for an id (any spelling), if the book has it. */
    public Optional<ItemEntry> item(String id) {
        return Optional.ofNullable(items.get(Battle.squash(id)));
    }

    /**
     * Who makes an ability work: {@link Handling#IMPLEMENTED} when Core runs it, {@link Handling#AUTOMATIC} when it does nothing in this game
     * (no code, or only weather and terrain, or unknown to the book), {@link Handling#MANUAL} when it is scripted and the admin applies it.
     */
    public Handling abilityHandling(String id) {
        String key = Battle.squash(id);
        if (AbilityScripts.knows(key)) {
            return Handling.IMPLEMENTED;
        }
        if (AbilityScripts.noEffect(key)) {
            return Handling.AUTOMATIC;
        }
        AbilityEntry entry = abilities.get(key);
        return entry != null && entry.effectClass() != EffectClass.AUTOMATIC ? Handling.MANUAL : Handling.AUTOMATIC;
    }

    /**
     * Who makes a held item work in a battle: {@link Handling#IMPLEMENTED} when Core runs it; {@link Handling#MANUAL} for a scripted held
     * item or berry that it does not run; {@link Handling#AUTOMATIC} for anything else (it does nothing while held).
     */
    public Handling itemHandling(String id) {
        if (ItemScripts.knows(id)) {
            return Handling.IMPLEMENTED;
        }
        ItemEntry entry = items.get(Battle.squash(id));
        boolean heldKind = entry != null && (entry.category().equals("held_items") || entry.category().equals("berries"));
        return heldKind && entry.effectClass() == EffectClass.SCRIPTED ? Handling.MANUAL : Handling.AUTOMATIC;
    }

    public CombatSettings combat() {
        return combat;
    }

    public TypeChart chart() {
        return chart;
    }

    public DiceSettings dice() {
        return dice;
    }

    /** The move data for an id, if the book has it. */
    public Optional<MoveEntry> entry(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** The hit and damage data of a move. Struggle is made up when the data does not have it. */
    public Optional<MoveProfile> profile(String id) {
        MoveProfile p = profiles.get(id);
        if (p == null && Battle.MoveSlot.STRUGGLE.equals(id)) {
            return Optional.of(new MoveProfile(id, PokemonType.NORMAL, MoveProfile.Category.PHYSICAL, 50, Optional.empty(), 0, false, false,
                    Optional.empty()));
        }
        return Optional.ofNullable(p);
    }

    /** A move as a Pokémon knows it, with the move's PP, priority and target from the data; empty if the book does not have the move. */
    public Optional<MoveSlot> slot(String id) {
        return entry(id).map(m -> new MoveSlot(m.id(), m.name(), m.pp(), m.pp(), m.priority(), m.target()));
    }

    /** What Natural Gift does with this held item, if it is a berry. */
    public Optional<Gift> gift(String itemId) {
        return Optional.ofNullable(gifts.get(itemId));
    }

    /** Who makes this move work. */
    public Handling handling(MoveEntry move) {
        return handling(move, effects);
    }

    /**
     * Who makes this move work, for a given list of implemented moves and conditions. A scripted move counts as implemented when it is on
     * the list of moves, or when it has no code of its own and every condition it sets is on the list of conditions.
     */
    public static Handling handling(MoveEntry move, Implemented list) {
        if (move.reason().contains("z_max") || move.effectClass() == EffectClass.MANUAL) {
            return Handling.MANUAL;
        }
        if (move.effectClass() == EffectClass.AUTOMATIC) {
            return Handling.AUTOMATIC;
        }
        if (list.moves().contains(move.id())) {
            return Handling.IMPLEMENTED;
        }
        if (move.handlers().isEmpty() && !move.reason().isEmpty() && list.conditions().containsAll(move.reason())) {
            return Handling.IMPLEMENTED;
        }
        return Handling.PARTIAL;
    }

    /** True if Core runs the move completely (plain data, or written in Java). */
    public boolean runsByItself(MoveEntry move) {
        Handling h = handling(move);
        return h == Handling.AUTOMATIC || h == Handling.IMPLEMENTED;
    }

    /** True if the condition (such as {@code volatileStatus:mustrecharge}) is run by Core for any move. */
    public boolean runsCondition(String condition) {
        return effects.conditions().contains(condition);
    }

    /** The moves and conditions Core runs by hand, from {@value #IMPLEMENTED_FILE}. */
    public static Implemented implementedEffects() {
        Implemented list = implemented;
        if (list == null) {
            synchronized (MoveBook.class) {
                if (implemented == null) {
                    implemented = loadImplemented();
                }
                list = implemented;
            }
        }
        return list;
    }

    private static Implemented loadImplemented() {
        try (InputStream in = MoveBook.class.getResourceAsStream(IMPLEMENTED_FILE)) {
            if (in == null) {
                throw new IllegalStateException(IMPLEMENTED_FILE + " is missing from Core");
            }
            JsonNode root = new ObjectMapper().readTree(in);
            Set<String> moves = new HashSet<>();
            root.path("moves").forEach(n -> moves.add(n.asText()));
            Set<String> conditions = new HashSet<>();
            root.path("conditions").forEach(n -> conditions.add(n.asText()));
            Set<String> abilities = new HashSet<>();
            root.path("abilities").forEach(n -> abilities.add(n.asText()));
            Set<String> noEffect = new HashSet<>();
            root.path("abilitiesNoEffect").forEach(n -> noEffect.add(n.asText()));
            Set<String> items = new HashSet<>();
            root.path("items").forEach(n -> items.add(n.asText()));
            return new Implemented(moves, conditions, abilities, noEffect, items);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + IMPLEMENTED_FILE + ": " + e.getMessage(), e);
        }
    }
}
