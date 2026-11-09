/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.List;
import java.util.Map;

/**
 * A trainer as it is stored (chapter 4). Everything else on the sheet (proficiency, Armor Class, skill bonuses, features) is
 * worked out from this and the class by {@link TrainerRulebook#sheet(Trainer)}. Immutable: every change gives a new trainer.
 *
 * @param name    the trainer's name in the game
 * @param classId the class
 * @param level   the level the trainer has reached (it can be lower than the XP allows until the player confirms a level-up)
 * @param xp      total trainer XP
 * @param scores  the ability scores
 * @param skills  the skills the player picked (the class may add expertise skills on top)
 * @param maxHp   maximum HP
 * @param hp      HP now
 * @param money   ₽
 * @param items   the bag as item id to count
 */
public record Trainer(String name, String classId, int level, long xp, AbilityScores scores, List<String> skills, int maxHp, int hp,
                      long money, Map<String, Integer> items) {

    public Trainer {
        skills = List.copyOf(skills);
        items = Map.copyOf(items);
        if (level < 1) {
            throw new IllegalArgumentException("A level is at least 1, not " + level);
        }
        if (maxHp < 1) {
            throw new IllegalArgumentException("Maximum HP is at least 1, not " + maxHp);
        }
        if (hp < 0 || hp > maxHp) {
            throw new IllegalArgumentException("HP is 0 to " + maxHp + ", not " + hp);
        }
        if (money < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }
    }

    /** True at 0 HP. */
    public boolean isDown() {
        return hp == 0;
    }

    /** A copy that took this much damage (never below 0 HP). */
    public Trainer damage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Damage cannot be negative");
        }
        return withHp(Math.max(0, hp - amount));
    }

    /** A copy that healed this much (never above the maximum). */
    public Trainer heal(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Healing cannot be negative");
        }
        return withHp((int) Math.min(maxHp, (long) hp + amount));
    }

    /** A copy at full HP. */
    public Trainer healFully() {
        return withHp(maxHp);
    }

    /**
     * The end of an overworld round: the trainer regains {@code trainer.hp.recoverPerRoundPct} of the maximum, rounded down but
     * at least 1 HP when the setting is above 0. A trainer at 0 HP does not recover by walking.
     */
    public Trainer recoverRound(int percent) {
        if (percent <= 0 || hp == 0 || hp == maxHp) {
            return this;
        }
        return heal(Math.max(1, maxHp * percent / 100));
    }

    /** A copy with other HP. */
    public Trainer withHp(int value) {
        return new Trainer(name, classId, level, xp, scores, skills, maxHp, value, money, items);
    }

    /** A copy with other money. */
    public Trainer withMoney(long value) {
        return new Trainer(name, classId, level, xp, scores, skills, maxHp, hp, value, items);
    }

    /** A copy with another bag. */
    public Trainer withItems(Map<String, Integer> value) {
        return new Trainer(name, classId, level, xp, scores, skills, maxHp, hp, money, value);
    }

    /** A copy with another name. */
    public Trainer withName(String value) {
        return new Trainer(value, classId, level, xp, scores, skills, maxHp, hp, money, items);
    }
}
