/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * One thing that happened in a game (chapter 14.5). The game is the list of its events: {@link GameReducer} turns them into a
 * {@link GameState}. An event says what <em>happened</em>, with every number already decided (a roll carries the faces that were rolled,
 * a random choice carries its value), so applying it needs no dice and no clock and always gives the same result.
 *
 * <p>To add an event: add a record here (and to {@code permits}), a line in {@link EventTypes} and a branch in {@link GameReducer}. The
 * compiler stops at the reducer until the branch exists.
 */
public sealed interface GameEvent {

    /** The name that is stored in the log ({@code roll.requested}). */
    String type();

    /** What the event is about, for the audit trail: a trainer id, a request id, or {@code game}. */
    String target();

    /** The game goes to another status. */
    record StatusChanged(GameStatus to) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.STATUS_CHANGED;
        }

        @Override
        public String target() {
            return "game";
        }
    }

    /** The game moves on to the next round ({@code round} must be the current round plus one). */
    record RoundAdvanced(int round) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.ROUND_ADVANCED;
        }

        @Override
        public String target() {
            return "game";
        }
    }

    /** A trainer joins, or an existing trainer is replaced by an edited copy (the admin's powers of chapter 12.4 use this too). */
    record TrainerSet(String trainerId, Trainer trainer) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.TRAINER_SET;
        }

        @Override
        public String target() {
            return trainerId;
        }
    }

    /**
     * A trainer gains or loses HP (chapter 4.6): an enemy's attack, healing, the end of a knock-out. {@code delta} is negative for damage
     * and positive for healing; the HP stays between 0 and the maximum. {@code cause} is the end of the line for the log, or null.
     */
    record TrainerHpChanged(String trainerId, int delta, String cause) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.TRAINER_HP_CHANGED;
        }

        @Override
        public String target() {
            return trainerId;
        }
    }

    /**
     * What happens to a trainer at 0 HP is set, or ended ({@code knockout} null). The rule is in {@link GameState.Knockout}; the desk
     * works out the new state (a death saving throw, a respawn) and this event stores it.
     */
    record KnockoutSet(String trainerId, GameState.Knockout knockout) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.KNOCKOUT_SET;
        }

        @Override
        public String target() {
            return trainerId;
        }
    }

    /**
     * A Pokémon is made, or replaced by an edited copy (chapter 5.1): a wild one that appears, one that is caught, one that grows, learns
     * a move or evolves after a battle, one that is healed. The sheet is given complete.
     */
    record PokemonSet(PokemonSheet pokemon) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.POKEMON_SET;
        }

        @Override
        public String target() {
            return pokemon.id();
        }
    }

    /** A Pokémon leaves the game: a wild one that despawns, or one that is released. Prompts about it go with it. */
    record PokemonRemoved(String pokemonId) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.POKEMON_REMOVED;
        }

        @Override
        public String target() {
            return pokemonId;
        }
    }

    /** Something waits for an answer: a move to learn, an evolution (chapter 7.15, step 3). */
    record PromptOpened(Prompt prompt) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.PROMPT_OPENED;
        }

        @Override
        public String target() {
            return prompt.id();
        }
    }

    /** A prompt was answered and is gone. What the answer did is added by other events in the same action. */
    record PromptAnswered(String promptId, String answer) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.PROMPT_ANSWERED;
        }

        @Override
        public String target() {
            return promptId;
        }
    }

    // ------------------------------------------------------------------------------------ bag, shops and trading (phase 50)

    /** A merchant is set up, or its shelves changed (a purchase, a restock, the admin's edit). */
    record MerchantSet(Merchant merchant) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.MERCHANT_SET;
        }

        @Override
        public String target() {
            return merchant == null ? "merchant" : merchant.id();
        }
    }

    /** A merchant is taken out of the game. */
    record MerchantRemoved(String merchantId) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.MERCHANT_REMOVED;
        }

        @Override
        public String target() {
            return merchantId;
        }
    }

    /** A trade is proposed, or the open trade changes (the other trainer confirmed). */
    record TradeSet(Trade trade) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.TRADE_SET;
        }

        @Override
        public String target() {
            return trade == null ? "trade" : trade.id();
        }
    }

    /** A trade is over: it happened, or it was declined, cancelled or refused. What changed hands is in the other events of the same action. */
    record TradeClosed(String tradeId, String outcome) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.TRADE_CLOSED;
        }

        @Override
        public String target() {
            return tradeId;
        }
    }

    /** A line for the game log that changes nothing: "Ana bought 2 Potions for 400 ₽", "Ana used a Rare Candy on Pikachu". */
    record GameNoted(String trainerId, String text) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.GAME_NOTED;
        }

        @Override
        public String target() {
            return trainerId == null ? "game" : trainerId;
        }
    }

    /** A trainer leaves the game. Their open roll requests are cancelled with them. */
    record TrainerRemoved(String trainerId) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.TRAINER_REMOVED;
        }

        @Override
        public String target() {
            return trainerId;
        }
    }

    /**
     * The game asks for a roll (chapter 6.7). The fields after {@code reason} are described at {@link GameState.RollRequest}; an event
     * written before phase 43 has only the first four and gets the defaults.
     */
    record RollRequested(String requestId, String trainerId, Dice dice, String reason, GameState.Roller roller, String rollerUserId,
                         GameState.RollClass rollClass, List<GameState.Band> bands, Double trueChance, List<RollModifier> modifiers,
                         String explanation, long deadlineMillis) implements GameEvent {

        public RollRequested {
            roller = roller == null ? GameState.Roller.PLAYER : roller;
            rollClass = rollClass == null ? GameState.RollClass.NORMAL : rollClass;
            bands = bands == null ? List.of() : List.copyOf(bands);
            modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
            explanation = explanation == null ? "" : explanation;
        }

        /** A plain request for a player's roll. */
        public RollRequested(String requestId, String trainerId, Dice dice, String reason) {
            this(requestId, trainerId, dice, reason, GameState.Roller.PLAYER, null, GameState.RollClass.NORMAL, List.of(), null, List.of(), "", 0);
        }

        @Override
        public String type() {
            return EventTypes.ROLL_REQUESTED;
        }

        @Override
        public String target() {
            return requestId;
        }
    }

    /**
     * A player typed the result of a real roll. The request now waits for the admin (approve, edit or reject) unless approval is off,
     * in which case {@link RollDesk} goes straight to {@link RollAnswered}. Exactly one of {@code faces} (one value per die) and
     * {@code total} is given.
     */
    record RollSubmitted(String requestId, List<Integer> faces, Integer total, String byUserId, long atMillis) implements GameEvent {
        public RollSubmitted {
            faces = faces == null ? List.of() : List.copyOf(faces);
        }

        @Override
        public String type() {
            return EventTypes.ROLL_SUBMITTED;
        }

        @Override
        public String target() {
            return requestId;
        }
    }

    /** The admin rejects what the player typed; the request is open again and the rejected value stays in its history. */
    record RollRejected(String requestId, String reason) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.ROLL_REJECTED;
        }

        @Override
        public String target() {
            return requestId;
        }
    }

    /**
     * A roll request is answered. {@code faces} has one value per die ({@code total} is null); if only the total was entered,
     * {@code faces} is empty and {@code total} is given. {@code source} is {@code digital}, {@code typed} or {@code admin} (chapter 14.6:
     * every roll is logged with its source).
     *
     * <p>A {@code digital} answer carries its {@code seed}, and its faces must be the ones {@link DigitalRoller} gives for it. A
     * {@code typed} answer to a request that was submitted is an approval: {@code approvedBy} is the admin, and {@code edited} says
     * whether the admin changed the value.
     */
    record RollAnswered(String requestId, List<Integer> faces, String source, Integer total, String approvedBy, boolean edited,
                        Long seed) implements GameEvent {
        public RollAnswered {
            faces = faces == null ? List.of() : List.copyOf(faces);
        }

        /** An answer with every die given and nothing else to say. */
        public RollAnswered(String requestId, List<Integer> faces, String source) {
            this(requestId, faces, source, null, null, false, null);
        }

        @Override
        public String type() {
            return EventTypes.ROLL_ANSWERED;
        }

        @Override
        public String target() {
            return requestId;
        }
    }

    /**
     * The admin changes an answered roll after the fact (chapter 6.7: change the value, swap the die, roll again). {@code dice} is the
     * new dice, or null to keep them. The old answer stays in the roll's history. A new {@code digital} value carries its seed.
     */
    record RollOverridden(String requestId, Dice dice, List<Integer> faces, Integer total, String source, Long seed,
                          String reason) implements GameEvent {
        public RollOverridden {
            faces = faces == null ? List.of() : List.copyOf(faces);
        }

        @Override
        public String type() {
            return EventTypes.ROLL_OVERRIDDEN;
        }

        @Override
        public String target() {
            return requestId;
        }
    }

    /** The time limit of a request ran out (the caller decides when, with its clock). The request is closed without an answer. */
    record RollExpired(String requestId) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.ROLL_EXPIRED;
        }

        @Override
        public String target() {
            return requestId;
        }
    }

    /** A roll request is withdrawn without an answer. */
    record RollCancelled(String requestId) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.ROLL_CANCELLED;
        }

        @Override
        public String target() {
            return requestId;
        }
    }

    /** A random choice was made (by the system, in secret). {@code value} is 0 up to, not including, {@code bound}. */
    record RandomDrawn(String purpose, int bound, int value) implements GameEvent {
        @Override
        public String type() {
            return EventTypes.RANDOM_DRAWN;
        }

        @Override
        public String target() {
            return purpose;
        }
    }

    // ------------------------------------------------------------------------------------------------------------- battles

    /**
     * Something that happens in a battle (chapter 7). The rolls of a battle are asked for, answered and changed with the roll events
     * above; these events carry the rest. {@link BattleReducer} keeps the rules.
     */
    sealed interface BattleEvent extends GameEvent
            permits BattleStarted, RoundOpened, InitiativeSet, ActionDeclared, ResolveStarted, ActionResolved, ReplacementSent,
            CombatantSet, QueueReordered, RoundEnded, BattleEnded, HpChanged, StatusSet, StageChanged, VolatileSet, HeldItemSet,
            BattleNoted, SwitchRequired, AdminTaskOpened, AdminTaskClosed, BallThrown, ParticipantLeft, FleeFailed, BattleSettled {

        /** The battle it is about. */
        String battleId();

        @Override
        default String target() {
            return battleId();
        }
    }

    /**
     * A battle starts (chapter 7.2). The participants and their Pokémon are given complete; the settings are copied into the battle.
     * Only one battle runs at a time: the last one must have ended.
     */
    record BattleStarted(String battleId, Battle.Type battleType, BattleSettings rules, List<Battle.Participant> participants,
                         List<Battle.Combatant> combatants) implements BattleEvent {
        public BattleStarted {
            participants = participants == null ? List.of() : List.copyOf(participants);
            combatants = combatants == null ? List.of() : List.copyOf(combatants);
        }

        @Override
        public String type() {
            return EventTypes.BATTLE_STARTED;
        }
    }

    /**
     * A round begins (steps 1 and 2 of chapter 7.3). {@code slots} are the initiative rolls that were asked for in the same action (one
     * per participant that can still fight); they are empty when initiative is not rolled again ({@code combat.initiative.rerollEachRound}
     * off), and then the round goes straight to declaring and {@code declareDeadlineMillis} is the time limit.
     */
    record RoundOpened(String battleId, int round, List<Battle.InitiativeSlot> slots, long declareDeadlineMillis) implements BattleEvent {
        public RoundOpened {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }

        @Override
        public String type() {
            return EventTypes.ROUND_OPENED;
        }
    }

    /**
     * Everyone has rolled initiative. The order is worked out from the rolls and the Speed; {@code tieBreaks} (a random number per
     * participant, decided by the caller) only decides a tie of both, the coin flip of chapter 7.3. The round goes on to declaring.
     */
    record InitiativeSet(String battleId, int round, Map<String, Integer> tieBreaks, long declareDeadlineMillis) implements BattleEvent {
        public InitiativeSet {
            tieBreaks = tieBreaks == null ? Map.of() : Map.copyOf(tieBreaks);
        }

        @Override
        public String type() {
            return EventTypes.INITIATIVE_SET;
        }
    }

    /** A Pokémon's action for the round is chosen (by its player, by the AI or by the admin). Choosing again replaces the choice. */
    record ActionDeclared(String battleId, int round, String actorId, Battle.Action action) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.ACTION_DECLARED;
        }
    }

    /** Declaring is over and the actions are put in order (switches, items and balls, flee attempts, then moves). */
    record ResolveStarted(String battleId, int round) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.RESOLVE_STARTED;
        }
    }

    /**
     * The next action in the queue has happened. What the action does to HP, status, bags and the catch is added by the later phases as
     * more events in the same action; this event does what the turn order needs: a move spends its PP, a switch swaps the Pokémon, and
     * the action leaves the queue.
     */
    record ActionResolved(String battleId, int round, String actorId, Battle.Result result) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.ACTION_RESOLVED;
        }
    }

    /** A participant sends in a Pokémon from the reserve for one that fainted. It does not use an action (chapter 7.3). */
    record ReplacementSent(String battleId, String participantId, String fainted, String replacement) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.REPLACEMENT_SENT;
        }
    }

    /** A Pokémon is replaced by an edited copy (the admin heals, damages or changes it; the later phases use this too). */
    record CombatantSet(String battleId, Battle.Combatant combatant) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.COMBATANT_SET;
        }
    }

    /** The admin puts the actions that have not happened yet in another order. */
    record QueueReordered(String battleId, List<String> queue) implements BattleEvent {
        public QueueReordered {
            queue = queue == null ? List.of() : List.copyOf(queue);
        }

        @Override
        public String type() {
            return EventTypes.QUEUE_REORDERED;
        }
    }

    /** The round is over (step 5). Every action has resolved. */
    record RoundEnded(String battleId, int round) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.ROUND_ENDED;
        }
    }

    /** The battle is over: one side is left, or the admin ended it. */
    record BattleEnded(String battleId, String reason) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.BATTLE_ENDED;
        }
    }

    // ------------------------------------------------------------------------------------------------ what moves do (phase 45, 46)

    /**
     * A Pokémon gains or loses HP: damage from a move, burn, poison, recoil, a self-hit, or healing. {@code delta} is negative for
     * damage and positive for healing; the HP stays between 0 and the maximum. {@code cause} is the end of the log line, such as
     * {@code from the burn}; null for a plain hit.
     */
    record HpChanged(String battleId, String combatantId, int delta, String cause) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.HP_CHANGED;
        }
    }

    /**
     * A Pokémon gets a major status, or loses it ({@code NONE}). {@code turns} is the number of turns it cannot act for sleep (1 to 3),
     * and 1 for bad poison; 0 otherwise. {@code cause} is the log line, or null for the standard one.
     */
    record StatusSet(String battleId, String combatantId, Battle.Status status, int turns, String cause) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.STATUS_SET;
        }
    }

    /** A stat stage changes by {@code delta} (kept between −6 and +6). {@code stat} is one of {@link Battle#STAGE_IDS}. */
    record StageChanged(String battleId, String combatantId, String stat, int delta, String cause) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.STAGE_CHANGED;
        }
    }

    /**
     * A short-lived condition is set to {@code value}, or removed when {@code value} is 0 (see {@link Battle.Volatiles}). {@code text}
     * is the log line; null for no line.
     */
    record VolatileSet(String battleId, String combatantId, String key, int value, String text) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.VOLATILE_SET;
        }
    }

    /** A Pokémon gets a held item, or loses it ({@code itemId} null): Knock Off, Covet, Natural Gift. */
    record HeldItemSet(String battleId, String combatantId, String itemId, String text) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.HELD_ITEM_SET;
        }
    }

    /** A line for the battle log that changes nothing: a miss, "it's super effective", "but it failed". */
    record BattleNoted(String battleId, String text) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.BATTLE_NOTED;
        }
    }

    /** A Pokémon on the field must be replaced before the battle goes on (after U-turn, Roar…). Its owner chooses, or the system draws. */
    record SwitchRequired(String battleId, String combatantId) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.SWITCH_REQUIRED;
        }
    }

    /** The game does not know what a move does: the admin has to apply it (chapter 7.8). */
    record AdminTaskOpened(String battleId, String taskId, String actorId, String moveId, String text) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.ADMIN_TASK_OPENED;
        }
    }

    /** The admin has applied the effect of a move by hand. */
    record AdminTaskClosed(String battleId, String taskId) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.ADMIN_TASK_CLOSED;
        }
    }

    // ------------------------------------------------------------------------------------ catching, fleeing, leaving (phase 48)

    /**
     * A trainer threw a Poké Ball (chapter 7.10). With {@code caught} the Pokémon leaves the battle for good (it is kept in
     * {@link Battle#caught()}); otherwise {@code shakes} (0 to 2) says how close it was, for the mod's animation. A caught Pokémon always
     * shook 3 times.
     */
    record BallThrown(String battleId, String combatantId, String participantId, String ball, int shakes, boolean caught) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.BALL_THROWN;
        }
    }

    /**
     * A participant leaves the battle and takes its Pokémon along (chapter 7.11, 4.6, 7.14). {@code reason} is {@code fled},
     * {@code knockedOut} (its trainer reached 0 HP) or {@code surrendered}.
     */
    record ParticipantLeft(String battleId, String participantId, String reason) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.PARTICIPANT_LEFT;
        }
    }

    /** The rewards of the battle have been paid (XP, money, evolution prompts, healing): see {@link AfterBattle}. It happens once. */
    record BattleSettled(String battleId) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.BATTLE_SETTLED;
        }
    }

    /** A trainer tried to run away and did not get away: the next try is easier (chapter 7.11). */
    record FleeFailed(String battleId, String participantId) implements BattleEvent {
        @Override
        public String type() {
            return EventTypes.FLEE_FAILED;
        }
    }
}
