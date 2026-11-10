/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.InitiativeSlot;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Result;
import net.quimmilho.pokednd.core.game.Battle.Step;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * Makes random battle events for the property tests (phase 44). It looks at the state and only returns an event that can happen
 * there (or null when the state does not allow a battle event right now), so a test fails if the generator and the reducer disagree.
 * It walks a battle through its life: start, rounds (initiative, declare, resolve, end), faints, replacements and the end.
 */
final class BattleGenerator {

    private static final String[] TARGETS = {"normal", "self", "allAdjacentFoes", "adjacentAlly", "allySide", "all"};

    private BattleGenerator() {
    }

    static GameEvent next(Random random, GameState state) {
        Battle b = state.battle();
        if (b == null || b.step() == Step.ENDED) {
            return start(random, state);
        }
        // sometimes something happens to a Pokémon, whatever the step
        if (random.nextInt(8) == 0) {
            return tweak(random, b);
        }
        if (random.nextInt(40) == 0) {
            return new GameEvent.BattleEnded(b.battleId(), random.nextBoolean() ? "enough" : null);
        }
        for (Participant p : b.participants().values()) {
            for (String id : p.active()) {
                Combatant c = b.combatant(id);
                if (c.fainted() || b.mustSwitch().contains(id)) {
                    String replacement = p.reserve().stream().filter(r -> !b.combatant(r).fainted()).findFirst().orElse(null);
                    if (replacement != null) {
                        return new GameEvent.ReplacementSent(b.battleId(), p.id(), id, replacement);
                    }
                }
            }
        }
        return switch (b.step()) {
            case START, ROUND_END -> openRound(random, state, b);
            case INITIATIVE -> {
                boolean ready = b.slots().stream().allMatch(s -> state.rolls().stream().anyMatch(r -> r.requestId().equals(s.requestId())));
                if (!ready) {
                    yield new GameEvent.BattleEnded(b.battleId(), "nobody answered");
                }
                Map<String, Integer> tieBreaks = new LinkedHashMap<>();
                b.slots().forEach(s -> tieBreaks.put(s.participantId(), random.nextInt(1000)));
                yield new GameEvent.InitiativeSet(b.battleId(), b.round(), tieBreaks, random.nextBoolean() ? 0 : 60_000);
            }
            case DECLARE -> declare(random, state, b);
            case RESOLVE -> resolve(random, b);
            case ENDED -> null;
        };
    }

    private static GameEvent start(Random random, GameState state) {
        List<String> trainers = new ArrayList<>(state.trainers().keySet());
        if (trainers.isEmpty()) {
            return null;
        }
        String trainerId = trainers.get(random.nextInt(trainers.size()));
        List<Combatant> mons = new ArrayList<>();
        mons.add(mon("m1", "p1", 20 + random.nextInt(80), random));
        mons.add(mon("m2", "p1", 20 + random.nextInt(80), random));
        mons.add(mon("m3", "p2", 20 + random.nextInt(80), random));
        List<Participant> ps = new ArrayList<>();
        ps.add(new Participant("p1", Kind.TRAINER, 0, "Trainer", trainerId, "user-ana", List.of("m1"), List.of("m2")));
        ps.add(new Participant("p2", random.nextBoolean() ? Kind.WILD : Kind.NPC, 1, "Foe", null, null, List.of("m3"), List.of()));
        BattleSettings rules = BattleSettings.defaults().withRerollEachRound(random.nextInt(3) != 0).withTrainerActionsFirst(random.nextInt(4) != 0)
                .withPpEnabled(random.nextInt(4) != 0);
        return new GameEvent.BattleStarted("b" + random.nextInt(1_000_000), Battle.Type.values()[random.nextInt(4)], rules, ps, mons);
    }

