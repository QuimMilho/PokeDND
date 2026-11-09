/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.rules.TrainerXp;

/**
 * The trainer settings of chapter 4.7, with their defaults. The XP curve, table, multiplier and level range live in
 * {@link TrainerXp}; the Handling, catch and flee conversions are in {@link TrainerBonuses}.
 *
 * @param allowedMethods      {@code trainer.abilityScores.method}: the methods a player may use (default point buy)
 * @param pointBuyBudget      {@code trainer.abilityScores.pointBuyBudget}: points for point buy (default 27)
 * @param startMoney          {@code trainer.start.money}: starting ₽ (default 3000)
 * @param startItems          {@code trainer.start.items}: the common starting kit (default 5 Poké Balls, 3 Potions)
 * @param hpRecoverPerRoundPct {@code trainer.hp.recoverPerRoundPct}: HP regained per overworld round (default 10)
 * @param koMode              {@code trainer.ko.mode}: what happens at 0 HP (default faintRespawn)
 * @param deathAllowed        {@code trainer.ko.deathAllowed}: with death saves, a third failure kills (default false)
 * @param xp                  the trainer XP settings, including {@code trainer.level.start} and {@code trainer.level.cap}
 */
public record TrainerSettings(Set<ScoreMethod> allowedMethods, int pointBuyBudget, long startMoney, Map<String, Integer> startItems,
                              int hpRecoverPerRoundPct, KoMode koMode, boolean deathAllowed, TrainerXp xp) {

    public TrainerSettings {
        allowedMethods = Set.copyOf(allowedMethods);
        if (allowedMethods.isEmpty()) {
            throw new IllegalArgumentException("trainer.abilityScores.method must allow at least one method");
        }
        if (pointBuyBudget < 0) {
            throw new IllegalArgumentException("trainer.abilityScores.pointBuyBudget cannot be negative");
        }
        if (startMoney < 0) {
            throw new IllegalArgumentException("trainer.start.money cannot be negative");
        }
        startItems = Map.copyOf(startItems);
        startItems.forEach((item, n) -> {
            if (n < 1) {
                throw new IllegalArgumentException("trainer.start.items needs a count of at least 1 for " + item);
            }
        });
        if (hpRecoverPerRoundPct < 0 || hpRecoverPerRoundPct > 100) {
            throw new IllegalArgumentException("trainer.hp.recoverPerRoundPct is 0 to 100, not " + hpRecoverPerRoundPct);
        }
    }

    /** The defaults of chapter 4.7. */
    public static TrainerSettings defaults() {
        return new TrainerSettings(EnumSet.of(ScoreMethod.POINT_BUY), 27, 3000, Map.of("poke_ball", 5, "potion", 3), 10,
                KoMode.FAINT_RESPAWN, false, TrainerXp.defaults());
    }

    /** A copy that allows these methods. */
    public TrainerSettings withAllowedMethods(Set<ScoreMethod> value) {
        return new TrainerSettings(value, pointBuyBudget, startMoney, startItems, hpRecoverPerRoundPct, koMode, deathAllowed, xp);
    }

    /** A copy with another point-buy budget. */
    public TrainerSettings withPointBuyBudget(int value) {
        return new TrainerSettings(allowedMethods, value, startMoney, startItems, hpRecoverPerRoundPct, koMode, deathAllowed, xp);
    }

    /** A copy with other starting money. */
    public TrainerSettings withStartMoney(long value) {
        return new TrainerSettings(allowedMethods, pointBuyBudget, value, startItems, hpRecoverPerRoundPct, koMode, deathAllowed, xp);
    }

    /** A copy with another starting kit. */
    public TrainerSettings withStartItems(Map<String, Integer> value) {
        return new TrainerSettings(allowedMethods, pointBuyBudget, startMoney, value, hpRecoverPerRoundPct, koMode, deathAllowed, xp);
    }

    /** A copy with another recovery per round. */
    public TrainerSettings withHpRecoverPerRoundPct(int value) {
        return new TrainerSettings(allowedMethods, pointBuyBudget, startMoney, startItems, value, koMode, deathAllowed, xp);
    }

    /** A copy with another knock-out mode. */
    public TrainerSettings withKoMode(KoMode value) {
        return new TrainerSettings(allowedMethods, pointBuyBudget, startMoney, startItems, hpRecoverPerRoundPct, value, deathAllowed, xp);
    }

    /** A copy that lets a third failed death save kill the trainer, or not. */
    public TrainerSettings withDeathAllowed(boolean value) {
        return new TrainerSettings(allowedMethods, pointBuyBudget, startMoney, startItems, hpRecoverPerRoundPct, koMode, value, xp);
    }

    /** A copy with other trainer XP settings. */
    public TrainerSettings withXp(TrainerXp value) {
        return new TrainerSettings(allowedMethods, pointBuyBudget, startMoney, startItems, hpRecoverPerRoundPct, koMode, deathAllowed, value);
    }
}
