/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import java.util.List;
import java.util.Optional;
import net.quimmilho.pokednd.core.dice.DiceChoice;
import net.quimmilho.pokednd.core.dice.DiceChoice.Band;
import net.quimmilho.pokednd.core.dice.DiceSettings;
import net.quimmilho.pokednd.core.dice.SmartDice;
import net.quimmilho.pokednd.core.dice.SmartDice.Outcome;
import net.quimmilho.pokednd.core.rules.Stats;

/**
 * The hit roll and the critical hit (chapter 7.5). The game builds three bands, <em>miss</em>, <em>hit</em> and
 * <em>critical hit</em>, and the smart-dice rule picks the die.
 *
 * <pre>
 * hitChance = clamp(moveAccuracy × accuracyStageMultiplier + commandBonus + featureBonuses, 0, 100)
 * bands     = [miss = 100 − hitChance, hit = hitChance − critChance, crit = critChance]
 * </pre>
 *
 * A crit is always a hit, so a crit chance above the hit chance is cut down to it.
 */
public final class HitRoll {

    private HitRoll() {
    }

    /** What a roll can decide. */
    public enum Result {
        /** The move misses. */
        MISS,
        /** The move hits. */
        HIT,
        /** The move hits and is a critical hit. */
        CRIT
    }

    /** The band labels used in {@link DiceChoice}. */
    public static final List<String> LABELS = List.of("miss", "hit", "crit");

    /**
     * What goes into a hit roll.
     *
     * @param accuracy       the move's accuracy in percent; empty when the move never misses (shown as a dash)
     * @param accuracyStage  the attacker's accuracy stage, −6 to +6
     * @param evasionStage   the target's evasion stage, −6 to +6
     * @param commandBonusPp the trainer's command bonus, in percentage points
     * @param featureBonusPp bonuses from class features, in percentage points
     * @param critStage      the crit stage: 0 for most moves, more for high-crit moves and some items
     * @param alwaysCrit     true for moves that always crit (Frost Breath): there is no crit roll, every hit is a crit
     */
    public record Input(Optional<Integer> accuracy, int accuracyStage, int evasionStage, double commandBonusPp,
                        double featureBonusPp, int critStage, boolean alwaysCrit) {

        /** A plain attack: this accuracy, no stages, no bonuses, crit stage 0. */
        public static Input plain(int accuracy) {
            return new Input(Optional.of(accuracy), 0, 0, 0, 0, 0, false);
        }

        /** A plain attack that never misses. */
        public static Input neverMisses() {
            return new Input(Optional.empty(), 0, 0, 0, 0, 0, false);
        }

        /** A copy with another accuracy stage. */
        public Input withAccuracyStage(int stage) {
            return new Input(accuracy, stage, evasionStage, commandBonusPp, featureBonusPp, critStage, alwaysCrit);
        }

        /** A copy with another evasion stage. */
        public Input withEvasionStage(int stage) {
            return new Input(accuracy, accuracyStage, stage, commandBonusPp, featureBonusPp, critStage, alwaysCrit);
        }

        /** A copy with another crit stage. */
        public Input withCritStage(int stage) {
            return new Input(accuracy, accuracyStage, evasionStage, commandBonusPp, featureBonusPp, stage, alwaysCrit);
        }

        /** A copy with bonuses in percentage points. */
        public Input withBonusesPp(double command, double features) {
            return new Input(accuracy, accuracyStage, evasionStage, command, features, critStage, alwaysCrit);
        }

        /** A copy that always crits. */
        public Input withAlwaysCrit(boolean always) {
            return new Input(accuracy, accuracyStage, evasionStage, commandBonusPp, featureBonusPp, critStage, always);
        }
    }

    /**
     * What to roll.
     *
     * @param hitChance  the chance to hit, 0 to 1 (a crit counts as a hit)
     * @param critChance the chance of a critical hit, 0 to 1 (never more than the hit chance)
     * @param choice     the die and the bands miss, hit, crit (a band that cannot happen has no faces)
     */
    public record Odds(double hitChance, double critChance, DiceChoice choice) {

        /** True if someone has to roll; false when the outcome is certain (always hits and never crits, or never hits). */
        public boolean rolled() {
            return choice.rolled();
        }

        /** The chance to miss, 0 to 1. */
        public double missChance() {
            return 1.0 - hitChance;
        }

        /** The band for an outcome. */
        public Band band(Result result) {
            return choice.bands().get(result.ordinal());
        }

        /** True if a roll can end in a miss. */
        public boolean canMiss() {
            return band(Result.MISS).possible();
        }

        /**
         * What a rolled number means.
         *
         * @throws IllegalArgumentException if the number is not on the die, or nothing is rolled
         */
        public Result resolve(int roll) {
            if (!rolled()) {
                throw new IllegalArgumentException("Nothing is rolled: use certain()");
            }
            if (roll < 1 || roll > choice.die()) {
                throw new IllegalArgumentException("A d" + choice.die() + " shows 1 to " + choice.die() + ", not " + roll);
            }
            for (Result r : Result.values()) {
                Band b = band(r);
                if (b.faces() > 0 && roll >= b.from() && roll <= b.to()) {
                    return r;
                }
            }
            throw new IllegalStateException("No band covers " + roll + " on a d" + choice.die());
        }

        /** The outcome when nothing is rolled. */
        public Result certain() {
            if (rolled()) {
                throw new IllegalStateException("Someone has to roll");
            }
            for (Result r : Result.values()) {
                if (band(r).realChance() > 0) {
                    return r;
                }
            }
            throw new IllegalStateException("No outcome is possible");
        }
    }

    /** The odds with the default dice settings. */
    public static Odds compute(Input input, CombatSettings settings) {
        return compute(input, settings, DiceSettings.defaults());
    }

    /** The odds of a hit roll: hit chance, crit chance and the die. */
    public static Odds compute(Input input, CombatSettings settings, DiceSettings dice) {
        double hitPct;
        if (input.accuracy().isEmpty()) {
            hitPct = 100.0; // the dash: never misses, only rolls for a crit
        } else {
            double stage = settings.accuracyStageEffects()
                    ? Stats.accuracyStageMultiplier(Stats.clampStage(input.accuracyStage() - input.evasionStage()))
                    : 1.0;
            hitPct = clamp(input.accuracy().get() * stage + input.commandBonusPp() + input.featureBonusPp());
        }
        double critPct = input.alwaysCrit() ? hitPct : Math.min(settings.critChancePct(input.critStage()), hitPct);
        return build(hitPct, critPct, dice);
    }

    /**
     * A one-hit-KO move (Fissure, Guillotine, Horn Drill, Sheer Cold): its accuracy is {@code 30 + (attacker level − target
     * level)} percent, and it fails outright if the attacker's level is lower. Stages and crits do not apply.
     */
    public static Odds oneHitKo(int attackerLevel, int targetLevel, DiceSettings dice) {
        double hitPct = attackerLevel < targetLevel ? 0.0 : clamp(30.0 + attackerLevel - targetLevel);
        return build(hitPct, 0.0, dice);
    }

    private static Odds build(double hitPct, double critPct, DiceSettings dice) {
        double hit = hitPct / 100.0;
        double crit = Math.min(critPct / 100.0, hit);
        double miss = 1.0 - hit;
        double plainHit = hit - crit;
        DiceChoice choice = SmartDice.choose(dice, List.of(new Outcome("miss", miss), new Outcome("hit", Math.max(0.0, plainHit)),
                new Outcome("crit", crit)));
        return new Odds(hit, crit, choice);
    }

    private static double clamp(double pct) {
        return Math.max(0.0, Math.min(100.0, pct));
    }
}
