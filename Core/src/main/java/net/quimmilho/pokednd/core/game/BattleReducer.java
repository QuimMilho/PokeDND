/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.ActionKind;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.InitiativeEntry;
import net.quimmilho.pokednd.core.game.Battle.InitiativeSlot;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Result;
import net.quimmilho.pokednd.core.game.Battle.Step;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.game.GameEvent.BattleEvent;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * The rules of a battle as {@code (state, event) → new state} (chapter 7). Like {@link GameReducer} it is pure: no clock, no dice, no
 * settings except the ones copied into the battle. An event that cannot happen is refused with {@link InvalidEventException} and the
 * state does not change. Every change also writes a line of the battle log in plain language, so the log is part of the state and the
 * same events always give the same log.
 */
final class BattleReducer {

    private BattleReducer() {
    }

    static GameState apply(GameState state, BattleEvent event) {
        return switch (event) {
            case GameEvent.BattleStarted e -> started(state, e);
            case GameEvent.RoundOpened e -> opened(state, e);
            case GameEvent.InitiativeSet e -> initiativeSet(state, e);
            case GameEvent.ActionDeclared e -> declared(state, e);
            case GameEvent.ResolveStarted e -> resolveStarted(state, e);
            case GameEvent.ActionResolved e -> resolved(state, e);
            case GameEvent.ReplacementSent e -> replacement(state, e);
            case GameEvent.CombatantSet e -> combatantSet(state, e);
            case GameEvent.QueueReordered e -> reordered(state, e);
            case GameEvent.RoundEnded e -> roundEnded(state, e);
            case GameEvent.BattleEnded e -> ended(state, e);
            case GameEvent.HpChanged e -> hpChanged(state, e);
            case GameEvent.StatusSet e -> statusSet(state, e);
            case GameEvent.StageChanged e -> stageChanged(state, e);
            case GameEvent.VolatileSet e -> volatileSet(state, e);
            case GameEvent.HeldItemSet e -> heldItemSet(state, e);
            case GameEvent.BattleNoted e -> noted(state, e);
            case GameEvent.SwitchRequired e -> switchRequired(state, e);
            case GameEvent.AdminTaskOpened e -> taskOpened(state, e);
            case GameEvent.AdminTaskClosed e -> taskClosed(state, e);
            case GameEvent.BallThrown e -> ballThrown(state, e);
            case GameEvent.ParticipantLeft e -> participantLeft(state, e);
            case GameEvent.FleeFailed e -> fleeFailed(state, e);
            case GameEvent.BattleSettled e -> settled(state, e);
        };
    }

    // ------------------------------------------------------------------------------------------------------------- starting

    private static GameState started(GameState state, GameEvent.BattleStarted e) {
        Battle old = state.battle();
        if (old != null && old.step() != Step.ENDED) {
            throw new InvalidEventException("The battle " + old.battleId() + " is still going on: only one battle runs at a time");
        }
        requireText(e.battleId(), "battle id");
        if (e.battleType() == null || e.rules() == null) {
            throw new InvalidEventException("A battle needs a type and its settings");
        }
        Map<String, Participant> participants = new LinkedHashMap<>();
        for (Participant p : e.participants()) {
            requireText(p.id(), "participant id");
            requireText(p.name(), "participant name");
            if (p.kind() == null) {
                throw new InvalidEventException("The participant " + p.id() + " needs a kind");
            }
            if (participants.put(p.id(), p) != null) {
                throw new InvalidEventException("The participant " + p.id() + " is there twice");
            }
            if (p.kind() == Kind.TRAINER && (p.trainerId() == null || !state.trainers().containsKey(p.trainerId()))) {
                throw new InvalidEventException("There is no trainer " + p.trainerId() + " for the participant " + p.id());
            }
            if (p.active().isEmpty() || p.active().size() > e.rules().activePerTrainer()) {
                throw new InvalidEventException(p.name() + " needs 1 to " + e.rules().activePerTrainer() + " active Pokémon, not " + p.active().size());
            }
        }
        if (participants.values().stream().map(Participant::side).distinct().count() < 2) {
            throw new InvalidEventException("A battle needs at least two sides");
        }
        Map<String, Combatant> combatants = new LinkedHashMap<>();
        for (Combatant c : e.combatants()) {
            requireText(c.id(), "Pokémon id");
            if (combatants.put(c.id(), c) != null) {
                throw new InvalidEventException("The Pokémon " + c.id() + " is there twice");
            }
            checkCombatant(c, participants);
        }
        Set<String> placed = new HashSet<>();
        for (Participant p : participants.values()) {
            for (String id : concat(p.active(), p.reserve())) {
                Combatant c = combatants.get(id);
                if (c == null || !c.ownerId().equals(p.id())) {
                    throw new InvalidEventException(p.name() + " lists the Pokémon " + id + ", which is not theirs");
                }
                if (!placed.add(id)) {
                    throw new InvalidEventException("The Pokémon " + id + " is listed twice");
                }
            }
        }
        if (placed.size() != combatants.size()) {
            throw new InvalidEventException("Every Pokémon must be on the field or in a reserve");
        }
        Battle battle = new Battle(e.battleId(), e.battleType(), e.rules(), 0, Step.START, participants, combatants, List.of(), List.of(), 0,
                Map.of(), List.of(), List.of(), List.of());
        return state.withBattle(battle.withMeetings().logged(openingLine(battle)));
    }

    private static String openingLine(Battle b) {
        Map<Integer, List<String>> sides = new LinkedHashMap<>();
        for (Participant p : b.participants().values()) {
            for (Combatant c : b.activeOf(p)) {
                sides.computeIfAbsent(p.side(), k -> new ArrayList<>()).add(b.label(c));
            }
        }
        String kind = switch (b.type()) {
            case WILD -> "A wild battle";
            case NPC_TRAINER -> "A trainer battle";
            case PVP -> "A PvP battle";
            case MIXED -> "A battle";
        };
        return kind + " begins: " + sides.values().stream().map(BattleReducer::andList).collect(Collectors.joining(" against ")) + ".";
    }

