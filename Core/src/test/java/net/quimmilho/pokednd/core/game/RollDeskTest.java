/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.function.LongSupplier;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.dice.DiceChoice;
import net.quimmilho.pokednd.core.dice.DiceSettings;
import net.quimmilho.pokednd.core.dice.SmartDice;
import net.quimmilho.pokednd.core.game.GameEvent.RollRequested;
import net.quimmilho.pokednd.core.game.GameEvent.StatusChanged;
import net.quimmilho.pokednd.core.game.GameEvent.TrainerSet;
import net.quimmilho.pokednd.core.game.GameState.Attempt;
import net.quimmilho.pokednd.core.game.GameState.RollClass;
import net.quimmilho.pokednd.core.game.GameState.RollRequest;
import net.quimmilho.pokednd.core.game.GameState.RollResult;
import net.quimmilho.pokednd.core.game.GameState.RollStatus;
import net.quimmilho.pokednd.core.game.GameState.Roller;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.game.RollSettings.InputMode;
import net.quimmilho.pokednd.core.game.RollSettings.SystemRolls;
import org.junit.jupiter.api.Test;

/**
 * Phase 43: every way of answering a roll request is a scripted scenario here. Each scenario plays through {@link GameLog}, as the
 * server will, so the events are also checked by the reducer, the log and the audit trail.
 */
class RollDeskTest {

    private static final Actor ADMIN = Actor.admin("u-dani");
    private static final Actor ANA = Actor.player("u-ana");
    private static final Actor BOB = Actor.player("u-bob");
    private static final long NOW = 1_000_000;

    private final RollSettings defaults = RollSettings.defaults();
    private final GameLog log = new GameLog(GameLog.Settings.defaults());
    private final LongSupplier seeds = DigitalRoller.seeds(RollSettings.RngSource.SEEDED, 99);

    RollDeskTest() {
        log.append("system", 1, List.of(new StatusChanged(GameStatus.RUNNING), new TrainerSet("ana", Fixtures.trainer("Ana"))));
    }

    private void play(Actor actor, List<GameEvent> events) {
        log.append(actor.userId(), NOW, events);
    }

    private RollRequested ask(String id, Dice dice) {
        return new RollRequested(id, "ana", dice, "Scratch", Roller.PLAYER, "u-ana", RollClass.NORMAL, List.of(), null, List.of(), "", 0);
    }

    private void open(RollSettings settings, RollRequested ask) {
        play(ADMIN, RollDesk.open(log.state(), settings, ask, NOW, seeds));
    }

    private RollResult result(String id) {
        return log.state().rolls().stream().filter(r -> r.requestId().equals(id)).findFirst().orElseThrow();
    }

    private RollRequest request(String id) {
        return log.state().pendingRolls().get(id);
    }

    private RollRefusedException refused(Runnable action) {
        return assertThrows(RollRefusedException.class, action::run);
    }

    // ----------------------------------------------------------------------------------------------- the three input modes

