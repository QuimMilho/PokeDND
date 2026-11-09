/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The fixed vocabulary of feature effects (chapter 4.4). A class feature is built from these keys, so an admin can make a
 * feature without code. Anything the vocabulary cannot say is a {@code narrative} effect that the admin adjudicates.
 */
public final class EffectKeys {

    /** How the same key from several features is combined. */
    public enum Combine {
        /** The values are added (most keys). */
        SUM,
        /** The values are multiplied (friendship gain: ×2 and ×1.5 give ×3). */
        PRODUCT,
        /** The effect is on or off; the value does not matter. */
        FLAG,
        /** A list of skills or text: nothing is added up. */
        LIST
    }

    private static final Map<String, Combine> KEYS = new LinkedHashMap<>();

    static {
        // Hit, crit, catch, flee (chapter 4.2 adds the Handling and DEX conversions on top of these)
        add("accuracy.bonusPp", Combine.SUM);
        add("crit.stageBonus", Combine.SUM);
        add("catch.multiplierBonus", Combine.SUM);
        add("flee.bonusPp", Combine.SUM);
        add("initiative.bonus", Combine.SUM);
        add("initiative.advantage", Combine.FLAG);
        // The overworld
        add("movement.bonus", Combine.SUM);
        add("encounter.chanceShiftPp", Combine.SUM);
        add("map.revealAll", Combine.FLAG);
        // Healing, XP, friendship, damage, HP
        add("heal.itemBonusPct", Combine.SUM);
        add("xp.bonusPct", Combine.SUM);
        add("friendship.gainMultiplier", Combine.PRODUCT);
        add("damage.trainerReductionFlat", Combine.SUM);
        add("hp.perLevelBonus", Combine.SUM);
        // Skills
        add("skill.expertise", Combine.LIST);
        add("skill.advantage", Combine.LIST);
        add("skill.reroll", Combine.LIST);
        // Once per battle
        add("battle.rerollPerBattle", Combine.SUM);
        add("battle.freeSwitchPerBattle", Combine.SUM);
        add("battle.freeItemPerBattle", Combine.SUM);
        add("battle.surviveKoPerBattle", Combine.SUM);
        add("battle.forceRerollPerBattle", Combine.SUM);
        add("battle.styleDie", Combine.SUM);
        add("battle.interposePerBattle", Combine.SUM);
        // Battle numbers
        add("battle.superEffectiveBonusPct", Combine.SUM);
        add("battle.statusResistPp", Combine.SUM);
        add("battle.startStage", Combine.LIST);
        // Once per session
        add("session.revive", Combine.SUM);
        add("session.fullHeal", Combine.SUM);
        add("session.survive", Combine.SUM);
        // Economy
        add("money.bonusPct", Combine.SUM);
        add("shop.discountPct", Combine.SUM);
        add("shop.sellBonusPct", Combine.SUM);
        add("loot.extraRolls", Combine.SUM);
        add("bag.extraSlots", Combine.SUM);
        // Text only
        add("narrative", Combine.LIST);
    }

    private EffectKeys() {
    }

    private static void add(String key, Combine how) {
        KEYS.put(key, how);
    }

    /** Every key of the vocabulary and how it is combined. */
    public static Map<String, Combine> all() {
        return Map.copyOf(KEYS);
    }

    /** The keys in the order of chapter 4.4. */
    public static Set<String> keys() {
        return java.util.Collections.unmodifiableSet(KEYS.keySet());
    }

    /** True if the key is part of the vocabulary. */
    public static boolean isKnown(String key) {
        return KEYS.containsKey(key);
    }

    /** How the key is combined; throws for an unknown key. */
    public static Combine combine(String key) {
        Combine c = KEYS.get(key);
        if (c == null) {
            throw new IllegalArgumentException("Unknown feature effect \"" + key + "\". Use a key of chapter 4.4, or narrative");
        }
        return c;
    }
}