    private static void checkCombatant(Combatant c, Map<String, Participant> participants) {
        if (!participants.containsKey(c.ownerId())) {
            throw new InvalidEventException("The Pokémon " + c.id() + " belongs to " + c.ownerId() + ", who is not in the battle");
        }
        if (c.level() < 1 || c.maxHp() < 1 || c.hp() < 0 || c.hp() > c.maxHp() || c.speed() < 0) {
            throw new InvalidEventException("The numbers of the Pokémon " + c.id() + " are not valid (level, HP and Speed)");
        }
        for (MoveSlot m : c.moves()) {
            if (m.pp() < 0 || m.pp() > m.maxPp()) {
                throw new InvalidEventException("The PP of " + m.name() + " of " + c.name() + " must be 0 to " + m.maxPp());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- rounds

    private static GameState opened(GameState state, GameEvent.RoundOpened e) {
        Battle b = running(state, e.battleId());
        if (b.step() != Step.START && b.step() != Step.ROUND_END) {
            throw new InvalidEventException("Round " + (b.round() + 1) + " cannot begin: round " + b.round() + " is not over");
        }
        if (e.round() != b.round() + 1) {
            throw new InvalidEventException("The next round is " + (b.round() + 1) + ", not " + e.round());
        }
        for (Participant p : b.participants().values()) {
            for (Combatant c : b.activeOf(p)) {
                if (c.fainted() && ableReserve(b, p)) {
                    throw new InvalidEventException(p.name() + " must send in a replacement for " + c.name() + " first");
                }
            }
        }
        if (!b.mustSwitch().isEmpty()) {
            throw new InvalidEventException(b.label(b.combatant(b.mustSwitch().get(0))) + " must be replaced first");
        }
        boolean roll = b.rules().rerollEachRound() || b.order().isEmpty();
        List<String> in = b.participants().values().stream().filter(p -> !b.out(p)).map(Participant::id).toList();
        Battle next;
        if (roll) {
            Set<String> seen = new LinkedHashSet<>();
            for (InitiativeSlot s : e.slots()) {
                if (!in.contains(s.participantId()) || !seen.add(s.participantId())) {
                    throw new InvalidEventException("An initiative roll is asked for " + s.participantId() + ", who cannot roll");
                }
                if (!state.pendingRolls().containsKey(s.requestId()) && state.rolls().stream().noneMatch(r -> r.requestId().equals(s.requestId()))) {
                    throw new InvalidEventException("The roll request " + s.requestId() + " does not exist");
                }
            }
            if (seen.size() != in.size()) {
                throw new InvalidEventException("Every participant that can still fight must roll initiative");
            }
            if (e.declareDeadlineMillis() != 0) {
                throw new InvalidEventException("The time limit starts when initiative is set");
            }
            next = b.with(e.round(), Step.INITIATIVE, e.slots(), b.order(), 0, Map.of(), List.of(), List.of());
        } else {
            if (!e.slots().isEmpty()) {
                throw new InvalidEventException("Initiative is not rolled again in this battle");
            }
            if (e.declareDeadlineMillis() < 0) {
                throw new InvalidEventException("The time limit cannot be negative");
            }
            next = b.with(e.round(), Step.DECLARE, List.of(), b.order(), e.declareDeadlineMillis(), Map.of(), List.of(), List.of());
        }
        return state.withBattle(next.logged("Round " + e.round() + " begins."));
    }

    private static GameState initiativeSet(GameState state, GameEvent.InitiativeSet e) {
        Battle b = running(state, e.battleId());
        requireStep(b, Step.INITIATIVE, e.round());
        if (e.declareDeadlineMillis() < 0) {
            throw new InvalidEventException("The time limit cannot be negative");
        }
        List<InitiativeEntry> order = BattleOrder.initiative(state, b, b.slots(), e.tieBreaks());
        String line = "Initiative: " + order.stream().map(en -> name(b.participants().get(en.participantId())) + " " + en.total()
                + " (" + en.roll() + (en.modifier() < 0 ? " − " + -en.modifier() : " + " + en.modifier()) + ")").collect(Collectors.joining(", ")) + ".";
        Battle next = b.with(b.round(), Step.DECLARE, b.slots(), order, e.declareDeadlineMillis(), Map.of(), List.of(), List.of());
        return state.withBattle(next.logged(line));
    }

    private static GameState declared(GameState state, GameEvent.ActionDeclared e) {
        Battle b = running(state, e.battleId());
        requireStep(b, Step.DECLARE, e.round());
        Combatant actor = b.combatant(e.actorId());
        if (actor == null) {
            throw new InvalidEventException("There is no Pokémon " + e.actorId() + " in the battle");
        }
        if (!b.isActive(actor)) {
            throw new InvalidEventException(b.label(actor) + " is not on the field");
        }
        if (actor.fainted()) {
            throw new InvalidEventException(b.label(actor) + " has fainted and cannot act");
        }
        if (e.action() == null || e.action().kind() == null) {
            throw new InvalidEventException("An action is missing");
        }
        checkAction(state, b, actor, e.action());
        Map<String, Battle.Action> declared = new LinkedHashMap<>(b.declared());
        declared.put(actor.id(), e.action());
        return state.withBattle(b.withDeclared(declared));
    }

    private static GameState resolveStarted(GameState state, GameEvent.ResolveStarted e) {
        Battle b = running(state, e.battleId());
        requireStep(b, Step.DECLARE, e.round());
        for (Combatant c : b.standing()) {
            if (!b.declared().containsKey(c.id())) {
                throw new InvalidEventException(b.label(c) + " has not chosen an action yet");
            }
        }
        List<String> queue = BattleOrder.queue(b);
        Battle next = b.with(b.round(), Step.RESOLVE, b.slots(), b.order(), b.declareDeadlineMillis(), b.declared(), queue, List.of());
        String line = queue.isEmpty() ? "Nobody acts this round."
                : "Turn order: " + queue.stream().map(id -> b.label(b.combatant(id))).collect(Collectors.joining(", ")) + ".";
        return state.withBattle(next.logged(line));
    }

    private static GameState resolved(GameState state, GameEvent.ActionResolved e) {
        Battle b = running(state, e.battleId());
        requireStep(b, Step.RESOLVE, e.round());
        if (b.queue().isEmpty()) {
            throw new InvalidEventException("Every action of this round has already happened");
        }
        if (!b.queue().get(0).equals(e.actorId())) {
            throw new InvalidEventException("The next action is that of " + b.label(b.combatant(b.queue().get(0))) + ", not of " + e.actorId());
        }
        Combatant actor = b.combatant(e.actorId());
        Action action = b.declared().get(actor.id());
        Participant owner = b.ownerOf(actor);
        if (e.result() == null) {
            throw new InvalidEventException("The result of an action is missing");
        }
        switch (e.result()) {
            case CANCELLED_FAINTED -> {
                if (!actor.fainted()) {
                    throw new InvalidEventException(b.label(actor) + " has not fainted, so its action is not cancelled");
                }
            }
            case CANCELLED_BY_ADMIN -> {
            }
            case CANCELLED_SWITCHED -> {
                if (b.isActive(actor)) {
                    throw new InvalidEventException(b.label(actor) + " is still on the field, so its action is not cancelled");
                }
            }
            case PREVENTED -> {
                if (action.kind() != ActionKind.MOVE) {
                    throw new InvalidEventException("Only a move can be prevented");
                }
            }
            case DONE, FAILED_NO_TARGET -> {
                if (actor.fainted()) {
                    throw new InvalidEventException(b.label(actor) + " has fainted, so its action is cancelled");
                }
                if (!b.isActive(actor)) {
                    throw new InvalidEventException(b.label(actor) + " is not on the field any more; the admin must cancel its action");
                }
                boolean none = noTarget(b, actor, action);
                if (none && e.result() == Result.DONE) {
                    throw new InvalidEventException("The target of " + b.label(actor) + " has fainted, so the action fails");
                }
                if (!none && e.result() == Result.FAILED_NO_TARGET) {
                    throw new InvalidEventException("The action of " + b.label(actor) + " still has a target, so it does not fail");
                }
            }
        }
        Battle next = b;
        String line;
        if (e.result() == Result.CANCELLED_FAINTED) {
            line = b.label(actor) + " fainted before it could act.";
        } else if (e.result() == Result.CANCELLED_BY_ADMIN) {
            line = "The admin cancelled the action of " + b.label(actor) + ".";
        } else if (e.result() == Result.CANCELLED_SWITCHED) {
            line = actor.name() + " is no longer on the field, so its action is cancelled.";
        } else if (e.result() == Result.PREVENTED) {
            line = null; // the lines that say why come before this event
        } else if (e.result() == Result.FAILED_NO_TARGET) {
            line = failedLine(b, actor, action);
        } else {
            switch (action.kind()) {
                case MOVE -> {
                    MoveSlot slot = moveOf(actor, action);
                    if (b.rules().ppEnabled() && !MoveSlot.STRUGGLE.equals(action.moveId()) && !isLocked(actor, action.moveId())) {
                        List<MoveSlot> moves = new ArrayList<>(actor.moves());
                        MoveSlot spent = slot.spend();
                        if (AbilityScripts.pressure(b, actor, action, slot.targetKind()) > 0) {
                            spent = spent.spend(); // Pressure: the move costs one more PP
                        }
                        moves.set(moves.indexOf(slot), spent);
                        next = replaceCombatant(next, actor.withMoves(moves));
                    }
                    line = moveLine(b, actor, action, slot);
                }
                case SWITCH -> {
                    Combatant in = b.combatant(action.switchTo());
                    if (in == null || !owner.reserve().contains(in.id()) || in.fainted()) {
                        throw new InvalidEventException(owner.name() + " cannot send in " + action.switchTo() + " any more");
                    }
                    next = swap(next, owner, actor.id(), in.id());
                    line = owner.name() + " withdraws " + actor.name() + " and sends out " + in.name() + ".";
                }
                case ITEM -> line = owner.name() + " uses " + pretty(action.itemId()) + " on " + b.combatant(action.targets().get(0)).name() + ".";
                case BALL -> line = owner.name() + " throws a " + pretty(action.itemId()) + " at " + b.label(b.combatant(action.targets().get(0))) + ".";
                case FLEE -> line = owner.name() + " tries to run away.";
                case SKIP -> line = b.label(actor) + " does nothing this round.";
                default -> throw new IllegalStateException();
            }
        }
        List<String> queue = new ArrayList<>(next.queue());
        queue.remove(0);
        List<String> done = new ArrayList<>(next.resolved());
        done.add(actor.id());
        next = next.withQueue(queue, done);
        return state.withBattle(line == null ? next : next.logged(line));
    }

    private static GameState replacement(GameState state, GameEvent.ReplacementSent e) {
        Battle b = running(state, e.battleId());
        Participant p = b.participants().get(e.participantId());
        if (p == null) {
            throw new InvalidEventException("There is no participant " + e.participantId());
        }
        Combatant out = b.combatant(e.fainted());
        Combatant in = b.combatant(e.replacement());
        if (out == null || !p.active().contains(out.id()) || !(out.fainted() || b.mustSwitch().contains(out.id()))) {
            throw new InvalidEventException(e.fainted() + " is not a fainted Pokémon of " + p.name() + " on the field");
        }
        if (in == null || !p.reserve().contains(in.id()) || in.fainted()) {
            throw new InvalidEventException(e.replacement() + " cannot come in: it is not in the reserve of " + p.name() + " or it has fainted");
        }
        Battle swapped = swap(b, p, out.id(), in.id());
        if (swapped.mustSwitch().contains(out.id())) {
            List<String> still = new ArrayList<>(swapped.mustSwitch());
            still.remove(out.id());
            swapped = swapped.withMustSwitch(still);
        }
        return state.withBattle(swapped.logged(p.name() + " sends out " + in.name() + " for " + out.name() + "."));
    }

    private static GameState combatantSet(GameState state, GameEvent.CombatantSet e) {
        Battle b = running(state, e.battleId());
        Combatant now = e.combatant();
        Combatant before = now == null ? null : b.combatant(now.id());
        if (before == null) {
            throw new InvalidEventException("There is no such Pokémon in the battle");
        }
        if (!before.ownerId().equals(now.ownerId())) {
            throw new InvalidEventException("A Pokémon cannot change owner");
        }
        checkCombatant(now, b.participants());
        Battle next = replaceCombatant(b, now);
        String line = !before.fainted() && now.fainted() ? b.label(now) + " fainted!"
                : before.fainted() && !now.fainted() ? b.label(now) + " is back on its feet."
                : "The admin changed " + b.label(now) + ".";
        return state.withBattle(next.logged(line));
    }

    private static GameState reordered(GameState state, GameEvent.QueueReordered e) {
        Battle b = running(state, e.battleId());
        if (b.step() != Step.RESOLVE) {
            throw new InvalidEventException("The order of the actions can only change while they resolve");
        }
        if (e.queue().size() != b.queue().size() || !new HashSet<>(e.queue()).equals(new HashSet<>(b.queue()))) {
            throw new InvalidEventException("The new order must contain exactly the actions that have not happened yet");
        }
        return state.withBattle(b.withQueue(e.queue(), b.resolved()).logged("The admin changed the order of the actions."));
    }

    private static GameState roundEnded(GameState state, GameEvent.RoundEnded e) {
        Battle b = running(state, e.battleId());
        requireStep(b, Step.RESOLVE, e.round());
        if (!b.queue().isEmpty()) {
            throw new InvalidEventException("The round cannot end: " + b.queue().size() + " action(s) have not happened yet");
        }
        if (!b.mustSwitch().isEmpty()) {
            throw new InvalidEventException("The round cannot end: " + b.label(b.combatant(b.mustSwitch().get(0))) + " must be replaced first");
        }
        Battle next = b.with(b.round(), Step.ROUND_END, List.of(), b.order(), 0, Map.of(), List.of(), b.resolved()).withReplaced(Map.of());
        return state.withBattle(next.logged("Round " + e.round() + " ends."));
    }

    private static GameState ended(GameState state, GameEvent.BattleEnded e) {
        Battle b = state.battle();
        if (b == null || !b.battleId().equals(e.battleId())) {
            throw new InvalidEventException("There is no battle " + e.battleId());
        }
        if (b.step() == Step.ENDED) {
            throw new InvalidEventException("The battle has already ended");
        }
        Battle next = b.with(b.round(), Step.ENDED, List.of(), b.order(), 0, Map.of(), List.of(), b.resolved());
        String why = e.reason() == null || e.reason().isBlank() ? "" : ": " + e.reason().strip();
        return state.withBattle(next.logged("The battle ends" + why + "."));
    }

    // ------------------------------------------------------------------------------------------------------------ what moves do

    private static Combatant pokemon(Battle b, String id) {
        Combatant c = id == null ? null : b.combatant(id);
        if (c == null) {
            throw new InvalidEventException("There is no Pokémon " + id + " in the battle");
        }
        return c;
    }

    private static GameState hpChanged(GameState state, GameEvent.HpChanged e) {
        Battle b = running(state, e.battleId());
        Combatant c = pokemon(b, e.combatantId());
        if (c.fainted()) {
            throw new InvalidEventException(b.label(c) + " has fainted already");
        }
        if (e.delta() == 0) {
            throw new InvalidEventException("A change of HP is not 0");
        }
        int hp = Math.max(0, Math.min(c.maxHp(), c.hp() + e.delta()));
        String cause = e.cause() == null || e.cause().isBlank() ? "" : " " + e.cause().strip();
        String line = e.delta() < 0
                ? b.label(c) + " takes " + (c.hp() - hp) + " damage" + cause + " (" + hp + "/" + c.maxHp() + " HP)."
                : b.label(c) + " recovers " + (hp - c.hp()) + " HP" + cause + " (" + hp + "/" + c.maxHp() + " HP).";
        Combatant next = c.withHp(hp);
        List<String> lines = new ArrayList<>(List.of(line));
        if (hp == 0) {
            next = next.withCondition(c.condition().afterFainting());
            lines.add(b.label(c) + " fainted!");
        }
        return state.withBattle(replaceCombatant(b, next).logged(lines.toArray(String[]::new)));
    }

    private static GameState statusSet(GameState state, GameEvent.StatusSet e) {
        Battle b = running(state, e.battleId());
        Combatant c = pokemon(b, e.combatantId());
        if (e.status() == null) {
            throw new InvalidEventException("A status is missing");
        }
        if (c.fainted()) {
            throw new InvalidEventException(b.label(c) + " has fainted and cannot get a status");
        }
        Battle.Status old = c.status();
        if (e.status() == Battle.Status.NONE) {
            if (old == Battle.Status.NONE) {
                throw new InvalidEventException(b.label(c) + " has no status to cure");
            }
        } else if (old != Battle.Status.NONE && !(old == Battle.Status.SLEEP && e.status() == Battle.Status.SLEEP)) {
            throw new InvalidEventException(b.label(c) + " is " + old.word() + " already");
        }
        if (e.turns() < 0 || e.turns() > 5) {
            throw new InvalidEventException("The turns of a status are 0 to 5, not " + e.turns());
        }
        int turns = e.status() == Battle.Status.SLEEP ? e.turns() : 0;
        Battle.Condition condition = c.condition().withStatus(e.status(), turns);
        if (e.status() == Battle.Status.TOXIC || e.status() == Battle.Status.NONE) {
            condition = condition.withVolatile(Battle.Volatiles.TOXIC, 0);
        }
        Combatant next = c.withCondition(condition);
        String line;
        if (e.cause() != null && !e.cause().isBlank()) {
            line = e.cause().strip();
        } else if (e.status() == Battle.Status.NONE) {
            line = switch (old) {
                case SLEEP -> b.label(c) + " woke up!";
                case FREEZE -> b.label(c) + " thawed out!";
                default -> b.label(c) + " is no longer " + old.word() + ".";
            };
        } else {
            line = switch (e.status()) {
                case BURN -> b.label(c) + " was burned!";
                case POISON -> b.label(c) + " was poisoned!";
                case TOXIC -> b.label(c) + " was badly poisoned!";
                case PARALYSIS -> b.label(c) + " is paralyzed! It may be unable to move!";
                case SLEEP -> b.label(c) + " fell asleep!";
                case FREEZE -> b.label(c) + " was frozen solid!";
                case NONE -> throw new IllegalStateException();
            };
        }
        return state.withBattle(replaceCombatant(b, next).logged(line));
    }

    /** The name of a stat stage in the log. */
    static String stageName(String id) {
        return switch (id) {
            case "atk" -> "Attack";
            case "def" -> "Defense";
            case "spa" -> "Sp. Atk";
            case "spd" -> "Sp. Def";
            case "spe" -> "Speed";
            case "accuracy" -> "accuracy";
            case "evasion" -> "evasiveness";
            default -> id;
        };
    }

    private static GameState stageChanged(GameState state, GameEvent.StageChanged e) {
        Battle b = running(state, e.battleId());
        Combatant c = pokemon(b, e.combatantId());
        if (!Battle.STAGE_IDS.contains(e.stat())) {
            throw new InvalidEventException("There is no stat " + e.stat() + " (use " + Battle.STAGE_IDS + ")");
        }
        if (e.delta() == 0) {
            throw new InvalidEventException("A change of stage is not 0");
        }
        if (c.fainted()) {
            throw new InvalidEventException(b.label(c) + " has fainted and its stats cannot change");
        }
        int old = c.condition().stage(e.stat());
        int now = Math.max(-6, Math.min(6, old + e.delta()));
        String who = b.label(c) + "'s " + stageName(e.stat());
        String line;
        if (now == old) {
            line = who + (e.delta() > 0 ? " won't go any higher!" : " won't go any lower!");
        } else {
            int moved = Math.abs(e.delta());
            line = who + (e.delta() > 0 ? (moved == 1 ? " rose!" : moved == 2 ? " rose sharply!" : " rose drastically!")
                    : (moved == 1 ? " fell!" : moved == 2 ? " harshly fell!" : " severely fell!"));
        }
        Combatant next = c.withCondition(c.condition().withStage(e.stat(), now));
        return state.withBattle(replaceCombatant(b, next).logged(line));
    }

    private static GameState volatileSet(GameState state, GameEvent.VolatileSet e) {
        Battle b = running(state, e.battleId());
        Combatant c = pokemon(b, e.combatantId());
        requireText(e.key(), "condition");
        if (e.value() < 0) {
            throw new InvalidEventException("The value of a condition is 0 or more");
        }
        Battle changed = replaceCombatant(b, c.withCondition(c.condition().withVolatile(e.key(), e.value())));
        return state.withBattle(e.text() == null || e.text().isBlank() ? changed : changed.logged(e.text().strip()));
    }

    private static GameState heldItemSet(GameState state, GameEvent.HeldItemSet e) {
        Battle b = running(state, e.battleId());
        Combatant c = pokemon(b, e.combatantId());
        String item = e.itemId() == null || e.itemId().isBlank() ? null : e.itemId();
        Battle changed = replaceCombatant(b, c.withCondition(c.condition().withHeldItem(item)));
        return state.withBattle(e.text() == null || e.text().isBlank() ? changed : changed.logged(e.text().strip()));
    }

    private static GameState noted(GameState state, GameEvent.BattleNoted e) {
        Battle b = running(state, e.battleId());
        requireText(e.text(), "text");
        return state.withBattle(b.logged(e.text().strip()));
    }

    private static GameState switchRequired(GameState state, GameEvent.SwitchRequired e) {
        Battle b = running(state, e.battleId());
        Combatant c = pokemon(b, e.combatantId());
        Participant p = b.ownerOf(c);
        if (!b.isActive(c)) {
            throw new InvalidEventException(b.label(c) + " is not on the field");
        }
        if (b.mustSwitch().contains(c.id())) {
            throw new InvalidEventException(b.label(c) + " has to be replaced already");
        }
        if (!ableReserve(b, p)) {
            throw new InvalidEventException(p.name() + " has no Pokémon left to send in");
        }
        List<String> list = new ArrayList<>(b.mustSwitch());
        list.add(c.id());
        return state.withBattle(b.withMustSwitch(list));
    }

    private static GameState taskOpened(GameState state, GameEvent.AdminTaskOpened e) {
        Battle b = running(state, e.battleId());
        requireText(e.taskId(), "task id");
        requireText(e.text(), "task text");
        pokemon(b, e.actorId());
        if (b.tasks().stream().anyMatch(t -> t.id().equals(e.taskId()))) {
            throw new InvalidEventException("The task " + e.taskId() + " is there already");
        }
        List<Battle.AdminTask> tasks = new ArrayList<>(b.tasks());
        tasks.add(new Battle.AdminTask(e.taskId(), e.actorId(), e.moveId(), e.text()));
        return state.withBattle(b.withTasks(tasks).logged("The admin has to apply by hand: " + e.text().strip()));
    }

    private static GameState taskClosed(GameState state, GameEvent.AdminTaskClosed e) {
        Battle b = running(state, e.battleId());
        if (b.tasks().stream().noneMatch(t -> t.id().equals(e.taskId()))) {
            throw new InvalidEventException("There is no open task " + e.taskId());
        }
        List<Battle.AdminTask> tasks = new ArrayList<>(b.tasks());
        tasks.removeIf(t -> t.id().equals(e.taskId()));
        return state.withBattle(b.withTasks(tasks).logged("The admin applied the effect by hand."));
    }

    // ------------------------------------------------------------------------------------------ catching, fleeing, leaving

    private static GameState ballThrown(GameState state, GameEvent.BallThrown e) {
        Battle b = running(state, e.battleId());
        Participant thrower = b.participants().get(e.participantId());
        if (thrower == null || thrower.kind() != Kind.TRAINER) {
            throw new InvalidEventException("Only a trainer throws a Poké Ball");
        }
        if (b.left().containsKey(thrower.id())) {
            throw new InvalidEventException(thrower.name() + " has left the battle");
        }
        requireText(e.ball(), "Poké Ball");
        if (e.shakes() < 0 || e.shakes() > 3 || e.caught() != (e.shakes() == 3)) {
            throw new InvalidEventException("A caught Pokémon shook 3 times and a Pokémon that broke free 0 to 2 times, not " + e.shakes());
        }
        Combatant target = pokemon(b, e.combatantId());
        if (target.fainted() || !b.isActive(target)) {
            throw new InvalidEventException(b.label(target) + " is not on the field");
        }
        if (b.sideOf(target) == thrower.side() || b.ownerOf(target).kind() == Kind.TRAINER) {
            throw new InvalidEventException("A Poké Ball can only be thrown at a Pokémon of the other side that is not another player's");
        }
        if (target.boss()) {
            throw new InvalidEventException("A boss cannot be caught");
        }
        String name = b.label(target);
        if (!e.caught()) {
            String line = switch (e.shakes()) {
                case 0 -> "Oh no! " + name + " broke free!";
                case 1 -> "Aww! It appeared to be caught!";
                default -> "Aargh! Almost had it!";
            };
            return state.withBattle(b.logged(line));
        }
        Combatant gone = target.withHp(0);
        gone = gone.withCondition(target.condition().afterFainting());
        Map<String, Battle.Catch> caught = new LinkedHashMap<>(b.caught());
        caught.put(target.id(), new Battle.Catch(target.hp(), thrower.id(), e.ball())); // kept, so the caught Pokémon has the HP it had when the ball hit
        return state.withBattle(replaceCombatant(b, gone).withCaught(caught).logged("Gotcha! " + name + " was caught!"));
    }

    private static GameState participantLeft(GameState state, GameEvent.ParticipantLeft e) {
        Battle b = running(state, e.battleId());
        Participant p = b.participants().get(e.participantId());
        if (p == null) {
            throw new InvalidEventException("There is no participant " + e.participantId());
        }
        if (b.left().containsKey(p.id())) {
            throw new InvalidEventException(p.name() + " has left already");
        }
        if (!List.of("fled", "knockedOut", "surrendered").contains(e.reason())) {
            throw new InvalidEventException("A participant leaves because it fled, was knocked out or surrendered, not \"" + e.reason() + "\"");
        }
        Set<String> mine = new HashSet<>(concat(p.active(), p.reserve()));
        Map<String, String> left = new LinkedHashMap<>(b.left());
        left.put(p.id(), e.reason());
        List<String> queue = new ArrayList<>(b.queue());
        queue.removeAll(mine);
        Map<String, Action> declared = new LinkedHashMap<>(b.declared());
        declared.keySet().removeAll(mine);
        List<String> mustSwitch = new ArrayList<>(b.mustSwitch());
        mustSwitch.removeAll(mine);
        String line = switch (e.reason()) {
            case "fled" -> name(p) + " got away safely!";
            case "knockedOut" -> name(p) + " is knocked out and leaves the battle.";
            default -> name(p) + " surrenders.";
        };
        Battle next = b.withLeft(left).withQueue(queue, b.resolved()).withDeclared(declared).withMustSwitch(mustSwitch);
        return state.withBattle(next.logged(line));
    }

    private static GameState settled(GameState state, GameEvent.BattleSettled e) {
        Battle b = state.battle();
        if (b == null || !b.battleId().equals(e.battleId())) {
            throw new InvalidEventException("There is no battle " + e.battleId());
        }
        if (b.step() != Step.ENDED) {
            throw new InvalidEventException("The rewards of a battle are paid after it has ended");
        }
        if (b.settled()) {
            throw new InvalidEventException("The rewards of this battle have been paid already");
        }
        return state.withBattle(b.withSettled().logged("The rewards of the battle are paid."));
    }

    private static GameState fleeFailed(GameState state, GameEvent.FleeFailed e) {
        Battle b = running(state, e.battleId());
        Participant p = b.participants().get(e.participantId());
        if (p == null || p.kind() != Kind.TRAINER) {
            throw new InvalidEventException("Only a trainer tries to run away");
        }
        if (b.left().containsKey(p.id())) {
            throw new InvalidEventException(p.name() + " has left the battle");
        }
        Map<String, Integer> attempts = new LinkedHashMap<>(b.fleeAttempts());
        attempts.merge(p.id(), 1, Integer::sum);
        return state.withBattle(b.withFleeAttempts(attempts).logged(p.name() + " couldn't get away!"));
    }

    // -------------------------------------------------------------------------------------------------------------- actions

    /**
     * Checks that an action is allowed for a Pokémon (chapter 7.4): the move is known and has PP (or it is Struggle with no PP left
     * anywhere), the target is legal, the Pokémon to switch in is in the reserve and has not fainted, the item or ball is in the bag, and
     * only a trainer throws a ball or runs away. Settings are checked by {@link BattleDesk}, because they come from the game.
     */
    static void checkAction(GameState state, Battle b, Combatant actor, Action action) {
        Participant owner = b.ownerOf(actor);
        String who = b.label(actor);
        switch (action.kind()) {
            case MOVE -> {
                String locked = b.choiceLockedMove(actor);
                if (locked != null && !locked.equals(action.moveId()) && !MoveSlot.STRUGGLE.equals(action.moveId())) {
                    throw new InvalidEventException(who + " is locked into " + pretty(locked) + " by its " + pretty(actor.condition().heldItem()));
                }
                MoveSlot slot = moveOf(actor, action, who, b.rules());
                TargetKind kind = slot.targetKind();
                List<String> chosen = action.targets();
                if (kind.choosesOne()) {
                    if (chosen.size() != 1) {
                        throw new InvalidEventException(slot.name() + " needs exactly one target");
                    }
                    if (!b.legalTargets(actor, kind).contains(chosen.get(0)) && !legalTrainerTarget(state, b, actor, kind, chosen.get(0))) {
                        throw new InvalidEventException(slot.name() + " cannot be aimed at " + chosen.get(0));
                    }
                } else if (kind == TargetKind.SELF) {
                    if (!chosen.isEmpty() && !chosen.equals(List.of(actor.id()))) {
                        throw new InvalidEventException(slot.name() + " can only be used on the user");
                    }
                } else if (!chosen.isEmpty()) {
                    throw new InvalidEventException(slot.name() + " does not choose a target");
                }
            }
            case SWITCH -> {
                Combatant in = action.switchTo() == null ? null : b.combatant(action.switchTo());
                if (in == null || !owner.reserve().contains(in.id())) {
                    throw new InvalidEventException(who + " can only be switched for a Pokémon of " + owner.name() + " in the reserve");
                }
                if (in.fainted()) {
                    throw new InvalidEventException(in.name() + " has fainted and cannot be sent in");
                }
            }
            case ITEM -> {
                if (owner.kind() == Kind.WILD) {
                    throw new InvalidEventException("A wild Pokémon cannot use an item");
                }
                requireText(action.itemId(), "item");
                requireBag(state, owner, action.itemId());
                if (action.targets().size() != 1) {
                    throw new InvalidEventException("An item is used on exactly one Pokémon");
                }
                Combatant target = b.combatant(action.targets().get(0));
                if (target == null || !target.ownerId().equals(owner.id())) {
                    throw new InvalidEventException("An item can only be used on a Pokémon of " + owner.name());
                }
            }
            case BALL -> {
                if (owner.kind() != Kind.TRAINER) {
                    throw new InvalidEventException("Only a trainer throws a Poké Ball");
                }
                requireText(action.itemId(), "Poké Ball");
                requireBag(state, owner, action.itemId());
                if (action.targets().size() != 1) {
                    throw new InvalidEventException("A Poké Ball is thrown at exactly one Pokémon");
                }
                Combatant target = b.combatant(action.targets().get(0));
                if (target == null || !b.standing().contains(target) || b.sideOf(target) == owner.side()) {
                    throw new InvalidEventException("A Poké Ball can only be thrown at a Pokémon of the other side that is on the field");
                }
                if (b.ownerOf(target).kind() == Kind.TRAINER) {
                    throw new InvalidEventException("A Poké Ball cannot be thrown at another player's Pokémon");
                }
                if (target.boss()) {
                    throw new InvalidEventException("A boss cannot be caught");
                }
            }
            case FLEE -> {
                if (owner.kind() != Kind.TRAINER) {
                    throw new InvalidEventException("Only a trainer runs away");
                }
            }
            case SKIP -> {
            }
        }
    }

    /**
     * True if an enemy (a wild Pokémon or an NPC's Pokémon, never a player's) may aim a move that chooses one foe at this trainer
     * (chapter 7.12): the trainer is on the other side, has not left the battle and is not at 0 HP.
     */
    static boolean legalTrainerTarget(GameState state, Battle b, Combatant actor, TargetKind kind, String target) {
        if (!Battle.isTrainerTarget(target) || kind != TargetKind.FOE || b.ownerOf(actor).kind() == Kind.TRAINER) {
            return false;
        }
        Participant p = b.participants().get(Battle.trainerTargetId(target));
        if (p == null || p.kind() != Kind.TRAINER || p.side() == b.sideOf(actor) || b.left().containsKey(p.id()) || p.trainerId() == null) {
            return false;
        }
        Trainer trainer = state.trainers().get(p.trainerId());
        return trainer != null && trainer.hp() > 0;
    }

    /** The move of an action: one the Pokémon knows, or Struggle. */
    private static MoveSlot moveOf(Combatant actor, Action action) {
        return moveOf(actor, action, actor.name(), null);
    }

    private static MoveSlot moveOf(Combatant actor, Action action, String who, BattleSettings rules) {
        String id = action.moveId();
        if (id == null || id.isBlank()) {
            throw new InvalidEventException("A move is missing");
        }
        if (MoveSlot.STRUGGLE.equals(id)) {
            if (rules != null && (!rules.ppEnabled() || !actor.outOfPp())) {
                throw new InvalidEventException(who + " can only use Struggle when no move has PP left");
            }
            return MoveSlot.struggle();
        }
        MoveSlot slot = actor.moves().stream().filter(m -> m.moveId().equals(id)).findFirst()
                .orElseThrow(() -> new InvalidEventException(who + " does not know " + id));
        if (rules != null && rules.ppEnabled() && slot.pp() <= 0 && !isLocked(actor, id)) {
            throw new InvalidEventException(slot.name() + " of " + who + " has no PP left");
        }
        return slot;
    }

    /** The move a Pokémon is locked into (Rollout, Uproar), or null. A locked move spends no PP again. */
    static String lockedMove(Combatant c) {
        if (c.condition().has(Battle.Volatiles.ROLLOUT)) {
            return "rollout";
        }
        if (c.condition().has(Battle.Volatiles.UPROAR)) {
            return "uproar";
        }
        return null;
    }

    private static boolean isLocked(Combatant c, String moveId) {
        return moveId != null && moveId.equals(lockedMove(c));
    }

    private static void requireBag(GameState state, Participant owner, String itemId) {
        if (owner.trainerId() == null) {
            return; // an NPC has no bag in the game state
        }
        Trainer trainer = state.trainers().get(owner.trainerId());
        if (trainer == null || trainer.items().getOrDefault(itemId, 0) < 1) {
            throw new InvalidEventException(owner.name() + " has no " + pretty(itemId));
        }
    }

    /** True when the action cannot reach anybody any more: "a move whose only target has fainted fails" (chapter 7.3). */
    static boolean noTarget(Battle b, Combatant actor, Action action) {
        if (action.kind() == ActionKind.MOVE) {
            TargetKind kind = moveOf(actor, action).targetKind();
            if (kind == TargetKind.SELF || kind == TargetKind.SIDE) {
                return false;
            }
            if (kind.choosesOne()) {
                String target = action.targets().get(0);
                if (Battle.isTrainerTarget(target)) {
                    Participant p = b.participants().get(Battle.trainerTargetId(target));
                    return p == null || b.out(p);
                }
                return !standing(b, b.resolveTarget(target));
            }
            return b.groupTargets(actor, kind).isEmpty();
        }
        if (action.kind() == ActionKind.BALL) {
            return !standing(b, b.resolveTarget(action.targets().get(0)));
        }
        return false;
    }

    /** True if the Pokémon is still on the field and has not fainted. */
    private static boolean standing(Battle b, String id) {
        Combatant c = b.combatant(id);
        return c != null && b.isActive(c) && !c.fainted();
    }

    // ------------------------------------------------------------------------------------------------------------------ text

    private static String moveLine(Battle b, Combatant actor, Action action, MoveSlot slot) {
        String prefix = MoveSlot.STRUGGLE.equals(slot.moveId()) ? b.label(actor) + " has no moves left and uses Struggle" : b.label(actor) + " uses " + slot.name();
        TargetKind kind = slot.targetKind();
        if (kind.choosesOne()) {
            String target = action.targets().get(0);
            if (Battle.isTrainerTarget(target)) {
                return prefix + " on " + b.participants().get(Battle.trainerTargetId(target)).name() + ".";
            }
            return prefix + " on " + b.label(b.combatant(b.resolveTarget(target))) + ".";
        }
        List<String> group = b.groupTargets(actor, kind);
        return group.isEmpty() ? prefix + "." : prefix + " on " + andList(group.stream().map(id -> b.label(b.combatant(id))).toList()) + ".";
    }

    private static String failedLine(Battle b, Combatant actor, Action action) {
        if (action.kind() == ActionKind.BALL) {
            return b.ownerOf(actor).name() + " throws a " + pretty(action.itemId()) + ", but " + b.label(b.combatant(action.targets().get(0)))
                    + " is no longer there.";
        }
        MoveSlot slot = moveOf(actor, action);
        return b.label(actor) + " uses " + slot.name() + ", but it has no target left.";
    }

    /** {@code Ana}, or {@code wild Rattata}. */
    static String name(Participant p) {
        return p.kind() == Kind.WILD ? "wild " + p.name() : p.name();
    }

    /** {@code potion} → {@code Potion}, {@code great_ball} → {@code Great Ball}. */
    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String word : id.split("[_\\-\\s]+")) {
            if (!word.isEmpty()) {
                sb.append(sb.length() == 0 ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    private static String andList(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    // -------------------------------------------------------------------------------------------------------------- helpers

    private static Battle running(GameState state, String battleId) {
        Battle b = state.battle();
        if (b == null || !b.battleId().equals(battleId)) {
            throw new InvalidEventException("There is no battle " + battleId);
        }
        if (b.step() == Step.ENDED) {
            throw new InvalidEventException("The battle " + battleId + " has ended");
        }
        return b;
    }

    private static void requireStep(Battle b, Step step, int round) {
        if (b.step() != step) {
            throw new InvalidEventException("This cannot happen now: the battle is at " + b.step().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + ", not at " + step.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        if (round != b.round()) {
            throw new InvalidEventException("The battle is in round " + b.round() + ", not " + round);
        }
    }

    private static boolean ableReserve(Battle b, Participant p) {
        return p.reserve().stream().anyMatch(id -> !b.combatant(id).fainted());
    }

    private static Battle replaceCombatant(Battle b, Combatant c) {
        Map<String, Combatant> combatants = new LinkedHashMap<>(b.combatants());
        combatants.put(c.id(), c);
        return b.withTeams(b.participants(), combatants);
    }

    /** The Pokémon {@code out} (on the field) and {@code in} (in the reserve) trade places, slot for slot. */
    private static Battle swap(Battle b, Participant p, String out, String in) {
        List<String> active = new ArrayList<>(p.active());
        List<String> reserve = new ArrayList<>(p.reserve());
        active.set(active.indexOf(out), in);
        reserve.set(reserve.indexOf(in), out);
        Map<String, Participant> participants = new LinkedHashMap<>(b.participants());
        participants.put(p.id(), p.withTeam(active, reserve));
        Map<String, Combatant> combatants = new LinkedHashMap<>(b.combatants());
        Combatant leaving = combatants.get(out);
        combatants.put(out, leaving.withCondition(leaving.condition().afterSwitchOut()));
        Battle swapped = b.withTeams(participants, combatants);
        if (leaving.fainted()) {
            return swapped.withMeetings();
        }
        Map<String, String> replaced = new LinkedHashMap<>(b.replacedBy());
        replaced.put(out, in);
        return swapped.withReplaced(replaced).withMeetings();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("The " + what + " is empty");
        }
    }
}
