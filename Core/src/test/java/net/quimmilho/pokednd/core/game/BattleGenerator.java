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
        if (b != null && b.step() == Step.ENDED && !b.settled() && random.nextInt(3) == 0) {
            return new GameEvent.BattleSettled(b.battleId());
        }
        if (b == null || b.step() == Step.ENDED) {
            return start(random, state);
        }
        // sometimes something happens to a Pokémon, whatever the step
        if (random.nextInt(8) == 0) {
            return tweak(random, state, b);
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
        if (!b.mustSwitch().isEmpty()) {
            return new GameEvent.BattleEnded(b.battleId(), "nobody to send in"); // a Pokémon that must be replaced and has no replacement: the battle cannot go on
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

    private static GameEvent tweak(Random random, GameState state, Battle b) {
        List<Combatant> all = new ArrayList<>(b.combatants().values());
        Combatant c = all.get(random.nextInt(all.size()));
        String id = b.battleId();
        return switch (random.nextInt(19)) {
            case 14 -> ball(random, b);
            case 15 -> leave(random, b);
            case 16 -> fleeFailed(random, b);
            case 17 -> trainerHp(random, state, b);
            case 18 -> knockout(random, state, b);
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

    // ------------------------------------------------------------------------------------------ phase 49: Pokémon and prompts

    /** One event about the Pokémon or the prompts of the game, valid in any state. */
    static GameEvent pokemonEvent(Random random, GameState state) {
        List<String> trainers = new ArrayList<>(state.trainers().keySet());
        String owner = trainers.isEmpty() || random.nextBoolean() ? null : trainers.get(random.nextInt(trainers.size()));
        return switch (random.nextInt(5)) {
            case 0, 1 -> new GameEvent.PokemonSet(PokemonSheet.of("pk" + random.nextInt(6), owner, "bulbasaur", 1 + random.nextInt(60), random.nextInt(5000),
                    random.nextInt(60), List.of(new MoveSlot("tackle", "Tackle", 10, 35, 0, "normal"))));
            case 2 -> {
                if (state.pokemon().isEmpty()) {
                    yield new GameEvent.PokemonSet(PokemonSheet.of("pk" + random.nextInt(6), owner, "bulbasaur", 5, 0, 20, List.of()));
                }
                List<String> ids = new ArrayList<>(state.pokemon().keySet());
                yield new GameEvent.PokemonRemoved(ids.get(random.nextInt(ids.size())));
            }
            case 3 -> openPrompt(random, state, owner);
            default -> {
                if (state.prompts().isEmpty()) {
                    yield openPrompt(random, state, owner);
                }
                List<String> ids = new ArrayList<>(state.prompts().keySet());
                yield new GameEvent.PromptAnswered(ids.get(random.nextInt(ids.size())), random.nextBoolean() ? "yes" : "no");
            }
        };
    }

    // ------------------------------------------------------------------------------------ phase 50: shops, trades and notes

    /** One event about the shops, the trades or the log of the game, valid in any state. */
    static GameEvent economyEvent(Random random, GameState state) {
        List<String> trainers = new ArrayList<>(state.trainers().keySet());
        if (!state.trades().isEmpty() && random.nextInt(3) == 0) {
            return new GameEvent.TradeClosed(new ArrayList<>(state.trades().keySet()).get(random.nextInt(state.trades().size())), "declined");
        }
        switch (random.nextInt(5)) {
            case 0 -> {
                return new GameEvent.GameNoted(trainers.isEmpty() || random.nextBoolean() ? null : trainers.get(random.nextInt(trainers.size())), "A line for the log.");
            }
            case 1 -> {
                if (!state.merchants().isEmpty() && random.nextBoolean()) {
                    return new GameEvent.MerchantRemoved(new ArrayList<>(state.merchants().keySet()).get(random.nextInt(state.merchants().size())));
                }
            }
            case 2 -> {
                if (!state.trades().isEmpty() && random.nextBoolean()) {
                    return new GameEvent.TradeClosed(new ArrayList<>(state.trades().keySet()).get(random.nextInt(state.trades().size())), "declined");
                }
            }
            case 3 -> {
                if (trainers.size() >= 2) {
                    int a = random.nextInt(trainers.size());
                    int b = (a + 1 + random.nextInt(trainers.size() - 1)) % trainers.size();
                    return new GameEvent.TradeSet(new Trade("tr" + random.nextInt(4), random.nextBoolean() ? "centre" : null, trainers.get(a), trainers.get(b),
                            new Trade.Side(List.of("pk" + random.nextInt(6)), java.util.Map.of("potion", 1 + random.nextInt(3)), random.nextInt(500)),
                            Trade.Side.nothing(), random.nextBoolean() ? Trade.Status.PROPOSED : Trade.Status.CONFIRMED));
                }
            }
            default -> {
            }
        }
        List<Merchant.StockLine> stock = new ArrayList<>();
        stock.add(Merchant.StockLine.of("potion", random.nextInt(9)));
        if (random.nextBoolean()) {
            stock.add(Merchant.StockLine.unlimited("poke_ball").withPrice(random.nextBoolean() ? null : 100 + random.nextInt(900)));
        }
        return new GameEvent.MerchantSet(new Merchant("m" + random.nextInt(3), "Mart " + random.nextInt(3), random.nextBoolean(), random.nextBoolean(),
                0.5 + random.nextInt(4) / 2.0, random.nextInt(12), state.round(), stock));
    }

    private static GameEvent openPrompt(Random random, GameState state, String owner) {
        String pokemon = state.pokemon().isEmpty() || random.nextBoolean() ? null : new ArrayList<>(state.pokemon().keySet()).get(random.nextInt(state.pokemon().size()));
        String id;
        do {
            id = "pr" + random.nextInt(1000);
        } while (state.prompts().containsKey(id)); // a prompt id is new
        return new GameEvent.PromptOpened(new Prompt(id, Prompt.Kind.values()[random.nextInt(4)], owner, pokemon, "ivysaur", "A question."));
    }

    // ---------------------------------------------------------------------------------------------- phase 48: catching, leaving, trainers

    /** A ball at a wild Pokémon that can be caught (or a note when there is none). */
    private static GameEvent ball(Random random, Battle b) {
        List<Participant> throwers = b.participants().values().stream().filter(p -> p.kind() == Kind.TRAINER && !b.left().containsKey(p.id())).toList();
        if (throwers.isEmpty()) {
            return new GameEvent.BattleNoted(b.battleId(), "Nobody throws.");
        }
        Participant thrower = throwers.get(random.nextInt(throwers.size()));
        List<Combatant> targets = b.standing().stream()
                .filter(c -> b.ownerOf(c).kind() != Kind.TRAINER && !c.boss() && b.sideOf(c) != thrower.side()).toList();
        if (targets.isEmpty()) {
            return new GameEvent.BattleNoted(b.battleId(), "Nothing to catch.");
        }
        boolean caught = random.nextInt(3) == 0;
        return new GameEvent.BallThrown(b.battleId(), targets.get(random.nextInt(targets.size())).id(), thrower.id(), "poke_ball",
                caught ? 3 : random.nextInt(3), caught);
    }

    /** A participant leaves, while at least one other stays. */
    private static GameEvent leave(Random random, Battle b) {
        List<Participant> staying = b.participants().values().stream().filter(p -> !b.left().containsKey(p.id())).toList();
        if (staying.size() < 2) {
            return new GameEvent.BattleNoted(b.battleId(), "Everybody stays.");
        }
        return new GameEvent.ParticipantLeft(b.battleId(), staying.get(random.nextInt(staying.size())).id(),
                new String[] {"fled", "knockedOut", "surrendered"}[random.nextInt(3)]);
    }

    private static GameEvent fleeFailed(Random random, Battle b) {
        List<Participant> trainers = b.participants().values().stream().filter(p -> p.kind() == Kind.TRAINER && !b.left().containsKey(p.id())).toList();
        return trainers.isEmpty() ? new GameEvent.BattleNoted(b.battleId(), "Nobody runs.")
                : new GameEvent.FleeFailed(b.battleId(), trainers.get(random.nextInt(trainers.size())).id());
    }

    /** A trainer of the battle loses or gains HP. */
    private static GameEvent trainerHp(Random random, GameState state, Battle b) {
        String id = trainerId(random, state, b);
        if (id == null) {
            return new GameEvent.BattleNoted(b.battleId(), "Nobody is hurt.");
        }
        Trainer trainer = state.trainers().get(id);
        if (trainer.hp() == 0 || random.nextInt(3) == 0) {
            return new GameEvent.TrainerHpChanged(id, 1 + random.nextInt(5), "from a test");
        }
        return new GameEvent.TrainerHpChanged(id, -1 - random.nextInt(trainer.hp()), "from a test");
    }

    /** What happens to a trainer at 0 HP is set or ended. */
    private static GameEvent knockout(Random random, GameState state, Battle b) {
        String id = trainerId(random, state, b);
        if (id == null) {
            return new GameEvent.BattleNoted(b.battleId(), "Nobody falls.");
        }
        if (state.knockouts().containsKey(id) && random.nextBoolean()) {
            return new GameEvent.KnockoutSet(id, null);
        }
        net.quimmilho.pokednd.core.trainer.KoMode mode = net.quimmilho.pokednd.core.trainer.KoMode.values()[random.nextInt(3)];
        return new GameEvent.KnockoutSet(id, new GameState.Knockout(mode, net.quimmilho.pokednd.core.trainer.KnockOut.DeathSaves.start()));
    }

    private static String trainerId(Random random, GameState state, Battle b) {
        List<String> ids = b.participants().values().stream().filter(p -> p.kind() == Kind.TRAINER && state.trainers().containsKey(p.trainerId()))
                .map(Participant::trainerId).toList();
        return ids.isEmpty() ? null : ids.get(random.nextInt(ids.size()));
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
