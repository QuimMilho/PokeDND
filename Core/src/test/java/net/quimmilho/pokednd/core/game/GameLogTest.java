/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.game.GameEvent.RandomDrawn;
import net.quimmilho.pokednd.core.game.GameEvent.RollAnswered;
import net.quimmilho.pokednd.core.game.GameEvent.RollRequested;
import net.quimmilho.pokednd.core.game.GameEvent.RoundAdvanced;
import net.quimmilho.pokednd.core.game.GameEvent.StatusChanged;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerSet;
import org.junit.jupiter.api.Test;

class GameLogTest {

    private static final String DANI = "user-dani";
    private static final String ANA = "user-ana";

    private static GameLog newLog() {
        return new GameLog(GameLog.Settings.defaults());
    }

    /** A running game with one trainer, Ana, in round 1. */
    private static GameLog runningGame() {
        GameLog log = newLog();
        log.append(DANI, 1000, List.of(new StatusChanged(GameStatus.RUNNING)));
        log.append(ANA, 2000, List.of(new TrainerSet("ana", Fixtures.trainer("Ana"))));
        log.append(DANI, 3000, List.of(new RoundAdvanced(1)));
        return log;
    }

    @Test
    void theStateIsWhatTheEventsMake() {
        GameLog log = runningGame();
        assertEquals(GameStatus.RUNNING, log.state().status());
        assertEquals(1, log.state().round());
        assertEquals(1, log.state().trainers().size());
        assertEquals(log.state(), log.replay());
        assertEquals(List.of(1L, 2L, 3L), log.entries().stream().map(LogEntry::seq).toList());
    }

    @Test
    void theEventsOfOneActionShareAnActionIdAndAreAddedTogether() {
        GameLog log = runningGame();
        GameLog.Appended a = log.append(DANI, 4000, List.of(new RollRequested("r1", "ana", Dice.d(20), "Initiative"), new RandomDrawn("enemy-initiative", 20, 9)));
        assertEquals(4, a.actionId());
        assertEquals(5, a.lastSeq());
        assertEquals(List.of(4L, 4L), log.entries().subList(3, 5).stream().map(LogEntry::actionId).toList());
    }

    @Test
    void anActionWithABadEventAddsNothing() {
        GameLog log = runningGame();
        GameState before = log.state();
        int entries = log.entries().size();
        int audit = log.audit().size();
        assertThrows(InvalidEventException.class, () -> log.append(DANI, 4000, List.of(new RollRequested("r1", "ana", Dice.d(20), "x"), new RoundAdvanced(7))));
        assertEquals(before, log.state());
        assertEquals(entries, log.entries().size());
        assertEquals(audit, log.audit().size());
        assertThrows(IllegalArgumentException.class, () -> log.append(DANI, 4000, List.of()));
    }

    // ---------------------------------------------------------------------------------------------------------------- audit

    @Test
    void everyEventHasAnAuditEntryWithWhoDidIt() {
        GameLog log = runningGame();
        log.append(ANA, 4000, List.of(new RollRequested("r1", "ana", Dice.d(20), "Initiative")));
        log.append(ANA, 5000, List.of(new RollAnswered("r1", List.of(14), "typed")));
        List<AuditEntry> audit = log.audit();
        assertEquals(log.entries().size(), audit.size());
        for (int i = 0; i < audit.size(); i++) {
            LogEntry entry = log.entries().get(i);
            assertEquals(entry.seq(), audit.get(i).seq());
            assertEquals(entry.actor(), audit.get(i).actor());
            assertEquals(entry.type(), audit.get(i).action());
            assertEquals(entry.atMillis(), audit.get(i).atMillis());
        }
        assertEquals(DANI, audit.get(0).actor());
        assertEquals("game.status", audit.get(0).action());
        assertEquals("game", audit.get(0).target());
        assertEquals(ANA, audit.get(4).actor());
        assertEquals("roll.answered", audit.get(4).action());
        assertEquals("r1", audit.get(4).target());
    }

    @Test
    void theAuditEntryShowsOnlyWhatChanged() {
        GameLog log = runningGame();
        AuditEntry status = log.audit().get(0);
        assertEquals("draft", status.before().get("status").asText());
        assertEquals("running", status.after().get("status").asText());
        assertFalse(status.after().has("trainers"), "the status change did not touch the trainers");

        log.append(DANI, 4000, List.of(new TrainerSet("ana", Fixtures.trainerWithHp(Fixtures.trainer("Ana"), 4))));
        AuditEntry hp = log.audit().get(3);
        assertEquals(9, hp.before().get("trainers").get("ana").get("hp").asInt());
        assertEquals(4, hp.after().get("trainers").get("ana").get("hp").asInt());
        assertFalse(hp.after().has("round"));
    }

    @Test
    void undoAndRedoAreInTheAuditTrailToo() {
        GameLog log = runningGame();
        int before = log.audit().size();
        GameLog.Changed undone = log.undo(DANI, 9000, 1);
        assertEquals(before + 1, log.audit().size());
        AuditEntry entry = log.audit().get(before);
        assertEquals(undone.audit(), entry);
        assertEquals(DANI, entry.actor());
        assertEquals("undo", entry.action());
        assertEquals("actions:3", entry.target());
        assertEquals(1, entry.before().get("round").asInt());
        assertEquals(0, entry.after().get("round").asInt());
        log.redo(DANI, 9500, 1);
        assertEquals("redo", log.audit().get(before + 1).action());
    }

