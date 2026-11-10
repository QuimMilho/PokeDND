/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ItemFixtures.ADMIN;
import static net.quimmilho.pokednd.core.game.ItemFixtures.NOW;
import static net.quimmilho.pokednd.core.game.ItemFixtures.PLAYER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.ItemFixtures.Table;
import net.quimmilho.pokednd.core.game.Trade.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 50: trading at a trade centre (chapter 8.8). A trade is proposed, confirmed by the other trainer and, if the game wants it,
 * approved by the admin; then Pokémon, items and money change hands in one action that can be undone whole. Trade evolutions start.
 */
class TradeTest {

    private Table t;

    @BeforeEach
    void setUp() {
        t = new Table();
        t.give(t.bulbasaur("pk-1", "ana", 10));
        t.give(t.sheet("pk-2", "bo", "pidgey", 10, "tackle"));
    }

    private BattleRefusedException refused(Runnable r) {
        return assertThrows(BattleRefusedException.class, r::run);
    }

    private void propose(String id, Side give, Side want) {
        t.play(TradeDesk.propose(t.state(), t.rules, id, "ana", "bo", "centre", give, want));
    }

    private void confirm(String id) {
        t.play(TradeDesk.confirm(t.state(), t.rules, id, "bo"));
    }

    /** Ana gives Bulbasaur, 2 potions and 500 ₽; Bo gives Pidgey and 3 Poké Balls. */
    private void bigOffer(String id) {
        propose(id, new Side(List.of("pk-1"), Map.of("potion", 2), 500), new Side(List.of("pk-2"), Map.of("poke_ball", 3), 0));
    }

    // ===================================================================================================================== happening

    @Test
    void aConfirmedTradeMovesPokemonItemsAndMoneyAtOnce() {
        bigOffer("tr-1");
        assertEquals(1, t.state().trades().size());
        assertEquals("ana", t.pokemon("pk-1").ownerId(), "a proposal changes nothing");
        assertEquals(3000, t.trainer("ana").money());
        confirm("tr-1");
        assertTrue(t.state().trades().isEmpty(), "the trade is over");
        assertEquals("bo", t.pokemon("pk-1").ownerId());
        assertEquals("ana", t.pokemon("pk-2").ownerId());
        assertEquals(PokemonSheet.Location.PARTY, t.pokemon("pk-1").location());
        assertEquals(1, t.count("ana", "potion"));
        assertEquals(5 + 3, t.count("ana", "poke_ball"));
        assertEquals(3 + 2, t.count("bo", "potion"));
        assertEquals(5 - 3, t.count("bo", "poke_ball"));
        assertEquals(3000 - 500, t.trainer("ana").money());
        assertEquals(3000 + 500, t.trainer("bo").money());
    }

    @Test
    void aTradeIsOneActionThatAnUndoTakesBackWholeAndTheLogCanBeRebuilt() {
        bigOffer("tr-1");
        GameState proposed = t.state();
        confirm("tr-1");
        GameState traded = t.state();
        t.log.undo("u-admin", NOW, 1);
        assertEquals(proposed, t.state(), "Pokémon, items and money are all back, and the trade is open again");
        assertEquals("ana", t.pokemon("pk-1").ownerId());
        t.log.redo("u-admin", NOW, 1);
        assertEquals(traded, t.state());
        GameLog again = GameLog.restore(t.log.settings(), t.log.entries(), t.log.snapshots(), t.log.audit());
        assertEquals(traded, again.state(), "trades survive the JSON of the stored events");
        t.log.undo("u-admin", NOW, 2); // the confirmation and the proposal
        assertTrue(t.state().trades().isEmpty());
    }

    @Test
    void aTradeThatCannotCompleteChangesNothingAndStaysOpen() {
        bigOffer("tr-1");
        t.bag("bo", "poke_ball", 1); // Bo spent his balls since the proposal
        GameState before = t.state();
        assertTrue(refused(() -> confirm("tr-1")).getMessage().contains("Poké Ball"));
        assertEquals(before, t.state(), "no Pokémon, item or money moved");
        t.bag("bo", "poke_ball", 5);
        t.money("ana", 100); // and Ana spent her money
        assertTrue(refused(() -> confirm("tr-1")).getMessage().contains("500"));
        t.money("ana", 3000);
        confirm("tr-1");
        assertTrue(t.state().trades().isEmpty());
    }

