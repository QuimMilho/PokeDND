/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.game.GameState.Attempt;
import net.quimmilho.pokednd.core.game.GameState.Band;
import net.quimmilho.pokednd.core.game.GameState.RandomDraw;
import net.quimmilho.pokednd.core.game.GameState.RollRequest;
import net.quimmilho.pokednd.core.game.GameState.RollResult;
import net.quimmilho.pokednd.core.game.GameState.RollStatus;
import net.quimmilho.pokednd.core.game.GameState.Roller;
import net.quimmilho.pokednd.core.game.GameState.Submission;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * {@code (state, event) → new state} (chapter 14.5). A pure function: no clock, no randomness, no input or output, so the same
 * events in the same order always give the same state. An event that cannot happen is refused with {@link InvalidEventException}
 * and the state is not changed.
 */
public final class GameReducer {

    private static final Set<String> ROLL_SOURCES = Set.of("digital", "typed", "admin");

    private GameReducer() {
    }

    /** The state after one event. */
    public static GameState apply(GameState state, GameEvent event) {
        return switch (event) {
            case GameEvent.StatusChanged e -> {
                if (state.status() == GameStatus.ENDED) {
                    throw new InvalidEventException("The game has ended and cannot change status");
                }
                yield state.withStatus(e.to());
            }
            case GameEvent.RoundAdvanced e -> {
                if (e.round() != state.round() + 1) {
                    throw new InvalidEventException("The next round is " + (state.round() + 1) + ", not " + e.round());
                }
                yield state.withRound(e.round());
            }
            case GameEvent.TrainerSet e -> {
                requireText(e.trainerId(), "trainer id");
                if (e.trainer() == null) {
                    throw new InvalidEventException("A trainer is missing");
                }
                Map<String, Trainer> trainers = new LinkedHashMap<>(state.trainers());
                trainers.put(e.trainerId(), e.trainer());
                yield state.withTrainers(trainers);
            }
            case GameEvent.TrainerRemoved e -> {
                if (!state.trainers().containsKey(e.trainerId())) {
                    throw new InvalidEventException("There is no trainer " + e.trainerId());
                }
                Map<String, Trainer> trainers = new LinkedHashMap<>(state.trainers());
                trainers.remove(e.trainerId());
                Map<String, RollRequest> pending = new LinkedHashMap<>(state.pendingRolls());
                pending.values().removeIf(r -> r.trainerId().equals(e.trainerId()));
                yield state.withTrainers(trainers).withPendingRolls(pending);
            }
            case GameEvent.RollRequested e -> {
                requireText(e.requestId(), "request id");
                if (e.dice() == null) {
                    throw new InvalidEventException("A roll request needs dice");
                }
                if (!state.trainers().containsKey(e.trainerId())) {
                    throw new InvalidEventException("There is no trainer " + e.trainerId());
                }
                if (state.pendingRolls().containsKey(e.requestId()) || state.rolls().stream().anyMatch(r -> r.requestId().equals(e.requestId()))) {
                    throw new InvalidEventException("The roll request " + e.requestId() + " already exists");
                }
                if (e.deadlineMillis() < 0) {
                    throw new InvalidEventException("The time limit of a roll request cannot be negative");
                }
                if (e.trueChance() != null && (e.trueChance() < 0 || e.trueChance() > 1)) {
                    throw new InvalidEventException("A chance is 0 to 1, not " + e.trueChance());
                }
                for (Band band : e.bands()) {
                    if (band.from() < 1 || band.to() < band.from() || band.to() > e.dice().max()) {
                        throw new InvalidEventException("The band \"" + band.label() + "\" (" + band.from() + " to " + band.to() + ") does not fit " + e.dice());
                    }
                }
                Map<String, RollRequest> pending = new LinkedHashMap<>(state.pendingRolls());
                pending.put(e.requestId(), new RollRequest(e.requestId(), e.trainerId(), e.dice(), e.reason(), e.roller(), e.rollerUserId(), e.rollClass(),
                        e.bands(), e.trueChance(), e.modifiers(), e.explanation(), e.deadlineMillis(), RollStatus.REQUESTED, null, List.of()));
                yield state.withPendingRolls(pending);
            }
            case GameEvent.RollSubmitted e -> {
                RollRequest request = openRequest(state, e.requestId());
                if (request.roller() != Roller.PLAYER) {
                    throw new InvalidEventException("Only a roll that a player must make can be typed in by a player");
                }
                if (request.status() == RollStatus.SUBMITTED) {
                    throw new InvalidEventException("The roll " + e.requestId() + " already has a value that waits for the admin");
                }
                requireText(e.byUserId(), "person who typed the roll");
                checkValue(request.dice(), e.faces(), e.total(), true);
                yield replaceRequest(state, request.withStatus(RollStatus.SUBMITTED, new Submission(e.faces(), e.total(), e.byUserId(), e.atMillis())));
            }
            case GameEvent.RollRejected e -> {
                RollRequest request = openRequest(state, e.requestId());
                if (request.status() != RollStatus.SUBMITTED) {
                    throw new InvalidEventException("The roll " + e.requestId() + " has no typed value to reject");
                }
                Submission typed = request.submission();
                List<Attempt> attempts = new ArrayList<>(request.attempts());
                attempts.add(new Attempt(typed.faces(), typed.total(), "typed", "rejected", e.reason()));
                yield replaceRequest(state, request.withStatus(RollStatus.REQUESTED, null).withAttempts(attempts));
            }
            case GameEvent.RollAnswered e -> {
                RollRequest request = openRequest(state, e.requestId());
                if (!ROLL_SOURCES.contains(e.source())) {
                    throw new InvalidEventException("A roll comes from digital, typed or admin, not \"" + e.source() + "\"");
                }
                boolean digital = "digital".equals(e.source());
                checkValue(request.dice(), e.faces(), e.total(), !digital);
                checkSeed(request.dice(), e.faces(), e.source(), e.seed());
                List<Attempt> attempts = new ArrayList<>(request.attempts());
                Submission typed = request.submission();
                if ("typed".equals(e.source())) {
                    if (request.roller() != Roller.PLAYER) {
                        throw new InvalidEventException("Only a roll that a player must make can be typed in by a player");
                    }
                    if (typed != null) {
                        requireText(e.approvedBy(), "admin who approved the roll");
                        boolean same = typed.faces().equals(e.faces()) && Objects.equals(typed.total(), e.total());
                        if (e.edited() == same) {
                            throw new InvalidEventException(same ? "The value was not changed, so it was not edited" : "The value was changed, so it was edited");
                        }
                        if (e.edited()) {
                            attempts.add(new Attempt(typed.faces(), typed.total(), "typed", "edited", ""));
                        }
                    } else if (e.edited()) {
                        throw new InvalidEventException("There is no typed value to edit");
                    }
                } else {
                    if (e.edited() || (digital && e.approvedBy() != null)) {
                        throw new InvalidEventException("Only a value that a player typed is approved or edited");
                    }
                    if (typed != null) {
                        attempts.add(new Attempt(typed.faces(), typed.total(), "typed", "replaced", ""));
                    }
                }
                Map<String, RollRequest> pending = new LinkedHashMap<>(state.pendingRolls());
                pending.remove(e.requestId());
                List<RollResult> rolls = new ArrayList<>(state.rolls());
                rolls.add(new RollResult(request.requestId(), request.trainerId(), request.dice(), e.faces(), e.source(), e.total(), request.bands(),
                        e.approvedBy(), e.edited(), e.seed(), attempts));
                yield state.withPendingRolls(pending).withRolls(rolls);
            }
            case GameEvent.RollOverridden e -> {
                int index = -1;
                for (int i = 0; i < state.rolls().size(); i++) {
                    if (state.rolls().get(i).requestId().equals(e.requestId())) {
                        index = i;
                    }
                }
                if (index < 0) {
                    throw new InvalidEventException("There is no answered roll " + e.requestId());
                }
                RollResult old = state.rolls().get(index);
                if (!"admin".equals(e.source()) && !"digital".equals(e.source())) {
                    throw new InvalidEventException("An admin replaces a roll by typing a value (admin) or rolling again (digital), not \"" + e.source() + "\"");
                }
                Dice dice = e.dice() == null ? old.dice() : e.dice();
                checkValue(dice, e.faces(), e.total(), !"digital".equals(e.source()));
                checkSeed(dice, e.faces(), e.source(), e.seed());
                List<Attempt> history = new ArrayList<>(old.history());
                history.add(new Attempt(old.faces(), old.enteredTotal(), old.source(), "replaced", e.reason()));
                List<Band> bands = dice.equals(old.dice()) ? old.bands() : List.of(); // other dice: the old band table no longer fits
                List<RollResult> rolls = new ArrayList<>(state.rolls());
                rolls.set(index, new RollResult(old.requestId(), old.trainerId(), dice, e.faces(), e.source(), e.total(), bands, null, false, e.seed(), history));
                yield state.withRolls(rolls);
            }
            case GameEvent.RollExpired e -> {
                RollRequest request = openRequest(state, e.requestId());
                if (request.deadlineMillis() <= 0) {
                    throw new InvalidEventException("The roll " + e.requestId() + " has no time limit, so it cannot expire");
                }
                Map<String, RollRequest> pending = new LinkedHashMap<>(state.pendingRolls());
                pending.remove(e.requestId());
                yield state.withPendingRolls(pending);
            }
            case GameEvent.RollCancelled e -> {
                openRequest(state, e.requestId());
                Map<String, RollRequest> pending = new LinkedHashMap<>(state.pendingRolls());
                pending.remove(e.requestId());
                yield state.withPendingRolls(pending);
            }
            case GameEvent.BattleEvent e -> BattleReducer.apply(state, e);
            case GameEvent.RandomDrawn e -> {
                requireText(e.purpose(), "purpose");
                if (e.bound() < 1 || e.value() < 0 || e.value() >= e.bound()) {
                    throw new InvalidEventException("A random choice is 0 up to " + e.bound() + " (not included), not " + e.value());
                }
                List<RandomDraw> draws = new ArrayList<>(state.draws());
                draws.add(new RandomDraw(e.purpose(), e.bound(), e.value()));
                yield state.withDraws(draws);
            }
        };
    }

