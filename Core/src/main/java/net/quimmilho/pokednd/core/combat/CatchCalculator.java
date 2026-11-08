/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import java.util.Optional;
import net.quimmilho.pokednd.core.dice.DiceSettings;
import net.quimmilho.pokednd.core.dice.SmartDice;
import net.quimmilho.pokednd.core.rules.MajorStatus;

/**
 * Catching a wild Pokémon (chapter 7.10). The official catch value {@code a} is turned into one chance, and the chance into
 * a smart-dice roll:
 *
 * <pre>
 * a      = ((3 × maxHP − 2 × currentHP) × catchRate × ballMultiplier) / (3 × maxHP) × statusMultiplier
 * chance = min(1, a / 255) × trainerMultiplier
 * </pre>
 *
 * It is the same probability as the official "four shakes" procedure, but it is one roll.
 */
public final class CatchCalculator {

    private CatchCalculator() {
    }

    /** What the Pokémon being caught is. */
    public enum Target {
        /** An ordinary wild Pokémon. */
        WILD,
        /** A boss: cannot be caught. */
        BOSS,
        /** A Pokémon that belongs to a trainer: only if {@code combat.catch.allowTrainerOwned} is on. */
        TRAINER_OWNED
    }

    /**
     * Settings of chapters 4.7 and 7.17, with their defaults.
     *
     * @param enabled          {@code combat.catch.enabled}: catching is allowed (default true)
     * @param allowTrainerOwned {@code combat.catch.allowTrainerOwned}: Pokémon of NPC trainers can be caught (default false)
     * @param bonusPerPoint    {@code combat.catch.bonusPerPoint}: relative catch bonus per Handling point (default 0.05)
     * @param allowPenalty     {@code combat.catch.allowPenalty}: negative Handling modifiers also reduce the chance (default false)
     */
    public record Settings(boolean enabled, boolean allowTrainerOwned, double bonusPerPoint, boolean allowPenalty) {

        /** The defaults. */
        public static Settings defaults() {
            return new Settings(true, false, 0.05, false);
        }

        /** A copy with catching switched on or off. */
        public Settings withEnabled(boolean value) {
            return new Settings(value, allowTrainerOwned, bonusPerPoint, allowPenalty);
        }

        /** A copy that lets trainers' Pokémon be caught. */
        public Settings withAllowTrainerOwned(boolean value) {
            return new Settings(enabled, value, bonusPerPoint, allowPenalty);
        }

        /** A copy with another bonus per Handling point. */
        public Settings withBonusPerPoint(double value) {
            return new Settings(enabled, allowTrainerOwned, value, allowPenalty);
        }

        /** A copy where negative Handling modifiers also count. */
        public Settings withAllowPenalty(boolean value) {
            return new Settings(enabled, allowTrainerOwned, bonusPerPoint, value);
        }
    }

    /**
     * One throw.
     *
     * @param target           what is being caught
     * @param maxHp            the target's maximum HP, at least 1
     * @param currentHp        its HP now; 0 means it has fainted
     * @param catchRate        the species' Catch Rate, 3 to 255
     * @param ball             the ball item id, for example {@code ultra_ball}
     * @param status           the target's major status, if any
     * @param handlingModifier the throwing trainer's Pokémon Handling modifier (can be negative)
     * @param otherMultiplier  other multipliers from class features, 1.0 if none
     * @param context          the round, the time and place, and what was caught before
     */
    public record Throw(Target target, int maxHp, int currentHp, int catchRate, String ball, Optional<MajorStatus> status,
                        int handlingModifier, double otherMultiplier, BallTable.Context context) {

        public Throw {
            if (maxHp < 1) {
                throw new IllegalArgumentException("Maximum HP is at least 1, not " + maxHp);
            }
            if (catchRate < 0 || catchRate > 255) {
                throw new IllegalArgumentException("A catch rate is 0 to 255, not " + catchRate);
            }
            if (otherMultiplier < 0) {
                throw new IllegalArgumentException("A multiplier cannot be negative");
            }
        }

        /** A plain throw at a wild Pokémon: no status, no Handling bonus, first round. */
        public static Throw wild(int maxHp, int currentHp, int catchRate, String ball) {
            return new Throw(Target.WILD, maxHp, currentHp, catchRate, ball, Optional.empty(), 0, 1.0,
                    BallTable.Context.firstRound(java.util.List.of()));
        }

        /** A copy with a major status. */
        public Throw withStatus(MajorStatus value) {
            return new Throw(target, maxHp, currentHp, catchRate, ball, Optional.of(value), handlingModifier, otherMultiplier, context);
        }

        /** A copy with a Handling modifier. */
        public Throw withHandling(int modifier) {
            return new Throw(target, maxHp, currentHp, catchRate, ball, status, modifier, otherMultiplier, context);
        }

        /** A copy with another context. */
        public Throw withContext(BallTable.Context value) {
            return new Throw(target, maxHp, currentHp, catchRate, ball, status, handlingModifier, otherMultiplier, value);
        }

        /** A copy with another target kind. */
        public Throw withTarget(Target value) {
            return new Throw(value, maxHp, currentHp, catchRate, ball, status, handlingModifier, otherMultiplier, context);
        }

        /** A copy with a class-feature multiplier. */
        public Throw withOtherMultiplier(double value) {
            return new Throw(target, maxHp, currentHp, catchRate, ball, status, handlingModifier, value, context);
        }
    }

