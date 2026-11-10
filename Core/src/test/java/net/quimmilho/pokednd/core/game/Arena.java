/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.GameEvent.StatusChanged;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerSet;
import net.quimmilho.pokednd.core.game.GameState.RollRequest;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.game.RollSettings.SystemRolls;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * A small stage for the battle tests of phases 45 and 46: a game with trainers, a battle, and the calls a server would make, all through
 * {@link GameLog} (so every event also goes through the reducer, the log, the audit trail and the JSON of the stored events). The dice
 * are typed by the admin: a test says which number every roll shows, and a roll nobody answered fails the test with the text of the
 * request, so a test shows exactly which rolls a move asks for.
 */
final class Arena {

    static final Actor ADMIN = Actor.admin("u-admin");
    static final long NOW = 1_000_000;

    /** The numbers the dice show. A key is the end of a roll request's id ({@code ana-1-hit0}); a value is a number, the faces of several dice, or a band name. */
    static final class Answers {
        private final Map<String, Object> values = new LinkedHashMap<>();

        static Answers none() {
            return new Answers();
        }

        static Answers of(Object... pairs) {
            Answers a = new Answers();
            for (int i = 0; i < pairs.length; i += 2) {
                a.values.put((String) pairs[i], pairs[i + 1]);
            }
            return a;
        }

        Answers with(String key, Object value) {
            values.put(key, value);
            return this;
        }
    }

    final GameLog log = new GameLog(GameLog.Settings.defaults());
    final RollSettings rolls;
    final LongSupplier seeds = DigitalRoller.seeds(RollSettings.RngSource.SEEDED, 7);
    final MoveBook book;
    /** Every roll that was asked, in the order it was asked: {@code id → text of the prompt}. */
    final Map<String, RollRequest> asked = new LinkedHashMap<>();

    Arena(MoveBook book, String... trainers) {
        this(book, RollSettings.defaults().withSystemRolls(SystemRolls.ADMIN_PHYSICAL, SystemRolls.AUTO), trainers);
    }

    Arena(MoveBook book, RollSettings rolls, String... trainers) {
        this.book = book;
        this.rolls = rolls;
        List<GameEvent> setup = new ArrayList<>();
        setup.add(new StatusChanged(GameStatus.RUNNING));
        for (String id : trainers) {
            setup.add(new TrainerSet(id, Fixtures.trainer(id.substring(0, 1).toUpperCase() + id.substring(1))));
        }
        log.append("system", 1, setup);
    }

    // ------------------------------------------------------------------------------------------------------------------ building

    Combatant mon(String id, String owner, String name, int level, int hp, List<PokemonType> types, int atk, int def, int spa, int spd, int spe,
                  String... moves) {
        List<MoveSlot> slots = new ArrayList<>();
        for (String m : moves) {
            slots.add(book.slot(m).orElseGet(() -> new MoveSlot(m, BattleReducer.pretty(m), 10, 10, 0, "normal")));
        }
        return new Combatant(id, owner, name, level, hp, hp, spe, false, slots, Battle.Build.of(types, atk, def, spa, spd, spe), null);
    }

    static Participant trainer(String id, List<String> active, List<String> reserve) {
        return new Participant(id, Kind.TRAINER, 0, id.substring(0, 1).toUpperCase() + id.substring(1), id, "u-" + id, active, reserve);
    }

    static Participant wild(String id, String name, String... mons) {
        return new Participant(id, Kind.WILD, 1, name, null, null, List.of(mons), List.of());
    }

    static Participant wildWithReserve(String id, String name, List<String> active, List<String> reserve) {
        return new Participant(id, Kind.WILD, 1, name, null, null, active, reserve);
    }