    /** The state after these events in order, starting from {@code start}. */
    public static GameState applyAll(GameState start, List<? extends GameEvent> events) {
        GameState state = start;
        for (GameEvent event : events) {
            state = apply(state, event);
        }
        return state;
    }

    private static RollRequest openRequest(GameState state, String requestId) {
        RollRequest request = state.pendingRolls().get(requestId);
        if (request == null) {
            throw new InvalidEventException("There is no open roll request " + requestId);
        }
        return request;
    }

    private static GameState replaceRequest(GameState state, RollRequest request) {
        Map<String, RollRequest> pending = new LinkedHashMap<>(state.pendingRolls());
        pending.put(request.requestId(), request);
        return state.withPendingRolls(pending);
    }

    /**
     * A value fits its dice: one value per die, each from 1 to the sides; or, if the total may be given instead, no dice and a total
     * from the lowest to the highest the dice can make.
     */
    static void checkValue(Dice dice, List<Integer> faces, Integer total, boolean totalAllowed) {
        if (total != null) {
            if (!totalAllowed) {
                throw new InvalidEventException("A digital roll shows every die, not only a total");
            }
            if (!faces.isEmpty()) {
                throw new InvalidEventException("Give every die or the total, not both");
            }
            if (total < dice.min() || total > dice.max()) {
                throw new InvalidEventException(dice + " makes a total from " + dice.min() + " to " + dice.max() + ", not " + total);
            }
            return;
        }
        if (faces.size() != dice.count()) {
            throw new InvalidEventException(dice + " needs " + dice.count() + " value(s), not " + faces.size());
        }
        for (int face : faces) {
            if (face < 1 || face > dice.sides()) {
                throw new InvalidEventException("A d" + dice.sides() + " shows 1 to " + dice.sides() + ", not " + face);
            }
        }
    }

    /** A digital roll must carry its seed and show exactly what that seed gives; no other source has a seed. */
    private static void checkSeed(Dice dice, List<Integer> faces, String source, Long seed) {
        if ("digital".equals(source)) {
            if (seed == null) {
                throw new InvalidEventException("A digital roll must carry its seed");
            }
            if (!DigitalRoller.roll(dice, seed).equals(faces)) {
                throw new InvalidEventException("The dice of a digital roll are not the ones its seed gives");
            }
        } else if (seed != null) {
            throw new InvalidEventException("Only a digital roll has a seed");
        }
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("The " + what + " is empty");
        }
    }
}