    @Test
    void theLimitsOfABagCanStopATrade() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withBagLimits(0, 6));
        bigOffer("tr-1"); // Ana would end up with 8 Poké Balls
        GameState before = t.state();
        assertTrue(refused(() -> confirm("tr-1")).getMessage().contains("stack"));
        assertEquals(before, t.state());
    }

    @Test
    void aGiftNeedsNothingInReturn() {
        propose("gift", new Side(List.of(), Map.of("potion", 1), 250), Side.nothing());
        confirm("gift");
        assertEquals(2, t.count("ana", "potion"));
        assertEquals(4, t.count("bo", "potion"));
        assertEquals(3250, t.trainer("bo").money());
    }

    // ===================================================================================================================== who may do what

    @Test
    void onlyTheTrainerWhoWasAskedConfirmsAndEitherSideCanCallItOff() {
        bigOffer("tr-1");
        refused(() -> TradeDesk.confirm(t.state(), t.rules, "tr-1", "ana")); // the proposer cannot confirm
        refused(() -> TradeDesk.confirm(t.state(), t.rules, "nope", "bo"));
        refused(() -> TradeDesk.decline(t.state(), PLAYER, "tr-1", "cy")); // a stranger
        List<GameEvent> declined = TradeDesk.decline(t.state(), PLAYER, "tr-1", "bo");
        assertTrue(declined.stream().anyMatch(e -> e instanceof GameEvent.TradeClosed c && c.outcome().equals("declined")));
        t.play(declined);
        assertTrue(t.state().trades().isEmpty());
        assertEquals("ana", t.pokemon("pk-1").ownerId());
        bigOffer("tr-2");
        t.play(TradeDesk.decline(t.state(), PLAYER, "tr-2", "ana"));
        assertTrue(t.state().trades().isEmpty(), "the proposer takes the offer back");
        bigOffer("tr-3");
        List<GameEvent> byAdmin = TradeDesk.decline(t.state(), ADMIN, "tr-3", "u-admin");
        assertTrue(byAdmin.stream().anyMatch(e -> e instanceof GameEvent.TradeClosed c && c.outcome().equals("refused")));
        t.play(byAdmin);
        assertTrue(t.state().trades().isEmpty());
    }

    @Test
    void whenTheGameNeedsApprovalTheAdminDecidesAfterTheConfirmation() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withTrade(true, EconomySettings.TradeLocation.TRADE_CENTRE));
        bigOffer("tr-1");
        refused(() -> TradeDesk.approve(t.state(), t.rules, ADMIN, "tr-1")); // nobody confirmed yet
        confirm("tr-1");
        assertEquals(Trade.Status.CONFIRMED, t.state().trades().get("tr-1").status());
        assertEquals("ana", t.pokemon("pk-1").ownerId(), "nothing moves before the admin approves");
        refused(() -> confirm("tr-1")); // confirmed already
        refused(() -> TradeDesk.approve(t.state(), t.rules, PLAYER, "tr-1"));
        t.play(TradeDesk.approve(t.state(), t.rules, ADMIN, "tr-1"));
        assertEquals("bo", t.pokemon("pk-1").ownerId());
        assertTrue(t.state().trades().isEmpty());
    }

    @Test
    void tradingHappensAtATradeCentreUnlessTheGameAllowsAnywhere() {
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", null, Side.pokemon("pk-1"), Side.pokemon("pk-2")));
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withTrade(false, EconomySettings.TradeLocation.ANYWHERE));
        t.play(TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", null, Side.pokemon("pk-1"), Side.pokemon("pk-2")));
        assertNull(t.state().trades().get("tr-1").centreId());
        confirm("tr-1");
        assertEquals("bo", t.pokemon("pk-1").ownerId());
    }

    // ===================================================================================================================== what can be traded

    @Test
    void aTradeMustBeBetweenTwoTrainersAboutSomethingTheyHave() {
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "ana", "centre", Side.pokemon("pk-1"), Side.nothing()));
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "cy", "centre", Side.pokemon("pk-1"), Side.nothing()));
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", "centre", Side.nothing(), Side.nothing())); // nothing at all
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", "centre", Side.pokemon("pk-2"), Side.nothing())); // Pidgey is Bo's
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", "centre", Side.pokemon("pk-1", "pk-1"), Side.nothing()));
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", "centre", new Side(List.of(), Map.of("revive", 1), 0), Side.nothing()));
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", "centre", new Side(List.of(), Map.of(), 99_999), Side.nothing()));
        refused(() -> TradeDesk.propose(t.state(), t.rules, "tr-1", "ana", "bo", "centre", Side.nothing(), Side.pokemon("pk-1"))); // Bo is asked for Ana's own
        assertThrows(IllegalArgumentException.class, () -> new Side(List.of(), Map.of("potion", 0), 0));
        assertThrows(IllegalArgumentException.class, () -> new Side(List.of(), Map.of(), -1));
        assertTrue(t.state().trades().isEmpty());
        propose("tr-1", Side.pokemon("pk-1"), Side.pokemon("pk-2"));
        refused(() -> propose("tr-1", Side.pokemon("pk-1"), Side.pokemon("pk-2"))); // the id exists
    }

    @Test
    void aPokemonInAnUnsettledBattleCannotBeTraded() {
        PokemonSheet p = t.pokemon("pk-1");
        PokemonSheet wild = ProgressFixtures.sheet(t.rules.species(), "w-1", null, "rattata", 5, "tackle");
        t.play(List.of(new GameEvent.PokemonSet(wild)));
        Arena arena = new Arena(t.rules.moves(), "ana", "bo");
        arena.play(List.of(new GameEvent.PokemonSet(p), new GameEvent.PokemonSet(t.pokemon("pk-2")), new GameEvent.PokemonSet(wild)));
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("pk-1"), List.of()), Arena.wild("wild", "Rattata", "w-1")),
                List.of(ProgressFixtures.combatant(p, "ana", t.rules.species()), ProgressFixtures.combatant(wild, "wild", t.rules.species())));
        assertTrue(assertThrows(BattleRefusedException.class, () -> TradeDesk.propose(arena.state(), t.rules, "tr-1", "ana", "bo", "centre", Side.pokemon("pk-1"), Side.nothing()))
                .getMessage().contains("battle"));
        TradeDesk.propose(arena.state(), t.rules, "tr-1", "ana", "bo", "centre", Side.nothing(), Side.pokemon("pk-2")); // the other one is free
    }

    @Test
    void aTrainerWhoLeavesTakesTheirOpenTradesWithThem() {
        bigOffer("tr-1");
        t.play(List.of(new GameEvent.TrainerRemoved("bo")));
        assertTrue(t.state().trades().isEmpty());
    }

    // ===================================================================================================================== the party

    @Test
    void aPokemonGoesToThePartyWhileThereIsRoomAndToThePcAfterThat() {
        t.rules = t.rules.withProgress(ProgressSettings.defaults().withPartySize(1));
        // a swap: each party is empty for a moment, so each gets the other's Pokémon in the party
        propose("swap", Side.pokemon("pk-1"), Side.pokemon("pk-2"));
        confirm("swap");
        assertEquals(PokemonSheet.Location.PARTY, t.pokemon("pk-1").location());
        assertEquals(PokemonSheet.Location.PARTY, t.pokemon("pk-2").location());
        // a gift to a full party: the new Pokémon waits in the PC
        t.give(t.sheet("pk-3", "ana", "rattata", 5, "tackle"));
        propose("gift", Side.pokemon("pk-3"), Side.nothing());
        confirm("gift");
        assertEquals("bo", t.pokemon("pk-3").ownerId());
        assertEquals(PokemonSheet.Location.PC, t.pokemon("pk-3").location());
        assertEquals(PokemonSheet.Location.PARTY, t.pokemon("pk-1").location());
    }

    // ===================================================================================================================== trade evolutions

    @Test
    void aTradeEvolutionIsOfferedToTheNewOwner() {
        t.give(t.sheet("pk-3", "ana", "kadabra", 30, "tackle"));
        propose("evo", Side.pokemon("pk-3"), Side.nothing());
        confirm("evo");
        assertEquals("bo", t.pokemon("pk-3").ownerId());
        Prompt p = t.state().prompts().get("evolve-pk-3-alakazam");
        assertEquals("bo", p.trainerId(), "the new owner is asked");
        assertEquals("kadabra", t.pokemon("pk-3").speciesId(), "and the Pokémon waits for the answer");
        t.play(ProgressDesk.answer(t.state(), PLAYER, p.id(), "yes", t.rules.species(), ItemFixtures.moves(), ProgressFixtures.RULES, t.rules.progress()));
        assertEquals("alakazam", t.pokemon("pk-3").speciesId());
    }

    @Test
    void aTradeEvolutionHappensAtOnceWhenTheGameIsSetToAutomatic() {
        t.rules = t.rules.withProgress(ProgressSettings.defaults().withEvolution(ProgressSettings.EvolutionMode.AUTOMATIC, ProgressSettings.UnknownFallback.ASK_ADMIN));
        t.give(t.sheet("pk-3", "ana", "kadabra", 30, "tackle"));
        propose("evo", Side.pokemon("pk-3"), Side.pokemon("pk-2"));
        confirm("evo");
        assertEquals("alakazam", t.pokemon("pk-3").speciesId());
        assertEquals("bo", t.pokemon("pk-3").ownerId());
        assertEquals("pidgey", t.pokemon("pk-2").speciesId(), "a Pokémon with no trade evolution is only traded");
        assertTrue(t.trainer("bo").xp() > 0, "the evolution gave the new owner trainer XP");
        assertEquals(List.of(), t.state().prompts().keySet().stream().toList());
    }
}
