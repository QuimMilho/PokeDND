/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.game.GameEvent.RollAnswered;
import net.quimmilho.pokednd.core.game.GameEvent.RollCancelled;
import net.quimmilho.pokednd.core.game.GameEvent.RollExpired;
import net.quimmilho.pokednd.core.game.GameEvent.RollOverridden;
import net.quimmilho.pokednd.core.game.GameEvent.RollRejected;
import net.quimmilho.pokednd.core.game.GameEvent.RollRequested;
import net.quimmilho.pokednd.core.game.GameEvent.RollSubmitted;
import net.quimmilho.pokednd.core.game.GameState.RollRequest;
import net.quimmilho.pokednd.core.game.GameState.RollStatus;
import net.quimmilho.pokednd.core.game.GameState.Roller;
import net.quimmilho.pokednd.core.game.GameState.Submission;

/**
 * The flow around every roll (chapter 6.7): the game asks, a person rolls a real die (or taps Roll), the admin approves, and the
 * result is recorded. Each method is one human action. It looks at the state and the settings ({@link RollSettings}), and returns the
 * events to add with {@link GameLog#append}, or refuses with a {@link RollRefusedException} whose message can be shown to the person.
 *
 * <p>The desk is pure: it has no clock (the time is given), no random numbers of its own (a seed is given) and changes nothing. Before
 * it returns, it applies the events to a copy of the state, so an event that the reducer would refuse (a 7 on a d6) never leaves the
 * desk.
 */
public final class RollDesk {

    /** The name written as the approver when a typed roll was approved by the clock ({@code dice.approval.autoSeconds}). */
    public static final String AUTO_APPROVER = "auto";

    /**
     * Who does something.
     *
     * @param userId the account of the person
     * @param admin  true for an admin of the game
     */
    public record Actor(String userId, boolean admin) {
        public static Actor admin(String userId) {
            return new Actor(userId, true);
        }

        public static Actor player(String userId) {
            return new Actor(userId, false);
        }
    }

    private RollDesk() {
    }

    // ---------------------------------------------------------------------------------------------------------------- asking

    /**
     * Opens a roll request. Who must answer comes from the kind of roll and the settings: a normal roll is for the player it names; an
     * enemy roll or a secret roll is made by the system, or by the admin with real dice if {@code dice.enemy.mode} or
     * {@code dice.secret.mode} is {@code adminPhysical}. A request for the system is answered at once with a digital roll. A time limit
     * from {@code dice.prompt.timeoutSeconds} is added to a request that waits for a person.
     *
     * @param ask  the request as the rules want it (see {@link RollRequests}); its {@code roller} is used for a normal roll
     * @param now  the time now, in milliseconds since 1970
     * @param seeds where seeds of digital rolls come from (see {@link DigitalRoller#seeds})
     */
    public static List<GameEvent> open(GameState state, RollSettings settings, RollRequested ask, long now, LongSupplier seeds) {
        Roller roller = switch (ask.rollClass()) {
            case NORMAL -> ask.roller();
            case ENEMY -> settings.enemyMode() == RollSettings.SystemRolls.AUTO ? Roller.SYSTEM : Roller.ADMIN;
            case SECRET -> settings.secretMode() == RollSettings.SystemRolls.AUTO ? Roller.SYSTEM : Roller.ADMIN;
        };
        long deadline = ask.deadlineMillis();
        if (deadline == 0 && roller != Roller.SYSTEM && settings.timeoutSeconds() > 0) {
            deadline = now + settings.timeoutSeconds() * 1000L;
        }
        RollRequested request = new RollRequested(ask.requestId(), ask.trainerId(), ask.dice(), ask.reason(), roller,
                roller == Roller.PLAYER ? ask.rollerUserId() : null, ask.rollClass(), ask.bands(), ask.trueChance(), ask.modifiers(),
                ask.explanation(), deadline);
        List<GameEvent> events = new ArrayList<>();
        events.add(request);
        if (roller == Roller.SYSTEM) {
            long seed = seeds.getAsLong();
            events.add(new RollAnswered(ask.requestId(), DigitalRoller.roll(ask.dice(), seed), "digital", null, null, false, seed));
        }
        return checked(state, events);
    }

