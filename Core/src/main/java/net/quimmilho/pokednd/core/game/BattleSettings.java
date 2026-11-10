/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.Locale;
import java.util.Map;

/**
 * The combat settings that decide how a battle is run (chapter 7.17). They are copied into the battle when it starts
 * ({@link GameEvent.BattleStarted}), so changing a setting in the middle of a battle does not change that battle.
 *
 * @param activePerTrainer       {@code combat.activePerTrainer}: active Pokémon per participant (default 1)
 * @param speedDivisor           {@code combat.initiative.speedDivisor}: the initiative modifier is {@code floor(Speed / divisor)} (default 20)
 * @param rerollEachRound        {@code combat.initiative.rerollEachRound}: roll initiative every round, otherwise once per battle (default true)
 * @param trainerActionsFirst    {@code combat.order.trainerActionsFirst}: switches, items, balls and flee attempts resolve before moves (default true)
 * @param declareHidden          {@code combat.declare.hidden}: other players do not see what was declared (default true)
 * @param turnTimerEnabled       {@code combat.turnTimer.enabled} (default false)
 * @param turnTimerSeconds       {@code combat.turnTimer.seconds} (default 90)
 * @param onTimeout              {@code combat.turnTimer.onTimeout}: what happens to a player who did not declare in time (default skip)
 * @param ppEnabled              {@code combat.pp.enabled}: moves use PP (default true)
 * @param catchEnabled           {@code combat.catch.enabled} (default true)
 * @param catchAllowTrainerOwned {@code combat.catch.allowTrainerOwned} (default false)
 * @param fleeAllowTrainerBattles {@code combat.flee.allowTrainerBattles} (default false)
 * @param fleeAllowBoss          {@code combat.flee.allowBoss} (default true)
 */
public record BattleSettings(int activePerTrainer, int speedDivisor, boolean rerollEachRound, boolean trainerActionsFirst,
                             boolean declareHidden, boolean turnTimerEnabled, int turnTimerSeconds, OnTimeout onTimeout,
                             boolean ppEnabled, boolean catchEnabled, boolean catchAllowTrainerOwned,
                             boolean fleeAllowTrainerBattles, boolean fleeAllowBoss) {

    /** {@code combat.turnTimer.onTimeout}. */
    public enum OnTimeout {
        /** The player does nothing this round. */
        SKIP,
        /** The game picks the first move that has PP, against the first foe. */
        AUTO_MOVE;

        /** The word written in the setting: {@code skip} or {@code autoMove}. */
        public String id() {
            return this == SKIP ? "skip" : "autoMove";
        }

        /** Reads a setting value, ignoring case. */
        public static OnTimeout parse(String text) {
            String t = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
            if (t.equals("skip")) {
                return SKIP;
            }
            if (t.equals("automove")) {
                return AUTO_MOVE;
            }
            throw new IllegalArgumentException("combat.turnTimer.onTimeout must be skip or autoMove, not " + text);
        }
    }

    public BattleSettings {
        if (activePerTrainer < 1) {
            throw new IllegalArgumentException("combat.activePerTrainer is at least 1");
        }
        if (speedDivisor < 1) {
            throw new IllegalArgumentException("combat.initiative.speedDivisor is at least 1");
        }
        if (turnTimerSeconds < 1) {
            throw new IllegalArgumentException("combat.turnTimer.seconds is at least 1");
        }
        if (onTimeout == null) {
            throw new IllegalArgumentException("combat.turnTimer.onTimeout needs a value");
        }
    }

    /** The defaults of chapter 7.17. */
    public static BattleSettings defaults() {
        return new BattleSettings(1, 20, true, true, true, false, 90, OnTimeout.SKIP, true, true, false, false, true);
    }

    /**
     * Reads the settings from their keys in chapter 7.17 ({@code combat.initiative.speedDivisor} → {@code "20"}). A key that is missing
     * keeps its default.
     *
     * @throws IllegalArgumentException for a value that is not a number, a yes/no or a known word
     */
    public static BattleSettings fromValues(Map<String, String> values) {
        BattleSettings d = defaults();
        return new BattleSettings(
                number(values, "combat.activePerTrainer", d.activePerTrainer),
                number(values, "combat.initiative.speedDivisor", d.speedDivisor),
                flag(values, "combat.initiative.rerollEachRound", d.rerollEachRound),
                flag(values, "combat.order.trainerActionsFirst", d.trainerActionsFirst),
                flag(values, "combat.declare.hidden", d.declareHidden),
                flag(values, "combat.turnTimer.enabled", d.turnTimerEnabled),
                number(values, "combat.turnTimer.seconds", d.turnTimerSeconds),
                values.containsKey("combat.turnTimer.onTimeout") ? OnTimeout.parse(values.get("combat.turnTimer.onTimeout")) : d.onTimeout,
                flag(values, "combat.pp.enabled", d.ppEnabled),
                flag(values, "combat.catch.enabled", d.catchEnabled),
                flag(values, "combat.catch.allowTrainerOwned", d.catchAllowTrainerOwned),
                flag(values, "combat.flee.allowTrainerBattles", d.fleeAllowTrainerBattles),
                flag(values, "combat.flee.allowBoss", d.fleeAllowBoss));
    }

    public BattleSettings withActivePerTrainer(int value) {
        return new BattleSettings(value, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    public BattleSettings withRerollEachRound(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, value, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    public BattleSettings withTrainerActionsFirst(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, value, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    public BattleSettings withDeclareHidden(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, value, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    public BattleSettings withTurnTimer(boolean enabled, int seconds, OnTimeout action) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, enabled, seconds,
                action, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    public BattleSettings withPpEnabled(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, value, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    public BattleSettings withSpeedDivisor(int value) {
        return new BattleSettings(activePerTrainer, value, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    public BattleSettings withFlee(boolean allowTrainerBattles, boolean allowBoss) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, allowTrainerBattles, allowBoss);
    }

    public BattleSettings withCatch(boolean enabled, boolean allowTrainerOwned) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, enabled, allowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss);
    }

    private static int number(Map<String, String> values, String key, int fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, not " + v);
        }
    }

    private static boolean flag(Map<String, String> values, String key, boolean fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        String t = v.strip().toLowerCase(Locale.ROOT);
        if (t.equals("true")) {
            return true;
        }
        if (t.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false, not " + v);
    }
}
