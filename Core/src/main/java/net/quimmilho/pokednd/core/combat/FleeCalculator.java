/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import net.quimmilho.pokednd.core.dice.DiceSettings;
import net.quimmilho.pokednd.core.dice.SmartDice;

/**
 * Fleeing from a battle (chapter 7.11). The official escape formula gives the chance, and the chance becomes a smart-dice roll:
 *
 * <pre>
 * F      = floor(ownSpeed × 128 / opponentSpeed) + 30 × numberOfAttempts
 * chance = min(1, F / 256) + the trainer's DEX bonus
 * </pre>
 *
 * If your Speed is at least the opponent's, you always escape. The first try counts as attempt 1.
 */
public final class FleeCalculator {

    private FleeCalculator() {
    }

    /** What kind of battle it is. */
    public enum Battle {
        /** A wild Pokémon. */
        WILD,
        /** An NPC trainer or a PvP battle. */
        TRAINER,
        /** A boss. */
        BOSS
    }

    /**
     * Settings of chapters 4.7 and 7.17, with their defaults.
     *
     * @param allowTrainerBattles {@code combat.flee.allowTrainerBattles}: flee from NPC and PvP battles (default false)
     * @param allowBoss           {@code combat.flee.allowBoss}: flee from bosses (default true)
     * @param bonusPpPerPoint     {@code combat.flee.bonusPpPerPoint}: percentage points per DEX modifier point (default 2)
     */
    public record Settings(boolean allowTrainerBattles, boolean allowBoss, double bonusPpPerPoint) {

        /** The defaults. */
        public static Settings defaults() {
            return new Settings(false, true, 2.0);
        }

        /** A copy that allows fleeing from NPC and PvP battles. */
        public Settings withAllowTrainerBattles(boolean value) {
            return new Settings(value, allowBoss, bonusPpPerPoint);
        }

        /** A copy that allows or forbids fleeing from bosses. */
        public Settings withAllowBoss(boolean value) {
            return new Settings(allowTrainerBattles, value, bonusPpPerPoint);
        }

        /** A copy with another bonus per DEX point. */
        public Settings withBonusPpPerPoint(double value) {
            return new Settings(allowTrainerBattles, allowBoss, value);
        }
    }

    /**
     * One try to flee.
     *
     * @param battle        what kind of battle it is
     * @param ownSpeed      the fleeing trainer's active Pokémon's Speed
     * @param opponentSpeed the opponent's Speed (the fastest one, if there are several)
     * @param attempt       which try this is for this trainer in this battle: 1 for the first
     * @param dexModifier   the trainer's DEX modifier (can be negative)
     */
    public record Attempt(Battle battle, int ownSpeed, int opponentSpeed, int attempt, int dexModifier) {

        public Attempt {
            if (ownSpeed < 0 || opponentSpeed < 0) {
                throw new IllegalArgumentException("Speed cannot be negative");
            }
            if (attempt < 1) {
                throw new IllegalArgumentException("The first try is attempt 1, not " + attempt);
            }
        }

        /** A try to flee from a wild Pokémon, with no DEX bonus. */
        public static Attempt wild(int ownSpeed, int opponentSpeed, int attempt) {
            return new Attempt(Battle.WILD, ownSpeed, opponentSpeed, attempt, 0);
        }

        /** A copy with a DEX modifier. */
        public Attempt withDex(int modifier) {
            return new Attempt(battle, ownSpeed, opponentSpeed, attempt, modifier);
        }

        /** A copy for another kind of battle. */
        public Attempt inBattle(Battle value) {
            return new Attempt(value, ownSpeed, opponentSpeed, attempt, dexModifier);
        }
    }

    /**
     * The odds of one try.
     *
     * @param allowed fleeing is allowed in this battle
     * @param reason  empty when allowed; otherwise a plain sentence for the screen
     * @param f       the official {@code F} (0 when fleeing is not allowed or Speed decides)
     * @param chance  the chance to escape, 0 to 1
     * @param roll    the die: a success roll, or "always"/"never" when nobody rolls
     */
    public record Odds(boolean allowed, String reason, int f, double chance, SmartDice.Success roll) {
    }

    /** The odds with the default settings and dice. */
    public static Odds odds(Attempt attempt) {
        return odds(attempt, Settings.defaults(), DiceSettings.defaults());
    }

    /** The odds of a try to flee. */
    public static Odds odds(Attempt a, Settings settings, DiceSettings dice) {
        if (a.battle() == Battle.TRAINER && !settings.allowTrainerBattles()) {
            return refused("You cannot flee from a trainer battle.", dice);
        }
        if (a.battle() == Battle.BOSS && !settings.allowBoss()) {
            return refused("You cannot flee from a boss.", dice);
        }
        if (a.opponentSpeed() == 0 || a.ownSpeed() >= a.opponentSpeed()) {
            return new Odds(true, "", 0, 1.0, SmartDice.success(dice, 1.0));
        }
        int f = (int) ((long) a.ownSpeed() * 128 / a.opponentSpeed()) + 30 * a.attempt();
        double chance = Math.min(1.0, f / 256.0) + a.dexModifier() * settings.bonusPpPerPoint() / 100.0;
        chance = Math.max(0.0, Math.min(1.0, chance));
        return new Odds(true, "", f, chance, SmartDice.success(dice, chance));
    }

    private static Odds refused(String reason, DiceSettings dice) {
        return new Odds(false, reason, 0, 0.0, SmartDice.success(dice, 0.0));
    }
}
