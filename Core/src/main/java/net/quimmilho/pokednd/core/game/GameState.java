/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * Everything the rules engine knows about a game at one moment (chapter 14.5). It is immutable and is built only by
 * {@link GameReducer} from events, so the same events always give the same state. The battle is here; later phases add parties and the map.
 *
 * <p>Rolls and random choices that were already made are part of the state ({@link #rolls}, {@link #draws}) and are never made
 * again: that is what lets an undo, a redo and a replay give back exactly the same game.
 *
 * @param status       where the game is in its life
 * @param round        the round the game is in (0 before the first)
 * @param trainers     the trainers by id, in the order they were added
 * @param pendingRolls the roll requests that wait for an answer, by request id, oldest first
 * @param rolls        the answered rolls, in the order they were answered
 * @param draws        the random choices that were made, in order
 * @param battle       the battle that is going on, or the one that has just ended (null if there has been none); only one runs at a time
 */
public record GameState(GameStatus status, int round, Map<String, Trainer> trainers, Map<String, RollRequest> pendingRolls,
                        List<RollResult> rolls, List<RandomDraw> draws, Battle battle) {

    /** Who has to answer a roll request. */
    public enum Roller {
        /** The trainer's player rolls a real die, or taps Roll in the app. */
        PLAYER("player"),
        /** The admin rolls and types the result (also for the rolls of enemies and secret rolls in the {@code adminPhysical} mode). */
        ADMIN("admin"),
        /** The system rolls by itself, in secret or for the enemy. */
        SYSTEM("system");

        private final String id;

        Roller(String id) {
            this.id = id;
        }

        @JsonValue
        public String id() {
            return id;
        }
    }

    /** What kind of roll it is, for the settings {@code dice.enemy.mode} and {@code dice.secret.mode} (chapter 6.7, last paragraph). */
    public enum RollClass {
        /** A roll of the table's drama: attack, damage, skill check, catch. */
        NORMAL("normal"),
        /** A roll for an enemy. */
        ENEMY("enemy"),
        /** A secret roll: encounter, shiny, boss, wild level, loot. */
        SECRET("secret");

        private final String id;

        RollClass(String id) {
            this.id = id;
        }

        @JsonValue
        public String id() {
            return id;
        }
    }

    /** Where an open request is on the way of chapter 6.7: asked ({@code requested}) or typed by the player and waiting for the admin. */
    public enum RollStatus {
        REQUESTED("requested"),
        SUBMITTED("submitted");

        private final String id;

        RollStatus(String id) {
            this.id = id;
        }

        @JsonValue
        public String id() {
            return id;
        }
    }

    /** One line of the band table of a request, such as {@code 1–19 hit}: the faces {@code from} to {@code to} mean {@code label}. */
    public record Band(String label, int from, int to) {
        /** {@code 5–19 hit} or {@code 20 crit}. */
        public String text() {
            return (from == to ? String.valueOf(from) : from + "–" + to) + " " + label;
        }
    }

    /**
     * What a player typed and the admin has not answered yet.
     *
     * @param faces    one value per die; empty when only the total was typed
     * @param total    the total typed instead of the dice (null when every die was typed)
     * @param byUserId who typed it
     * @param atMillis when (milliseconds since 1970, given by the caller: the rules have no clock)
     */
    public record Submission(List<Integer> faces, Integer total, String byUserId, long atMillis) {
        public Submission {
            faces = faces == null ? List.of() : List.copyOf(faces);
        }
    }

    /**
     * An earlier answer that did not stay: a submission the admin rejected or edited, or a result that was replaced later. The roll log
     * shows these next to the final value (chapter 6.6: "the roll log shows both").
     *
     * @param outcome {@code rejected}, {@code edited} or {@code replaced}
     * @param note    why, if anyone said
     */
    public record Attempt(List<Integer> faces, Integer total, String source, String outcome, String note) {
        public Attempt {
            faces = faces == null ? List.of() : List.copyOf(faces);
            note = note == null ? "" : note;
        }
    }

    /**
     * A roll the game asks for and nobody has answered yet (chapter 6.7). Fields after {@code reason} were added in phase 43; a request
     * from an older log has the defaults.
     *
     * @param requestId      the id of the request, never reused in a game
     * @param trainerId      whose roll it is ("Roll for Scratch")
     * @param dice           the dice to roll
     * @param reason         why, in a few words
     * @param roller         who must answer
     * @param rollerUserId   the person who must answer when {@code roller} is {@link Roller#PLAYER} (null if not known)
     * @param rollClass      normal, enemy or secret
     * @param bands          what the faces mean ({@code 1–19 hit · 20 crit}); empty for a plain total
     * @param trueChance     the exact chance of the best outcome that was asked for, 0 to 1 (null if there is none)
     * @param modifiers      the bonuses and penalties already counted in the chance or to be added by the player
     * @param explanation    the text for the screen
     * @param deadlineMillis when the request expires (milliseconds since 1970), 0 for never
     * @param status         asked, or submitted and waiting for the admin
     * @param submission     what the player typed, while {@code status} is submitted
     * @param attempts       submissions that were rejected earlier, oldest first
     */
    public record RollRequest(String requestId, String trainerId, Dice dice, String reason, Roller roller, String rollerUserId,
                              RollClass rollClass, List<Band> bands, Double trueChance, List<RollModifier> modifiers, String explanation,
                              long deadlineMillis, RollStatus status, Submission submission, List<Attempt> attempts) {

        public RollRequest {
            roller = roller == null ? Roller.PLAYER : roller;
            rollClass = rollClass == null ? RollClass.NORMAL : rollClass;
            bands = bands == null ? List.of() : List.copyOf(bands);
            modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
            explanation = explanation == null ? "" : explanation;
            status = status == null ? RollStatus.REQUESTED : status;
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }

        /** A plain request for a player's roll: no bands, no modifiers, no time limit. */
        public RollRequest(String requestId, String trainerId, Dice dice, String reason) {
            this(requestId, trainerId, dice, reason, Roller.PLAYER, null, RollClass.NORMAL, List.of(), null, List.of(), "", 0, RollStatus.REQUESTED, null, List.of());
        }

        /** A copy with another status and submission. */
        public RollRequest withStatus(RollStatus value, Submission sub) {
            return new RollRequest(requestId, trainerId, dice, reason, roller, rollerUserId, rollClass, bands, trueChance, modifiers, explanation,
                    deadlineMillis, value, sub, attempts);
        }

        /** A copy with these earlier attempts. */
        public RollRequest withAttempts(List<Attempt> value) {
            return new RollRequest(requestId, trainerId, dice, reason, roller, rollerUserId, rollClass, bands, trueChance, modifiers, explanation,
                    deadlineMillis, status, submission, value);
        }
    }

    /**
     * An answered roll.
     *
     * @param faces        what each die showed; empty when only the total was entered
     * @param source       who gave it: {@code digital}, {@code typed} (a player's real die) or {@code admin}
     * @param enteredTotal the total, when it was entered instead of the dice (null otherwise)
     * @param bands        what the faces mean, copied from the request
     * @param approvedBy   the admin who approved a typed roll (null if nobody had to)
     * @param edited       true if the admin changed what the player typed
     * @param seed         the seed of a digital roll: the faces are what {@link DigitalRoller} gives for it (null for other sources)
     * @param history      earlier answers that did not stay, oldest first
     */
    public record RollResult(String requestId, String trainerId, Dice dice, List<Integer> faces, String source, Integer enteredTotal,
                             List<Band> bands, String approvedBy, boolean edited, Long seed, List<Attempt> history) {

        public RollResult {
            faces = faces == null ? List.of() : List.copyOf(faces);
            bands = bands == null ? List.of() : List.copyOf(bands);
            history = history == null ? List.of() : List.copyOf(history);
        }

        /** A plain answer: every die typed or rolled, nothing approved, no history. */
        public RollResult(String requestId, String trainerId, Dice dice, List<Integer> faces, String source) {
            this(requestId, trainerId, dice, faces, source, null, List.of(), null, false, null, List.of());
        }

        /** The total of the dice. */
        public int total() {
            return faces.isEmpty() && enteredTotal != null ? enteredTotal : faces.stream().mapToInt(Integer::intValue).sum();
        }

        /** What the total means in the band table of the request ({@code hit}), or null if there are no bands. */
        public String outcome() {
            int t = total();
            return bands.stream().filter(b -> t >= b.from() && t <= b.to()).map(Band::label).findFirst().orElse(null);
        }
    }

    /**
     * A random choice that was made, such as "is there an encounter on this node": {@code value} is 0 up to (not including)
     * {@code bound}.
     */
    public record RandomDraw(String purpose, int bound, int value) {
    }

    public GameState {
        trainers = frozen(trainers);
        pendingRolls = frozen(pendingRolls);
        rolls = List.copyOf(rolls);
        draws = List.copyOf(draws);
    }

    /** A game that has just been made. */
    public static GameState empty() {
        return new GameState(GameStatus.DRAFT, 0, Map.of(), Map.of(), List.of(), List.of(), null);
    }

    private static <V> Map<String, V> frozen(Map<String, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /** A copy with another status. */
    public GameState withStatus(GameStatus value) {
        return new GameState(value, round, trainers, pendingRolls, rolls, draws, battle);
    }

    /** A copy in another round. */
    public GameState withRound(int value) {
        return new GameState(status, value, trainers, pendingRolls, rolls, draws, battle);
    }

    /** A copy with these trainers. */
    public GameState withTrainers(Map<String, Trainer> value) {
        return new GameState(status, round, value, pendingRolls, rolls, draws, battle);
    }

    /** A copy with these pending roll requests. */
    public GameState withPendingRolls(Map<String, RollRequest> value) {
        return new GameState(status, round, trainers, value, rolls, draws, battle);
    }

    /** A copy with these answered rolls. */
    public GameState withRolls(List<RollResult> value) {
        return new GameState(status, round, trainers, pendingRolls, value, draws, battle);
    }

    /** A copy with these random choices. */
    public GameState withDraws(List<RandomDraw> value) {
        return new GameState(status, round, trainers, pendingRolls, rolls, value, battle);
    }

    /** A copy with this battle (null for none). */
    public GameState withBattle(Battle value) {
        return new GameState(status, round, trainers, pendingRolls, rolls, draws, value);
    }
}
