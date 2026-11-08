/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * The ball table (chapter 7.10): how much each Poké Ball changes the catch chance. It is data: {@link #defaults()} covers
 * the 48 balls of the repository, and {@link #with} makes an edited copy.
 *
 * <p>Suggested (Appendix D): the table covers the common balls with their own rule. Every other ball is marked
 * <em>special</em>, is not for sale, and counts as ×1 until someone edits it. The Ancient balls (Hisui) are the same as their
 * regular counterpart; the Ancient Origin Ball, which has no regular counterpart, catches like a Master Ball.
 */
public final class BallTable {

    /** How a ball works. */
    public enum Kind {
        /** A fixed multiplier. */
        FLAT,
        /** Always catches. */
        ALWAYS_CATCHES,
        /** ×5 on the first round, else ×1. */
        QUICK,
        /** ×3.5 against Water or Bug, else ×1. */
        NET,
        /** ×3 at night or in caves, else ×1. */
        DUSK,
        /** {@code min(4, 1 + 0.3 × round)}. */
        TIMER,
        /** ×3.5 if the species was caught before in this game, else ×1. */
        REPEAT
    }

    /**
     * One ball.
     *
     * @param id         the item id, for example {@code ultra_ball}
     * @param kind       how it works
     * @param multiplier the multiplier for {@link Kind#FLAT}; for the conditional kinds, the multiplier when the condition holds
     *                   (0 for the Timer Ball, which has its own formula, and for the Master Ball)
     * @param special    true for the balls that are not part of the common set: not for sale, ×1 until edited
     * @param note       one plain sentence for the screen
     */
    public record Rule(String id, Kind kind, double multiplier, boolean special, String note) {
    }

    /**
     * Where and when the ball is thrown.
     *
     * @param round        the battle round, counted from 1 (the first round of the battle is 1)
     * @param nightOrCave  true at night or in a cave (Dusk Ball)
     * @param caughtBefore true if this species was caught before in this game (Repeat Ball)
     * @param targetTypes  the wild Pokémon's types (Net Ball)
     */
    public record Context(int round, boolean nightOrCave, boolean caughtBefore, List<PokemonType> targetTypes) {

        public Context {
            targetTypes = List.copyOf(targetTypes);
            if (round < 1) {
                throw new IllegalArgumentException("The round counts from 1, not " + round);
            }
        }

        /** The first round, by day, nothing caught before. */
        public static Context firstRound(List<PokemonType> targetTypes) {
            return new Context(1, false, false, targetTypes);
        }
    }

    private static volatile BallTable defaults;

    private final Map<String, Rule> rules;

    private BallTable(Map<String, Rule> rules) {
        this.rules = Map.copyOf(rules);
    }

    /** The table of chapter 7.10 with every ball of the repository. */
    public static BallTable defaults() {
        BallTable table = defaults;
        if (table == null) {
            synchronized (BallTable.class) {
                if (defaults == null) {
                    defaults = build();
                }
                table = defaults;
            }
        }
        return table;
    }

    private static BallTable build() {
        Map<String, Rule> m = new TreeMap<>();
        // The common balls.
        add(m, new Rule("poke_ball", Kind.FLAT, 1.0, false, "x1"));
        add(m, new Rule("premier_ball", Kind.FLAT, 1.0, false, "x1"));
        add(m, new Rule("great_ball", Kind.FLAT, 1.5, false, "x1.5"));
        add(m, new Rule("ultra_ball", Kind.FLAT, 2.0, false, "x2"));
        add(m, new Rule("master_ball", Kind.ALWAYS_CATCHES, 0.0, false, "Always catches"));
        add(m, new Rule("quick_ball", Kind.QUICK, 5.0, false, "x5 on the first round, else x1"));
        add(m, new Rule("net_ball", Kind.NET, 3.5, false, "x3.5 against Water or Bug, else x1"));
        add(m, new Rule("dusk_ball", Kind.DUSK, 3.0, false, "x3 at night or in caves, else x1"));
        add(m, new Rule("timer_ball", Kind.TIMER, 0.0, false, "min(4, 1 + 0.3 x round)"));
        add(m, new Rule("repeat_ball", Kind.REPEAT, 3.5, false, "x3.5 if the species was caught before in this game, else x1"));
        // Ancient balls (Hisui): the same as their regular counterpart.
        add(m, new Rule("ancient_poke_ball", Kind.FLAT, 1.0, false, "Same as the Poke Ball"));
        add(m, new Rule("ancient_great_ball", Kind.FLAT, 1.5, false, "Same as the Great Ball"));
        add(m, new Rule("ancient_ultra_ball", Kind.FLAT, 2.0, false, "Same as the Ultra Ball"));
        add(m, new Rule("ancient_origin_ball", Kind.ALWAYS_CATCHES, 0.0, true, "Suggested: catches like a Master Ball"));
        // Every other ball: special, x1 until edited.
        for (String id : List.of("ancient_azure_ball", "ancient_citrine_ball", "ancient_feather_ball", "ancient_gigaton_ball",
                "ancient_heavy_ball", "ancient_ivory_ball", "ancient_jet_ball", "ancient_leaden_ball", "ancient_roseate_ball",
                "ancient_slate_ball", "ancient_verdant_ball", "ancient_wing_ball", "azure_ball", "beast_ball", "cherish_ball",
                "citrine_ball", "dive_ball", "dream_ball", "fast_ball", "friend_ball", "heal_ball", "heavy_ball", "level_ball",
                "love_ball", "lure_ball", "luxury_ball", "moon_ball", "nest_ball", "park_ball", "roseate_ball", "safari_ball",
                "slate_ball", "sport_ball", "verdant_ball")) {
            add(m, new Rule(id, Kind.FLAT, 1.0, true, "Special ball: x1 until edited"));
        }
        return new BallTable(m);
    }

    private static void add(Map<String, Rule> map, Rule rule) {
        if (map.put(rule.id(), rule) != null) {
            throw new IllegalStateException("Ball listed twice: " + rule.id());
        }
    }

    /** The rule of a ball, if the table knows it. */
    public Optional<Rule> rule(String id) {
        return Optional.ofNullable(rules.get(id));
    }

    /** True if the table has a rule for this ball. */
    public boolean contains(String id) {
        return rules.containsKey(id);
    }

    /** All rules, sorted by id. */
    public Collection<Rule> all() {
        return new ArrayList<>(new TreeMap<>(rules).values());
    }

    /** A copy with a rule added or replaced. */
    public BallTable with(Rule rule) {
        Map<String, Rule> copy = new TreeMap<>(rules);
        copy.put(rule.id(), rule);
        return new BallTable(copy);
    }

    /**
     * The multiplier of a ball in this situation. A ball the table does not know counts as ×1. The Master Ball has no
     * multiplier: use {@link #alwaysCatches(String)}.
     */
    public double multiplier(String ballId, Context context) {
        Rule rule = rules.get(ballId);
        if (rule == null) {
            return 1.0;
        }
        return switch (rule.kind()) {
            case FLAT -> rule.multiplier();
            case ALWAYS_CATCHES -> 1.0;
            case QUICK -> context.round() == 1 ? rule.multiplier() : 1.0;
            case NET -> context.targetTypes().contains(PokemonType.WATER) || context.targetTypes().contains(PokemonType.BUG)
                    ? rule.multiplier() : 1.0;
            case DUSK -> context.nightOrCave() ? rule.multiplier() : 1.0;
            case TIMER -> Math.min(4.0, 1.0 + 0.3 * context.round());
            case REPEAT -> context.caughtBefore() ? rule.multiplier() : 1.0;
        };
    }

    /** True for a ball that always catches (the Master Ball). */
    public boolean alwaysCatches(String ballId) {
        Rule rule = rules.get(ballId);
        return rule != null && rule.kind() == Kind.ALWAYS_CATCHES;
    }
}
