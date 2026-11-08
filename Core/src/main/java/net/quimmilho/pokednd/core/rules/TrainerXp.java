/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.EnumMap;
import java.util.Map;

/**
 * Trainer experience (chapter 4.5): the curve {@code factor × L × (L − 1)} and the XP each event is worth.
 *
 * @param curveFactor {@code trainer.xp.curveFactor}: total XP to reach level L is {@code factor × L × (L − 1)} (default 25)
 * @param multiplier  {@code trainer.xp.multiplier}: scales all trainer XP (default 1.0)
 * @param levelStart  {@code trainer.level.start}: the starting level (default 1)
 * @param levelCap    {@code trainer.level.cap}: the highest level (default 20)
 * @param table       {@code trainer.xp.table}: what each event is worth (default: the table of 4.5)
 */
public record TrainerXp(int curveFactor, double multiplier, int levelStart, int levelCap, Map<Event, Reward> table) {

    /** The things that give trainer XP. */
    public enum Event {
        /** Defeat a wild Pokémon: its level. */
        DEFEAT_WILD,
        /** Defeat an NPC trainer or a PvP opponent: 5 × the opponent's highest level. */
        DEFEAT_TRAINER,
        /** Defeat a boss: 10 × its level. */
        DEFEAT_BOSS,
        /** Catch a Pokémon: 10. */
        CATCH,
        /** Evolve a Pokémon, or hatch an egg: 20. */
        EVOLVE_OR_HATCH,
        /** Discover a node (the first arrival of anyone in the game): 10. */
        DISCOVER_NODE,
        /** Pass a gate or event skill check for the first time: 15. */
        FIRST_SKILL_CHECK,
        /** Complete a quest: set by the quest, 100 by default. */
        QUEST
    }

    /**
     * What an event is worth.
     *
     * @param amount   the XP, or the XP per level when {@code perLevel} is true
     * @param perLevel true if the amount is multiplied by a level (the wild Pokémon's, the opponent's highest, the boss's)
     */
    public record Reward(int amount, boolean perLevel) {
        public Reward {
            if (amount < 0) {
                throw new IllegalArgumentException("A reward cannot be negative");
            }
        }
    }

    public TrainerXp {
        if (curveFactor < 1) {
            throw new IllegalArgumentException("trainer.xp.curveFactor must be at least 1");
        }
        if (multiplier < 0) {
            throw new IllegalArgumentException("trainer.xp.multiplier cannot be negative");
        }
        if (levelStart < 1 || levelCap < levelStart) {
            throw new IllegalArgumentException("trainer.level.start must be at least 1 and not above trainer.level.cap");
        }
        EnumMap<Event, Reward> copy = new EnumMap<>(Event.class);
        copy.putAll(table);
        for (Event e : Event.values()) {
            if (!copy.containsKey(e)) {
                throw new IllegalArgumentException("trainer.xp.table has no entry for " + e);
            }
        }
        table = Map.copyOf(copy);
    }

    /** The table of chapter 4.5. */
    public static Map<Event, Reward> defaultTable() {
        EnumMap<Event, Reward> t = new EnumMap<>(Event.class);
        t.put(Event.DEFEAT_WILD, new Reward(1, true));
        t.put(Event.DEFEAT_TRAINER, new Reward(5, true));
        t.put(Event.DEFEAT_BOSS, new Reward(10, true));
        t.put(Event.CATCH, new Reward(10, false));
        t.put(Event.EVOLVE_OR_HATCH, new Reward(20, false));
        t.put(Event.DISCOVER_NODE, new Reward(10, false));
        t.put(Event.FIRST_SKILL_CHECK, new Reward(15, false));
        t.put(Event.QUEST, new Reward(100, false));
        return t;
    }

    /** The defaults of chapter 4.5 and 4.7. */
    public static TrainerXp defaults() {
        return new TrainerXp(25, 1.0, 1, 20, defaultTable());
    }

    /** A copy with another multiplier. */
    public TrainerXp withMultiplier(double other) {
        return new TrainerXp(curveFactor, other, levelStart, levelCap, table);
    }

    /** A copy with another curve factor. */
    public TrainerXp withCurveFactor(int factor) {
        return new TrainerXp(factor, multiplier, levelStart, levelCap, table);
    }

    /** A copy with another level cap. */
    public TrainerXp withLevelCap(int cap) {
        return new TrainerXp(curveFactor, multiplier, levelStart, cap, table);
    }

    /** A copy with one event worth something else. */
    public TrainerXp withReward(Event event, Reward reward) {
        EnumMap<Event, Reward> copy = new EnumMap<>(table);
        copy.put(event, reward);
        return new TrainerXp(curveFactor, multiplier, levelStart, levelCap, copy);
    }

    /** The total XP a trainer has on reaching this level: {@code factor × L × (L − 1)}. Level 1 is 0. */
    public long totalXp(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("A level is at least 1, not " + level);
        }
        return (long) curveFactor * level * (level - 1);
    }

    /** The level a trainer with this much total XP has, never above the cap and never below the starting level. */
    public int levelFor(long xp) {
        int level = levelStart;
        while (level < levelCap && xp >= totalXp(level + 1)) {
            level++;
        }
        return level;
    }

    /**
     * The XP an event gives, with the multiplier applied and rounded down.
     *
     * @param event the event
     * @param level the level that counts for events worth XP per level (the wild Pokémon's, the opponent's highest, the
     *              boss's); ignored for the others. For {@link Event#QUEST} it is the quest's own XP, or 0 to use the table
     */
    public int award(Event event, int level) {
        Reward reward = table.get(event);
        double base;
        if (event == Event.QUEST && level > 0) {
            base = level;
        } else if (reward.perLevel()) {
            if (level < 1) {
                throw new IllegalArgumentException(event + " needs the level of the opponent");
            }
            base = (double) reward.amount() * level;
        } else {
            base = reward.amount();
        }
        return (int) Math.floor(base * multiplier + 1e-9);
    }
}
