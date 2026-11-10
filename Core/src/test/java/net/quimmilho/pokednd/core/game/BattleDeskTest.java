/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import net.quimmilho.pokednd.core.game.Battle.Result;
import net.quimmilho.pokednd.core.game.Battle.Step;
import net.quimmilho.pokednd.core.game.GameEvent.StatusChanged;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerSet;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.game.RollSettings.SystemRolls;
import org.junit.jupiter.api.Test;

/**
 * Phase 44: battle rounds and the six actions, as scripted scenarios with fixed rolls. Everything plays through {@link GameLog}, as the
 * server will, so the events also go through the reducer, the log, the audit trail and the JSON of the stored events.
 *
 * <p>The cast: five trainers on one side against one wild Rattata. Their Speed gives the initiative modifier {@code floor(Speed / 20)}.
 * <pre>
 *   Ana  Charmander  Speed 60 (+3)   Ember (priority 0)
 *   Bo   Squirtle    Speed 43 (+2)   Quick Attack (priority +1)
 *   Cy   Bulbasaur   Speed 45 (+2)   Pidgey waits in the reserve (Speed 56)
 *   Dee  Pikachu     Speed 90 (+4)
 *   Eli  Geodude     Speed 20 (+1)
 *   wild Rattata     Speed 72 (+3)   Tackle
 * </pre>
 */
class BattleDeskTest {

    private static final Actor ADMIN = Actor.admin("u-dani");
    private static final Actor ANA = Actor.player("u-ana");
    private static final Actor BO = Actor.player("u-bo");
    private static final long NOW = 1_000_000;

    private final RollSettings rolls = RollSettings.defaults().withSystemRolls(SystemRolls.ADMIN_PHYSICAL, SystemRolls.AUTO);
    private final GameLog log = new GameLog(GameLog.Settings.defaults());
    private final LongSupplier seeds = DigitalRoller.seeds(RollSettings.RngSource.SEEDED, 7);

    BattleDeskTest() {
        List<GameEvent> setup = new ArrayList<>();
        setup.add(new StatusChanged(GameStatus.RUNNING));
        for (String id : List.of("ana", "bo", "cy", "dee", "eli")) {
            setup.add(new TrainerSet(id, Fixtures.trainer(id.substring(0, 1).toUpperCase() + id.substring(1))));
        }
        log.append("system", 1, setup);
    }

    // ------------------------------------------------------------------------------------------------------------- scaffolding

    private static MoveSlot move(String id, String name, int pp, int priority, String target) {
        return new MoveSlot(id, name, pp, pp, priority, target);
    }

    private static Combatant mon(String id, String owner, String name, int speed, int hp, MoveSlot... moves) {
        return new Combatant(id, owner, name, 20, hp, hp, speed, false, List.of(moves));
    }

    private static Participant trainer(String id, String user, List<String> active, List<String> reserve) {
        return new Participant(id, Kind.TRAINER, 0, id.substring(0, 1).toUpperCase() + id.substring(1), id, user, active, reserve);
    }

    private static Participant wild(String id, String name, String mon) {
        return new Participant(id, Kind.WILD, 1, name, null, null, List.of(mon), List.of());
    }

    private void play(Actor actor, List<GameEvent> events) {
        log.append(actor.userId(), NOW, events);
    }

    private Battle battle() {
        return log.state().battle();
    }

    private void startBattle(BattleSettings rules) {
        List<Participant> participants = List.of(
                trainer("ana", "u-ana", List.of("ana-1"), List.of()),
                trainer("bo", "u-bo", List.of("bo-1"), List.of()),
                trainer("cy", "u-cy", List.of("cy-1"), List.of("cy-2")),
                trainer("dee", "u-dee", List.of("dee-1"), List.of()),
                trainer("eli", "u-eli", List.of("eli-1"), List.of()),
                wild("rat", "Rattata", "rat-1"));
        MoveSlot tackle = move("tackle", "Tackle", 35, 0, "normal");
        List<Combatant> combatants = List.of(
                mon("ana-1", "ana", "Charmander", 60, 40, move("ember", "Ember", 25, 0, "normal"), move("growl", "Growl", 40, 0, "allAdjacentFoes"),
                        move("swords", "Swords Dance", 20, 0, "self")),
                mon("bo-1", "bo", "Squirtle", 43, 42, move("quick-attack", "Quick Attack", 30, 1, "normal")),
                mon("cy-1", "cy", "Bulbasaur", 45, 45, tackle),
                mon("cy-2", "cy", "Pidgey", 56, 40, tackle),
                mon("dee-1", "dee", "Pikachu", 90, 35, tackle),
                mon("eli-1", "eli", "Geodude", 20, 40, tackle),
                mon("rat-1", "rat", "Rattata", 72, 30, tackle));
        play(ADMIN, BattleDesk.start(log.state(), ADMIN, "b1", Battle.Type.WILD, rules, participants, combatants));
    }

    /** Opens the next round, answers every initiative roll with the given die (the admin types them) and fixes the order. */
    private void openRound(Map<String, Integer> dice) {
        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        for (Map.Entry<String, Integer> e : dice.entrySet()) {
            String request = "b1-r" + battle().round() + "-init-" + e.getKey();
            play(ADMIN, RollDesk.enter(log.state(), ADMIN, request, List.of(e.getValue()), null));
        }
        assertTrue(BattleDesk.initiativeReady(log.state()));
        play(ADMIN, BattleDesk.setOrder(log.state(), NOW, seeds));
    }

    /** The dice of the documented example: Eli 20, Bo 15, Ana 10, Rattata 10, Dee 9, Cy 5. */
    private static Map<String, Integer> exampleDice() {
        Map<String, Integer> dice = new LinkedHashMap<>();
        dice.put("ana", 10);
        dice.put("bo", 15);
        dice.put("cy", 5);
        dice.put("dee", 9);
        dice.put("eli", 20);
        dice.put("rat", 10);
        return dice;
    }

    private void declare(Actor who, String actorId, Action action) {
        play(who, BattleDesk.declare(log.state(), who, actorId, action, NOW));
    }