    // ------------------------------------------------------------------------------------------------------ the player's side

    /**
     * A player types the result of a real die. With approval on (the default) the request then waits for the admin; with approval off
     * the value is accepted at once. With {@code dice.entry.perDie} the player types every die; otherwise only the total.
     */
    public static List<GameEvent> submit(GameState state, RollSettings settings, Actor actor, String requestId, List<Integer> faces,
                                         Integer total, long now) {
        RollRequest request = open(state, requestId);
        requireAssignedPlayer(request, actor);
        if (!settings.allows(RollSettings.InputMode.PLAYER_SUBMIT)) {
            throw new RollRefusedException("Typing the result of a real die is not allowed in this game");
        }
        if (settings.entryPerDie() && total != null) {
            throw new RollRefusedException("Type the value of every die, not only the total");
        }
        if (!settings.entryPerDie() && (total == null || !faces.isEmpty())) {
            throw new RollRefusedException("Type only the total of the dice");
        }
        if (request.status() == RollStatus.SUBMITTED) {
            throw new RollRefusedException("Your result is already waiting for the admin");
        }
        if (!settings.approvalRequired()) {
            // nobody has to approve: the value counts at once (who typed it is in the audit trail)
            return checked(state, List.of(new RollAnswered(requestId, faces, "typed", total, null, false, null)));
        }
        return checked(state, List.of(new RollSubmitted(requestId, faces, total, actor.userId(), now)));
    }

    /**
     * A player taps Roll and the system rolls for them. The seed is given by the caller (from {@link DigitalRoller#seeds}) and is kept in
     * the event, so the same log always shows the same dice. No approval is needed.
     */
    public static List<GameEvent> rollDigital(GameState state, RollSettings settings, Actor actor, String requestId, long seed) {
        RollRequest request = open(state, requestId);
        if (request.roller() == Roller.ADMIN) {
            throw new RollRefusedException("This roll is made with real dice and typed in by the admin");
        }
        if (request.roller() == Roller.PLAYER && !actor.admin()) {
            requireAssignedPlayer(request, actor);
        }
        if (!actor.admin() && request.roller() == Roller.SYSTEM) {
            throw new RollRefusedException("The system makes this roll by itself");
        }
        if (!settings.allows(RollSettings.InputMode.DIGITAL)) {
            throw new RollRefusedException("Digital rolls are not allowed in this game");
        }
        if (request.status() == RollStatus.SUBMITTED) {
            throw new RollRefusedException("A result is already waiting for the admin");
        }
        return checked(state, List.of(new RollAnswered(requestId, DigitalRoller.roll(request.dice(), seed), "digital", null, null, false, seed)));
    }

    // -------------------------------------------------------------------------------------------------------- the admin's side

    /** The admin approves what the player typed. */
    public static List<GameEvent> approve(GameState state, Actor actor, String requestId) {
        requireAdmin(actor, "approve a roll");
        Submission typed = submitted(open(state, requestId));
        return checked(state, List.of(new RollAnswered(requestId, typed.faces(), "typed", typed.total(), actor.userId(), false, null)));
    }

    /** The admin approves what the player typed after changing the value. A value that is the same as typed is simply approved. */
    public static List<GameEvent> edit(GameState state, Actor actor, String requestId, List<Integer> faces, Integer total) {
        requireAdmin(actor, "edit a roll");
        Submission typed = submitted(open(state, requestId));
        boolean same = typed.faces().equals(faces) && Objects.equals(typed.total(), total);
        return checked(state, List.of(new RollAnswered(requestId, faces, "typed", total, actor.userId(), !same, null)));
    }

    /** The admin rejects what the player typed; the player is asked again. */
    public static List<GameEvent> reject(GameState state, Actor actor, String requestId, String reason) {
        requireAdmin(actor, "reject a roll");
        submitted(open(state, requestId));
        return checked(state, List.of(new RollRejected(requestId, reason)));
    }