    // ---------------------------------------------------------------------------------------------------------------- undo

    @Test
    void undoGoesBackToTheExactPreviousState() {
        GameLog log = runningGame();
        GameState before = log.state();
        log.append(DANI, 4000, List.of(new RoundAdvanced(2), new RandomDrawn("weather", 4, 2)));
        assertEquals(2, log.state().round());
        GameLog.Changed changed = log.undo(DANI, 5000, 1);
        assertEquals(before, log.state());
        assertEquals(before, changed.state());
        assertEquals(before, log.replay());
        assertTrue(log.entries().get(3).undone() && log.entries().get(4).undone(), "both events of the action are undone");
        assertEquals(5, log.entries().size(), "nothing is deleted from the log");
    }

    @Test
    void undoPutsBackTheRollRequestThatWasAnswered() {
        GameLog log = runningGame();
        log.append(DANI, 4000, List.of(new RollRequested("r1", "ana", Dice.d(20), "Initiative")));
        GameState waiting = log.state();
        log.append(ANA, 5000, List.of(new RollAnswered("r1", List.of(14), "typed")));
        assertTrue(log.state().pendingRolls().isEmpty());
        assertEquals(1, log.state().rolls().size());

        log.undo(DANI, 6000, 1);
        assertEquals(waiting, log.state());
        assertTrue(log.state().pendingRolls().containsKey("r1"), "the request can be answered again");
        assertTrue(log.state().rolls().isEmpty());
    }

    @Test
    void aRollOrRandomChoiceThatWasMadeIsNotMadeAgainByUndoAndRedo() {
        GameLog log = runningGame();
        log.append(DANI, 4000, List.of(new RollRequested("r1", "ana", Dice.d(20), "Initiative"), new RandomDrawn("encounter", 100, 61)));
        log.append(ANA, 5000, List.of(new RollAnswered("r1", DigitalRoller.roll(Dice.d(20), 17L), "digital", null, null, false, 17L)));
        GameState original = log.state();
        log.undo(DANI, 6000, 2);
        log.redo(DANI, 7000, 2);
        assertEquals(original, log.state());
        assertEquals(DigitalRoller.roll(Dice.d(20), 17L).get(0), log.state().rolls().get(0).total());
        assertEquals(61, log.state().draws().get(0).value());
    }

    @Test
    void undoSeveralAndRedoSeveral() {
        GameLog log = newLog();
        GameState[] states = new GameState[6];
        states[0] = log.state();
        for (int i = 1; i <= 5; i++) {
            log.append(DANI, i, List.of(new RandomDrawn("d" + i, 10, i)));
            states[i] = log.state();
        }
        log.undo(DANI, 10, 3);
        assertEquals(states[2], log.state());
        assertEquals(3, log.redoableActions());
        log.redo(DANI, 11, 1);
        assertEquals(states[3], log.state());
        log.redo(DANI, 12, 2);
        assertEquals(states[5], log.state());
        assertEquals(0, log.redoableActions());
    }

    @Test
    void undoToAPointInTheHistory() {
        GameLog log = newLog();
        long second = 0;
        for (int i = 1; i <= 4; i++) {
            long id = log.append(DANI, i, List.of(new RandomDrawn("d" + i, 10, i))).actionId();
            if (i == 2) {
                second = id;
            }
        }
        GameState atSecond = GameLog.restore(log.settings(), log.entries().subList(0, 2), List.of(), List.of()).state();
        log.undoTo(DANI, 20, second);
        assertEquals(atSecond, log.state());
        assertThrows(IllegalArgumentException.class, () -> log.undoTo(DANI, 21, 99));
        log.undoTo(DANI, 22, 0);
        assertEquals(GameState.empty(), log.state());
    }

    @Test
    void anActionAddedAfterAnUndoEndsTheRedo() {
        GameLog log = runningGame();
        log.undo(DANI, 4000, 1);
        assertEquals(1, log.redoableActions());
        log.append(DANI, 5000, List.of(new RandomDrawn("x", 2, 1)));
        assertEquals(0, log.redoableActions());
        assertThrows(IllegalArgumentException.class, () -> log.redo(DANI, 6000, 1));
        assertEquals(log.state(), log.replay());
    }

    @Test
    void actionsThatWereReplacedByANewActionNeverComeBack() {
        GameLog log = newLog();
        for (int i = 1; i <= 5; i++) {
            log.append(DANI, i, List.of(new RandomDrawn("d" + i, 10, i)));
        }
        log.undo(DANI, 10, 2); // actions 4 and 5 are undone ...
        log.append(DANI, 11, List.of(new RandomDrawn("new", 10, 9))); // ... and replaced by a new action 6
        GameState withSix = log.state();
        log.undo(DANI, 12, 1); // undoing the new action must not make 4 and 5 redoable: action 6 was made without them
        assertEquals(1, log.redoableActions());
        assertTrue(log.entries().get(3).discarded() && log.entries().get(4).discarded());
        log.redo(DANI, 13, 1);
        assertEquals(withSix, log.state());
        assertEquals(log.replay(), log.state());
    }