    private void declareExampleActions() {
        declare(ANA, "ana-1", Action.move("ember", "rat-1"));
        declare(BO, "bo-1", Action.move("quick-attack", "rat-1"));
        declare(Actor.player("u-cy"), "cy-1", Action.switchTo("cy-2"));
        declare(Actor.player("u-dee"), "dee-1", Action.item("potion", "dee-1"));
        declare(Actor.player("u-eli"), "eli-1", Action.flee());
        declare(ADMIN, "rat-1", Action.move("tackle", "ana-1"));
    }

    private void resolveRound() {
        play(ADMIN, BattleDesk.startResolving(log.state()));
        while (!battle().queue().isEmpty()) {
            play(ADMIN, BattleDesk.resolveNext(log.state()));
        }
        play(ADMIN, BattleDesk.endRound(log.state()));
    }

    private BattleRefusedException refused(Runnable action) {
        return assertThrows(BattleRefusedException.class, action::run);
    }

    private List<String> order() {
        return battle().order().stream().map(Battle.InitiativeEntry::participantId).toList();
    }

    // ----------------------------------------------------------------------------------------------- the documented order

    @Test
    void initiativeIs1d20PlusFloorOfSpeedOver20AndTiesGoToTheHigherSpeed() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());

        // Eli 20+1=21, Bo 15+2=17, then three with 13: Dee (Speed 90), Rattata (72), Ana (60); Cy 5+2=7
        assertEquals(List.of("eli", "bo", "dee", "rat", "ana", "cy"), order());
        assertEquals(List.of(21, 17, 13, 13, 13, 7), battle().order().stream().map(Battle.InitiativeEntry::total).toList());
        assertEquals(Step.DECLARE, battle().step());
        assertEquals("Initiative: Eli 21 (20 + 1), Bo 17 (15 + 2), Dee 13 (9 + 4), wild Rattata 13 (10 + 3), Ana 13 (10 + 3), Cy 7 (5 + 2).",
                battle().log().get(battle().log().size() - 1));
    }

    @Test
    void aTieOfTotalAndSpeedIsDecidedByTheDrawnNumberNotByTheOrderOfTheList() {
        GameLog second = new GameLog(GameLog.Settings.defaults());
        second.append("system", 1, List.of(new StatusChanged(GameStatus.RUNNING), new TrainerSet("ana", Fixtures.trainer("Ana"))));
        MoveSlot tackle = move("tackle", "Tackle", 35, 0, "normal");
        List<Participant> participants = List.of(trainer("ana", "u-ana", List.of("ana-1"), List.of()), wild("r1", "Rattata", "r1-1"),
                new Participant("r2", Kind.WILD, 2, "Pidgey", null, null, List.of("r2-1"), List.of()));
        List<Combatant> mons = List.of(mon("ana-1", "ana", "Charmander", 60, 40, tackle), mon("r1-1", "r1", "Rattata", 60, 30, tackle),
                mon("r2-1", "r2", "Pidgey", 60, 30, tackle));
        second.append("admin", NOW, BattleDesk.start(second.state(), ADMIN, "t1", Battle.Type.MIXED, BattleSettings.defaults(), participants, mons));
        second.append("admin", NOW, BattleDesk.openRound(second.state(), rolls, Map.of(), NOW, seeds));
        for (String id : List.of("ana", "r1", "r2")) {
            second.append("admin", NOW, RollDesk.enter(second.state(), ADMIN, "t1-r1-init-" + id, List.of(10), null));
        }
        // three with the same total (13) and the same Speed (60): only the drawn number is left to decide
        GameState a = GameReducer.apply(second.state(), new GameEvent.InitiativeSet("t1", 1, Map.of("ana", 100, "r1", 300, "r2", 200), 0));
        assertEquals(List.of("r1", "r2", "ana"), a.battle().order().stream().map(Battle.InitiativeEntry::participantId).toList());
        GameState b = GameReducer.apply(second.state(), new GameEvent.InitiativeSet("t1", 1, Map.of("ana", 300, "r1", 200, "r2", 100), 0));
        assertEquals(List.of("ana", "r1", "r2"), b.battle().order().stream().map(Battle.InitiativeEntry::participantId).toList());

        // the desk draws the numbers from the seeds, and the same seeds always give the same order
        List<GameEvent> first = BattleDesk.setOrder(second.state(), NOW, DigitalRoller.seeds(RollSettings.RngSource.SEEDED, 5));
        assertEquals(first, BattleDesk.setOrder(second.state(), NOW, DigitalRoller.seeds(RollSettings.RngSource.SEEDED, 5)));
    }

    @Test
    void switchesThenItemsThenFleeThenMovesByPriorityAndInitiative() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        play(ADMIN, BattleDesk.startResolving(log.state()));

        // switch (Cy) · item (Dee) · flee (Eli) · Quick Attack, priority +1 (Bo) · priority 0 by initiative: Rattata, then Ana
        assertEquals(List.of("cy-1", "dee-1", "eli-1", "bo-1", "rat-1", "ana-1"), battle().queue());
        assertEquals("Turn order: Cy's Bulbasaur, Dee's Pikachu, Eli's Geodude, Bo's Squirtle, wild Rattata, Ana's Charmander.",
                battle().log().get(battle().log().size() - 1));
    }

    @Test
    void aWholeRoundResolvesInThatOrderAndWritesTheBattleLog() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        resolveRound();

        List<String> lines = battle().log();
        assertEquals("A wild battle begins: Ana's Charmander, Bo's Squirtle, Cy's Bulbasaur, Dee's Pikachu and Eli's Geodude against wild Rattata.",
                lines.get(0));
        assertEquals(List.of(
                "Cy withdraws Bulbasaur and sends out Pidgey.",
                "Dee uses Potion on Pikachu.",
                "Eli tries to run away.",
                "Bo's Squirtle uses Quick Attack on wild Rattata.",
                "wild Rattata uses Tackle on Ana's Charmander.",
                "Ana's Charmander uses Ember on wild Rattata.",
                "Round 1 ends."), lines.subList(lines.size() - 7, lines.size()));

        Battle b = battle();
        assertEquals(Step.ROUND_END, b.step());
        assertEquals(List.of("cy-1", "dee-1", "eli-1", "bo-1", "rat-1", "ana-1"), b.resolved());
        assertEquals(List.of("cy-2"), b.participants().get("cy").active(), "the switch swapped the Pokémon");
        assertEquals(List.of("cy-1"), b.participants().get("cy").reserve());
        assertEquals(24, b.combatant("ana-1").moves().get(0).pp(), "a move spends one PP");
        assertEquals(29, b.combatant("bo-1").moves().get(0).pp());
        assertEquals(34, b.combatant("rat-1").moves().get(0).pp());
        assertEquals(35, b.combatant("cy-2").moves().get(0).pp(), "a Pokémon that did not act keeps its PP");
    }

    @Test
    void initiativeIsRolledAgainEveryRound() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        resolveRound();

        Map<String, Integer> second = new LinkedHashMap<>();
        second.put("ana", 20);
        second.put("bo", 1);
        second.put("cy", 1);
        second.put("dee", 1);
        second.put("eli", 1);
        second.put("rat", 15);
        openRound(second);
        // Ana 23, Rattata 18, Dee 5, Cy (now Pidgey, Speed 56, +2) 3, Bo 3, Eli 2 — Cy and Bo tie on 3: Pidgey is faster
        assertEquals(2, battle().round());
        assertEquals(List.of("ana", "rat", "dee", "cy", "bo", "eli"), order());
        assertTrue(battle().declared().isEmpty(), "last round's choices are gone");
    }

    @Test
    void initiativeStaysTheSameWhenItIsNotRolledAgain() {
        startBattle(BattleSettings.defaults().withRerollEachRound(false));
        openRound(exampleDice());
        List<String> first = order();
        declare(ANA, "ana-1", Action.skip());
        for (String id : List.of("bo-1", "cy-1", "dee-1", "eli-1", "rat-1")) {
            declare(ADMIN, id, Action.skip());
        }
        resolveRound();

        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        assertEquals(Step.DECLARE, battle().step(), "no initiative rolls are asked for");
        assertEquals(2, battle().round());
        assertEquals(first, order());
        assertTrue(log.state().pendingRolls().isEmpty());
    }

    @Test
    void withTrainerActionsFirstOffEverythingIsOrderedLikeAMove() {
        startBattle(BattleSettings.defaults().withTrainerActionsFirst(false));
        openRound(exampleDice());
        declareExampleActions();
        play(ADMIN, BattleDesk.startResolving(log.state()));
        // Bo has priority +1; the rest go by initiative: Eli, Dee, Rattata, Ana, Cy
        assertEquals(List.of("bo-1", "eli-1", "dee-1", "rat-1", "ana-1", "cy-1"), battle().queue());
    }

    @Test
    void theAdminCanDragActionsAroundAndCancelOne() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        play(ADMIN, BattleDesk.startResolving(log.state()));

        play(ADMIN, BattleDesk.reorder(log.state(), ADMIN, List.of("ana-1", "rat-1", "bo-1", "eli-1", "dee-1", "cy-1")));
        assertEquals(List.of("ana-1", "rat-1", "bo-1", "eli-1", "dee-1", "cy-1"), battle().queue());
        refused(() -> BattleDesk.reorder(log.state(), ADMIN, List.of("ana-1")));
        refused(() -> BattleDesk.reorder(log.state(), ANA, List.of("ana-1", "rat-1", "bo-1", "eli-1", "dee-1", "cy-1")));

        play(ADMIN, BattleDesk.cancelAction(log.state(), ADMIN, "eli-1"));
        assertFalse(battle().queue().contains("eli-1"));
        assertTrue(battle().log().contains("The admin cancelled the action of Eli's Geodude."));
        // the cancelled action cost nothing and nobody else moved
        assertEquals(List.of("ana-1", "rat-1", "bo-1", "dee-1", "cy-1"), battle().queue());
    }

    // -------------------------------------------------------------------------------------------- skipping and fainting

    @Test
    void aPlayerCanSkipTheirTurn() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declare(ANA, "ana-1", Action.skip());
        for (String id : List.of("bo-1", "cy-1", "dee-1", "eli-1", "rat-1")) {
            declare(ADMIN, id, Action.skip());
        }
        assertTrue(BattleDesk.allDeclared(log.state()));
        resolveRound();
        assertTrue(battle().log().contains("Ana's Charmander does nothing this round."));
        assertEquals(25, battle().combatant("ana-1").moves().get(0).pp(), "skipping costs no PP");
    }

    @Test
    void theTurnTimerSkipsThePlayersWhoDidNotChoose() {
        BattleSettings timer = BattleSettings.defaults().withTurnTimer(true, 90, BattleSettings.OnTimeout.SKIP);
        startBattle(timer);
        openRound(exampleDice());
        assertEquals(NOW + 90_000, battle().declareDeadlineMillis());
        declare(ANA, "ana-1", Action.move("ember", "rat-1"));

        assertTrue(BattleDesk.expireDeclarations(log.state(), NOW + 89_999).isEmpty(), "not yet");
        refused(() -> BattleDesk.declare(log.state(), BO, "bo-1", Action.skip(), NOW + 90_001));
        play(ADMIN, BattleDesk.expireDeclarations(log.state(), NOW + 90_000));

        assertTrue(BattleDesk.allDeclared(log.state()));
        assertEquals(Action.move("ember", "rat-1"), battle().declared().get("ana-1"), "a choice that was made stays");
        assertEquals(Action.skip(), battle().declared().get("bo-1"));
        assertEquals(Action.skip(), battle().declared().get("rat-1"));
    }

    @Test
    void theTurnTimerCanPlayTheFirstMoveWithPpInstead() {
        BattleSettings timer = BattleSettings.defaults().withTurnTimer(true, 30, BattleSettings.OnTimeout.AUTO_MOVE);
        startBattle(timer);
        openRound(exampleDice());
        play(ADMIN, BattleDesk.expireDeclarations(log.state(), NOW + 30_000));
        assertEquals(Action.move("ember", "rat-1"), battle().declared().get("ana-1"));
        assertEquals(Action.move("tackle", "ana-1"), battle().declared().get("rat-1"), "the wild Pokémon aims at the first foe");
    }

    @Test
    void aFaintedPokemonCannotDeclareAnAction() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("ana-1").withHp(0)));
        assertTrue(battle().log().contains("Ana's Charmander fainted!"));

        BattleRefusedException e = refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.move("ember", "rat-1"), NOW));
        assertTrue(e.getMessage().contains("fainted"), e.getMessage());
        // and nobody waits for it: the others can go on
        for (String id : List.of("bo-1", "cy-1", "dee-1", "eli-1", "rat-1")) {
            declare(ADMIN, id, Action.skip());
        }
        assertTrue(BattleDesk.allDeclared(log.state()));
    }

    @Test
    void anActionIsCancelledWhenItsPokemonFaintsBeforeItsTurn() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        play(ADMIN, BattleDesk.startResolving(log.state()));

        // the order is Cy, Dee, Eli, Bo, Rattata, Ana: Rattata's Tackle knocks Ana's Charmander out (phase 45 will do the damage; here the admin does)
        for (int i = 0; i < 5; i++) {
            play(ADMIN, BattleDesk.resolveNext(log.state()));
        }
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("ana-1").withHp(0)));

        List<GameEvent> next = BattleDesk.resolveNext(log.state());
        assertEquals(Result.CANCELLED_FAINTED, ((GameEvent.ActionResolved) next.get(0)).result());
        play(ADMIN, next);
        assertEquals("Ana's Charmander fainted before it could act.", battle().log().get(battle().log().size() - 1));
        assertEquals(25, battle().combatant("ana-1").moves().get(0).pp(), "no PP was spent");

        // the reducer itself refuses to let a fainted Pokémon act
        GameState fresh = rewindToBeforeAnasTurn();
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(fresh, new GameEvent.ActionResolved("b1", 1, "ana-1", Result.DONE)));
    }

    private GameState rewindToBeforeAnasTurn() {
        // rebuild: same battle, Ana fainted, Ana's action next
        GameLog other = new GameLog(GameLog.Settings.defaults());
        other.append("system", 1, List.of(new StatusChanged(GameStatus.RUNNING), new TrainerSet("ana", Fixtures.trainer("Ana")),
                new TrainerSet("bo", Fixtures.trainer("Bo"))));
        MoveSlot ember = move("ember", "Ember", 25, 0, "normal");
        List<Participant> ps = List.of(trainer("ana", "u-ana", List.of("ana-1"), List.of()), wild("rat", "Rattata", "rat-1"));
        List<Combatant> cs = List.of(mon("ana-1", "ana", "Charmander", 60, 40, ember), mon("rat-1", "rat", "Rattata", 72, 30, ember));
        other.append("a", NOW, BattleDesk.start(other.state(), ADMIN, "b1", Battle.Type.WILD, BattleSettings.defaults(), ps, cs));
        other.append("a", NOW, BattleDesk.openRound(other.state(), rolls, Map.of(), NOW, seeds));
        other.append("a", NOW, RollDesk.enter(other.state(), ADMIN, "b1-r1-init-ana", List.of(3), null));
        other.append("a", NOW, RollDesk.enter(other.state(), ADMIN, "b1-r1-init-rat", List.of(4), null));
        other.append("a", NOW, BattleDesk.setOrder(other.state(), NOW, seeds));
        other.append("a", NOW, BattleDesk.declare(other.state(), ADMIN, "ana-1", Action.move("ember", "rat-1"), NOW));
        other.append("a", NOW, BattleDesk.declare(other.state(), ADMIN, "rat-1", Action.skip(), NOW));
        other.append("a", NOW, BattleDesk.startResolving(other.state()));
        // the first in the queue is Rattata (skip, priority 0, faster), then Ana: let Rattata go and faint Ana
        other.append("a", NOW, BattleDesk.resolveNext(other.state()));
        other.append("a", NOW, BattleDesk.change(other.state(), ADMIN, other.state().battle().combatant("ana-1").withHp(0)));
        return other.state();
    }

    @Test
    void aMoveWhoseOnlyTargetHasFaintedFails() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        play(ADMIN, BattleDesk.startResolving(log.state()));
        for (int i = 0; i < 3; i++) {
            play(ADMIN, BattleDesk.resolveNext(log.state())); // Cy, Dee, Eli
        }
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("rat-1").withHp(0)));

        List<GameEvent> bo = BattleDesk.resolveNext(log.state());
        assertEquals(Result.FAILED_NO_TARGET, ((GameEvent.ActionResolved) bo.get(0)).result());
        play(ADMIN, bo);
        assertEquals("Bo's Squirtle uses Quick Attack, but it has no target left.", battle().log().get(battle().log().size() - 1));
        assertEquals(30, battle().combatant("bo-1").moves().get(0).pp(), "a failed move spends no PP");
        assertThrows(InvalidEventException.class,
                () -> GameReducer.apply(log.state(), new GameEvent.ActionResolved("b1", 1, "rat-1", Result.DONE)));
    }

    // ---------------------------------------------------------------------------------------- the rules for each action

    @Test
    void aMoveNeedsPpAndStruggleOnlyComesWhenNoMoveHasPpLeft() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        Combatant ana = battle().combatant("ana-1");
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, ana.withMoves(List.of(
                new MoveSlot("ember", "Ember", 0, 25, 0, "normal"), new MoveSlot("growl", "Growl", 3, 40, 0, "allAdjacentFoes")))));

        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.move("ember", "rat-1"), NOW));
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.struggle("rat-1"), NOW));
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.move("flamethrower", "rat-1"), NOW));
        declare(ANA, "ana-1", Action.move("growl"));

        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("ana-1").withMoves(List.of(
                new MoveSlot("ember", "Ember", 0, 25, 0, "normal"), new MoveSlot("growl", "Growl", 0, 40, 0, "allAdjacentFoes")))));
        declare(ANA, "ana-1", Action.struggle("rat-1"));
        assertEquals(Action.struggle("rat-1"), battle().declared().get("ana-1"));
    }

    @Test
    void struggleIsResolvedWithoutSpendingPp() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("ana-1").withMoves(List.of(new MoveSlot("ember", "Ember", 0, 25, 0, "normal")))));
        declare(ANA, "ana-1", Action.struggle("rat-1"));
        for (String id : List.of("bo-1", "cy-1", "dee-1", "eli-1", "rat-1")) {
            declare(ADMIN, id, Action.skip());
        }
        resolveRound();
        assertTrue(battle().log().contains("Ana's Charmander has no moves left and uses Struggle on wild Rattata."));
        assertEquals(0, battle().combatant("ana-1").moves().get(0).pp());
    }

    @Test
    void ppCanBeSwitchedOff() {
        startBattle(BattleSettings.defaults().withPpEnabled(false));
        openRound(exampleDice());
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("ana-1").withMoves(List.of(new MoveSlot("ember", "Ember", 0, 25, 0, "normal")))));
        declare(ANA, "ana-1", Action.move("ember", "rat-1"));
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.struggle("rat-1"), NOW));
    }

    @Test
    void onlyLegalTargetsAreAccepted() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        Combatant ana = battle().combatant("ana-1");
        assertEquals(List.of("rat-1"), battle().legalTargets(ana, Battle.TargetKind.FOE));
        assertEquals(List.of("bo-1", "cy-1", "dee-1", "eli-1"), battle().legalTargets(ana, Battle.TargetKind.ALLY));
        assertEquals(List.of("ana-1", "bo-1", "cy-1", "dee-1", "eli-1"), battle().legalTargets(ana, Battle.TargetKind.ALLY_OR_SELF));

        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.move("ember", "bo-1"), NOW)); // an ally
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.move("ember"), NOW)); // no target
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.move("growl", "rat-1"), NOW)); // a group move picks nobody
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.move("swords", "rat-1"), NOW)); // a self move
        declare(ANA, "ana-1", Action.move("growl"));
        declare(ANA, "ana-1", Action.move("swords"));
        declare(ANA, "ana-1", Action.move("swords", "ana-1"));
    }

    @Test
    void aGroupMoveLogsEveryoneItReaches() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declare(ANA, "ana-1", Action.move("growl"));
        for (String id : List.of("bo-1", "cy-1", "dee-1", "eli-1", "rat-1")) {
            declare(ADMIN, id, Action.skip());
        }
        resolveRound();
        assertTrue(battle().log().contains("Ana's Charmander uses Growl on wild Rattata."), battle().log().toString());
    }

    @Test
    void aSwitchNeedsAHealthyPokemonFromTheReserve() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        Actor cy = Actor.player("u-cy");
        refused(() -> BattleDesk.declare(log.state(), cy, "cy-1", Action.switchTo("ana-1"), NOW)); // not in the reserve
        refused(() -> BattleDesk.declare(log.state(), cy, "cy-1", Action.switchTo("cy-1"), NOW)); // already on the field
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.switchTo("cy-2"), NOW)); // nobody to switch to
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("cy-2").withHp(0)));
        refused(() -> BattleDesk.declare(log.state(), cy, "cy-1", Action.switchTo("cy-2"), NOW)); // fainted
    }

    @Test
    void anItemMustBeInTheBagAndUsedOnOwnPokemon() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        Actor dee = Actor.player("u-dee");
        refused(() -> BattleDesk.declare(log.state(), dee, "dee-1", Action.item("revive", "dee-1"), NOW)); // not in the bag
        refused(() -> BattleDesk.declare(log.state(), dee, "dee-1", Action.item("potion", "ana-1"), NOW)); // not Dee's
        declare(dee, "dee-1", Action.item("potion", "dee-1"));
        refused(() -> BattleDesk.declare(log.state(), ADMIN, "rat-1", Action.item("potion", "rat-1"), NOW)); // a wild Pokémon has no bag
    }

    @Test
    void aBallCanOnlyBeThrownAtAWildPokemonThatIsNotABoss() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.ball("master_ball", "rat-1"), NOW)); // none in the bag
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.ball("poke_ball", "bo-1"), NOW)); // a friend
        declare(ANA, "ana-1", Action.ball("poke_ball", "rat-1"));

        play(ADMIN, BattleDesk.change(log.state(), ADMIN, new Combatant("rat-1", "rat", "Rattata", 20, 30, 30, 72, true, List.of())));
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.ball("poke_ball", "rat-1"), NOW)); // a boss
    }

    @Test
    void catchingCanBeSwitchedOff() {
        startBattle(BattleSettings.defaults().withCatch(false, false));
        openRound(exampleDice());
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.ball("poke_ball", "rat-1"), NOW));
    }

    @Test
    void fleeingFromABossNeedsTheSetting() {
        startBattle(BattleSettings.defaults().withFlee(false, false));
        openRound(exampleDice());
        declare(ANA, "ana-1", Action.flee()); // running from an ordinary wild Pokémon is allowed
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, new Combatant("rat-1", "rat", "Rattata", 20, 30, 30, 72, true, List.of())));
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.flee(), NOW));
    }

    @Test
    void fleeingFromABossOrATrainerNeedsTheSettings() {
        GameLog other = new GameLog(GameLog.Settings.defaults());
        other.append("system", 1, List.of(new StatusChanged(GameStatus.RUNNING), new TrainerSet("ana", Fixtures.trainer("Ana"))));
        MoveSlot tackle = move("tackle", "Tackle", 35, 0, "normal");
        Participant npc = new Participant("brock", Kind.NPC, 1, "Brock", null, null, List.of("onix-1"), List.of());
        List<Combatant> cs = List.of(mon("ana-1", "ana", "Charmander", 60, 40, tackle), mon("onix-1", "brock", "Onix", 30, 60, tackle));
        for (boolean allowed : new boolean[] {false, true}) {
            GameLog run = new GameLog(GameLog.Settings.defaults());
            run.append("system", 1, List.of(new StatusChanged(GameStatus.RUNNING), new TrainerSet("ana", Fixtures.trainer("Ana"))));
            run.append("a", NOW, BattleDesk.start(run.state(), ADMIN, "n1", Battle.Type.NPC_TRAINER,
                    BattleSettings.defaults().withFlee(allowed, true), List.of(trainer("ana", "u-ana", List.of("ana-1"), List.of()), npc), cs));
            run.append("a", NOW, BattleDesk.openRound(run.state(), rolls, Map.of(), NOW, seeds));
            run.append("a", NOW, RollDesk.enter(run.state(), ADMIN, "n1-r1-init-ana", List.of(5), null));
            run.append("a", NOW, RollDesk.enter(run.state(), ADMIN, "n1-r1-init-brock", List.of(5), null));
            run.append("a", NOW, BattleDesk.setOrder(run.state(), NOW, seeds));
            if (allowed) {
                run.append("a", NOW, BattleDesk.declare(run.state(), ANA, "ana-1", Action.flee(), NOW));
            } else {
                refused(() -> BattleDesk.declare(run.state(), ANA, "ana-1", Action.flee(), NOW));
            }
        }
    }

    // ----------------------------------------------------------------------------------------------- who may do what

    @Test
    void onlyTheOwnerOrTheAdminChoosesForAPokemon() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        BattleRefusedException e = refused(() -> BattleDesk.declare(log.state(), BO, "ana-1", Action.skip(), NOW));
        assertEquals("This is not your Pokémon", e.getMessage());
        declare(ADMIN, "ana-1", Action.skip());
        declare(ANA, "ana-1", Action.move("ember", "rat-1")); // changing one's mind is fine
        assertEquals(Action.move("ember", "rat-1"), battle().declared().get("ana-1"));
    }

    @Test
    void declaringIsHiddenFromOtherPlayersButNotFromTheAdmin() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declare(ANA, "ana-1", Action.move("ember", "rat-1"));
        declare(BO, "bo-1", Action.skip());

        assertEquals(Map.of("ana-1", Action.move("ember", "rat-1")), BattleDesk.visibleDeclarations(battle(), ANA));
        assertEquals(Map.of("bo-1", Action.skip()), BattleDesk.visibleDeclarations(battle(), BO));
        assertEquals(2, BattleDesk.visibleDeclarations(battle(), ADMIN).size());
        assertTrue(BattleDesk.visibleDeclarations(battle(), Actor.player("u-spectator")).isEmpty());
    }

    @Test
    void declaringIsOpenWhenTheSettingIsOff() {
        startBattle(BattleSettings.defaults().withDeclareHidden(false));
        openRound(exampleDice());
        declare(ANA, "ana-1", Action.skip());
        assertEquals(Map.of("ana-1", Action.skip()), BattleDesk.visibleDeclarations(battle(), BO));
    }

    @Test
    void resolvingWaitsForEveryone() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declare(ANA, "ana-1", Action.skip());
        assertFalse(BattleDesk.allDeclared(log.state()));
        BattleRefusedException e = refused(() -> BattleDesk.startResolving(log.state()));
        assertTrue(e.getMessage().startsWith("Waiting for Bo's Squirtle"), e.getMessage());
    }

    @Test
    void theStepsCannotBeSkipped() {
        startBattle(BattleSettings.defaults());
        refused(() -> BattleDesk.startResolving(log.state()));
        refused(() -> BattleDesk.setOrder(log.state(), NOW, seeds));
        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        refused(() -> BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds)); // the round is not over
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.skip(), NOW)); // initiative is not set
        refused(() -> BattleDesk.setOrder(log.state(), NOW, seeds)); // wait: nobody has rolled — the reducer refuses
        assertFalse(BattleDesk.initiativeReady(log.state()));
    }

    // ---------------------------------------------------------------------------------- replacements, ending, one at a time

    @Test
    void aFaintedPokemonIsReplacedFromTheReserveWithoutUsingAnAction() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("cy-1").withHp(0)));

        refused(() -> BattleDesk.sendReplacement(log.state(), BO, "cy", "cy-2")); // not Bo's team
        refused(() -> BattleDesk.sendReplacement(log.state(), Actor.player("u-cy"), "ana", "cy-2")); // nobody fainted there
        play(Actor.player("u-cy"), BattleDesk.sendReplacement(log.state(), Actor.player("u-cy"), "cy", "cy-2"));
        assertEquals(List.of("cy-2"), battle().participants().get("cy").active());
        assertEquals(List.of("cy-1"), battle().participants().get("cy").reserve());
        assertTrue(battle().log().contains("Cy sends out Pidgey for Bulbasaur."));
        // the new Pokémon has to choose like everybody else this round
        assertFalse(BattleDesk.allDeclared(log.state()));
        assertTrue(BattleDesk.allDeclared(declaredAll()));
    }

    private GameState declaredAll() {
        for (String id : List.of("ana-1", "bo-1", "cy-2", "dee-1", "eli-1", "rat-1")) {
            declare(ADMIN, id, Action.skip());
        }
        return log.state();
    }

    @Test
    void aPokemonThatFaintedBeforeTheFirstRoundCanBeReplacedToo() {
        startBattle(BattleSettings.defaults());
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("cy-1").withHp(0)));
        refused(() -> BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds)); // Cy must send in a replacement first
        play(ADMIN, BattleDesk.sendReplacement(log.state(), Actor.player("u-cy"), "cy", "cy-2"));
        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        assertEquals(1, battle().round());
    }

    @Test
    void aNewRoundWaitsForTheReplacementOfAFaintedPokemon() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        play(ADMIN, BattleDesk.startResolving(log.state()));
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("dee-1").withHp(0)));
        while (!battle().queue().isEmpty()) {
            play(ADMIN, BattleDesk.resolveNext(log.state()));
        }
        play(ADMIN, BattleDesk.endRound(log.state()));
        // Dee has nobody else left: the battle goes on without Dee
        Battle b = battle();
        assertTrue(b.out(b.participants().get("dee")));
        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        assertFalse(battle().slots().stream().anyMatch(s -> s.participantId().equals("dee")), "a trainer who is out does not roll");

        // Cy fainting with a healthy reserve must send in a replacement first
        GameLog other = new GameLog(GameLog.Settings.defaults());
        other.append("system", 1, List.of(new StatusChanged(GameStatus.RUNNING), new TrainerSet("ana", Fixtures.trainer("Ana"))));
        MoveSlot tackle = move("tackle", "Tackle", 35, 0, "normal");
        other.append("a", NOW, BattleDesk.start(other.state(), ADMIN, "b2", Battle.Type.WILD, BattleSettings.defaults(),
                List.of(trainer("ana", "u-ana", List.of("ana-1"), List.of("ana-2")), wild("rat", "Rattata", "rat-1")),
                List.of(mon("ana-1", "ana", "Charmander", 60, 40, tackle), mon("ana-2", "ana", "Pidgey", 56, 40, tackle),
                        mon("rat-1", "rat", "Rattata", 72, 30, tackle))));
        other.append("a", NOW, BattleDesk.change(other.state(), ADMIN, other.state().battle().combatant("ana-1").withHp(0)));
        BattleRefusedException e = assertThrows(BattleRefusedException.class, () -> BattleDesk.openRound(other.state(), rolls, Map.of(), NOW, seeds));
        assertTrue(e.getMessage().contains("replacement"), e.getMessage());
    }

    @Test
    void theBattleEndsWhenOnlyOneSideIsLeftOrWhenTheAdminSaysSo() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        assertTrue(BattleDesk.endIfOver(log.state()).isEmpty());
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("rat-1").withHp(0)));
        List<GameEvent> over = BattleDesk.endIfOver(log.state());
        assertEquals(1, over.stream().filter(e -> e instanceof GameEvent.BattleEnded).count());
        play(ADMIN, over);
        assertEquals(Step.ENDED, battle().step());
        assertEquals("The battle ends: only one side is left.", battle().log().get(battle().log().size() - 1));
        refused(() -> BattleDesk.declare(log.state(), ANA, "ana-1", Action.skip(), NOW));
        assertTrue(BattleDesk.endIfOver(log.state()).isEmpty());
    }

    @Test
    void theAdminEndsABattleAndWithdrawsTheRollsNobodyAnswered() {
        startBattle(BattleSettings.defaults());
        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        assertEquals(6, log.state().pendingRolls().size());
        refused(() -> BattleDesk.end(log.state(), ANA, "I want to go home"));
        play(ADMIN, BattleDesk.end(log.state(), ADMIN, "the group had to leave"));
        assertTrue(log.state().pendingRolls().isEmpty());
        assertEquals("The battle ends: the group had to leave.", battle().log().get(battle().log().size() - 1));
    }

    @Test
    void onlyOneBattleRunsAtATime() {
        startBattle(BattleSettings.defaults());
        MoveSlot tackle = move("tackle", "Tackle", 35, 0, "normal");
        List<Participant> ps = List.of(trainer("ana", "u-ana", List.of("x-1"), List.of()), wild("w", "Rattata", "x-2"));
        List<Combatant> cs = List.of(mon("x-1", "ana", "Charmander", 60, 40, tackle), mon("x-2", "w", "Rattata", 72, 30, tackle));
        BattleRefusedException e = refused(() -> BattleDesk.start(log.state(), ADMIN, "b2", Battle.Type.WILD, BattleSettings.defaults(), ps, cs));
        assertTrue(e.getMessage().contains("only one battle"), e.getMessage());
        play(ADMIN, BattleDesk.end(log.state(), ADMIN, "next!"));
        play(ADMIN, BattleDesk.start(log.state(), ADMIN, "b2", Battle.Type.WILD, BattleSettings.defaults(), ps, cs));
        assertEquals("b2", battle().battleId());
        assertEquals(0, battle().round());
    }

    @Test
    void aBattleIsCheckedWhenItStarts() {
        MoveSlot tackle = move("tackle", "Tackle", 35, 0, "normal");
        Participant ana = trainer("ana", "u-ana", List.of("a-1"), List.of());
        Participant rat = wild("w", "Rattata", "w-1");
        Combatant a = mon("a-1", "ana", "Charmander", 60, 40, tackle);
        Combatant w = mon("w-1", "w", "Rattata", 72, 30, tackle);
        BattleSettings rules = BattleSettings.defaults();
        refused(() -> BattleDesk.start(log.state(), ANA, "x", Battle.Type.WILD, rules, List.of(ana, rat), List.of(a, w))); // not the admin
        refused(() -> BattleDesk.start(log.state(), ADMIN, "x", Battle.Type.WILD, rules, List.of(ana), List.of(a))); // one side
        refused(() -> BattleDesk.start(log.state(), ADMIN, "x", Battle.Type.WILD, rules, List.of(rat), List.of(w))); // no trainer
        refused(() -> BattleDesk.start(log.state(), ADMIN, "x", Battle.Type.WILD, rules, List.of(ana, rat), List.of(a))); // a Pokémon missing
        refused(() -> BattleDesk.start(log.state(), ADMIN, "x", Battle.Type.WILD, rules,
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "nobody", "u-ana", List.of("a-1"), List.of()), rat), List.of(a, w)));
        refused(() -> BattleDesk.start(log.state(), ADMIN, "x", Battle.Type.WILD, rules,
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("a-1", "a-2"), List.of()), rat),
                List.of(a, mon("a-2", "ana", "Pidgey", 56, 40, tackle), w))); // two active but combat.activePerTrainer is 1
        play(ADMIN, BattleDesk.start(log.state(), ADMIN, "x", Battle.Type.WILD, rules.withActivePerTrainer(2),
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("a-1", "a-2"), List.of()), rat),
                List.of(a, mon("a-2", "ana", "Pidgey", 56, 40, tackle), w)));
        assertEquals(Step.START, battle().step());
    }

    @Test
    void twoPokemonOnTheFieldOfOneTrainerActInTheirSlotOrder() {
        MoveSlot tackle = move("tackle", "Tackle", 35, 0, "normal");
        GameLog doubles = new GameLog(GameLog.Settings.defaults());
        doubles.append("system", 1, List.of(new StatusChanged(GameStatus.RUNNING), new TrainerSet("ana", Fixtures.trainer("Ana"))));
        doubles.append("a", NOW, BattleDesk.start(doubles.state(), ADMIN, "d1", Battle.Type.WILD, BattleSettings.defaults().withActivePerTrainer(2),
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("a-1", "a-2"), List.of()), wild("w", "Rattata", "w-1")),
                List.of(mon("a-1", "ana", "Charmander", 20, 40, tackle), mon("a-2", "ana", "Pidgey", 90, 40, tackle), mon("w-1", "w", "Rattata", 72, 30, tackle))));
        doubles.append("a", NOW, BattleDesk.openRound(doubles.state(), rolls, Map.of(), NOW, seeds));
        // the participant rolls once, with its fastest Pokémon: Pidgey, Speed 90, +4
        Battle.InitiativeSlot slot = doubles.state().battle().slots().stream().filter(s -> s.participantId().equals("ana")).findFirst().orElseThrow();
        assertEquals(4, slot.modifier());
        assertEquals(90, slot.speed());
        doubles.append("a", NOW, RollDesk.enter(doubles.state(), ADMIN, "d1-r1-init-ana", List.of(10), null));
        doubles.append("a", NOW, RollDesk.enter(doubles.state(), ADMIN, "d1-r1-init-w", List.of(10), null));
        doubles.append("a", NOW, BattleDesk.setOrder(doubles.state(), NOW, seeds));
        doubles.append("a", NOW, BattleDesk.declare(doubles.state(), ANA, "a-2", Action.move("tackle", "w-1"), NOW));
        doubles.append("a", NOW, BattleDesk.declare(doubles.state(), ANA, "a-1", Action.move("tackle", "w-1"), NOW));
        doubles.append("a", NOW, BattleDesk.declare(doubles.state(), ADMIN, "w-1", Action.skip(), NOW));
        doubles.append("a", NOW, BattleDesk.startResolving(doubles.state()));
        assertEquals(List.of("a-1", "a-2", "w-1").size(), doubles.state().battle().queue().size());
        assertEquals("a-1", doubles.state().battle().queue().get(0), "the first slot acts before the second");
    }

    // ------------------------------------------------------------------------------------------------------------ the log

    @Test
    void theBattleLogReplaysFromTheEvents() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        resolveRound();
        play(ADMIN, BattleDesk.change(log.state(), ADMIN, battle().combatant("rat-1").withHp(0)));
        play(ADMIN, BattleDesk.endIfOver(log.state()));

        GameState now = log.state();
        assertFalse(now.battle().log().isEmpty());
        assertEquals(now, log.replay(), "replaying every event gives the same state, log included");
        assertEquals(now.battle().log(), log.replay().battle().log());

        // as the database gives it back: the stored JSON of every event, read again
        GameLog restored = GameLog.restore(log.settings(), log.entries(), log.snapshots(), log.audit());
        assertEquals(now, restored.state());
        assertEquals(now.battle().log(), restored.state().battle().log());

        // and the state itself can be stored and read (snapshots)
        assertEquals(now, GameStateJson.read(GameStateJson.write(now)));
    }

    @Test
    void undoTakesTheBattleBackAStepAndRedoBringsItForward() {
        startBattle(BattleSettings.defaults());
        openRound(exampleDice());
        declareExampleActions();
        play(ADMIN, BattleDesk.startResolving(log.state()));
        List<String> before = battle().log();

        play(ADMIN, BattleDesk.resolveNext(log.state()));
        assertEquals(before.size() + 1, battle().log().size());
        log.undo("u-dani", NOW, 1);
        assertEquals(before, battle().log());
        assertEquals(6, battle().queue().size());
        log.redo("u-dani", NOW, 1);
        assertEquals(5, battle().queue().size());
        assertEquals(log.state(), log.replay());
    }

    @Test
    void theSettingsAreReadFromTheirKeys() {
        BattleSettings s = BattleSettings.fromValues(Map.of(
                "combat.activePerTrainer", "2",
                "combat.initiative.speedDivisor", "10",
                "combat.initiative.rerollEachRound", "false",
                "combat.order.trainerActionsFirst", "false",
                "combat.declare.hidden", "false",
                "combat.turnTimer.enabled", "true",
                "combat.turnTimer.seconds", "45",
                "combat.turnTimer.onTimeout", "autoMove",
                "combat.pp.enabled", "false"));
        assertEquals(2, s.activePerTrainer());
        assertEquals(10, s.speedDivisor());
        assertFalse(s.rerollEachRound());
        assertFalse(s.trainerActionsFirst());
        assertFalse(s.declareHidden());
        assertTrue(s.turnTimerEnabled());
        assertEquals(45, s.turnTimerSeconds());
        assertEquals(BattleSettings.OnTimeout.AUTO_MOVE, s.onTimeout());
        assertFalse(s.ppEnabled());
        assertTrue(s.catchEnabled(), "a key that is missing keeps its default");
        assertEquals(BattleSettings.defaults(), BattleSettings.fromValues(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> BattleSettings.fromValues(Map.of("combat.turnTimer.onTimeout", "panic")));
        assertThrows(IllegalArgumentException.class, () -> BattleSettings.fromValues(Map.of("combat.pp.enabled", "maybe")));
        assertThrows(IllegalArgumentException.class, () -> BattleSettings.fromValues(Map.of("combat.initiative.speedDivisor", "0")));
    }

    @Test
    void theSpeedDivisorChangesTheInitiativeModifier() {
        startBattle(BattleSettings.defaults().withSpeedDivisor(10));
        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of("ana", 2), NOW, seeds));
        Map<String, Integer> modifiers = new LinkedHashMap<>();
        battle().slots().forEach(s -> modifiers.put(s.participantId(), s.modifier()));
        assertEquals(6 + 2, modifiers.get("ana"), "Speed 60 / 10, plus the bonus of a class feature");
        assertEquals(9, modifiers.get("dee"));
        assertEquals(7, modifiers.get("rat"));
    }

    @Test
    void theRequestsForInitiativeAreAskedFromTheRightPeople() {
        startBattle(BattleSettings.defaults());
        play(ADMIN, BattleDesk.openRound(log.state(), rolls, Map.of(), NOW, seeds));
        GameState.RollRequest anas = log.state().pendingRolls().get("b1-r1-init-ana");
        assertEquals(GameState.Roller.PLAYER, anas.roller());
        assertEquals("u-ana", anas.rollerUserId());
        assertEquals("ana", anas.trainerId());
        assertEquals(net.quimmilho.pokednd.core.dice.Dice.d(20), anas.dice());
        assertTrue(anas.explanation().contains("add +3 to the die"), anas.explanation());
        GameState.RollRequest rats = log.state().pendingRolls().get("b1-r1-init-rat");
        assertEquals(GameState.Roller.ADMIN, rats.roller(), "the enemy's roll is made by the admin with real dice in this setup");
        assertEquals(GameState.RollClass.ENEMY, rats.rollClass());
        assertEquals("ana", rats.trainerId(), "an enemy roll is kept under the first trainer of the battle");
    }

    @Test
    void enemyInitiativeIsRolledByTheSystemWhenTheSettingsSayAuto() {
        RollSettings auto = RollSettings.defaults();
        startBattle(BattleSettings.defaults());
        play(ADMIN, BattleDesk.openRound(log.state(), auto, Map.of(), NOW, seeds));
        assertTrue(log.state().rolls().stream().anyMatch(r -> r.requestId().equals("b1-r1-init-rat") && r.source().equals("digital")));
        assertFalse(BattleDesk.initiativeReady(log.state()), "the players have not rolled yet");
        for (String id : List.of("ana", "bo", "cy", "dee", "eli")) {
            play(ADMIN, RollDesk.rollDigital(log.state(), auto, ADMIN, "b1-r1-init-" + id, 40 + id.length()));
        }
        assertTrue(BattleDesk.initiativeReady(log.state()));
    }
}