    /**
     * The odds of one throw.
     *
     * @param allowed          false if the throw cannot catch anything (see {@code reason})
     * @param reason           empty when allowed; otherwise a plain sentence for the screen
     * @param catchValue       the official {@code a} (before the 255 limit)
     * @param ballMultiplier   the ball's multiplier in this situation
     * @param statusMultiplier ×2.5 for sleep or freeze, ×1.5 for paralysis, poison or burn, else ×1
     * @param trainerMultiplier the Handling bonus times the class features
     * @param chance           the chance to catch, 0 to 1
     * @param roll             the die: a success roll, or "always"/"never" when nobody rolls
     */
    public record Odds(boolean allowed, String reason, double catchValue, double ballMultiplier, double statusMultiplier,
                       double trainerMultiplier, double chance, SmartDice.Success roll) {
    }

    /** The odds with the default settings, ball table and dice. */
    public static Odds odds(Throw t) {
        return odds(t, Settings.defaults(), BallTable.defaults(), DiceSettings.defaults());
    }

    /** The odds of a throw. */
    public static Odds odds(Throw t, Settings settings, BallTable balls, DiceSettings dice) {
        if (!settings.enabled()) {
            return refused("Catching is switched off in this game.", dice);
        }
        if (t.target() == Target.BOSS) {
            return refused("A boss cannot be caught.", dice);
        }
        if (t.target() == Target.TRAINER_OWNED && !settings.allowTrainerOwned()) {
            return refused("This Pokémon belongs to a trainer and cannot be caught.", dice);
        }
        if (t.currentHp() <= 0) {
            return refused("A fainted Pokémon cannot be caught.", dice);
        }
        double ball = balls.multiplier(t.ball(), t.context());
        if (balls.alwaysCatches(t.ball())) {
            return new Odds(true, "", 255.0, 1.0, 1.0, 1.0, 1.0, SmartDice.success(dice, 1.0));
        }
        int maxHp = t.maxHp();
        int hp = Math.min(t.currentHp(), maxHp);
        double status = statusMultiplier(t.status());
        double a = (3.0 * maxHp - 2.0 * hp) * t.catchRate() * ball / (3.0 * maxHp) * status;
        double trainer = trainerMultiplier(t.handlingModifier(), t.otherMultiplier(), settings);
        double chance = Math.max(0.0, Math.min(1.0, Math.min(1.0, a / 255.0) * trainer));
        return new Odds(true, "", a, ball, status, trainer, chance, SmartDice.success(dice, chance));
    }

    /** ×2.5 for sleep or freeze, ×1.5 for paralysis, poison or burn, otherwise ×1. */
    public static double statusMultiplier(Optional<MajorStatus> status) {
        if (status.isEmpty()) {
            return 1.0;
        }
        return switch (status.get()) {
            case SLEEP, FREEZE -> 2.5;
            case PARALYSIS, POISON, BURN -> 1.5;
        };
    }

    /**
     * The trainer's multiplier: {@code 1 + bonusPerPoint × Handling modifier}, times the class features. A negative Handling
     * modifier counts as 0 unless {@code combat.catch.allowPenalty} is on.
     */
    public static double trainerMultiplier(int handlingModifier, double otherMultiplier, Settings settings) {
        int points = settings.allowPenalty() ? handlingModifier : Math.max(0, handlingModifier);
        return Math.max(0.0, 1.0 + settings.bonusPerPoint() * points) * otherMultiplier;
    }

    private static Odds refused(String reason, DiceSettings dice) {
        return new Odds(false, reason, 0.0, 1.0, 1.0, 1.0, 0.0, SmartDice.success(dice, 0.0));
    }
}