    @Test
    void theAdminEntersTheResultForAnyone() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ADMIN, RollDesk.enter(log.state(), ADMIN, "r1", List.of(14), null));
        RollResult r = result("r1");
        assertEquals("admin", r.source());
        assertEquals(14, r.total());
        assertNull(r.approvedBy());
        assertTrue(log.state().pendingRolls().isEmpty());
    }

    @Test
    void theAdminCanEnterEvenWhenEveryWayOfAnsweringIsSwitchedOffForPlayers() {
        RollSettings onlyDigital = defaults.withInputModes(EnumSet.of(InputMode.DIGITAL));
        open(onlyDigital, ask("r1", Dice.d(20)));
        play(ADMIN, RollDesk.enter(log.state(), ADMIN, "r1", List.of(3), null));
        assertEquals(3, result("r1").total());
    }

    @Test
    void aPlayerTypesTheResultAndTheAdminApprovesIt() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(17), null, NOW));
        assertEquals(RollStatus.SUBMITTED, request("r1").status());
        assertEquals(List.of(17), request("r1").submission().faces());
        assertTrue(log.state().rolls().isEmpty(), "nothing counts before the admin approves");

        play(ADMIN, RollDesk.approve(log.state(), ADMIN, "r1"));
        RollResult r = result("r1");
        assertEquals("typed", r.source());
        assertEquals(17, r.total());
        assertEquals("u-dani", r.approvedBy());
        assertFalse(r.edited());
        assertTrue(r.history().isEmpty());
    }

    @Test
    void theAdminEditsWhatThePlayerTypedAndBothValuesStayInTheLog() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(17), null, NOW));
        play(ADMIN, RollDesk.edit(log.state(), ADMIN, "r1", List.of(7), null));
        RollResult r = result("r1");
        assertEquals(7, r.total());
        assertTrue(r.edited());
        assertEquals(List.of(new Attempt(List.of(17), null, "typed", "edited", "")), r.history());
    }

    @Test
    void anEditThatChangesNothingIsAnApproval() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(17), null, NOW));
        play(ADMIN, RollDesk.edit(log.state(), ADMIN, "r1", List.of(17), null));
        assertFalse(result("r1").edited());
        assertTrue(result("r1").history().isEmpty());
    }

    @Test
    void aRejectedResultAsksThePlayerAgain() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(20), null, NOW));
        play(ADMIN, RollDesk.reject(log.state(), ADMIN, "r1", "Roll on the table, please"));
        assertEquals(RollStatus.REQUESTED, request("r1").status());
        assertNull(request("r1").submission());

        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(6), null, NOW + 5000));
        play(ADMIN, RollDesk.approve(log.state(), ADMIN, "r1"));
        RollResult r = result("r1");
        assertEquals(6, r.total());
        assertEquals(List.of(new Attempt(List.of(20), null, "typed", "rejected", "Roll on the table, please")), r.history());
    }

    @Test
    void withoutApprovalWhatThePlayerTypesCountsAtOnce() {
        RollSettings noApproval = defaults.withApproval(false, 0);
        open(noApproval, ask("r1", Dice.d(20)));
        List<GameEvent> events = RollDesk.submit(log.state(), noApproval, ANA, "r1", List.of(11), null, NOW);
        play(ANA, events);
        assertTrue(log.state().pendingRolls().isEmpty());
        RollResult r = result("r1");
        assertEquals("typed", r.source());
        assertEquals(11, r.total());
        assertNull(r.approvedBy());
    }

    @Test
    void aTypedResultIsApprovedByTheClockAfterTheSecondsInTheSetting() {
        RollSettings auto = defaults.withApproval(true, 30);
        open(auto, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.submit(log.state(), auto, ANA, "r1", List.of(9), null, NOW));
        assertTrue(RollDesk.autoApprove(log.state(), auto, NOW + 29_999).isEmpty());
        assertTrue(RollDesk.autoApprove(log.state(), defaults, NOW + 99_999).isEmpty(), "0 seconds means never");
        play(ADMIN, RollDesk.autoApprove(log.state(), auto, NOW + 30_000));
        assertEquals(RollDesk.AUTO_APPROVER, result("r1").approvedBy());
        assertEquals(9, result("r1").total());
    }

    @Test
    void aDigitalRollIsMadeByTheSystemAndNeedsNoApproval() {
        open(defaults, ask("r1", new Dice(2, 8)));
        play(ANA, RollDesk.rollDigital(log.state(), defaults, ANA, "r1", 1234L));
        RollResult r = result("r1");
        assertEquals("digital", r.source());
        assertEquals(1234L, r.seed());
        assertEquals(DigitalRoller.roll(new Dice(2, 8), 1234L), r.faces());
        assertNull(r.approvedBy());
    }

    @Test
    void enemyAndSecretRollsAreMadeByTheSystemInSecret() {
        RollRequested enemy = new RollRequested("e1", "ana", Dice.d(20), "Initiative of the Rattata", Roller.PLAYER, null, RollClass.ENEMY, List.of(), null, List.of(), "", 0);
        play(ADMIN, RollDesk.open(log.state(), defaults, enemy, NOW, seeds));
        assertTrue(log.state().pendingRolls().isEmpty(), "answered at once");
        assertEquals("digital", result("e1").source());
        assertEquals(DigitalRoller.roll(Dice.d(20), result("e1").seed()), result("e1").faces());
    }

    @Test
    void whenTheModeIsAdminPhysicalTheAdminRollsRealDiceForEnemiesAndSecrets() {
        RollSettings physical = defaults.withSystemRolls(SystemRolls.ADMIN_PHYSICAL, SystemRolls.ADMIN_PHYSICAL);
        RollRequested secret = new RollRequested("s1", "ana", Dice.d(100), "Shiny?", Roller.PLAYER, "u-ana", RollClass.SECRET, List.of(), null, List.of(), "", 0);
        play(ADMIN, RollDesk.open(log.state(), physical, secret, NOW, seeds));
        assertEquals(Roller.ADMIN, request("s1").roller());
        assertNull(request("s1").rollerUserId(), "the player is not asked");

        assertTrue(refused(() -> RollDesk.submit(log.state(), physical, ANA, "s1", List.of(50), null, NOW)).getMessage().contains("not for a player"));
        assertTrue(refused(() -> RollDesk.rollDigital(log.state(), physical, ANA, "s1", 5L)).getMessage().contains("real dice"));
        play(ADMIN, RollDesk.enter(log.state(), ADMIN, "s1", List.of(88), null));
        assertEquals("admin", result("s1").source());
    }

    @Test
    void thePlayerMayTypeOnlyTheTotalWhenTheSettingSaysSo() {
        RollSettings totals = defaults.withEntryPerDie(false);
        open(totals, ask("r1", new Dice(2, 8)));
        assertTrue(refused(() -> RollDesk.submit(log.state(), totals, ANA, "r1", List.of(3, 4), null, NOW)).getMessage().contains("total"));
        assertTrue(refused(() -> RollDesk.submit(log.state(), totals, ANA, "r1", List.of(), 17, NOW)).getMessage().contains("2d8 makes a total from 2 to 16"));
        play(ANA, RollDesk.submit(log.state(), totals, ANA, "r1", List.of(), 11, NOW));
        play(ADMIN, RollDesk.approve(log.state(), ADMIN, "r1"));
        assertEquals(11, result("r1").total());
        assertTrue(result("r1").faces().isEmpty());
        assertEquals(11, result("r1").enteredTotal());
    }

    @Test
    void withPerDieEntryTheTotalIsNotAccepted() {
        open(defaults, ask("r1", new Dice(2, 8)));
        assertTrue(refused(() -> RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(), 9, NOW)).getMessage().contains("every die"));
    }

    @Test
    void aRequestWithATimeLimitExpires() {
        RollSettings timed = defaults.withTimeoutSeconds(60);
        open(timed, ask("r1", Dice.d(20)));
        assertEquals(NOW + 60_000, request("r1").deadlineMillis());
        assertTrue(RollDesk.expire(log.state(), NOW + 59_999).isEmpty());
        play(ADMIN, RollDesk.expire(log.state(), NOW + 60_000));
        assertTrue(log.state().pendingRolls().isEmpty());
        assertTrue(log.state().rolls().isEmpty(), "an expired request has no answer");
    }

    @Test
    void theAdminCanCancelARequest() {
        open(defaults, ask("r1", Dice.d(20)));
        assertTrue(refused(() -> RollDesk.cancel(log.state(), ANA, "r1")).getMessage().contains("Only the admin"));
        play(ADMIN, RollDesk.cancel(log.state(), ADMIN, "r1"));
        assertTrue(log.state().pendingRolls().isEmpty());
    }

    // ------------------------------------------------------------------------------------------------------ what is refused

    @Test
    void aValueThatDoesNotFitTheDiceIsRefused() {
        open(defaults, ask("r1", Dice.d(6)));
        assertTrue(refused(() -> RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(7), null, NOW)).getMessage().contains("A d6 shows 1 to 6, not 7"));
        assertTrue(refused(() -> RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(0), null, NOW)).getMessage().contains("not 0"));
        assertTrue(refused(() -> RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(2, 3), null, NOW)).getMessage().contains("needs 1 value"));
        assertTrue(refused(() -> RollDesk.enter(log.state(), ADMIN, "r1", List.of(9), null)).getMessage().contains("not 9"));
        assertTrue(refused(() -> RollDesk.enter(log.state(), ADMIN, "r1", List.of(), null)).getMessage().contains("needs 1 value"));
        assertEquals(RollStatus.REQUESTED, request("r1").status(), "nothing changed");
    }

    @Test
    void anEditCannotSlipInAValueThatDoesNotFit() {
        open(defaults, ask("r1", Dice.d(6)));
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(4), null, NOW));
        assertTrue(refused(() -> RollDesk.edit(log.state(), ADMIN, "r1", List.of(7), null)).getMessage().contains("not 7"));
    }

    @Test
    void onlyThePlayerWhoMustRollCanAnswerAndOnlyTheAdminApproves() {
        open(defaults, ask("r1", Dice.d(20)));
        assertTrue(refused(() -> RollDesk.submit(log.state(), defaults, BOB, "r1", List.of(10), null, NOW)).getMessage().contains("another player"));
        assertTrue(refused(() -> RollDesk.rollDigital(log.state(), defaults, BOB, "r1", 1L)).getMessage().contains("another player"));
        assertTrue(refused(() -> RollDesk.enter(log.state(), ANA, "r1", List.of(10), null)).getMessage().contains("Only the admin"));
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(10), null, NOW));
        assertTrue(refused(() -> RollDesk.approve(log.state(), ANA, "r1")).getMessage().contains("Only the admin"));
        assertTrue(refused(() -> RollDesk.reject(log.state(), ANA, "r1", "")).getMessage().contains("Only the admin"));
        assertTrue(refused(() -> RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(11), null, NOW)).getMessage().contains("already waiting"));
        assertTrue(refused(() -> RollDesk.rollDigital(log.state(), defaults, ANA, "r1", 1L)).getMessage().contains("already waiting"));
    }

    @Test
    void aWayOfAnsweringThatIsSwitchedOffIsRefused() {
        RollSettings noTyping = defaults.withInputModes(EnumSet.of(InputMode.DIGITAL, InputMode.ADMIN_ENTER));
        open(noTyping, ask("r1", Dice.d(20)));
        assertTrue(refused(() -> RollDesk.submit(log.state(), noTyping, ANA, "r1", List.of(10), null, NOW)).getMessage().contains("not allowed"));

        RollSettings noDigital = defaults.withInputModes(EnumSet.of(InputMode.PLAYER_SUBMIT));
        assertTrue(refused(() -> RollDesk.rollDigital(log.state(), noDigital, ANA, "r1", 1L)).getMessage().contains("not allowed"));
    }

    @Test
    void thereIsNothingToApproveBeforeThePlayerTypedSomething() {
        open(defaults, ask("r1", Dice.d(20)));
        assertTrue(refused(() -> RollDesk.approve(log.state(), ADMIN, "r1")).getMessage().contains("No player has typed"));
        assertTrue(refused(() -> RollDesk.reject(log.state(), ADMIN, "r1", "x")).getMessage().contains("No player has typed"));
        assertTrue(refused(() -> RollDesk.approve(log.state(), ADMIN, "nope")).getMessage().contains("no open roll request"));
    }

    @Test
    void aRequestIsAnsweredOnlyOnce() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ADMIN, RollDesk.enter(log.state(), ADMIN, "r1", List.of(5), null));
        assertTrue(refused(() -> RollDesk.enter(log.state(), ADMIN, "r1", List.of(6), null)).getMessage().contains("no open roll request"));
        assertTrue(refused(() -> RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(6), null, NOW)).getMessage().contains("no open roll request"));
    }

    @Test
    void theReducerRefusesDigitalRollsThatDoNotMatchTheirSeed() {
        open(defaults, ask("r1", Dice.d(20)));
        GameState s = log.state();
        List<Integer> real = DigitalRoller.roll(Dice.d(20), 5L);
        int other = real.get(0) == 20 ? 1 : real.get(0) + 1;
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new GameEvent.RollAnswered("r1", List.of(other), "digital", null, null, false, 5L)), "faces that the seed does not give");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new GameEvent.RollAnswered("r1", real, "digital")), "no seed");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new GameEvent.RollAnswered("r1", real, "typed", null, null, false, 5L)), "a seed on a typed roll");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new GameEvent.RollAnswered("r1", List.of(), "digital", 10, null, false, 5L)), "a digital roll with only a total");
        GameReducer.apply(s, new GameEvent.RollAnswered("r1", real, "digital", null, null, false, 5L));
    }

    @Test
    void theReducerKeepsTheEditedFlagHonest() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(10), null, NOW));
        GameState s = log.state();
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new GameEvent.RollAnswered("r1", List.of(12), "typed", null, "u-dani", false, null)), "changed but not marked");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new GameEvent.RollAnswered("r1", List.of(10), "typed", null, "u-dani", true, null)), "marked but not changed");
        assertThrows(InvalidEventException.class, () -> GameReducer.apply(s, new GameEvent.RollAnswered("r1", List.of(10), "typed", null, null, false, null)), "no approver");
    }

    // ------------------------------------------------------------------------------------------------ digital rolls replay

    @Test
    void digitalRollsAreTheSameWhenTheLogIsReplayedStoredAndRestored() {
        open(defaults, ask("r1", new Dice(3, 6)));
        open(defaults, ask("r2", Dice.d(20)));
        play(ANA, RollDesk.rollDigital(log.state(), defaults, ANA, "r1", seeds.getAsLong()));
        play(ANA, RollDesk.rollDigital(log.state(), defaults, ANA, "r2", seeds.getAsLong()));
        GameState original = log.state();
        List<RollResult> rolls = original.rolls();

        assertEquals(original, log.replay(), "replay from the first event");
        assertEquals(original, GameLog.restore(log.settings(), log.entries(), log.snapshots(), log.audit()).state(), "restored from the stored parts");
        assertEquals(original, GameStateJson.read(GameStateJson.write(original)), "a snapshot is lossless");
        GameState again = GameState.empty();
        for (LogEntry e : log.entries()) {
            again = GameReducer.apply(again, EventTypes.read(e.type(), e.payload()));
        }
        assertEquals(rolls, again.rolls(), "the faces come back from the stored events");
        for (RollResult r : rolls) {
            assertEquals(DigitalRoller.roll(r.dice(), r.seed()), r.faces(), "each roll is what its seed gives");
        }
    }

    @Test
    void undoAndRedoDoNotRollAgain() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.rollDigital(log.state(), defaults, ANA, "r1", 777L));
        RollResult first = result("r1");

        log.undo("u-dani", NOW + 1, 1);
        assertTrue(log.state().rolls().isEmpty());
        assertEquals(RollStatus.REQUESTED, request("r1").status(), "the request is open again");
        log.redo("u-dani", NOW + 2, 1);
        assertEquals(first, result("r1"), "the same seed and the same faces");
    }

    @Test
    void theAdminCanChangeOrRerollAnAnsweredRollAndTheOldValueStaysInTheHistory() {
        open(defaults, ask("r1", Dice.d(20)));
        play(ANA, RollDesk.rollDigital(log.state(), defaults, ANA, "r1", 3L));
        List<Integer> before = result("r1").faces();

        assertTrue(refused(() -> RollDesk.override(log.state(), ANA, "r1", null, List.of(1), null, "x")).getMessage().contains("Only the admin"));
        play(ADMIN, RollDesk.override(log.state(), ADMIN, "r1", null, List.of(19), null, "the die rolled off the table"));
        assertEquals(19, result("r1").total());
        assertEquals("admin", result("r1").source());
        assertNull(result("r1").seed());
        assertEquals(new Attempt(before, null, "digital", "replaced", "the die rolled off the table"), result("r1").history().get(0));

        play(ADMIN, RollDesk.reroll(log.state(), ADMIN, "r1", null, 4L, "roll again"));
        assertEquals("digital", result("r1").source());
        assertEquals(DigitalRoller.roll(Dice.d(20), 4L), result("r1").faces());
        assertEquals(2, result("r1").history().size());

        // another die: the old band table no longer fits
        play(ADMIN, RollDesk.override(log.state(), ADMIN, "r1", Dice.d(6), List.of(4), null, "a d6 was used"));
        assertEquals(Dice.d(6), result("r1").dice());
        assertTrue(assertThrows(RollRefusedException.class, () -> RollDesk.override(log.state(), ADMIN, "r1", null, List.of(9), null, "x")).getMessage().contains("not 9"));

        log.undo("u-dani", NOW + 9, 3);
        assertEquals(before, result("r1").faces(), "undo brings back the first digital roll");
    }

    // ---------------------------------------------------------------------------------- the request, its bands and its text

    @Test
    void aSmartRollRequestShowsTheDieTheBandsTheChanceAndTheModifiers() {
        DiceChoice choice = SmartDice.choose(DiceSettings.defaults(), List.of(new SmartDice.Outcome("miss", 0.20), new SmartDice.Outcome("hit", 0.75), new SmartDice.Outcome("crit", 0.05)));
        List<RollModifier> mods = List.of(new RollModifier(RollModifier.Kind.POINTS, "command bonus", 4), new RollModifier(RollModifier.Kind.STAGE, "accuracy stage", -1));
        RollRequested ask = RollRequests.smart("r1", "ana", "Scratch", choice, 0.8, mods, RollClass.NORMAL, "u-ana", true);
        assertEquals(Dice.d(20), ask.dice());
        assertEquals("Roll 1d20 for Scratch · 1–4 miss · 5–19 hit · 20 crit · chance 80 % (+4 pp command bonus, −1 accuracy stage)", ask.explanation());

        open(defaults, ask);
        play(ANA, RollDesk.submit(log.state(), defaults, ANA, "r1", List.of(20), null, NOW));
        play(ADMIN, RollDesk.approve(log.state(), ADMIN, "r1"));
        assertEquals("crit", result("r1").outcome());
    }

    @Test
    void theTrueChanceCanBeHiddenFromThePrompt() {
        DiceChoice choice = SmartDice.choose(DiceSettings.defaults(), List.of(new SmartDice.Outcome("fail", 0.5), new SmartDice.Outcome("success", 0.5)));
        RollRequested ask = RollRequests.smart("r1", "ana", "Flee", choice, 0.5, List.of(), RollClass.NORMAL, "u-ana", false);
        assertFalse(ask.explanation().contains("chance"));
        assertEquals(0.5, ask.trueChance(), "the chance is still on the request for the admin");
    }

    @Test
    void aCertainOutcomeIsNotRolled() {
        DiceChoice sure = SmartDice.choose(DiceSettings.defaults(), List.of(new SmartDice.Outcome("hit", 1.0)));
        assertThrows(IllegalArgumentException.class, () -> RollRequests.smart("r1", "ana", "x", sure, 1.0, List.of(), RollClass.NORMAL, null, true));
    }

    @Test
    void aClassicRollIsAD20WithTheNumberThatIsNeeded() {
        RollRequested ask = RollRequests.classic("r1", "ana", "Insight check", 3, 15, List.of(new RollModifier(RollModifier.Kind.ADD, "Wisdom", 3)), RollClass.NORMAL, "u-ana");
        assertEquals(Dice.d(20), ask.dice());
        assertEquals(List.of(new GameState.Band("fail", 1, 11), new GameState.Band("success", 12, 20)), ask.bands());
        assertEquals(0.45, ask.trueChance(), 1e-9);
        assertEquals("Roll 1d20 for Insight check · 1–11 fail · 12–20 success · chance 45 % (+3 Wisdom) · add +3 to the die · needs 15", ask.explanation());
        RollRequested easy = RollRequests.classic("r2", "ana", "Easy", 10, 5, List.of(), RollClass.NORMAL, null);
        assertEquals(List.of(new GameState.Band("success", 1, 20)), easy.bands());
        RollRequested impossible = RollRequests.classic("r3", "ana", "Hard", 0, 30, List.of(), RollClass.NORMAL, null);
        assertEquals(List.of(new GameState.Band("fail", 1, 20)), impossible.bands());
        RollRequested initiative = RollRequests.classic("r4", "ana", "Initiative", 2, null, List.of(), RollClass.NORMAL, null);
        assertTrue(initiative.bands().isEmpty());
        assertNull(initiative.trueChance());
    }

    @Test
    void aBandThatDoesNotFitTheDiceIsRefused() {
        RollRequested bad = new RollRequested("r1", "ana", Dice.d(6), "x", Roller.PLAYER, null, RollClass.NORMAL, List.of(new GameState.Band("hit", 4, 9)), null, List.of(), "", 0);
        assertThrows(RollRefusedException.class, () -> RollDesk.open(log.state(), defaults, bad, NOW, seeds));
    }

    @Test
    void modifiersReadAsShortText() {
        assertEquals("+4 pp command bonus", new RollModifier(RollModifier.Kind.POINTS, "command bonus", 4).text());
        assertEquals("−1 accuracy stage", new RollModifier(RollModifier.Kind.STAGE, "accuracy stage", -1).text());
        assertEquals("×1.25 Great Ball", new RollModifier(RollModifier.Kind.MULTIPLIER, "Great Ball", 1.25).text());
        assertEquals("+3 Dexterity", new RollModifier(RollModifier.Kind.ADD, "Dexterity", 3).text());
        assertEquals("−2", new RollModifier(RollModifier.Kind.ADD, "", -2).text());
    }

    // -------------------------------------------------------------------------------------------------- settings and old logs

    @Test
    void theSettingsDefaultsAreThoseOfChapter6_8AndTheTextFormsParse() {
        RollSettings s = RollSettings.defaults();
        assertEquals("playerSubmit,digital,adminEnter", s.inputModesText());
        assertTrue(s.approvalRequired() && s.entryPerDie() && s.showTrueChance());
        assertEquals(0, s.approvalAutoSeconds());
        assertEquals(0, s.timeoutSeconds());
        assertEquals(SystemRolls.AUTO, s.enemyMode());
        assertEquals(RollSettings.RngSource.SECURE, s.rngSource());
        assertEquals(EnumSet.of(InputMode.DIGITAL, InputMode.ADMIN_ENTER), RollSettings.parseInputModes("digital, adminEnter"));
        assertThrows(IllegalArgumentException.class, () -> RollSettings.parseInputModes("telepathy"));
        assertThrows(IllegalArgumentException.class, () -> RollSettings.parseInputModes(""));
        assertEquals(SystemRolls.ADMIN_PHYSICAL, RollSettings.parseSystemRolls("adminPhysical").orElseThrow());
        assertEquals(RollSettings.RngSource.SEEDED, RollSettings.parseRngSource("seeded").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> s.withInputModes(EnumSet.noneOf(InputMode.class)));
    }

    @Test
    void eventsWrittenBeforePhase43AreStillRead() {
        var oldRequest = EventTypes.Json.MAPPER.createObjectNode().put("requestId", "r1").put("trainerId", "ana").put("reason", "Initiative");
        oldRequest.putObject("dice").put("count", 1).put("sides", 20);
        RollRequested read = (RollRequested) EventTypes.read(EventTypes.ROLL_REQUESTED, oldRequest);
        assertEquals(new RollRequested("r1", "ana", Dice.d(20), "Initiative"), read);
        assertEquals(Roller.PLAYER, read.roller());

        var oldAnswer = EventTypes.Json.MAPPER.createObjectNode().put("requestId", "r1").put("source", "typed");
        oldAnswer.putArray("faces").add(12);
        assertEquals(new GameEvent.RollAnswered("r1", List.of(12), "typed"), EventTypes.read(EventTypes.ROLL_ANSWERED, oldAnswer));
    }
}
