/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.game.GameEvent.RandomDrawn;
import net.quimmilho.pokednd.core.game.GameEvent.RollAnswered;
import net.quimmilho.pokednd.core.game.GameEvent.RollCancelled;
import net.quimmilho.pokednd.core.game.GameEvent.RollRequested;
import net.quimmilho.pokednd.core.game.GameEvent.RoundAdvanced;
import net.quimmilho.pokednd.core.game.GameEvent.StatusChanged;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerRemoved;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerSet;
import net.quimmilho.pokednd.core.trainer.Trainer;
import org.junit.jupiter.api.Test;

/**
 * Property tests (phase 42): the same rules hold for hundreds of generated games, each made from its own seed, so a failure can be
 * repeated by running that seed again. The generator only makes events that can happen, by trying them on the current state.
 */
class GameLogPropertyTest {

    private static final int GAMES = 400;
    private static final String[] ACTORS = {"user-dani", "user-ana", "system"};

    /** One random action: one to three events, each valid after the one before it. */
    static List<GameEvent> randomAction(Random random, GameState from) {
        List<GameEvent> events = new ArrayList<>();
        GameState state = from;
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            GameEvent event = randomEvent(random, state);
            state = GameReducer.apply(state, event); // valid by construction; this throws if the generator is wrong
            events.add(event);
        }
        return events;
    }

    static GameEvent randomEvent(Random random, GameState state) {
        List<String> trainers = new ArrayList<>(state.trainers().keySet());
        List<String> pending = new ArrayList<>(state.pendingRolls().keySet());
        for (int attempt = 0; ; attempt++) {
            switch (random.nextInt(16)) {
                case 0 -> {
                    if (state.status() != GameStatus.ENDED) {
                        GameStatus[] all = GameStatus.values();
                        return new StatusChanged(all[random.nextInt(all.length)]);
                    }
                }
                case 1 -> {
                    return new RoundAdvanced(state.round() + 1);
                }
                case 2 -> {
                    String id = random.nextInt(3) == 0 && !trainers.isEmpty() ? trainers.get(random.nextInt(trainers.size())) : "t" + random.nextInt(6);
                    Trainer base = Fixtures.trainer("Trainer " + id);
                    return new TrainerSet(id, Fixtures.trainerWithHp(base, random.nextInt(base.maxHp() + 1)));
                }
                case 3 -> {
                    if (!trainers.isEmpty() && random.nextInt(3) == 0) {
                        return new TrainerRemoved(trainers.get(random.nextInt(trainers.size())));
                    }
                }
                case 4, 5 -> {
                    if (!trainers.isEmpty()) {
                        Dice dice = new Dice(1 + random.nextInt(3), 2 + random.nextInt(99));
                        GameState.Roller[] rollers = GameState.Roller.values();
                        List<GameState.Band> bands = random.nextBoolean() ? List.of() : List.of(new GameState.Band("low", 1, dice.max() / 2), new GameState.Band("high", dice.max() / 2 + 1, dice.max()));
                        return new RollRequested("r" + random.nextInt(1_000_000), trainers.get(random.nextInt(trainers.size())), dice, "reason " + random.nextInt(10),
                                rollers[random.nextInt(rollers.length)], "user-" + random.nextInt(3), GameState.RollClass.values()[random.nextInt(3)], bands, random.nextBoolean() ? null : random.nextDouble(),
                                random.nextBoolean() ? List.of() : List.of(new RollModifier(RollModifier.Kind.POINTS, "bonus", random.nextInt(9))), "text", random.nextInt(3) == 0 ? 5_000_000L : 0L);
                    }
                }
                case 6 -> {
                    if (!pending.isEmpty()) {
                        return new Answer(random, state, pending).event();
                    }
                }
                case 7 -> {
                    if (!pending.isEmpty() && random.nextInt(3) == 0) {
                        return new RollCancelled(pending.get(random.nextInt(pending.size())));
                    }
                }
                case 8 -> {
                    // a player types a value for a request that asks a player and has none yet
                    List<String> open = pending.stream().filter(id -> state.pendingRolls().get(id).roller() == GameState.Roller.PLAYER
                            && state.pendingRolls().get(id).status() == GameState.RollStatus.REQUESTED).toList();
                    if (!open.isEmpty()) {
                        String id = open.get(random.nextInt(open.size()));
                        Dice dice = state.pendingRolls().get(id).dice();
                        if (random.nextInt(4) == 0) {
                            return new GameEvent.RollSubmitted(id, List.of(), dice.count() + random.nextInt(dice.max() - dice.min() + 1), "user-ana", random.nextInt(1000));
                        }
                        return new GameEvent.RollSubmitted(id, faces(random, dice), null, "user-ana", random.nextInt(1000));
                    }
                }
                case 9 -> {
                    List<String> typed = pending.stream().filter(id -> state.pendingRolls().get(id).status() == GameState.RollStatus.SUBMITTED).toList();
                    if (!typed.isEmpty()) {
                        return new GameEvent.RollRejected(typed.get(random.nextInt(typed.size())), "again");
                    }
                }
                case 10 -> {
                    if (!state.rolls().isEmpty()) {
                        GameState.RollResult r = state.rolls().get(random.nextInt(state.rolls().size()));
                        Dice dice = random.nextBoolean() ? null : new Dice(1 + random.nextInt(2), 2 + random.nextInt(30));
                        Dice use = dice == null ? r.dice() : dice;
                        if (random.nextBoolean()) {
                            long seed = random.nextLong();
                            return new GameEvent.RollOverridden(r.requestId(), dice, DigitalRoller.roll(use, seed), null, "digital", seed, "again");
                        }
                        return new GameEvent.RollOverridden(r.requestId(), dice, faces(random, use), null, "admin", null, "fix");
                    }
                }
                case 11 -> {
                    List<String> timed = pending.stream().filter(id -> state.pendingRolls().get(id).deadlineMillis() > 0).toList();
                    if (!timed.isEmpty()) {
                        return new GameEvent.RollExpired(timed.get(random.nextInt(timed.size())));
                    }
                }
                case 13, 14, 15 -> {
                    GameEvent battleEvent = BattleGenerator.next(random, state);
                    if (battleEvent != null) {
                        return battleEvent;
                    }
                }
                default -> {
                    int bound = 1 + random.nextInt(100);
                    return new RandomDrawn("purpose-" + random.nextInt(5), bound, random.nextInt(bound));
                }
            }
            if (attempt > 50) {
                return new RandomDrawn("fallback", 2, random.nextInt(2));
            }
            // the random branch did not apply to this state; try another
        }
    }

    /** An answer for one of the open requests. */
    private record Answer(Random random, GameState state, List<String> pending) {
        GameEvent event() {
            String id = pending.get(random.nextInt(pending.size()));
            GameState.RollRequest request = state.pendingRolls().get(id);
            Dice dice = request.dice();
            List<Integer> faces = faces(random, dice);
            String[] sources = {"digital", "typed", "admin"};
            String source = sources[random.nextInt(sources.length)];
            if (request.submission() != null && random.nextBoolean()) {
                // the admin approves what the player typed, with or without a change
                GameState.Submission typed = request.submission();
                if (random.nextBoolean()) {
                    return new RollAnswered(id, typed.faces(), "typed", typed.total(), "user-dani", false, null);
                }
                List<Integer> changed = faces(random, dice);
                boolean same = typed.faces().equals(changed) && typed.total() == null;
                return new RollAnswered(id, changed, "typed", null, "user-dani", !same, null);
            }
            if (source.equals("typed") && request.roller() != GameState.Roller.PLAYER) {
                source = "admin";
            }
            if (source.equals("typed") && request.submission() != null) {
                source = "admin";
            }
            if (source.equals("digital")) {
                long seed = random.nextLong(); // a digital roll shows what its seed gives
                return new RollAnswered(id, DigitalRoller.roll(dice, seed), source, null, null, false, seed);
            }
            return new RollAnswered(id, faces, source);
        }
    }

    static List<Integer> faces(Random random, Dice dice) {
        List<Integer> faces = new ArrayList<>();
        for (int i = 0; i < dice.count(); i++) {
            faces.add(1 + random.nextInt(dice.sides()));
        }
        return faces;
    }

    /** Everything one random game did, so that it can be checked afterwards. */
    private static final class Played {
        final GameLog log;
        final List<GameState> stateAfterEachAction = new ArrayList<>();
        final List<List<GameEvent>> actions = new ArrayList<>();

        Played(GameLog log) {
            this.log = log;
        }
    }

    /** Plays a random game: mostly new actions, sometimes undo and redo. After every step the log must agree with itself. */
    private static Played play(long seed, int steps) {
        Random random = new Random(seed);
        GameLog.Settings settings = new GameLog.Settings(1 + random.nextInt(30), 1 + random.nextInt(3));
        Played p = new Played(new GameLog(settings));
        long time = 1_000_000;
        for (int step = 0; step < steps; step++) {
            String actor = ACTORS[random.nextInt(ACTORS.length)];
            time += random.nextInt(5000);
            int choice = random.nextInt(10);
            if (choice < 6 || (p.log.undoableActions() == 0 && p.log.redoableActions() == 0)) {
                List<GameEvent> events = randomAction(random, p.log.state());
                p.log.append(actor, time, events);
                p.actions.add(events);
                p.stateAfterEachAction.add(p.log.state());
            } else if (choice < 8 && p.log.undoableActions() > 0) {
                p.log.undo(actor, time, 1 + random.nextInt(p.log.undoableActions()));
            } else if (p.log.redoableActions() > 0) {
                p.log.redo(actor, time, 1 + random.nextInt(p.log.redoableActions()));
            }
            assertEquals(p.log.replay(), p.log.state(), "seed " + seed + ", step " + step + ": the state must equal a replay from the start");
        }
        return p;
    }

    @Test
    void theGeneratedGamesUseEveryKindOfEvent() {
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (long seed = 0; seed < GAMES; seed++) {
            for (List<GameEvent> action : play(seed, 50).actions) {
                action.forEach(e -> seen.add(e.type()));
            }
        }
        assertEquals(new java.util.TreeSet<>(EventTypes.all()), seen, "a kind of event that is never generated is never property-tested");
    }

    @Test
    void replayingTheSameEventsAlwaysGivesTheSameState() {
        for (long seed = 0; seed < GAMES; seed++) {
            Random random = new Random(seed);
            List<GameEvent> events = new ArrayList<>();
            GameState state = GameState.empty();
            for (int i = 0; i < 60; i++) {
                for (GameEvent e : randomAction(random, state)) {
                    events.add(e);
                    state = GameReducer.apply(state, e);
                }
            }
            GameState first = GameReducer.applyAll(GameState.empty(), events);
            GameState second = GameReducer.applyAll(GameState.empty(), events);
            assertEquals(state, first, "seed " + seed);
            assertEquals(first, second, "seed " + seed);
            assertEquals(first.hashCode(), second.hashCode(), "seed " + seed);

            // the same events read back from their stored form give the same state again
            List<GameEvent> reread = new ArrayList<>();
            for (GameEvent e : events) {
                reread.add(EventTypes.read(e.type(), EventTypes.payload(e)));
            }
            assertEquals(events, reread, "seed " + seed + ": every event survives being stored");
            assertEquals(first, GameReducer.applyAll(GameState.empty(), reread), "seed " + seed);
        }
    }

    @Test
    void theSameActionsInTheSameLogGiveTheSameEntriesAuditAndSnapshots() {
        for (long seed = 0; seed < GAMES; seed++) {
            Played a = play(seed, 50);
            Played b = play(seed, 50);
            assertEquals(a.log.state(), b.log.state(), "seed " + seed);
            assertEquals(a.log.entries(), b.log.entries(), "seed " + seed);
            assertEquals(a.log.audit(), b.log.audit(), "seed " + seed);
            assertEquals(a.log.snapshots(), b.log.snapshots(), "seed " + seed);
        }
    }

    @Test
    void aLogLoadedFromItsStoredPartsHasTheSameState() {
        for (long seed = 0; seed < GAMES; seed++) {
            Played p = play(seed, 50);
            GameLog restored = GameLog.restore(p.log.settings(), p.log.entries(), p.log.snapshots(), p.log.audit());
            assertEquals(p.log.state(), restored.state(), "seed " + seed);
            assertEquals(p.log.undoableActions(), restored.undoableActions(), "seed " + seed);
            assertEquals(p.log.redoableActions(), restored.redoableActions(), "seed " + seed);
        }
    }

    @Test
    void undoingNActionsThenRedoingThemGivesBackTheOriginalState() {
        for (long seed = 0; seed < GAMES; seed++) {
            Random random = new Random(seed * 31 + 7);
            GameLog log = new GameLog(new GameLog.Settings(25, 1 + random.nextInt(3)));
            List<GameState> after = new ArrayList<>();
            after.add(log.state());
            for (int i = 0; i < 40; i++) {
                log.append("user-dani", i, randomAction(random, log.state()));
                after.add(log.state());
            }
            GameState original = log.state();
            int n = 1 + random.nextInt(log.undoableActions());

            log.undo("user-dani", 100, n);
            assertEquals(after.get(after.size() - 1 - n), log.state(), "seed " + seed + ": after undoing " + n + " the state is the one from before them");
            assertEquals(n, log.redoableActions(), "seed " + seed);

            log.redo("user-dani", 101, n);
            assertEquals(original, log.state(), "seed " + seed + ": undo " + n + " then redo " + n);
            assertEquals(original, log.replay(), "seed " + seed);

            // one step at a time gives the same result
            log.undo("user-dani", 102, n);
            for (int i = 0; i < n; i++) {
                log.redo("user-dani", 103 + i, 1);
            }
            assertEquals(original, log.state(), "seed " + seed + ": undo " + n + " then redo one by one");
        }
    }

    @Test
    void rollsAndRandomChoicesNeverChangeAfterAnUndoAndRedo() {
        for (long seed = 0; seed < GAMES; seed++) {
            Played p = play(seed, 50);
            GameState state = p.log.state();
            int undoable = p.log.undoableActions();
            if (undoable == 0) {
                continue;
            }
            p.log.undo("user-dani", 9_999_999, undoable);
            // the rolls and draws still present are a prefix of what there was: nothing was made again
            // (an admin can change a roll later, so only which rolls there are is compared, and that each digital one still matches its seed)
            assertEquals(state.rolls().subList(0, p.log.state().rolls().size()).stream().map(GameState.RollResult::requestId).toList(),
                    p.log.state().rolls().stream().map(GameState.RollResult::requestId).toList(), "seed " + seed);
            for (GameState.RollResult r : p.log.state().rolls()) {
                if (r.seed() != null) {
                    assertEquals(DigitalRoller.roll(r.dice(), r.seed()), r.faces(), "seed " + seed + ": a digital roll is what its seed gives");
                }
            }
            assertTrue(state.draws().subList(0, p.log.state().draws().size()).equals(p.log.state().draws()), "seed " + seed);
            p.log.redo("user-dani", 10_000_000, undoable);
            assertEquals(state.rolls(), p.log.state().rolls(), "seed " + seed);
            assertEquals(state.draws(), p.log.state().draws(), "seed " + seed);
        }
    }

    @Test
    void aSnapshotWrittenAsJsonAndReadBackIsTheSameState() throws Exception {
        ObjectMapper text = new ObjectMapper();
        for (long seed = 0; seed < GAMES; seed++) {
            Played p = play(seed, 40);
            List<GameState> states = new ArrayList<>(p.stateAfterEachAction);
            states.add(p.log.state());
            states.add(GameState.empty());
            for (GameState state : states) {
                var json = GameStateJson.write(state);
                assertEquals(state, GameStateJson.read(json), "seed " + seed + ": through a JSON tree");
                // and through real text, as the database stores it
                var parsed = text.readTree(text.writeValueAsString(json));
                assertEquals(state, GameStateJson.read(parsed), "seed " + seed + ": through JSON text");
                assertEquals(json, GameStateJson.write(GameStateJson.read(parsed)), "seed " + seed + ": the JSON itself does not change");
            }
            for (Snapshot s : p.log.snapshots()) {
                assertEquals(s.state(), GameStateJson.write(s.read()), "seed " + seed);
            }
        }
    }

    @Test
    void everyEventHasAnAuditEntryAndEveryUndoOrRedoToo() {
        for (long seed = 0; seed < GAMES; seed++) {
            Played p = play(seed, 50);
            int events = p.log.entries().size();
            long flips = p.log.audit().stream().filter(a -> a.action().equals("undo") || a.action().equals("redo")).count();
            assertEquals(events + flips, p.log.audit().size(), "seed " + seed);
            for (AuditEntry a : p.log.audit()) {
                assertTrue(a.actor() != null && !a.actor().isBlank(), "seed " + seed + ": every audit entry says who");
            }
            // the entry of each event says what the entry of the log says
            long seen = 0;
            for (AuditEntry a : p.log.audit()) {
                if (a.actionId() > 0) {
                    LogEntry entry = p.log.entries().get((int) a.seq() - 1);
                    assertEquals(entry.type(), a.action(), "seed " + seed);
                    assertEquals(entry.actor(), a.actor(), "seed " + seed);
                    seen++;
                }
            }
            assertEquals(events, seen, "seed " + seed);
        }
    }
}
