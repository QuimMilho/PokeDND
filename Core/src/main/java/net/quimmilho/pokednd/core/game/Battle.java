/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.Stats;

/**
 * One battle as the game state keeps it (chapter 7): who fights, the Pokémon on the field, the round, what each player declared and
 * the order in which the actions resolve. It is immutable and is changed only by {@link BattleReducer}, so the same events always give
 * the same battle. {@link BattleDesk} turns what people do into the events.
 *
 * <p>A round has five steps (chapter 7.3). They are the {@link Step}s: <b>start of round</b> and <b>initiative</b> ({@code INITIATIVE}:
 * everyone rolls 1d20 plus the Speed modifier), <b>declare</b> ({@code DECLARE}), <b>resolve</b> ({@code RESOLVE}) and <b>end of round</b>
 * ({@code ROUND_END}). Hits, damage and effects are added to the resolve step by later phases.
 *
 * @param battleId            the id of the battle, unique in a game
 * @param type                wild, NPC trainer, PvP or mixed
 * @param rules               the combat settings the battle was started with
 * @param round               the round the battle is in (0 before the first)
 * @param step                where the round is
 * @param participants        the trainers and wild Pokémon that fight, by id, in the order they were added
 * @param combatants          every Pokémon of the battle, active or in reserve, by id
 * @param slots               the initiative rolls of this round that were asked for (empty when initiative is not rolled again)
 * @param order               the initiative order of the battle, first to act first (kept from round to round when it is not rolled again)
 * @param declareDeadlineMillis when the players must have declared (milliseconds since 1970), 0 for no time limit
 * @param declared            what each active Pokémon will do this round, by the id of the Pokémon
 * @param queue               the actions that still have to resolve, by the id of the acting Pokémon, next first
 * @param resolved            the Pokémon whose action has resolved this round, in order
 * @param log                 the battle log in plain language, oldest first
 * @param tasks               what the admin still has to apply by hand (moves the game does not know), oldest first
 * @param mustSwitch          the Pokémon on the field that have to be replaced before the battle goes on (after U-turn, or a forced switch)
 * @param replacedBy          the Pokémon that left the field this round (not fainted) and the one that took its place: a move aimed
 *                            at the first one hits the second (cleared when the round ends)
 */
