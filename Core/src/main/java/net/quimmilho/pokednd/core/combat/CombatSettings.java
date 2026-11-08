/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import java.util.List;
import java.util.Locale;

/**
 * The combat settings that hit, crit and damage use (chapter 7.17), with the defaults of the specification.
 *
 * @param critChancesPct      {@code combat.crit.chancesPct}: crit chance in percent for stage 0, +1, +2 and +3 or more (default 5, 10, 15, 25)
 * @param critMultiplier      {@code combat.crit.multiplier}: damage of a critical hit (default 1.5)
 * @param stabMultiplier      {@code combat.stab.multiplier}: same-type attack bonus (default 1.5)
 * @param accuracyStageEffects {@code combat.accuracy.stageEffects}: accuracy and evasion stages change the hit chance (default true)
 * @param dicePowerFactor     {@code combat.damage.dicePowerFactor}: effective power is the dice total times this (default 5)
 * @param powerTable          {@code combat.damage.powerTable}: move power to damage dice (default: the table of 7.6)
 * @param multihitDamageDice  {@code combat.multihit.damageDice}: roll the damage dice once or for every hit (default once)
 */
public record CombatSettings(List<Integer> critChancesPct, double critMultiplier, double stabMultiplier,
                             boolean accuracyStageEffects, int dicePowerFactor, PowerTable powerTable,
                             MultihitDamageDice multihitDamageDice) {

    /** {@code combat.multihit.damageDice}. */
    public enum MultihitDamageDice {
        /** Roll damage once and use the result for every hit (default). */
        ONCE,
        /** Roll damage again for every hit. */
        PER_HIT;

        /** The value written in the settings: {@code once} or {@code perHit}. */
        public String id() {
            return this == ONCE ? "once" : "perHit";
        }

        /** Reads a setting value, ignoring case. */
        public static MultihitDamageDice parse(String text) {
            String t = text.strip().toLowerCase(Locale.ROOT);
            if (t.equals("once")) {
                return ONCE;
            }
            if (t.equals("perhit")) {
                return PER_HIT;
            }
            throw new IllegalArgumentException("combat.multihit.damageDice must be once or perHit, not " + text);
        }
    }

    public CombatSettings {
        critChancesPct = List.copyOf(critChancesPct);
        if (critChancesPct.size() != 4) {
            throw new IllegalArgumentException("combat.crit.chancesPct needs four values (stage 0, +1, +2, +3 or more)");
        }
        for (int c : critChancesPct) {
            if (c < 0 || c > 100) {
                throw new IllegalArgumentException("combat.crit.chancesPct values are 0 to 100, not " + c);
            }
        }
        if (critMultiplier < 0 || stabMultiplier < 0) {
            throw new IllegalArgumentException("Damage multipliers cannot be negative");
        }
        if (dicePowerFactor < 1) {
            throw new IllegalArgumentException("combat.damage.dicePowerFactor must be at least 1");
        }
    }

    /** The defaults of chapter 7.17. */
    public static CombatSettings defaults() {
        return new CombatSettings(List.of(5, 10, 15, 25), 1.5, 1.5, true, 5, PowerTable.defaults(), MultihitDamageDice.ONCE);
    }

    /** A copy with another power table. */
    public CombatSettings withPowerTable(PowerTable table) {
        return new CombatSettings(critChancesPct, critMultiplier, stabMultiplier, accuracyStageEffects, dicePowerFactor, table,
                multihitDamageDice);
    }

    /** A copy with other crit chances. */
    public CombatSettings withCritChancesPct(List<Integer> chances) {
        return new CombatSettings(chances, critMultiplier, stabMultiplier, accuracyStageEffects, dicePowerFactor, powerTable,
                multihitDamageDice);
    }

    /** A copy with another critical-hit multiplier. */
    public CombatSettings withCritMultiplier(double multiplier) {
        return new CombatSettings(critChancesPct, multiplier, stabMultiplier, accuracyStageEffects, dicePowerFactor, powerTable,
                multihitDamageDice);
    }

    /** A copy with another same-type bonus. */
    public CombatSettings withStabMultiplier(double multiplier) {
        return new CombatSettings(critChancesPct, critMultiplier, multiplier, accuracyStageEffects, dicePowerFactor, powerTable,
                multihitDamageDice);
    }

    /** A copy with accuracy stages switched on or off. */
    public CombatSettings withAccuracyStageEffects(boolean enabled) {
        return new CombatSettings(critChancesPct, critMultiplier, stabMultiplier, enabled, dicePowerFactor, powerTable,
                multihitDamageDice);
    }

    /** A copy with another dice-to-power factor. */
    public CombatSettings withDicePowerFactor(int factor) {
        return new CombatSettings(critChancesPct, critMultiplier, stabMultiplier, accuracyStageEffects, factor, powerTable,
                multihitDamageDice);
    }

    /** A copy with another multi-hit damage rule. */
    public CombatSettings withMultihitDamageDice(MultihitDamageDice rule) {
        return new CombatSettings(critChancesPct, critMultiplier, stabMultiplier, accuracyStageEffects, dicePowerFactor, powerTable,
                rule);
    }

    /** The crit chance (percent) for a crit stage: stage 0 and below use the first value, +3 and above the last. */
    public int critChancePct(int critStage) {
        return critChancesPct.get(Math.max(0, Math.min(3, critStage)));
    }

    /** How many times the damage dice are rolled for a move that hits this many times. */
    public int damageRolls(int hits) {
        return multihitDamageDice == MultihitDamageDice.ONCE ? 1 : Math.max(1, hits);
    }
}