    Arena start(Battle.Type type, BattleSettings rules, List<Participant> participants, List<Combatant> combatants) {
        play(BattleDesk.start(log.state(), ADMIN, "b1", type, rules, participants, combatants));
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------- reading

    GameState state() {
        return log.state();
    }

    Battle battle() {
        return log.state().battle();
    }

    Combatant mon(String id) {
        return battle().combatant(id);
    }

    List<String> lines() {
        return battle().log();
    }

    /** True if some line of the battle log contains this text. */
    boolean said(String text) {
        return lines().stream().anyMatch(l -> l.contains(text));
    }

    String lastLine() {
        return lines().get(lines().size() - 1);
    }

    /** The lines of the log after the first {@code from} lines. */
    List<String> linesFrom(int from) {
        return lines().subList(from, lines().size());
    }

    // --------------------------------------------------------------------------------------------------------------------- doing

    void play(List<GameEvent> events) {
        log.append(ADMIN.userId(), NOW, events);
    }

    /** Opens the next round and answers the initiative rolls (participant id → the d20) and fixes the order. */
    Arena round(Map<String, Integer> initiative) {
        play(BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        if (battle().step() == Battle.Step.INITIATIVE) {
            for (Map.Entry<String, Integer> e : initiative.entrySet()) {
                if (!log.state().pendingRolls().containsKey("b1-r" + battle().round() + "-init-" + e.getKey())) {
                    continue; // the system has rolled it already
                }
                play(RollDesk.enter(log.state(), ADMIN, "b1-r" + battle().round() + "-init-" + e.getKey(), List.of(e.getValue()), null));
            }
            assertTrue(BattleDesk.initiativeReady(log.state()), "every initiative roll is answered");
            play(BattleDesk.setOrder(log.state(), NOW, seeds));
        }
        return this;
    }

    Arena declare(String actorId, Action action) {
        play(BattleDesk.declare(log.state(), ADMIN, actorId, action, NOW));
        return this;
    }

    /** Starts resolving and plays every action, answering the dice from {@code answers}. Stops early when a Pokémon has to be replaced. */
    Arena resolve(Answers answers) {
        play(BattleDesk.startResolving(log.state()));
        return keepResolving(answers);
    }

    Arena keepResolving(Answers answers) {
        int guard = 0;
        while (!battle().queue().isEmpty() && battle().mustSwitch().isEmpty()) {
            if (++guard > 300) {
                fail("the battle does not move on: " + lastLine());
            }
            List<GameEvent> events = BattleDesk.resolveNext(log.state(), book, rolls, NOW, seeds);
            play(events);
            List<RollRequest> open = BattleDesk.awaiting(log.state());
            for (RollRequest r : open) {
                answer(r, answers);
            }
            if (events.isEmpty() && open.isEmpty()) {
                fail("nothing happened and nothing is asked: " + lastLine());
            }
        }
        return this;
    }

    /** One call of {@code resolveNext}; the roll requests it makes are answered from {@code answers}. */
    List<GameEvent> resolveOnce(Answers answers) {
        List<GameEvent> events = BattleDesk.resolveNext(log.state(), book, rolls, NOW, seeds);
        play(events);
        for (RollRequest r : BattleDesk.awaiting(log.state())) {
            answer(r, answers);
        }
        return events;
    }

    private void answer(RollRequest r, Answers answers) {
        asked.put(r.requestId(), r);
        Object value = null;
        for (Map.Entry<String, Object> e : answers.values.entrySet()) {
            if (r.requestId().endsWith("-" + e.getKey())) {
                value = e.getValue();
            }
        }
        if (value == null) {
            fail("no answer for the roll " + r.requestId() + ": " + r.explanation());
        }
        List<Integer> faces = null;
        Integer total = null;
        if (value instanceof String band) {
            GameState.Band found = r.bands().stream().filter(x -> x.label().equals(band)).findFirst()
                    .orElseThrow(() -> new AssertionError("the roll " + r.requestId() + " has no band \"" + band + "\": " + r.explanation()));
            faces = List.of(found.from());
        } else if (value instanceof int[] many) {
            faces = new ArrayList<>();
            for (int v : many) {
                faces.add(v);
            }
        } else if (r.dice().count() == 1) {
            faces = List.of((Integer) value);
        } else {
            total = (Integer) value;
            faces = List.of();
        }
        play(RollDesk.enter(log.state(), ADMIN, r.requestId(), faces, total));
    }

    /** The end of the round (burn and poison, timers). */
    Arena endRound() {
        play(BattleDesk.endRound(log.state()));
        return this;
    }

    /** Everybody on the field chooses a move; the dice are typed from {@code answers}. A whole round in one call. */
    Arena fullRound(Map<String, Integer> initiative, Map<String, Action> actions, Answers answers) {
        round(initiative);
        actions.forEach(this::declare);
        resolve(answers);
        if (battle().mustSwitch().isEmpty()) {
            endRound();
        }
        return this;
    }
}
