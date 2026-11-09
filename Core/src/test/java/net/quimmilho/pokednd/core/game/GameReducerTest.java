/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.game.GameEvent.RandomDrawn;
import net.quimmilho.pokednd.core.game.GameEvent.RollAnswered;
import net.quimmilho.pokednd.core.game.GameEvent.RollCancelled;
import net.quimmilho.pokednd.core.game.GameEvent.RollRequested;
import net.quimmilho.pokednd.core.game.GameEvent.RoundAdvanced;
import net.quimmilho.pokednd.core.game.GameEvent.StatusChanged;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerRemoved;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerSet;
import org.junit.jupiter.api.Test;

class GameReducerTest {

    private static GameState withAna() {
        return GameReducer.apply(GameState.empty(), new TrainerSet("ana", Fixtures.trainer("Ana")));
    }

    @Test
    void aNewGameIsADraftInRoundZero() {
        GameState s = GameState.empty();
        assertEquals(GameStatus.DRAFT, s.status());
        assertEquals(0, s.round());
        assertTrue(s.trainers().isEmpty() && s.pendingRolls().isEmpty() && s.rolls().isEmpty() && s.draws().isEmpty());
    }

    @Test
    void applyingAnEventDoesNotChangeTheOldState() {
        GameState before = GameState.empty();
        GameState after = GameReducer.apply(before, new StatusChanged(GameStatus.LOBBY));
        assertEquals(GameStatus.DRAFT, before.status());
        assertEquals(GameStatus.LOBBY, after.status());
    }