    private static Combatant mon(String id, String owner, int speed, Random random) {
        List<MoveSlot> moves = new ArrayList<>();
        for (int i = 0; i < 1 + random.nextInt(3); i++) {
            int pp = random.nextInt(4);
            moves.add(new MoveSlot("move" + i, "Move " + i, pp, 5, random.nextInt(4) - 1, TARGETS[random.nextInt(TARGETS.length)]));
        }
        int hp = 5 + random.nextInt(40);
        // the Pokémon on the field start healthy; the one in the reserve may already have fainted
        boolean down = id.equals("m2") && random.nextInt(4) == 0;
        return new Combatant(id, owner, "Mon " + id, 5, down ? 0 : hp, hp, speed, random.nextInt(6) == 0, moves);
    }

    private static GameEvent tweak(Random random, Battle b) {
        List<Combatant> all = new ArrayList<>(b.combatants().values());
        Combatant c = all.get(random.nextInt(all.size()));
        String id = b.battleId();
        return switch (random.nextInt(14)) {
            case 0 -> new GameEvent.CombatantSet(id, c.withHp(0));
            case 1 -> new GameEvent.CombatantSet(id, c.withHp(1 + random.nextInt(c.maxHp())));
            case 2 -> new GameEvent.CombatantSet(id, c.withSpeed(random.nextInt(120)));
            case 3 -> c.fainted() ? new GameEvent.BattleNoted(id, "A pause.") : new GameEvent.HpChanged(id, c.id(), random.nextBoolean() ? -1 - random.nextInt(c.maxHp()) : 1 + random.nextInt(5), "from a test");
            case 4 -> {
                if (c.fainted()) {
                    yield new GameEvent.BattleNoted(id, "Nothing happens.");
                }
                if (c.status() == Battle.Status.NONE) {
                    Battle.Status status = Battle.Status.values()[1 + random.nextInt(6)];
                    yield new GameEvent.StatusSet(id, c.id(), status, status == Battle.Status.SLEEP ? 1 + random.nextInt(3) : 0, null);
                }
                yield new GameEvent.StatusSet(id, c.id(), Battle.Status.NONE, 0, null);
            }
            case 5 -> c.fainted() ? new GameEvent.BattleNoted(id, "Calm.") : new GameEvent.StageChanged(id, c.id(), Battle.STAGE_IDS.get(random.nextInt(7)), random.nextBoolean() ? 1 + random.nextInt(3) : -1 - random.nextInt(3), null);
            case 6 -> new GameEvent.VolatileSet(id, c.id(), random.nextBoolean() ? "flinch" : "confusion", random.nextInt(3), random.nextBoolean() ? "A condition changes." : null);
            case 7 -> new GameEvent.HeldItemSet(id, c.id(), random.nextBoolean() ? "oran_berry" : null, null);
            case 8 -> new GameEvent.BattleNoted(id, "It's a note " + random.nextInt(100) + ".");
            case 9 -> {
                Participant p = b.ownerOf(c);
                boolean able = p.reserve().stream().anyMatch(r -> !b.combatant(r).fainted());
                if (b.isActive(c) && !b.mustSwitch().contains(c.id()) && able) {
                    yield new GameEvent.SwitchRequired(id, c.id());
                }
                yield new GameEvent.BattleNoted(id, "Nobody switches.");
            }
            case 10, 11 -> b.tasks().isEmpty()
                    ? new GameEvent.AdminTaskOpened(id, "t" + random.nextInt(1_000_000), c.id(), "move0", "Apply the effect of Move 0.")
                    : new GameEvent.AdminTaskClosed(id, b.tasks().get(random.nextInt(b.tasks().size())).id());
            default -> new GameEvent.CombatantSet(id, c.withHp(c.maxHp()));
        };
    }