    @Test
    void onlyTheLastUndoDepthActionsCanBeUndone() {
        GameLog log = new GameLog(new GameLog.Settings(3, 1));
        for (int i = 1; i <= 5; i++) {
            log.append(DANI, i, List.of(new RandomDrawn("d" + i, 10, i)));
        }
        assertEquals(3, log.undoableActions());
        assertEquals(3, log.recentActions().size());
        assertEquals(5, log.recentActions().get(0).actionId(), "newest first");
        assertThrows(IllegalArgumentException.class, () -> log.undo(DANI, 9, 4));
        assertThrows(IllegalArgumentException.class, () -> log.undo(DANI, 9, 0));
        log.undo(DANI, 9, 3);
        assertEquals(2, log.state().draws().size());
        assertEquals(2, log.undoableActions());
    }

    @Test
    void thereIsNothingToUndoInANewGame() {
        GameLog log = newLog();
        assertEquals(0, log.undoableActions());
        assertThrows(IllegalArgumentException.class, () -> log.undo(DANI, 1, 1));
    }

    // ---------------------------------------------------------------------------------------------------------------- snapshots

    @Test
    void aSnapshotIsKeptWhenARoundStarts() {
        GameLog log = runningGame();
        assertEquals(1, log.snapshots().size());
        Snapshot s = log.snapshots().get(0);
        assertEquals(3, s.seq());
        assertEquals(1, s.round());
        assertEquals(log.state(), s.read());
        log.append(DANI, 4000, List.of(new RoundAdvanced(2)));
        assertEquals(2, log.snapshots().size());
    }

    @Test
    void snapshotsFollowTheAutosaveSetting() {
        GameLog log = new GameLog(new GameLog.Settings(20, 2));
        for (int r = 1; r <= 4; r++) {
            log.append(DANI, r, List.of(new RoundAdvanced(r)));
        }
        assertEquals(List.of(2, 4), log.snapshots().stream().map(Snapshot::round).toList());
    }

    @Test
    void theStateIsRebuiltFromTheNewestSnapshotAndTheEntriesAfterIt() {
        GameLog log = runningGame();
        log.append(DANI, 4000, List.of(new RoundAdvanced(2)));
        log.append(ANA, 5000, List.of(new RollRequested("r1", "ana", Dice.d(6), "x")));
        GameLog restored = GameLog.restore(log.settings(), log.entries(), log.snapshots(), log.audit());
        assertEquals(log.state(), restored.state());
        assertEquals(log.state(), restored.replay());
        assertEquals(log.audit(), restored.audit());
    }

    @Test
    void anUndoRemovesTheSnapshotsItMakesWrongAndARedoRebuildsCorrectly() {
        GameLog log = runningGame();
        log.append(DANI, 4000, List.of(new RoundAdvanced(2)));
        assertEquals(2, log.snapshots().size());
        GameState round2 = log.state();

        log.undo(DANI, 5000, 1); // round 2 never happened: its snapshot goes
        assertEquals(1, log.snapshots().size());
        assertEquals(1, log.state().round());

        log.redo(DANI, 6000, 1);
        assertEquals(round2, log.state());
        assertEquals(log.replay(), log.state());

        // an action from before the snapshot is undone: the snapshot of round 1 contains it, so it goes as well
        GameLog log2 = runningGame();
        log2.undo(DANI, 7000, 3); // all three actions
        assertTrue(log2.snapshots().isEmpty());
        assertEquals(GameState.empty(), log2.state());
    }

    @Test
    void restoreRefusesALogWithAGap() {
        GameLog log = runningGame();
        List<LogEntry> broken = List.of(log.entries().get(0), log.entries().get(2));
        assertThrows(InvalidEventException.class, () -> GameLog.restore(log.settings(), broken, List.of(), List.of()));
    }

    @Test
    void restoreRefusesAnEventTheVersionDoesNotKnow() {
        GameLog log = runningGame();
        LogEntry strange = new LogEntry(4, 4, "quest.completed", DANI, 1, log.entries().get(2).payload(), false, false);
        List<LogEntry> entries = new java.util.ArrayList<>(log.entries());
        entries.add(strange);
        assertThrows(InvalidEventException.class, () -> GameLog.restore(log.settings(), entries, List.of(), List.of()));
    }

    @Test
    void undoneEntriesAreKeptAndStaySkippedAfterARestore() {
        GameLog log = runningGame();
        log.undo(DANI, 4000, 1);
        GameLog restored = GameLog.restore(log.settings(), log.entries(), log.snapshots(), log.audit());
        assertEquals(log.state(), restored.state());
        assertEquals(1, restored.redoableActions());
        restored.redo(DANI, 5000, 1);
        assertEquals(1, restored.state().round());
    }

    @Test
    void theSettingsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new GameLog.Settings(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new GameLog.Settings(20, 0));
    }
}