    @Test
    void statusAndRound() {
        GameState s = GameReducer.applyAll(GameState.empty(), List.of(new StatusChanged(GameStatus.LOBBY), new StatusChanged(GameStatus.RUNNING), new RoundAdvanced(1)));
        assertEquals(GameStatus.RUNNING, s.status());
        assertEquals(1, s.round());
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RoundAdvanced(3)));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RoundAdvanced(1)));
        GameState ended = GameReducer.apply(s, new StatusChanged(GameStatus.ENDED));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(ended, new StatusChanged(GameStatus.RUNNING)));
    }

    @Test
    void aTrainerIsAddedReplacedAndRemoved() {
        GameState s = withAna();
        assertEquals("Ana", s.trainers().get("ana").name());
        GameState hurt = GameReducer.apply(s, new TrainerSet("ana", Fixtures.trainerWithHp(Fixtures.trainer("Ana"), 4)));
        assertEquals(1, hurt.trainers().size());
        assertEquals(4, hurt.trainers().get("ana").hp());
        GameState gone = GameReducer.apply(hurt, new TrainerRemoved("ana"));
        assertTrue(gone.trainers().isEmpty());
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(gone, new TrainerRemoved("ana")));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new TrainerSet(" ", Fixtures.trainer("X"))));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new TrainerSet("x", null)));
    }

    @Test
    void aRollIsRequestedAndAnsweredWithTheFacesThatWereRolled() {
        GameState s = GameReducer.apply(withAna(), new RollRequested("r1", "ana", new Dice(2, 6), "Initiative"));
        assertEquals(1, s.pendingRolls().size());
        GameState done = GameReducer.apply(s, new RollAnswered("r1", DigitalRoller.roll(new Dice(2, 6), 42L), "digital", null, null, false, 42L));
        assertTrue(done.pendingRolls().isEmpty());
        assertEquals(1, done.rolls().size());
        assertEquals(DigitalRoller.roll(new Dice(2, 6), 42L).stream().mapToInt(Integer::intValue).sum(), done.rolls().get(0).total());
        assertEquals(42L, done.rolls().get(0).seed());
        assertEquals("digital", done.rolls().get(0).source());
        assertEquals("ana", done.rolls().get(0).trainerId());
    }

    @Test
    void aRollThatDoesNotFitItsRequestIsRefused() {
        GameState s = GameReducer.apply(withAna(), new RollRequested("r1", "ana", new Dice(2, 6), "Initiative"));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RollAnswered("r1", List.of(3), "typed")), "one die too few");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RollAnswered("r1", List.of(3, 7), "typed")), "a 7 on a d6");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RollAnswered("r1", List.of(0, 3), "typed")), "a 0");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RollAnswered("r1", List.of(3, 3), "magic")), "an unknown source");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RollAnswered("nope", List.of(3, 3), "typed")), "no such request");
    }

    @Test
    void aRequestNeedsATrainerAndAFreshId() {
        GameState s = GameReducer.apply(withAna(), new RollRequested("r1", "ana", Dice.d(20), "x"));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RollRequested("r2", "bob", Dice.d(20), "x")));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RollRequested("r1", "ana", Dice.d(20), "x")), "id of an open request");
        GameState answered = GameReducer.apply(s, new RollAnswered("r1", List.of(10), "typed"));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(answered, new RollRequested("r1", "ana", Dice.d(20), "x")), "id of an answered request");
    }

    @Test
    void aCancelledRequestIsGoneAndRemovingATrainerCancelsTheirRequests() {
        GameState s = GameReducer.applyAll(withAna(), List.of(new RollRequested("r1", "ana", Dice.d(20), "x"), new RollRequested("r2", "ana", Dice.d(6), "y")));
        GameState cancelled = GameReducer.apply(s, new RollCancelled("r1"));
        assertEquals(List.of("r2"), List.copyOf(cancelled.pendingRolls().keySet()));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(cancelled, new RollCancelled("r1")));
        assertTrue(GameReducer.apply(s, new TrainerRemoved("ana")).pendingRolls().isEmpty());
    }

    @Test
    void aRandomChoiceIsRecordedWithItsValue() {
        GameState s = GameReducer.apply(GameState.empty(), new RandomDrawn("encounter:route1", 100, 37));
        assertEquals(new GameState.RandomDraw("encounter:route1", 100, 37), s.draws().get(0));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RandomDrawn("x", 100, 100)));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RandomDrawn("x", 100, -1)));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RandomDrawn("x", 0, 0)));
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RandomDrawn("", 2, 1)));
    }

    @Test
    void aRefusedEventLeavesTheStateAsItWas() {
        GameState s = withAna();
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new RoundAdvanced(9)));
        assertSame(s, s);
        assertEquals(withAna(), s);
    }

    @Test
    void everyEventTypeCanBeWrittenAndReadBack() {
        List<GameEvent> events = List.of(new StatusChanged(GameStatus.PAUSED), new RoundAdvanced(4), new TrainerSet("ana", Fixtures.trainer("Ana")), new TrainerRemoved("ana"),
                new RollRequested("r3", "ana", Dice.d(20), "Scratch", GameState.Roller.ADMIN, "u-ana", GameState.RollClass.ENEMY, List.of(new GameState.Band("hit", 5, 20)), 0.8,
                        List.of(new RollModifier(RollModifier.Kind.MULTIPLIER, "ball", 1.25)), "Roll 1d20", 90_000),
                new RollAnswered("r4", List.of(), "typed", 11, "u-dani", true, null), new RollCancelled("r2"), new RandomDrawn("flee", 6, 5),
                new GameEvent.RollSubmitted("r5", List.of(4), null, "u-ana", 1234), new GameEvent.RollRejected("r5", "again"),
                new GameEvent.RollOverridden("r5", Dice.d(6), List.of(3), null, "admin", null, "fix"), new GameEvent.RollExpired("r6"));
        assertEquals(EventTypes.all().size(), events.size(), "this test covers every event type");
        for (GameEvent event : events) {
            assertEquals(event, EventTypes.read(event.type(), EventTypes.payload(event)), event.type());
        }
        // other shapes of the same events
        for (GameEvent event : List.of(new RollRequested("r1", "ana", new Dice(3, 8), "Damage"), new RollAnswered("r1", List.of(1, 8, 4), "admin"),
                new RollAnswered("r3", List.of(7), "digital", null, null, false, 99L), new GameEvent.RollSubmitted("r5", List.of(), 11, "u-ana", 1234),
                new GameEvent.RollOverridden("r5", null, List.of(3), null, "digital", 5L, null))) {
            assertEquals(event, EventTypes.read(event.type(), EventTypes.payload(event)), event.type());
        }
    }

    @Test
    void anUnknownOrBrokenStoredEventIsRefused() {
        assertThrows(InvalidEventException.class, () -> EventTypes.read("quest.completed", EventTypes.payload(new RoundAdvanced(1))));
        assertThrows(InvalidEventException.class, () -> EventTypes.read(EventTypes.TRAINER_SET, EventTypes.payload(new RoundAdvanced(1)).put("trainer", 5)));
    }

    @Test
    void aFieldAddedByANewerVersionIsSkipped() {
        var payload = EventTypes.payload(new RoundAdvanced(2)).put("addedLater", true);
        assertEquals(new RoundAdvanced(2), EventTypes.read(EventTypes.ROUND_ADVANCED, payload));
    }
}
