/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import net.quimmilho.pokednd.core.rules.Stats;

/**
 * Damage (chapter 7.6): the player rolls dice instead of the move's fixed power, and the official formula does the rest.
 * The usual random factor of 0.85 to 1.0 is dropped, because the dice already supply the randomness.
 *
 * <pre>
 * base   = floor(floor(floor(2 × Level / 5 + 2) × EffectivePower × Attack / Defense) / 50) + 2
 * damage = floor(base × Crit × STAB × TypeEffectiveness × Burn × Other)      (at least 1 if the target is not immune)
 * </pre>
 *
 * The effective power is the dice total times {@code combat.damage.dicePowerFactor} (5).
 */
public final class Damage {

    private Damage() {
    }

    /**
     * What goes into one damage calculation.
     *
     * @param level             the attacker's level
     * @param diceTotal         what the player rolled on the damage dice, plus any style dice from class features
     * @param attack            the attacker's Attack (physical moves) or Sp. Atk (special moves), before stages
     * @param defense           the target's Defense or Sp. Def, before stages
     * @param attackStage       the attacker's stage in that stat, −6 to +6
     * @param defenseStage      the target's stage in that stat, −6 to +6
     * @param crit              true on a critical hit
     * @param stab              true if the move's type matches one of the attacker's types
     * @param typeEffectiveness 0, 0.25, 0.5, 1, 2 or 4 (chapter 7.7)
     * @param burnedPhysical    true for a physical move from a burned attacker
     * @param other             everything else from abilities and items (1.0 if nothing applies)
     */
    public record Input(int level, int diceTotal, int attack, int defense, int attackStage, int defenseStage, boolean crit,
                        boolean stab, double typeEffectiveness, boolean burnedPhysical, double other) {

        /** A simple case: no stages, no crit, no STAB, neutral type, no burn, nothing else. */
        public static Input plain(int level, int diceTotal, int attack, int defense) {
            return new Input(level, diceTotal, attack, defense, 0, 0, false, false, 1.0, false, 1.0);
        }

        /** A copy with a critical hit. */
        public Input withCrit(boolean value) {
            return new Input(level, diceTotal, attack, defense, attackStage, defenseStage, value, stab, typeEffectiveness,
                    burnedPhysical, other);
        }

        /** A copy with STAB. */
        public Input withStab(boolean value) {
            return new Input(level, diceTotal, attack, defense, attackStage, defenseStage, crit, value, typeEffectiveness,
                    burnedPhysical, other);
        }

        /** A copy with another type effectiveness. */
        public Input withTypeEffectiveness(double value) {
            return new Input(level, diceTotal, attack, defense, attackStage, defenseStage, crit, stab, value, burnedPhysical, other);
        }

        /** A copy with stat stages. */
        public Input withStages(int attackStage, int defenseStage) {
            return new Input(level, diceTotal, attack, defense, attackStage, defenseStage, crit, stab, typeEffectiveness,
                    burnedPhysical, other);
        }

        /** A copy with the burn penalty. */
        public Input withBurn(boolean value) {
            return new Input(level, diceTotal, attack, defense, attackStage, defenseStage, crit, stab, typeEffectiveness, value,
                    other);
        }

        /** A copy with the "other" factor from abilities and items. */
        public Input withOther(double value) {
            return new Input(level, diceTotal, attack, defense, attackStage, defenseStage, crit, stab, typeEffectiveness,
                    burnedPhysical, value);
        }
    }

    /**
     * The result.
     *
     * @param effectivePower the dice total times the power factor
     * @param base           the damage before the multipliers
     * @param damage         the final damage (0 only if the target is immune)
     * @param multiplier     crit × STAB × type × burn × other, as applied
     * @param immune         true if the type chart says the move has no effect
     */
    public record Result(int effectivePower, int base, int damage, double multiplier, boolean immune) {
    }

    /** The effective power: the dice total times {@code combat.damage.dicePowerFactor}. */
    public static int effectivePower(int diceTotal, CombatSettings settings) {
        return diceTotal * settings.dicePowerFactor();
    }

    /**
     * Works out the damage.
     *
     * <p>On a critical hit the target's positive Defense stages and the attacker's negative Attack stages are ignored.
     *
     * @throws IllegalArgumentException if the level is below 1 or the dice total is negative
     */
    public static Result compute(Input in, CombatSettings settings) {
        if (in.level() < 1) {
            throw new IllegalArgumentException("A level is at least 1, not " + in.level());
        }
        if (in.diceTotal() < 0) {
            throw new IllegalArgumentException("The dice total cannot be negative");
        }
        int attackStage = in.crit() ? Math.max(0, in.attackStage()) : in.attackStage();
        int defenseStage = in.crit() ? Math.min(0, in.defenseStage()) : in.defenseStage();
        long attack = Math.max(1, Stats.applyStage(in.attack(), attackStage));
        long defense = Math.max(1, Stats.applyStage(in.defense(), defenseStage));
        int power = effectivePower(in.diceTotal(), settings);

        long levelTerm = 2L * in.level() / 5 + 2;
        long inner = levelTerm * power * attack / defense;
        int base = (int) (inner / 50) + 2;

        double multiplier = (in.crit() ? settings.critMultiplier() : 1.0) * (in.stab() ? settings.stabMultiplier() : 1.0)
                * in.typeEffectiveness() * (in.burnedPhysical() ? 0.5 : 1.0) * in.other();
        boolean immune = in.typeEffectiveness() == 0.0;
        int damage = immune ? 0 : Math.max(1, (int) Math.floor(base * multiplier + 1e-9));
        return new Result(power, base, damage, multiplier, immune);
    }

    /** Damage of a fixed-damage move such as Seismic Toss (the user's level) or Dragon Rage (40); 0 if the target is immune. */
    public static int fixed(MoveProfile.FixedDamage fixed, int attackerLevel, double typeEffectiveness) {
        return typeEffectiveness == 0.0 ? 0 : fixed.damage(attackerLevel);
    }
}
