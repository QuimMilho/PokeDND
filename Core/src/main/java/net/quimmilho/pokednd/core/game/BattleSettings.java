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
 * @param abilitiesEnabled       {@code combat.abilities.enabled}: abilities have effects (default true; chapter 7.9)
 * @param heldItemsEnabled       {@code combat.heldItems.enabled}: held items have effects (default true; chapter 7.9)
 * @param enemy                  how enemies choose and attack trainers (chapters 7.12 and 7.13)
 * @param knockOut               what happens to a trainer at 0 HP (chapter 4.6)
 * @param prizes                 what a won battle pays, and whether PvP is allowed (chapter 7.14)
 */
public record BattleSettings(int activePerTrainer, int speedDivisor, boolean rerollEachRound, boolean trainerActionsFirst,
                             boolean declareHidden, boolean turnTimerEnabled, int turnTimerSeconds, OnTimeout onTimeout,
                             boolean ppEnabled, boolean catchEnabled, boolean catchAllowTrainerOwned,
                             boolean fleeAllowTrainerBattles, boolean fleeAllowBoss, boolean abilitiesEnabled, boolean heldItemsEnabled,
                             Enemy enemy, KnockOutRules knockOut, Prizes prizes) {

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
        enemy = enemy == null ? Enemy.defaults() : enemy;
        knockOut = knockOut == null ? KnockOutRules.defaults() : knockOut;
        prizes = prizes == null ? Prizes.defaults() : prizes;
    }

    /** The shape of phase 47 and 48: the prizes have their defaults. */
    public BattleSettings(int activePerTrainer, int speedDivisor, boolean rerollEachRound, boolean trainerActionsFirst,
                          boolean declareHidden, boolean turnTimerEnabled, int turnTimerSeconds, OnTimeout onTimeout,
                          boolean ppEnabled, boolean catchEnabled, boolean catchAllowTrainerOwned,
                          boolean fleeAllowTrainerBattles, boolean fleeAllowBoss, boolean abilitiesEnabled, boolean heldItemsEnabled,
                          Enemy enemy, KnockOutRules knockOut) {
        this(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds, onTimeout,
                ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, null);
    }

    /**
     * What a won battle pays and who may fight each other (chapter 7.14).
     *
     * @param perLevel     {@code combat.prize.perLevel}: ₽ per level of the opponent's highest Pokémon, paid by an NPC trainer (default 40)
     * @param pvpEnabled   {@code combat.pvp.enabled}: players may fight each other (default true)
     * @param wagerAllowed {@code combat.pvp.wagerAllowed}: a PvP battle may have a wager (default false)
     */
    public record Prizes(int perLevel, boolean pvpEnabled, boolean wagerAllowed) {
        public Prizes {
            if (perLevel < 0) {
                throw new IllegalArgumentException("combat.prize.perLevel cannot be negative");
            }
        }

        /** The defaults of chapter 7.17. */
        public static Prizes defaults() {
            return new Prizes(40, true, false);
        }
    }
    /** The shape of phase 47: the settings of enemies and of knock-outs have their defaults. */
    public BattleSettings(int activePerTrainer, int speedDivisor, boolean rerollEachRound, boolean trainerActionsFirst,
                          boolean declareHidden, boolean turnTimerEnabled, int turnTimerSeconds, OnTimeout onTimeout,
                          boolean ppEnabled, boolean catchEnabled, boolean catchAllowTrainerOwned,
                          boolean fleeAllowTrainerBattles, boolean fleeAllowBoss, boolean abilitiesEnabled, boolean heldItemsEnabled) {
        this(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds, onTimeout,
                ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, null, null);
    }

    /** How enemies choose their actions and attack trainers (chapters 7.12 and 7.13). */
    public enum AiLevel {
        /** Mostly random. */
        EASY("easy"),
        /** Picks among the best moves, weighted by their score. */
        NORMAL("normal"),
        /** Always the best move; NPC trainers switch out of bad matchups. */
        HARD("hard");

        private final String id;

        AiLevel(String id) {
            this.id = id;
        }

        /** The word written in the setting. */
        public String id() {
            return id;
        }

        /** Reads a setting value, ignoring case. */
        public static AiLevel parse(String text) {
            String t = text == null ? "" : text.strip();
            for (AiLevel level : values()) {
                if (level.id.equalsIgnoreCase(t)) {
                    return level;
                }
            }
            throw new IllegalArgumentException("combat.ai.level must be easy, normal or hard, not " + text);
        }
    }

    /**
     * The settings of enemies (chapters 7.12 and 7.13).
     *
     * @param aiLevel            {@code combat.ai.level} (default normal)
     * @param targetTrainerPct   {@code combat.ai.targetTrainerPct}: the chance per enemy action to aim at a trainer, 0 to 100 (default 10)
     * @param damageScale        {@code combat.trainer.damageScale}: damage to a trainer is multiplied by this (default 0.25)
     * @param defenseBase        {@code combat.trainer.defenseBase}: the trainer's Defense value, base (default 20)
     * @param defensePerLevel    {@code combat.trainer.defensePerLevel}: the trainer's Defense value, per trainer level (default 4)
     * @param attackLevelDivisor {@code combat.trainer.attackLevelDivisor}: the enemy's attack roll adds {@code level / divisor} (default 8)
     */
    public record Enemy(AiLevel aiLevel, int targetTrainerPct, double damageScale, int defenseBase, int defensePerLevel, int attackLevelDivisor) {
        public Enemy {
            if (aiLevel == null) {
                throw new IllegalArgumentException("combat.ai.level needs a value");
            }
            if (targetTrainerPct < 0 || targetTrainerPct > 100) {
                throw new IllegalArgumentException("combat.ai.targetTrainerPct is 0 to 100, not " + targetTrainerPct);
            }
            if (damageScale <= 0) {
                throw new IllegalArgumentException("combat.trainer.damageScale is above 0");
            }
            if (attackLevelDivisor < 1) {
                throw new IllegalArgumentException("combat.trainer.attackLevelDivisor is at least 1");
            }
        }

        /** The defaults of chapter 7.17. */
        public static Enemy defaults() {
            return new Enemy(AiLevel.NORMAL, 10, 0.25, 20, 4, 8);
        }

        public Enemy withAiLevel(AiLevel value) {
            return new Enemy(value, targetTrainerPct, damageScale, defenseBase, defensePerLevel, attackLevelDivisor);
        }

        public Enemy withTargetTrainerPct(int value) {
            return new Enemy(aiLevel, value, damageScale, defenseBase, defensePerLevel, attackLevelDivisor);
        }

        /** The Defense value of a trainer of this level: {@code 20 + 4 x level} with the defaults. */
        public int trainerDefense(int trainerLevel) {
            return defenseBase + defensePerLevel * trainerLevel;
        }
    }

    /**
     * What happens to a trainer at 0 HP (chapter 4.6).
     *
     * @param mode         {@code trainer.ko.mode} (default faintRespawn)
     * @param deathAllowed {@code trainer.ko.deathAllowed}: with death saves, a third failure kills the trainer (default false)
     * @param moneyLossPct {@code combat.whiteout.moneyLossPct}: the ₽ lost on a knock-out, 0 to 100 (default 10)
     */
    public record KnockOutRules(net.quimmilho.pokednd.core.trainer.KoMode mode, boolean deathAllowed, int moneyLossPct) {
        public KnockOutRules {
            if (mode == null) {
                throw new IllegalArgumentException("trainer.ko.mode needs a value");
            }
            if (moneyLossPct < 0 || moneyLossPct > 100) {
                throw new IllegalArgumentException("combat.whiteout.moneyLossPct is 0 to 100, not " + moneyLossPct);
            }
        }

        /** The defaults of chapters 4.7 and 7.17. */
        public static KnockOutRules defaults() {
            return new KnockOutRules(net.quimmilho.pokednd.core.trainer.KoMode.FAINT_RESPAWN, false, 10);
        }
    }

    /** The defaults of chapter 7.17. */
    public static BattleSettings defaults() {
        return new BattleSettings(1, 20, true, true, true, false, 90, OnTimeout.SKIP, true, true, false, false, true, true, true, Enemy.defaults(), KnockOutRules.defaults(), Prizes.defaults());
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
                flag(values, "combat.flee.allowBoss", d.fleeAllowBoss),
                flag(values, "combat.abilities.enabled", d.abilitiesEnabled),
                flag(values, "combat.heldItems.enabled", d.heldItemsEnabled),
                new Enemy(values.containsKey("combat.ai.level") ? AiLevel.parse(values.get("combat.ai.level")) : d.enemy.aiLevel,
                        number(values, "combat.ai.targetTrainerPct", d.enemy.targetTrainerPct),
                        decimal(values, "combat.trainer.damageScale", d.enemy.damageScale),
                        number(values, "combat.trainer.defenseBase", d.enemy.defenseBase),
                        number(values, "combat.trainer.defensePerLevel", d.enemy.defensePerLevel),
                        number(values, "combat.trainer.attackLevelDivisor", d.enemy.attackLevelDivisor)),
                new KnockOutRules(values.containsKey("trainer.ko.mode") ? net.quimmilho.pokednd.core.trainer.KoMode.parse(values.get("trainer.ko.mode")) : d.knockOut.mode,
                        flag(values, "trainer.ko.deathAllowed", d.knockOut.deathAllowed),
                        number(values, "combat.whiteout.moneyLossPct", d.knockOut.moneyLossPct)),
                new Prizes(number(values, "combat.prize.perLevel", d.prizes.perLevel), flag(values, "combat.pvp.enabled", d.prizes.pvpEnabled),
                        flag(values, "combat.pvp.wagerAllowed", d.prizes.wagerAllowed)));
    }

    public BattleSettings withActivePerTrainer(int value) {
        return new BattleSettings(value, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withRerollEachRound(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, value, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withTrainerActionsFirst(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, value, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withDeclareHidden(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, value, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withTurnTimer(boolean enabled, int seconds, OnTimeout action) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, enabled, seconds,
                action, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withPpEnabled(boolean value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, value, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withSpeedDivisor(int value) {
        return new BattleSettings(activePerTrainer, value, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withFlee(boolean allowTrainerBattles, boolean allowBoss) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, allowTrainerBattles, allowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    public BattleSettings withCatch(boolean enabled, boolean allowTrainerOwned) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, enabled, allowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, prizes);
    }

    /** Switches abilities ({@code combat.abilities.enabled}) and held items ({@code combat.heldItems.enabled}) on or off. */
    public BattleSettings withTraits(boolean abilities, boolean heldItems) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilities, heldItems, enemy, knockOut, prizes);
    }

    /** The settings of enemies (how they choose, and the numbers of the attack on a trainer). */
    public BattleSettings withEnemy(Enemy value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, value, knockOut, prizes);
    }

    /** What happens to a trainer at 0 HP. */
    public BattleSettings withKnockOut(KnockOutRules value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, value, prizes);
    }

    /** What a won battle pays, and whether PvP is allowed. */
    public BattleSettings withPrizes(Prizes value) {
        return new BattleSettings(activePerTrainer, speedDivisor, rerollEachRound, trainerActionsFirst, declareHidden, turnTimerEnabled, turnTimerSeconds,
                onTimeout, ppEnabled, catchEnabled, catchAllowTrainerOwned, fleeAllowTrainerBattles, fleeAllowBoss, abilitiesEnabled, heldItemsEnabled, enemy, knockOut, value);
    }

    private static double decimal(Map<String, String> values, String key, double fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number, not " + v);
        }
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