    /** The admin types the result for anyone, at any time (always allowed, whatever {@code dice.input.modes} says). */
    public static List<GameEvent> enter(GameState state, Actor actor, String requestId, List<Integer> faces, Integer total) {
        requireAdmin(actor, "enter a roll");
        open(state, requestId);
        return checked(state, List.of(new RollAnswered(requestId, faces, "admin", total, null, false, null)));
    }

    /** The admin withdraws a request. */
    public static List<GameEvent> cancel(GameState state, Actor actor, String requestId) {
        requireAdmin(actor, "cancel a roll");
        open(state, requestId);
        return checked(state, List.of(new RollCancelled(requestId)));
    }

    /** The admin changes the value of a roll that was already answered; {@code dice} may be another die, or null to keep it. */
    public static List<GameEvent> override(GameState state, Actor actor, String requestId, Dice dice, List<Integer> faces, Integer total, String reason) {
        requireAdmin(actor, "change a roll");
        return checked(state, List.of(new RollOverridden(requestId, dice, faces, total, "admin", null, reason)));
    }

    /** The admin rolls an answered roll again with the system's dice. The old value stays in the history. */
    public static List<GameEvent> reroll(GameState state, Actor actor, String requestId, Dice dice, long seed, String reason) {
        requireAdmin(actor, "roll again");
        Dice use = dice == null ? answered(state, requestId).dice() : dice;
        return checked(state, List.of(new RollOverridden(requestId, dice, DigitalRoller.roll(use, seed), null, "digital", seed, reason)));
    }

    // ----------------------------------------------------------------------------------------------------- the clock's side

    /** The requests whose time limit has run out at {@code now}, closed without an answer. */
    public static List<GameEvent> expire(GameState state, long now) {
        List<GameEvent> events = new ArrayList<>();
        for (RollRequest r : state.pendingRolls().values()) {
            if (r.deadlineMillis() > 0 && r.deadlineMillis() <= now) {
                events.add(new RollExpired(r.requestId()));
            }
        }
        return events;
    }

    /** The typed rolls that have waited for the admin for {@code dice.approval.autoSeconds}, approved by the clock. */
    public static List<GameEvent> autoApprove(GameState state, RollSettings settings, long now) {
        List<GameEvent> events = new ArrayList<>();
        if (settings.approvalAutoSeconds() <= 0) {
            return events;
        }
        for (RollRequest r : state.pendingRolls().values()) {
            if (r.status() == RollStatus.SUBMITTED && r.submission().atMillis() + settings.approvalAutoSeconds() * 1000L <= now) {
                Submission typed = r.submission();
                events.add(new RollAnswered(r.requestId(), typed.faces(), "typed", typed.total(), AUTO_APPROVER, false, null));
            }
        }
        return events;
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    private static RollRequest open(GameState state, String requestId) {
        RollRequest request = state.pendingRolls().get(requestId);
        if (request == null) {
            throw new RollRefusedException("There is no open roll request " + requestId);
        }
        return request;
    }

    private static Submission submitted(RollRequest request) {
        if (request.status() != RollStatus.SUBMITTED) {
            throw new RollRefusedException("No player has typed a result for this roll yet");
        }
        return request.submission();
    }

    private static GameState.RollResult answered(GameState state, String requestId) {
        return state.rolls().stream().filter(r -> r.requestId().equals(requestId)).findFirst()
                .orElseThrow(() -> new RollRefusedException("There is no answered roll " + requestId));
    }

    private static void requireAdmin(Actor actor, String what) {
        if (!actor.admin()) {
            throw new RollRefusedException("Only the admin can " + what);
        }
    }

    private static void requireAssignedPlayer(RollRequest request, Actor actor) {
        if (request.roller() != Roller.PLAYER) {
            throw new RollRefusedException("This roll is not for a player");
        }
        if (request.rollerUserId() != null && !request.rollerUserId().equals(actor.userId())) {
            throw new RollRefusedException("This roll is for another player");
        }
    }

    /** Applies the events to a copy of the state: if the reducer refuses one, the desk refuses with the same words. */
    private static List<GameEvent> checked(GameState state, List<GameEvent> events) {
        try {
            GameReducer.applyAll(state, events);
        } catch (InvalidEventException e) {
            throw new RollRefusedException(e.getMessage());
        }
        return List.copyOf(events);
    }

}