public record Battle(String battleId, Type type, BattleSettings rules, int round, Step step, Map<String, Participant> participants,
                     Map<String, Combatant> combatants, List<InitiativeSlot> slots, List<InitiativeEntry> order,
                     long declareDeadlineMillis, Map<String, Action> declared, List<String> queue, List<String> resolved,
                     List<String> log, List<AdminTask> tasks, List<String> mustSwitch, Map<String, String> replacedBy) {

    /** The kinds of battle of chapter 7.1. */
    public enum Type {
        WILD, NPC_TRAINER, PVP, MIXED
    }

    /** Where a round is. */
    public enum Step {
        /** The battle has started and no round has begun. */
        START,
        /** The round has begun and the initiative rolls are asked for. */
        INITIATIVE,
        /** Everybody chooses an action. */
        DECLARE,
        /** The actions happen one by one. */
        RESOLVE,
        /** The round is over and the next one has not begun. */
        ROUND_END,
        /** The battle is over. */
        ENDED
    }

    /** What a participant is. */
    public enum Kind {
        /** A trainer of the game, played by a person. */
        TRAINER,
        /** A trainer run by the admin (an NPC). */
        NPC,
        /** A wild Pokémon, alone or as a boss. */
        WILD
    }

    /**
     * One side of the field that acts: a trainer, an NPC, or a lone wild Pokémon. The ids of the Pokémon say which are on the field
     * ({@code active}, the first ones act first) and which wait ({@code reserve}).
     *
     * @param id        the id in this battle
     * @param kind      trainer, NPC or wild
     * @param side      which side it fights on: participants with the same number are friends
     * @param name      the name shown in the log
     * @param trainerId the trainer in the game state (null for an NPC or a wild Pokémon)
     * @param userId    the person who plays it (null when nobody does: the admin or the AI chooses)
     */
    public record Participant(String id, Kind kind, int side, String name, String trainerId, String userId, List<String> active,
                              List<String> reserve) {
        public Participant {
            active = List.copyOf(active);
            reserve = List.copyOf(reserve);
        }

        /** A copy with other Pokémon on the field and in reserve. */
        public Participant withTeam(List<String> newActive, List<String> newReserve) {
            return new Participant(id, kind, side, name, trainerId, userId, newActive, newReserve);
        }
    }

    /** The major status conditions of chapter 5.6. A Pokémon has at most one. */
    public enum Status {
        NONE("none"), BURN("brn"), POISON("psn"), TOXIC("tox"), PARALYSIS("par"), SLEEP("slp"), FREEZE("frz");

        private final String id;

        Status(String id) {
            this.id = id;
        }

        /** The short id the move data uses ({@code brn}, {@code psn}, {@code tox}, {@code par}, {@code slp}, {@code frz}). */
        public String id() {
            return id;
        }

        /** Reads a short id of the move data; null if it is not one of the six. */
        public static Status fromId(String text) {
            for (Status s : values()) {
                if (s != NONE && s.id.equals(text)) {
                    return s;
                }
            }
            return null;
        }

        /** The word used in the battle log: {@code burned}, {@code poisoned}… */
        public String word() {
            return switch (this) {
                case NONE -> "healthy";
                case BURN -> "burned";
                case POISON -> "poisoned";
                case TOXIC -> "badly poisoned";
                case PARALYSIS -> "paralyzed";
                case SLEEP -> "asleep";
                case FREEZE -> "frozen";
            };
        }
    }

    /** The ids of the stat stages (the keys of {@link Condition#stages()}), as the move data writes them in {@code boosts}. */
    public static final List<String> STAGE_IDS = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");

    /**
     * What a Pokémon is made of for this battle and does not change in it (except through the admin): its types, the five stats before
     * stages, and the few facts that some moves read. A Pokémon made without these (the first tests of phase 44) has no types and
     * stats of 0, which is fine for turn order but makes no damage.
     *
     * @param types       one or two types; empty if not known
     * @param attack      Attack before stages
     * @param defense     Defense before stages
     * @param spAttack    Sp. Atk before stages
     * @param spDefense   Sp. Def before stages
     * @param speed       Speed before stages and paralysis (0 means: the {@code speed} of the Pokémon is already the final one)
     * @param gender      {@code M}, {@code F} or {@code N} (none)
     * @param friendship  0 to 255, read by Return and Frustration
     * @param hiddenPower the type of Hidden Power, worked out from the IVs (null: Normal)
     */
    public record Build(List<PokemonType> types, int attack, int defense, int spAttack, int spDefense, int speed, String gender,
                        int friendship, PokemonType hiddenPower) {
        public Build {
            types = types == null ? List.of() : List.copyOf(types);
            gender = gender == null ? "N" : gender;
        }

        /** Nothing is known: no types, stats of 0. */
        public static Build unknown(int speed) {
            return new Build(List.of(), 0, 0, 0, 0, speed, "N", 70, null);
        }

        /** Types and the five stats; gender none, friendship 70. */
        public static Build of(List<PokemonType> types, int attack, int defense, int spAttack, int spDefense, int speed) {
            return new Build(types, attack, defense, spAttack, spDefense, speed, "N", 70, null);
        }

        public Build withGender(String value) {
            return new Build(types, attack, defense, spAttack, spDefense, speed, value, friendship, hiddenPower);
        }

        public Build withFriendship(int value) {
            return new Build(types, attack, defense, spAttack, spDefense, speed, gender, value, hiddenPower);
        }

        public Build withHiddenPower(PokemonType value) {
            return new Build(types, attack, defense, spAttack, spDefense, speed, gender, friendship, value);
        }

        public Build withTypes(List<PokemonType> value) {
            return new Build(value, attack, defense, spAttack, spDefense, speed, gender, friendship, hiddenPower);
        }
    }

    /**
     * What is going on with a Pokémon right now: its status, its stat stages and the short-lived conditions ("volatiles") that moves
     * set. Everything here ends when the Pokémon leaves the field or faints, except the status (and the held item).
     *
     * @param status      the major status
     * @param statusTurns sleep: turns the Pokémon still cannot act; else 0
     * @param stages      stage by id (see {@link #STAGE_IDS}); a missing id is 0
     * @param volatiles   condition by key, with a number: confusion = attempts left, {@code flinch} = 1, {@code protect} = 1,
     *                    {@code substitute} = the decoy's HP, … (see {@link Volatiles})
     * @param heldItem    the id of the held item, or null
     */
    public record Condition(Status status, int statusTurns, Map<String, Integer> stages, Map<String, Integer> volatiles, String heldItem) {
        public static final Condition NONE = new Condition(Status.NONE, 0, Map.of(), Map.of(), null);

        public Condition {
            status = status == null ? Status.NONE : status;
            stages = frozen(stages);
            volatiles = frozen(volatiles);
        }

        /** The stage of a stat, −6 to +6. */
        public int stage(String id) {
            return stages.getOrDefault(id, 0);
        }

        /** The value of a volatile, 0 if it is not set. */
        public int volatileValue(String key) {
            return volatiles.getOrDefault(key, 0);
        }

        /** True if the volatile is set. */
        public boolean has(String key) {
            return volatiles.getOrDefault(key, 0) > 0;
        }

        public Condition withStatus(Status value, int turns) {
            return new Condition(value, turns, stages, volatiles, heldItem);
        }

        public Condition withStage(String id, int value) {
            Map<String, Integer> copy = new LinkedHashMap<>(stages);
            if (value == 0) {
                copy.remove(id);
            } else {
                copy.put(id, value);
            }
            return new Condition(status, statusTurns, copy, volatiles, heldItem);
        }

        /** A value of 0 or less removes the volatile. */
        public Condition withVolatile(String key, int value) {
            Map<String, Integer> copy = new LinkedHashMap<>(volatiles);
            if (value <= 0) {
                copy.remove(key);
            } else {
                copy.put(key, value);
            }
            return new Condition(status, statusTurns, stages, copy, heldItem);
        }

        public Condition withHeldItem(String value) {
            return new Condition(status, statusTurns, stages, volatiles, value);
        }

        /** What stays when the Pokémon leaves the field: the status (and the sleep counter) and the held item; bad poison starts again at 1. */
        public Condition afterSwitchOut() {
            return new Condition(status, statusTurns, Map.of(), Map.of(), heldItem);
        }

        /** What stays when the Pokémon faints: only the held item. */
        public Condition afterFainting() {
            return new Condition(Status.NONE, 0, Map.of(), Map.of(), heldItem);
        }
    }

    /**
     * A Pokémon in the battle. Its stats are the ones it fights with now: {@code speed} already counts stat stages and paralysis
     * (chapter 7.3), and the reducer keeps it up to date from {@link Build#speed()}.
     *
     * @param id        the id in this battle
     * @param ownerId   the participant it belongs to
     * @param boss      true for a giant boss Pokémon (it cannot be caught)
     * @param build     types, stats and facts that do not change in the battle
     * @param condition status, stages, volatiles and held item
     */
    public record Combatant(String id, String ownerId, String name, int level, int hp, int maxHp, int speed, boolean boss,
                            List<MoveSlot> moves, Build build, Condition condition) {
        public Combatant {
            moves = List.copyOf(moves);
            build = build == null ? Build.unknown(speed) : build;
            condition = condition == null ? Condition.NONE : condition;
        }

        /** A Pokémon with nothing but a speed and moves (turn order only, no damage): the shape of phase 44. */
        public Combatant(String id, String ownerId, String name, int level, int hp, int maxHp, int speed, boolean boss,
                         List<MoveSlot> moves) {
            this(id, ownerId, name, level, hp, maxHp, speed, boss, moves, null, null);
        }

        /** True at 0 HP. */
        public boolean fainted() {
            return hp <= 0;
        }

        /** The types it has; empty if not known. */
        public List<PokemonType> types() {
            return build.types();
        }

        /** The major status. */
        public Status status() {
            return condition.status();
        }

        /** A copy with other HP. */
        public Combatant withHp(int value) {
            return new Combatant(id, ownerId, name, level, value, maxHp, speed, boss, moves, build, condition);
        }

        /** A copy with another build (types, stats, gender, friendship…). */
        public Combatant withBuild(Build value) {
            return new Combatant(id, ownerId, name, level, hp, maxHp, speed, boss, moves, value, condition);
        }

        /** A copy with other moves. */
        public Combatant withMoves(List<MoveSlot> value) {
            return new Combatant(id, ownerId, name, level, hp, maxHp, speed, boss, value, build, condition);
        }

        /** A copy with another Speed. */
        public Combatant withSpeed(int value) {
            return new Combatant(id, ownerId, name, level, hp, maxHp, value, boss, moves, build, condition);
        }

        /** A copy with another condition; the Speed is worked out again from the Speed stage and paralysis. */
        public Combatant withCondition(Condition value) {
            Combatant c = new Combatant(id, ownerId, name, level, hp, maxHp, speed, boss, moves, build, value);
            if (build.speed() <= 0) {
                return c;
            }
            int s = Stats.applyStage(build.speed(), value.stage("spe"));
            if (value.status() == Status.PARALYSIS) {
                s = s / 2;
            }
            return c.withSpeed(s);
        }

        /** True when no move has PP left, so the Pokémon can only use Struggle. */
        public boolean outOfPp() {
            return moves.stream().noneMatch(m -> m.pp() > 0);
        }
    }

    /**
     * Something the admin has to apply by hand (chapter 7.8, 19.6): the part of a move that the game does not know. It stays on the
     * list until the admin closes it.
     *
     * @param id      the id of the task
     * @param actorId the Pokémon that used the move
     * @param moveId  the move
     * @param text    what to do, in plain language (the move's own text)
     */
    public record AdminTask(String id, String actorId, String moveId, String text) {
    }

    /** The keys of the volatile conditions the rules know. */
    public static final class Volatiles {
        public static final String CONFUSION = "confusion";
        public static final String FLINCH = "flinch";
        public static final String PROTECT = "protect";
        public static final String ENDURE = "endure";
        /** How many turns in a row a Pokémon has used Protect, Endure and the like (each use is less likely to work). */
        public static final String STALL = "stall";
        public static final String SUBSTITUTE = "substitute";
        public static final String RECHARGE = "mustrecharge";
        public static final String HELPING_HAND = "helpinghand";
        public static final String ATTRACT = "attract";
        public static final String CURSE = "curse";
        /** Bad poison: how many turns it has lasted (the damage is n/16; missing means 1). Ends with the switch-out, like in the games. */
        public static final String TOXIC = "toxic";
        public static final String UPROAR = "uproar";
        /** Rollout: turns left in the lock. */
        public static final String ROLLOUT = "rollout";
        /** How many times Rollout has hit in this lock (the power doubles each time). */
        public static final String ROLLOUT_HITS = "rollouthits";
        /** 1 if the Pokémon's last move failed or missed (Stomping Tantrum). */
        public static final String LAST_FAILED = "lastfailed";
        /** {@code used.<moveId>}: the move was used this round (Round). */
        public static final String USED = "used.";

        private Volatiles() {
        }
    }


    /**
     * A move a Pokémon knows.
     *
     * @param priority the priority bracket, +5 to −7 (0 for most moves)
     * @param target   the {@code target} of the move as written in {@code attack.csv} ({@code normal}, {@code allAdjacentFoes}, {@code self}…)
     */
    public record MoveSlot(String moveId, String name, int pp, int maxPp, int priority, String target) {

        /** The id of Struggle, the move of a Pokémon that has no PP left anywhere. */
        public static final String STRUGGLE = "struggle";

        /** Struggle: priority 0, one foe, no PP. */
        public static MoveSlot struggle() {
            return new MoveSlot(STRUGGLE, "Struggle", 1, 1, 0, "normal");
        }

        /** Who the move can be aimed at. */
        public TargetKind targetKind() {
            return TargetKind.parse(target);
        }

        /** A copy with one PP less (never below 0). */
        public MoveSlot spend() {
            return new MoveSlot(moveId, name, Math.max(0, pp - 1), maxPp, priority, target);
        }
    }

    /** How a move is aimed (the {@code target} column, chapter 7.4). */
    public enum TargetKind {
        /** The user. */
        SELF,
        /** One foe, chosen by the player ({@code normal}, {@code adjacentFoe}, {@code any}, {@code randomNormal}, {@code scripted}). */
        FOE,
        /** One ally ({@code adjacentAlly}). */
        ALLY,
        /** One ally or the user ({@code adjacentAllyOrSelf}). */
        ALLY_OR_SELF,
        /** Every foe ({@code allAdjacentFoes}). */
        ALL_FOES,
        /** Every other Pokémon on the field ({@code allAdjacent}). */
        ALL_OTHERS,
        /** Every Pokémon on the field, the user too ({@code all}). */
        EVERYONE,
        /** Every ally ({@code allies}). */
        ALL_ALLIES,
        /** A whole side or team ({@code allySide}, {@code foeSide}, {@code allyTeam}): no single Pokémon is chosen. */
        SIDE;

        /** Reads the {@code target} column; an unknown word is treated as one foe. */
        public static TargetKind parse(String text) {
            String t = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
            return switch (t) {
                case "self" -> SELF;
                case "adjacentally" -> ALLY;
                case "adjacentallyorself" -> ALLY_OR_SELF;
                case "alladjacentfoes" -> ALL_FOES;
                case "alladjacent" -> ALL_OTHERS;
                case "all" -> EVERYONE;
                case "allies" -> ALL_ALLIES;
                case "allyside", "foeside", "allyteam" -> SIDE;
                default -> FOE;
            };
        }

        /** True when the player picks one Pokémon (so the action carries a target). */
        public boolean choosesOne() {
            return this == FOE || this == ALLY || this == ALLY_OR_SELF;
        }
    }

    /** The six actions of chapter 7.4. */
    public enum ActionKind {
        MOVE, SWITCH, ITEM, BALL, FLEE, SKIP
    }

    /**
     * What an active Pokémon does this round.
     *
     * @param kind     one of the six actions
     * @param moveId   the move (a {@link MoveSlot#STRUGGLE} when no move has PP left); null for the other actions
     * @param targets  the Pokémon aimed at: one for a move that chooses, the Pokémon an item is used on, the wild Pokémon a ball is thrown at
     * @param switchTo the Pokémon from the reserve that comes in
     * @param itemId   the bag item (a potion) or the Poké Ball
     */
    public record Action(ActionKind kind, String moveId, List<String> targets, String switchTo, String itemId) {
        public Action {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }

        public static Action move(String moveId, String... targets) {
            return new Action(ActionKind.MOVE, moveId, List.of(targets), null, null);
        }

        public static Action struggle(String... targets) {
            return move(MoveSlot.STRUGGLE, targets);
        }

        public static Action switchTo(String combatantId) {
            return new Action(ActionKind.SWITCH, null, List.of(), combatantId, null);
        }

        public static Action item(String itemId, String targetCombatantId) {
            return new Action(ActionKind.ITEM, null, List.of(targetCombatantId), null, itemId);
        }

        public static Action ball(String ballId, String wildCombatantId) {
            return new Action(ActionKind.BALL, null, List.of(wildCombatantId), null, ballId);
        }

        public static Action flee() {
            return new Action(ActionKind.FLEE, null, List.of(), null, null);
        }

        public static Action skip() {
            return new Action(ActionKind.SKIP, null, List.of(), null, null);
        }
    }

    /**
     * The initiative roll that was asked for.
     *
     * @param modifier {@code floor(Speed / divisor)} plus any class bonus: added to the die
     * @param speed    the Speed the modifier came from (it breaks ties)
     */
    public record InitiativeSlot(String participantId, String requestId, int modifier, int speed) {
    }

    /**
     * The initiative of one participant once everyone has rolled.
     *
     * @param roll     what the dice showed
     * @param total    {@code roll + modifier}
     * @param tieBreak a random number, used only when the total and the Speed are the same (the coin flip of chapter 7.3)
     */
    public record InitiativeEntry(String participantId, int roll, int modifier, int total, int speed, int tieBreak) {
    }

    /** How an action ended. */
    public enum Result {
        /** It happened. */
        DONE,
        /** The Pokémon it was aimed at had fainted: it fails. */
        FAILED_NO_TARGET,
        /** The acting Pokémon fainted before its turn. */
        CANCELLED_FAINTED,
        /** The admin took the action out of the round. */
        CANCELLED_BY_ADMIN,
        /** The Pokémon left the field (it was sent back or switched) before its turn. */
        CANCELLED_SWITCHED,
        /** The Pokémon could not act: asleep, frozen, paralyzed, confused, in love, flinched or recharging. No PP is spent. */
        PREVENTED
    }

    public Battle {
        participants = frozen(participants);
        combatants = frozen(combatants);
        slots = List.copyOf(slots);
        order = List.copyOf(order);
        declared = frozen(declared);
        queue = List.copyOf(queue);
        resolved = List.copyOf(resolved);
        log = List.copyOf(log);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        mustSwitch = mustSwitch == null ? List.of() : List.copyOf(mustSwitch);
        replacedBy = replacedBy == null ? Map.of() : frozen(replacedBy);
    }

    /** A battle as phase 44 made it: no admin tasks, nobody has to switch. */
    public Battle(String battleId, Type type, BattleSettings rules, int round, Step step, Map<String, Participant> participants,
                  Map<String, Combatant> combatants, List<InitiativeSlot> slots, List<InitiativeEntry> order,
                  long declareDeadlineMillis, Map<String, Action> declared, List<String> queue, List<String> resolved, List<String> log) {
        this(battleId, type, rules, round, step, participants, combatants, slots, order, declareDeadlineMillis, declared, queue, resolved,
                log, List.of(), List.of(), Map.of());
    }

    private static <V> Map<String, V> frozen(Map<String, V> map) {
        if (map == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    // ------------------------------------------------------------------------------------------------------------- changing

    /** A copy with these fields changed; the others stay. */
    Battle with(int newRound, Step newStep, List<InitiativeSlot> newSlots, List<InitiativeEntry> newOrder, long deadline,
                Map<String, Action> newDeclared, List<String> newQueue, List<String> newResolved) {
        return new Battle(battleId, type, rules, newRound, newStep, participants, combatants, newSlots, newOrder, deadline, newDeclared,
                newQueue, newResolved, log, tasks, mustSwitch, replacedBy);
    }

    Battle withTeams(Map<String, Participant> newParticipants, Map<String, Combatant> newCombatants) {
        return new Battle(battleId, type, rules, round, step, newParticipants, newCombatants, slots, order, declareDeadlineMillis, declared,
                queue, resolved, log, tasks, mustSwitch, replacedBy);
    }

    Battle withQueue(List<String> newQueue, List<String> newResolved) {
        return new Battle(battleId, type, rules, round, step, participants, combatants, slots, order, declareDeadlineMillis, declared,
                newQueue, newResolved, log, tasks, mustSwitch, replacedBy);
    }

    Battle withDeclared(Map<String, Action> newDeclared) {
        return new Battle(battleId, type, rules, round, step, participants, combatants, slots, order, declareDeadlineMillis, newDeclared,
                queue, resolved, log, tasks, mustSwitch, replacedBy);
    }

    Battle withStep(Step newStep) {
        return new Battle(battleId, type, rules, round, newStep, participants, combatants, slots, order, declareDeadlineMillis, declared,
                queue, resolved, log, tasks, mustSwitch, replacedBy);
    }

    Battle withReplaced(Map<String, String> newReplacedBy) {
        return new Battle(battleId, type, rules, round, step, participants, combatants, slots, order, declareDeadlineMillis, declared,
                queue, resolved, log, tasks, mustSwitch, newReplacedBy);
    }

    Battle withTasks(List<AdminTask> newTasks) {
        return new Battle(battleId, type, rules, round, step, participants, combatants, slots, order, declareDeadlineMillis, declared,
                queue, resolved, log, newTasks, mustSwitch, replacedBy);
    }

    Battle withMustSwitch(List<String> newMustSwitch) {
        return new Battle(battleId, type, rules, round, step, participants, combatants, slots, order, declareDeadlineMillis, declared,
                queue, resolved, log, tasks, newMustSwitch, replacedBy);
    }

    /** A copy with these lines added to the end of the log. */
    Battle logged(String... lines) {
        List<String> all = new ArrayList<>(log);
        all.addAll(List.of(lines));
        return new Battle(battleId, type, rules, round, step, participants, combatants, slots, order, declareDeadlineMillis, declared,
                queue, resolved, all, tasks, mustSwitch, replacedBy);
    }

    // -------------------------------------------------------------------------------------------------------------- reading

    /**
     * The Pokémon a move aimed at {@code id} reaches now: the same one, or the one that took its place if it left the field this round
     * (switched out, or sent back by U-turn or Roar). A Pokémon that fainted is not replaced in this way: a move aimed at it fails.
     */
    public String resolveTarget(String id) {
        String now = id;
        for (int i = 0; i < 6; i++) {
            Combatant c = combatants.get(now);
            if (c != null && isActive(c)) {
                return now;
            }
            String next = replacedBy.get(now);
            if (next == null) {
                return now;
            }
            now = next;
        }
        return now;
    }

    /** The Pokémon with this id, or null. */
    public Combatant combatant(String id) {
        return combatants.get(id);
    }

    /** The participant a Pokémon belongs to. */
    public Participant ownerOf(Combatant combatant) {
        return participants.get(combatant.ownerId());
    }

    /** The side a Pokémon fights on. */
    public int sideOf(Combatant combatant) {
        return ownerOf(combatant).side();
    }

    /** The Pokémon of a participant that are on the field, fainted or not, in slot order. */
    public List<Combatant> activeOf(Participant participant) {
        return participant.active().stream().map(combatants::get).toList();
    }

    /** True if the Pokémon is on the field now. */
    public boolean isActive(Combatant combatant) {
        return ownerOf(combatant).active().contains(combatant.id());
    }

    /** True when every Pokémon of the participant has fainted: it can no longer take part. */
    public boolean out(Participant participant) {
        return java.util.stream.Stream.concat(participant.active().stream(), participant.reserve().stream())
                .allMatch(id -> combatants.get(id).fainted());
    }

    /** The Pokémon on the field that have not fainted, in participant and slot order. */
    public List<Combatant> standing() {
        List<Combatant> list = new ArrayList<>();
        for (Participant p : participants.values()) {
            for (String id : p.active()) {
                Combatant c = combatants.get(id);
                if (!c.fainted()) {
                    list.add(c);
                }
            }
        }
        return list;
    }

    /** The Pokémon that must declare an action this round: on the field, not fainted, and not of a participant that is out. */
    public List<Combatant> actors() {
        return standing();
    }

    /** The sides that still have a Pokémon that can fight. */
    public Set<Integer> sidesStanding() {
        Set<Integer> sides = new LinkedHashSet<>();
        for (Participant p : participants.values()) {
            if (!out(p)) {
                sides.add(p.side());
            }
        }
        return sides;
    }

    /** True when at most one side can still fight. */
    public boolean over() {
        return sidesStanding().size() <= 1;
    }

    /**
     * The Pokémon a move of this kind can be aimed at, for the player to choose from ("the app only offers legal targets"): Pokémon on the
     * field that have not fainted. Empty for a move that does not choose (a group, a side, the user).
     */
    public List<String> legalTargets(Combatant actor, TargetKind kind) {
        int side = sideOf(actor);
        return standing().stream().filter(c -> switch (kind) {
            case FOE -> sideOf(c) != side;
            case ALLY -> sideOf(c) == side && !c.id().equals(actor.id());
            case ALLY_OR_SELF -> sideOf(c) == side;
            default -> false;
        }).map(Combatant::id).toList();
    }

    /** The Pokémon a move that hits a group reaches now (empty for a move that chooses, for the user alone and for a side). */
    public List<String> groupTargets(Combatant actor, TargetKind kind) {
        int side = sideOf(actor);
        return standing().stream().filter(c -> switch (kind) {
            case ALL_FOES -> sideOf(c) != side;
            case ALL_OTHERS -> !c.id().equals(actor.id());
            case EVERYONE -> true;
            case ALL_ALLIES -> sideOf(c) == side && !c.id().equals(actor.id());
            default -> false;
        }).map(Combatant::id).toList();
    }

    /** The place of a participant in the initiative order (0 acts first), or a large number if it did not roll. */
    public int initiativeIndex(String participantId) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).participantId().equals(participantId)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** How a Pokémon is called in the log: {@code Ana's Charmander}, {@code wild Rattata}. */
    public String label(Combatant combatant) {
        Participant owner = ownerOf(combatant);
        return owner.kind() == Kind.WILD ? "wild " + combatant.name() : owner.name() + "'s " + combatant.name();
    }
}
