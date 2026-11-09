/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.List;
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
}