    private static GameEvent openRound(Random random, GameState state, Battle b) {
        if (!b.rules().rerollEachRound() && !b.order().isEmpty()) {
            return new GameEvent.RoundOpened(b.battleId(), b.round() + 1, List.of(), random.nextBoolean() ? 0 : 30_000);
        }
        List<String> answered = state.rolls().stream().map(GameState.RollResult::requestId).toList();
        if (answered.isEmpty()) {
            return null;
        }
        List<InitiativeSlot> slots = new ArrayList<>();
        for (Participant p : b.participants().values()) {
            if (!b.out(p)) {
                slots.add(new InitiativeSlot(p.id(), answered.get(random.nextInt(answered.size())), random.nextInt(6), random.nextInt(120)));
            }
        }
        return new GameEvent.RoundOpened(b.battleId(), b.round() + 1, slots, 0);
    }

    private static GameEvent declare(Random random, GameState state, Battle b) {
        for (Combatant c : b.standing()) {
            if (b.declared().containsKey(c.id()) && random.nextInt(6) != 0) {
                continue;
            }
            return new GameEvent.ActionDeclared(b.battleId(), b.round(), c.id(), action(random, state, b, c));
        }
        return new GameEvent.ResolveStarted(b.battleId(), b.round());
    }

    private static Action action(Random random, GameState state, Battle b, Combatant c) {
        Participant owner = b.ownerOf(c);
        List<Action> options = new ArrayList<>();
        options.add(Action.skip());
        if (owner.kind() == Kind.TRAINER) {
            options.add(Action.flee());
        }
        owner.reserve().stream().filter(id -> !b.combatant(id).fainted()).forEach(id -> options.add(Action.switchTo(id)));
        Trainer trainer = owner.trainerId() == null ? null : state.trainers().get(owner.trainerId());
        if (owner.trainerId() == null || (trainer != null && trainer.items().getOrDefault("potion", 0) > 0)) {
            if (owner.kind() != Kind.WILD) {
                options.add(Action.item("potion", c.id()));
            }
        }
        for (MoveSlot m : c.moves()) {
            if (b.rules().ppEnabled() && m.pp() <= 0) {
                continue;
            }
            TargetKind kind = m.targetKind();
            if (kind.choosesOne()) {
                List<String> legal = b.legalTargets(c, kind);
                if (!legal.isEmpty()) {
                    options.add(Action.move(m.moveId(), legal.get(random.nextInt(legal.size()))));
                }
            } else {
                options.add(Action.move(m.moveId()));
            }
        }
        if (b.rules().ppEnabled() && c.outOfPp()) {
            List<String> foes = b.legalTargets(c, TargetKind.FOE);
            if (!foes.isEmpty()) {
                options.add(Action.struggle(foes.get(0)));
            }
        }
        return options.get(random.nextInt(options.size()));
    }

    private static GameEvent resolve(Random random, Battle b) {
        if (b.queue().isEmpty()) {
            return new GameEvent.RoundEnded(b.battleId(), b.round());
        }
        if (b.queue().size() > 1 && random.nextInt(6) == 0) {
            List<String> shuffled = new ArrayList<>(b.queue());
            Collections.shuffle(shuffled, random);
            return new GameEvent.QueueReordered(b.battleId(), shuffled);
        }
        Combatant actor = b.combatant(b.queue().get(0));
        Action action = b.declared().get(actor.id());
        Result result;
        if (actor.fainted()) {
            result = Result.CANCELLED_FAINTED;
        } else if (!b.isActive(actor) || random.nextInt(10) == 0) {
            result = Result.CANCELLED_BY_ADMIN;
        } else if (BattleReducer.noTarget(b, actor, action)) {
            result = Result.FAILED_NO_TARGET;
        } else if (action.kind() == Battle.ActionKind.SWITCH) {
            Combatant in = b.combatant(action.switchTo());
            result = b.ownerOf(actor).reserve().contains(in.id()) && !in.fainted() ? Result.DONE : Result.CANCELLED_BY_ADMIN;
        } else {
            result = Result.DONE;
        }
        return new GameEvent.ActionResolved(b.battleId(), b.round(), actor.id(), result);
    }
}
