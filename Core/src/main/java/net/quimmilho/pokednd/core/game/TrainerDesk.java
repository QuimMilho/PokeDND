/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.quimmilho.pokednd.core.game.GameState.Knockout;
import net.quimmilho.pokednd.core.game.GameState.RollResult;
import net.quimmilho.pokednd.core.trainer.KnockOut;
import net.quimmilho.pokednd.core.trainer.KnockOut.DeathSaves;
import net.quimmilho.pokednd.core.trainer.KoMode;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * What happens to a trainer who is at 0 HP (chapter 4.6), inside and outside a battle. {@code faintRespawn}: after the battle (or
 * at once, outside one) the trainer goes back to the healer with full HP and loses {@code combat.whiteout.moneyLossPct} % of their ₽
 * ({@link #respawn}); going to the last town is the work of the world (phase 51). {@code deathSaves}: the 5e saving throws, one roll of
 * 1d20 at a time ({@link #deathSaveRequest}, {@link #deathSaveResult}); an ally can stabilise ({@link #stabilise}). {@code admin}: the
 * battle gets a task and the admin decides.
 *
 * <p>The trainer leaves the battle when they reach 0 HP (decided 2026-11-10), so the saving throws happen in the overworld: whoever runs
 * the game asks for one at the start of each of the trainer's turns.
 */
public final class TrainerDesk {

    private TrainerDesk() {
    }

    /** The trainer back at a healer: full HP, {@code moneyLossPct} % of the ₽ gone, and the knock-out is over. */
    public static List<GameEvent> respawn(GameState state, String trainerId, int moneyLossPct) {
        Trainer trainer = trainer(state, trainerId);
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.TrainerSet(trainerId, KnockOut.respawn(trainer, moneyLossPct)));
        if (state.knockouts().containsKey(trainerId)) {
            events.add(new GameEvent.KnockoutSet(trainerId, null));
        }
        return check(state, events);
    }

    /**
     * Called when a battle has ended: every trainer whose knock-out is "go back to the healer" respawns (full HP, some ₽ lost). The
     * events are added right after {@code BattleEnded}.
     */
    static List<GameEvent> afterBattle(GameState state, BattleSettings rules) {
        List<GameEvent> events = new ArrayList<>();
        for (var entry : state.knockouts().entrySet()) {
            DeathSaves saves = entry.getValue().saves();
            if (saves.state() == DeathSaves.State.RESPAWN && state.trainers().containsKey(entry.getKey())) {
                events.addAll(respawn(state, entry.getKey(), rules.knockOut().moneyLossPct()));
            }
        }
        return events;
    }

    /**
     * Asks for the next death saving throw of a knocked-out trainer: 1d20, 10 or more is a success (the request id is
     * {@code ko-<trainer>-<n>}). The roll is the trainer's player's.
     *
     * @throws BattleRefusedException if the trainer is not unconscious in the {@code deathSaves} mode
     */
    public static List<GameEvent> deathSaveRequest(GameState state, RollSettings rollSettings, String trainerId, long now, LongSupplier seeds) {
        Trainer trainer = trainer(state, trainerId);
        Knockout ko = state.knockouts().get(trainerId);
        if (ko == null || ko.mode() != KoMode.DEATH_SAVES || ko.saves().isOver()) {
            throw new BattleRefusedException(trainer.name() + " has no death saving throw to roll");
        }
        String id = "ko-" + trainerId + "-" + (ko.rolled().size() + 1);
        if (state.pendingRolls().containsKey(id)) {
            throw new BattleRefusedException("The death saving throw " + id + " has been asked already");
        }
        GameEvent.RollRequested ask = RollRequests.classic(id, trainerId, "the death saving throw of " + trainer.name(), 0, 10, List.of(),
                GameState.RollClass.NORMAL, null);
        return check(state, new ArrayList<>(RollDesk.open(state, rollSettings, ask, now, seeds)));
    }

    /**
     * The death saving throw {@code requestId} has been answered: counts it. A natural 20 brings the trainer back to 1 HP; three
     * successes stabilise; three failures respawn the trainer (or, with {@code trainer.ko.deathAllowed}, the trainer dies and the admin
     * decides); a natural 1 counts as two failures.
     *
     * @throws BattleRefusedException if the roll is not answered, is not a death saving throw of this trainer, or was counted already
     */
    public static List<GameEvent> deathSaveResult(GameState state, BattleSettings rules, String trainerId, String requestId) {
        Trainer trainer = trainer(state, trainerId);
        Knockout ko = state.knockouts().get(trainerId);
        if (ko == null || ko.mode() != KoMode.DEATH_SAVES || ko.saves().isOver()) {
            throw new BattleRefusedException(trainer.name() + " has no death saving throw to count");
        }
        if (!requestId.startsWith("ko-" + trainerId + "-")) {
            throw new BattleRefusedException(requestId + " is not a death saving throw of " + trainer.name());
        }
        if (ko.rolled().contains(requestId)) {
            throw new BattleRefusedException("The death saving throw " + requestId + " has been counted already");
        }
        RollResult result = null;
        for (RollResult r : state.rolls()) {
            if (r.requestId().equals(requestId)) {
                result = r;
            }
        }
        if (result == null) {
            throw new BattleRefusedException("The death saving throw " + requestId + " has not been answered");
        }
        DeathSaves saves = ko.saves().roll(result.total(), rules.knockOut().deathAllowed());
        List<String> rolled = new ArrayList<>(ko.rolled());
        rolled.add(requestId);
        List<GameEvent> events = new ArrayList<>();
        switch (saves.state()) {
            case REVIVED -> {
                events.add(new GameEvent.TrainerHpChanged(trainerId, 1, "after a natural 20 on the death saving throw"));
                events.add(new GameEvent.KnockoutSet(trainerId, null));
            }
            case RESPAWN -> {
                events.add(new GameEvent.TrainerSet(trainerId, KnockOut.respawn(trainer, rules.knockOut().moneyLossPct())));
                events.add(new GameEvent.KnockoutSet(trainerId, null));
            }
            default -> events.add(new GameEvent.KnockoutSet(trainerId, new Knockout(ko.mode(), saves, rolled)));
        }
        return check(state, events);
    }

    /** An ally stabilised the trainer (a Medicine check of DC 10, or a healing effect): the saving throws stop, the trainer stays at 0 HP. */
    public static List<GameEvent> stabilise(GameState state, String trainerId) {
        Trainer trainer = trainer(state, trainerId);
        Knockout ko = state.knockouts().get(trainerId);
        if (ko == null || ko.mode() != KoMode.DEATH_SAVES || ko.saves().isOver()) {
            throw new BattleRefusedException(trainer.name() + " cannot be stabilised");
        }
        return check(state, List.of(new GameEvent.KnockoutSet(trainerId, new Knockout(ko.mode(), ko.saves().stabilise(), ko.rolled()))));
    }

    private static Trainer trainer(GameState state, String trainerId) {
        Trainer trainer = state.trainers().get(trainerId);
        if (trainer == null) {
            throw new BattleRefusedException("There is no trainer " + trainerId);
        }
        return trainer;
    }

    private static List<GameEvent> check(GameState state, List<GameEvent> events) {
        try {
            GameReducer.applyAll(state, events);
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
        return List.copyOf(events);
    }
}
