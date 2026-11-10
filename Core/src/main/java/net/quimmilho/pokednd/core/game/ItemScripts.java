/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * The held items that Core runs by itself (chapter 7.9, 8.8, phase 47). Every method reads the item through {@link Battle#itemOf}, which is
 * {@code null} when {@code combat.heldItems.enabled} is off. Ids are written without underscores here ({@code choiceband}); the game's own
 * ids ({@code choice_band}) are squashed by {@link Battle#squash}.
 *
 * <p>What they do: Leftovers heal 1/16 of the maximum HP at the end of the round. Choice Band and Choice Specs make the holder's Attack or
 * Sp. Atk ×1.5 and Choice Scarf its Speed ×1.5; all three lock the holder into the first move it uses until it leaves the field. Focus Sash
 * lets a Pokémon at full HP survive any hit with 1 HP, once. Life Orb makes moves ×1.3 and costs 1/10 of the maximum HP after a hit. A type
 * gem makes the first move of its type ×1.3 and is used up. Berries are eaten by themselves.
 */
final class ItemScripts {

    private static final Set<String> GEMS;
    private static final Set<String> HEALING_BERRIES = Set.of("oranberry", "sitrusberry", "figyberry", "wikiberry", "magoberry", "aguavberry", "iapapaberry");
    private static final Set<String> CURING_BERRIES = Set.of("cheriberry", "chestoberry", "pechaberry", "rawstberry", "aspearberry", "persimberry", "lumberry");
    private static final Set<String> OTHERS = Set.of("leftovers", "choiceband", "choicespecs", "choicescarf", "focussash", "lifeorb");
    private static final Set<String> ALL;

    static {
        Set<String> gems = new TreeSet<>();
        for (PokemonType t : PokemonType.values()) {
            gems.add(t.name().toLowerCase(java.util.Locale.ROOT) + "gem");
        }
        GEMS = Set.copyOf(gems);
        Set<String> all = new TreeSet<>(OTHERS);
        all.addAll(GEMS);
        all.addAll(HEALING_BERRIES);
        all.addAll(CURING_BERRIES);
        ALL = Set.copyOf(all);
    }

    private ItemScripts() {
    }

    /** True if Core runs this item (any spelling of the id). */
    static boolean knows(String id) {
        return ALL.contains(Battle.squash(id));
    }

    /** The ids Core runs, without underscores. */
    static Set<String> implemented() {
        return ALL;
    }

    static boolean isChoice(String item) {
        return item != null && item.startsWith("choice");
    }

    // =============================================================================================================== damage

    /**
     * What the held item of the attacker does to the damage of a move: Life Orb ×1.3, Choice Band ×1.5 for physical moves, Choice Specs ×1.5
     * for special moves, a type gem ×1.3 for a move of its type.
     */
    static double damageFactor(Battle b, Combatant a, PokemonType type, boolean physical) {
        String item = b.itemOf(a);
        if (item == null) {
            return 1.0;
        }
        return switch (item) {
            case "lifeorb" -> 1.3;
            case "choiceband" -> physical ? 1.5 : 1.0;
            case "choicespecs" -> physical ? 1.0 : 1.5;
            default -> gemType(item) == type ? 1.3 : 1.0;
        };
    }

    /** The type of a gem ({@code firegem} → Fire), or null if it is not a gem. */
    static PokemonType gemType(String item) {
        if (item == null || !GEMS.contains(item)) {
            return null;
        }
        return PokemonType.parse(item.substring(0, item.length() - 3)).orElse(null);
    }

    /**
     * The HP that eating a healing berry gives: Oran 10, Sitrus 1/4 of the maximum, Figy, Wiki, Mago, Aguav and Iapapa 1/3; 0 for any other item.
     * Used by the battle (when the HP calls for it) and by the bag (eaten at will).
     */
    static int berryHeal(String item, int maxHp) {
        String id = Battle.squash(item);
        if (!HEALING_BERRIES.contains(id)) {
            return 0;
        }
        return switch (id) {
            case "oranberry" -> 10;
            case "sitrusberry" -> Math.max(1, maxHp / 4);
            default -> Math.max(1, maxHp / 3);
        };
    }

    /** True if eating this berry cures this major status: Cheri paralysis, Chesto sleep, Pecha poison, Rawst burn, Aspear freeze, Lum any. */
    static boolean berryCures(String item, Status status) {
        return switch (Battle.squash(item)) {
            case "cheriberry" -> status == Status.PARALYSIS;
            case "chestoberry" -> status == Status.SLEEP;
            case "pechaberry" -> status == Status.POISON || status == Status.TOXIC;
            case "rawstberry" -> status == Status.BURN;
            case "aspearberry" -> status == Status.FREEZE;
            case "lumberry" -> status != Status.NONE;
            default -> false;
        };
    }

    /** True if Core knows this berry (it heals or cures). */
    static boolean isBerry(String item) {
        String id = Battle.squash(item);
        return HEALING_BERRIES.contains(id) || CURING_BERRIES.contains(id);
    }

    /** True if the attacker holds a gem of this type (it is used up by the move). */
    static boolean holdsGemOf(Battle b, Combatant a, PokemonType type) {
        return gemType(b.itemOf(a)) == type;
    }

    /** Choice Scarf: Speed ×1.5 (for the initiative). */
    static double speedFactor(Battle b, Combatant c) {
        return b.holds(c, "choicescarf") ? 1.5 : 1.0;
    }

    // ======================================================================================================= surviving a hit

    /** Focus Sash: a Pokémon at full HP survives a hit that would faint it. */
    static boolean sashSaves(Battle b, Combatant target, int damage) {
        return b.holds(target, "focussash") && target.hp() == target.maxHp() && damage >= target.hp();
    }

    /** Life Orb: what the holder loses after a hit (1/10 of the maximum HP, at least 1). */
    static int lifeOrbRecoil(Battle b, Combatant user) {
        return b.holds(user, "lifeorb") ? Math.max(1, user.maxHp() / 10) : 0;
    }

    // ============================================================================================================ the Choice lock

    /** The lock that a Choice item sets when the holder uses a move: the place of the move in its list plus 1. 0 if nothing is locked. */
    static int lockFor(Battle b, Combatant user, String moveId) {
        if (!isChoice(b.itemOf(user)) || user.condition().has(Volatiles.CHOICE_LOCK) && b.choiceLockedMove(user) != null) {
            return 0;
        }
        for (int i = 0; i < user.moves().size(); i++) {
            if (user.moves().get(i).moveId().equals(moveId)) {
                return i + 1;
            }
        }
        return 0;
    }

    // ================================================================================================= end of the round, berries

    /** Leftovers: 1/16 of the maximum HP at the end of the round. Null if the Pokémon has none or is at full HP. */
    static GameEvent residual(Battle b, Combatant c) {
        if (c.fainted() || c.hp() >= c.maxHp() || !b.holds(c, "leftovers")) {
            return null;
        }
        return new GameEvent.HpChanged(b.battleId(), c.id(), Math.max(1, c.maxHp() / 16), "from its Leftovers");
    }

    /**
     * The berry a Pokémon eats now, if its condition calls for it: Oran 10 HP and Sitrus 1/4 at half HP; Figy, Wiki, Mago, Aguav and Iapapa
     * 1/3 at a quarter of the HP (half with Gluttony); Cheri, Chesto, Pecha, Rawst and Aspear cure their status, Persim the confusion and Lum
     * everything. Empty events if it eats nothing. A foe with Unnerve keeps the Pokémon from eating. (The confusion that some berries cause
     * for a disliked flavour is not in the game.)
     */
    static List<GameEvent> berry(Battle b, Combatant c) {
        List<GameEvent> events = new ArrayList<>();
        String item = b.itemOf(c);
        if (item == null || c.fainted() || b.unnerved(c)) {
            return events;
        }
        String ate = b.label(c) + " ate its " + BattleReducer.pretty(c.condition().heldItem()) + "!";
        int heal = 0;
        boolean cureStatus = false;
        boolean cureConfusion = false;
        Status status = c.status();
        if (HEALING_BERRIES.contains(item)) {
            boolean greedy = b.hasAbility(c, "gluttony");
            boolean half = item.equals("oranberry") || item.equals("sitrusberry") || greedy;
            boolean low = half ? c.hp() * 2 <= c.maxHp() : c.hp() * 4 <= c.maxHp();
            heal = low ? berryHeal(item, c.maxHp()) : 0;
        } else if (CURING_BERRIES.contains(item)) {
            cureStatus = berryCures(item, status);
            cureConfusion = (item.equals("persimberry") || item.equals("lumberry")) && c.condition().has(Volatiles.CONFUSION);
        } else {
            return events;
        }
        if (heal > 0 && c.hp() < c.maxHp()) {
            events.add(new GameEvent.HeldItemSet(b.battleId(), c.id(), null, ate));
            events.add(new GameEvent.HpChanged(b.battleId(), c.id(), heal, "from the berry"));
        } else if (cureStatus || cureConfusion) {
            events.add(new GameEvent.HeldItemSet(b.battleId(), c.id(), null, ate));
            if (cureStatus) {
                events.add(new GameEvent.StatusSet(b.battleId(), c.id(), Status.NONE, 0, null));
            }
            if (cureConfusion) {
                events.add(new GameEvent.VolatileSet(b.battleId(), c.id(), Volatiles.CONFUSION, 0, b.label(c) + " snapped out of its confusion!"));
            }
        }
        return events;
    }
}
