/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ItemFixtures.ADMIN;
import static net.quimmilho.pokednd.core.game.ItemFixtures.NOW;
import static net.quimmilho.pokednd.core.game.ItemFixtures.PLAYER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.ItemFixtures.Table;
import net.quimmilho.pokednd.core.game.Merchant.StockLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 50: money, shops, merchants, tutors and healers (chapter 8.8) as scripted scenarios: buying and selling, the stock of a shelf, the
 * restock rounds, the price multiplier and the class features, the prices the admin sets, and the tutor's and the healer's price.
 */
class ShopTest {

    private Table t;

    @BeforeEach
    void setUp() {
        t = new Table();
        t.play(ShopDesk.setUp(t.state(), ADMIN, mart(3)));
    }

    /** Poké Mart: 5 potions, 2 revives, 1 TM, a Great Ball at its own price, Super Potions and a Rare Candy that never run out. */
    private Merchant mart(int restockRounds) {
        return Merchant.of("mart", "Poké Mart", restockRounds, 0, List.of(StockLine.of("potion", 5), StockLine.of("revive", 2), StockLine.of("tm_ember", 1),
                StockLine.unlimited("super_potion"), StockLine.unlimited("great_ball").withPrice(500), StockLine.unlimited("rare_candy")));
    }

    private Merchant mart() {
        return t.state().merchants().get("mart");
    }

    private BattleRefusedException refused(Runnable r) {
        return assertThrows(BattleRefusedException.class, r::run);
    }

    private void buy(String who, String item, int n) {
        t.play(ShopDesk.buy(t.state(), t.rules, who, "mart", item, n));
    }

    private int stock(String item) {
        return mart().line(item).quantity();
    }

    // ===================================================================================================================== buying

    @Test
    void aPurchaseMovesTheMoneyTheBagAndTheShelfTogether() {
        List<GameEvent> events = ShopDesk.buy(t.state(), t.rules, "ana", "mart", "potion", 2);
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.GameNoted n && n.text().contains("Ana bought 2 Potion from Poké Mart for 400 ₽")), "the log says so");
        t.play(events);
        assertEquals(3000 - 400, t.trainer("ana").money());
        assertEquals(3 + 2, t.count("ana", "potion"));
        assertEquals(3, stock("potion"));
        buy("ana", "super_potion", 1);
        assertEquals(700, 2600 - t.trainer("ana").money());
        assertEquals(-1, stock("super_potion"), "an unlimited shelf stays unlimited");
        buy("ana", "great_ball", 3);
        assertEquals(3 * 500, 1900 - t.trainer("ana").money(), "a price written on the shelf is the price");
        assertEquals(3, t.count("ana", "great_ball"));
    }

    @Test
    void youCannotBuyMoreThanYouHaveMoneyOrStockFor() {
        BattleRefusedException broke = refused(() -> buy("ana", "revive", 3)); // 3 x 1500 = 4500 > 3000, and only 2 are there
        assertTrue(broke.getMessage().contains("only 2"), broke.getMessage());
        buy("ana", "revive", 2); // 3000: exactly what Ana has
        assertEquals(0, t.trainer("ana").money());
        assertEquals(0, stock("revive"));
        assertTrue(refused(() -> buy("ana", "revive", 1)).getMessage().contains("no Revive left"));
        assertTrue(refused(() -> buy("ana", "potion", 1)).getMessage().contains("needs 200"));
        assertEquals(5, stock("potion"), "nothing changed");
        refused(() -> buy("ana", "tm_ember", 2)); // one on the shelf
        refused(() -> buy("ana", "x_attack", 1)); // not on the shelves
        refused(() -> buy("ana", "potion", 0));
        refused(() -> ShopDesk.buy(t.state(), t.rules, "ana", "nowhere", "potion", 1));
    }

    @Test
    void anItemWithoutAPriceIsNotSoldUntilTheAdminSetsOne() {
        assertTrue(refused(() -> buy("ana", "rare_candy", 1)).getMessage().contains("no price"));
        t.rules = t.rules.withEconomy(t.rules.economy().withPrice("rare_candy", 2500));
        buy("ana", "rare_candy", 1);
        assertEquals(500, t.trainer("ana").money());
        assertEquals(1, t.count("ana", "rare_candy"));
        // the admin can also change a price of the table
        t.rules = t.rules.withEconomy(t.rules.economy().withPrice("potion", 50));
        buy("ana", "potion", 2);
        assertEquals(400, t.trainer("ana").money());
    }

    @Test
    void everyTmCostsTheTmPriceOfTheGame() {
        buy("ana", "tm_ember", 1);
        assertEquals(0, t.trainer("ana").money());
        t.money("bo", 10_000);
        t.rules = t.rules.withEconomy(t.rules.economy().withPrices(0.5, 1200, 0, 1000));
        t.play(ShopDesk.restock(t.state(), ADMIN, "mart"));
        buy("bo", "tm_ember", 1);
        assertEquals(10_000 - 1200, t.trainer("bo").money());
    }

    @Test
    void theBagLimitsAlsoApplyToAPurchaseAndNothingChangesWhenItDoesNotFit() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withBagLimits(0, 5));
        GameState before = t.state();
        assertTrue(refused(() -> buy("ana", "potion", 3)).getMessage().contains("stack"));
        assertEquals(before, t.state());
        buy("ana", "potion", 2);
        assertEquals(5, t.count("ana", "potion"));
    }

    // ===================================================================================================================== prices

    @Test
    void aMerchantMultipliesTheTablePriceButNotAPriceItWroteItself() {
        t.play(ShopDesk.setUp(t.state(), ADMIN, mart().withMultiplier(1.5)));
        assertEquals(OptionalInt.of(300), ShopDesk.buyPrice(t.rules, mart(), "potion", t.trainer("ana")));
        assertEquals(OptionalInt.of(500), ShopDesk.buyPrice(t.rules, mart(), "great_ball", t.trainer("ana")), "the Great Ball has its own price");
        assertEquals(OptionalInt.empty(), ShopDesk.buyPrice(t.rules, mart(), "rare_candy", t.trainer("ana")));
        buy("ana", "potion", 1);
        assertEquals(3000 - 300, t.trainer("ana").money());
    }

    @Test
    void aClassFeatureGivesADiscountAndASellBonus() {
        t.becomes("ana", "coordinator", 1); // Showmanship: 5 % discount
        assertEquals(OptionalInt.of(190), ShopDesk.buyPrice(t.rules, mart(), "potion", t.trainer("ana")));
        assertEquals(OptionalInt.of(665), ShopDesk.buyPrice(t.rules, mart(), "super_potion", t.trainer("ana")));
        t.becomes("bo", "collector", 20); // Haggler and Midas Touch: 25 % off, 50 % more when selling
        assertEquals(OptionalInt.of(150), ShopDesk.buyPrice(t.rules, mart(), "potion", t.trainer("bo")));
        assertEquals(OptionalInt.of(150), ShopDesk.sellPrice(t.rules, mart(), "potion", t.trainer("bo")), "200 x 50 % x 150 %");
        assertEquals(OptionalInt.of(100), ShopDesk.sellPrice(t.rules, mart(), "potion", t.trainer("ana")));
        buy("bo", "potion", 4);
        assertEquals(3000 - 600, t.trainer("bo").money());
    }

    // ===================================================================================================================== selling

    @Test
    void youSellForHalfThePriceAndTheShelfDoesNotChange() {
        t.play(ShopDesk.sell(t.state(), t.rules, "ana", "mart", "potion", 2));
        assertEquals(3000 + 200, t.trainer("ana").money());
        assertEquals(1, t.count("ana", "potion"));
        assertEquals(5, stock("potion"), "what a player sells is not put on the shelf");
        t.bag("ana", "tm_slash", 1, "master_ball", 1, "rare_candy", 1, "oran_berry", 1);
        t.play(ShopDesk.sell(t.state(), t.rules, "ana", "mart", "tm_slash", 1));
        assertEquals(3200 + 1500, t.trainer("ana").money());
        refused(() -> ShopDesk.sell(t.state(), t.rules, "ana", "mart", "master_ball", 1)); // not for sale at all
        refused(() -> ShopDesk.sell(t.state(), t.rules, "ana", "mart", "rare_candy", 1));
        refused(() -> ShopDesk.sell(t.state(), t.rules, "ana", "mart", "oran_berry", 2)); // only one
        refused(() -> ShopDesk.sell(t.state(), t.rules, "ana", "mart", "oran_berry", 0));
        t.play(ShopDesk.sell(t.state(), t.rules, "ana", "mart", "oran_berry", 1));
        assertEquals(4700 + 50, t.trainer("ana").money(), "a berry costs 100");
    }

    @Test
    void aMerchantThatOnlySellsOrOnlyBuysDoesOnlyThat() {
        t.play(ShopDesk.setUp(t.state(), ADMIN, mart().withTrading(true, false)));
        assertTrue(refused(() -> ShopDesk.sell(t.state(), t.rules, "ana", "mart", "potion", 1)).getMessage().contains("does not buy"));
        t.play(ShopDesk.setUp(t.state(), ADMIN, mart().withTrading(false, true)));
        assertTrue(refused(() -> buy("ana", "potion", 1)).getMessage().contains("does not sell"));
        t.play(ShopDesk.sell(t.state(), t.rules, "ana", "mart", "potion", 1));
        assertEquals(3100, t.trainer("ana").money());
    }

    @Test
    void theSellRatioIsASetting() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withPrices(0.25, 3000, 0, 1000));
        t.play(ShopDesk.sell(t.state(), t.rules, "ana", "mart", "potion", 1));
        assertEquals(3050, t.trainer("ana").money());
    }

    // ================================================================================================================== restocking

    @Test
    void aMerchantFillsItsShelvesAgainAfterItsRestockRounds() {
        buy("ana", "potion", 5);
        buy("ana", "revive", 1);
        assertEquals(0, stock("potion"));
        assertEquals(List.of(), ShopDesk.restockDue(t.state()), "round 0: not due");
        t.nextRound();
        t.nextRound(); // round 2
        assertEquals(List.of(), ShopDesk.restockDue(t.state()));
        assertTrue(refused(() -> buy("ana", "potion", 1)).getMessage().contains("no Potion left"));
        t.nextRound(); // round 3: three rounds have gone by
        List<GameEvent> due = ShopDesk.restockDue(t.state());
        assertEquals(2, due.size(), "the shelves and a line for the log");
        t.play(due);
        assertEquals(5, stock("potion"));
        assertEquals(2, stock("revive"), "every shelf is full again");
        assertEquals(3, mart().lastRestock());
        assertEquals(List.of(), ShopDesk.restockDue(t.state()), "and not again in the same round");
        t.nextRound();
        t.nextRound(); // round 5
        assertEquals(List.of(), ShopDesk.restockDue(t.state()));
        t.nextRound(); // round 6
        assertEquals(2, ShopDesk.restockDue(t.state()).size());
    }

    @Test
    void aPurchaseFromAMerchantThatIsDueRestocksItFirst() {
        t.money("ana", 10_000);
        buy("ana", "potion", 5);
        assertEquals(0, stock("potion"));
        t.nextRound();
        t.nextRound();
        t.nextRound();
        assertEquals(0, stock("potion"), "the shelf has not been touched yet");
        assertEquals(5, ShopDesk.current(t.state(), "mart").line("potion").quantity(), "but this is what the player sees");
        buy("ana", "potion", 2);
        assertEquals(3, stock("potion"), "restocked to 5, and 2 bought");
        assertEquals(3, mart().lastRestock());
    }

    @Test
    void aMerchantWithNoRestockRoundsNeverRestocksAndTheAdminCanDoItByHand() {
        t.play(ShopDesk.setUp(t.state(), ADMIN, mart(0)));
        buy("ana", "potion", 5);
        for (int i = 0; i < 12; i++) {
            t.nextRound();
        }
        assertEquals(List.of(), ShopDesk.restockDue(t.state()));
        assertEquals(0, stock("potion"));
        refused(() -> ShopDesk.restock(t.state(), PLAYER, "mart"));
        t.play(ShopDesk.restock(t.state(), ADMIN, "mart"));
        assertEquals(5, stock("potion"));
    }

    @Test
    void theRestockIntervalOfANewMerchantComesFromTheSettings() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withRestockRounds(7));
        Merchant m = ShopDesk.merchant(t.state(), t.rules, "stall", "Stall", List.of(StockLine.of("potion", 1)));
        assertEquals(7, m.restockRounds());
        assertEquals(0, m.lastRestock());
        for (int i = 0; i < 4; i++) {
            t.nextRound();
        }
        assertEquals(4, ShopDesk.merchant(t.state(), t.rules, "late", "Late", List.of()).lastRestock(), "a merchant set up in round 4 restocks 7 rounds after that");
    }

    // ================================================================================================================ admin and undo

    @Test
    void onlyTheAdminSetsUpAndRemovesMerchants() {
        refused(() -> ShopDesk.setUp(t.state(), PLAYER, mart()));
        refused(() -> ShopDesk.remove(t.state(), PLAYER, "mart"));
        refused(() -> ShopDesk.remove(t.state(), ADMIN, "nowhere"));
        t.play(ShopDesk.remove(t.state(), ADMIN, "mart"));
        assertTrue(t.state().merchants().isEmpty());
        refused(() -> buy("ana", "potion", 1));
    }

    @Test
    void aPurchaseIsOneActionThatAnUndoTakesBackWholeAndTheLogCanBeRebuilt() {
        GameState before = t.state();
        buy("ana", "potion", 2);
        GameState bought = t.state();
        t.log.undo("u-admin", NOW, 1);
        assertEquals(before, t.state(), "the money, the bag and the shelf are all back");
        t.log.redo("u-admin", NOW, 1);
        assertEquals(bought, t.state());
        GameLog again = GameLog.restore(t.log.settings(), t.log.entries(), t.log.snapshots(), t.log.audit());
        assertEquals(bought, again.state(), "merchants survive the JSON of the stored events");
        assertEquals(bought, GameStateJson.read(GameStateJson.write(bought)));
        assertFalse(bought.merchants().isEmpty());
    }

    @Test
    void theAdminGivesAndTakesMoney() {
        t.play(ShopDesk.money(t.state(), t.rules, ADMIN, "ana", 500, "a reward"));
        assertEquals(3500, t.trainer("ana").money());
        t.play(ShopDesk.money(t.state(), t.rules, ADMIN, "ana", -3500, "a fine"));
        assertEquals(0, t.trainer("ana").money());
        refused(() -> ShopDesk.money(t.state(), t.rules, ADMIN, "ana", -1, null));
        refused(() -> ShopDesk.money(t.state(), t.rules, PLAYER, "ana", 5, null));
        refused(() -> ShopDesk.money(t.state(), t.rules, ADMIN, "ana", 0, null));
    }

    // ===================================================================================================================== tutors

    @Test
    void aTutorTeachesAMoveForMoneyFromTheListOfTheSpecies() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.play(ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "watergun", null, null, null));
        assertEquals(2000, t.trainer("ana").money(), "1,000 ₽ by default");
        assertEquals(List.of("tackle", "growl", "watergun"), t.pokemon("pk-1").moves().stream().map(MoveSlot::moveId).toList());
        refused(() -> ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "watergun", null, null, null)); // knows it
        refused(() -> ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "ember", null, null, null)); // a TM move, not a tutor move
        assertEquals(2000, t.trainer("ana").money());
    }

    @Test
    void aTutorCanSetItsOwnPriceAndTeachLegacyMoves() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        refused(() -> ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "swift", 250, Set.of("egg"), null)); // an egg-move tutor
        t.play(ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "swift", 250, Set.of("legacy"), null));
        assertEquals(2750, t.trainer("ana").money());
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withPrices(0.5, 3000, 0, 5000));
        assertTrue(refused(() -> ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "watergun", null, null, null)).getMessage().contains("needs 5000"));
        t.play(ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "scratch", 0, Set.of("egg"), null));
        assertEquals(2750, t.trainer("ana").money(), "a free tutor");
        assertEquals(4, t.pokemon("pk-1").moves().size());
        assertTrue(refused(() -> ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "watergun", 0, null, null)).getMessage().contains("forget"));
        t.play(ShopDesk.tutor(t.state(), t.rules, "ana", "pk-1", "watergun", 0, null, 0));
        assertEquals("watergun", t.pokemon("pk-1").moves().get(0).moveId(), "it took the place of Tackle");
    }

    // ===================================================================================================================== healers

    @Test
    void aHealerRestoresTheWholePartyAndTheTrainerForFree() {
        t.give(t.bulbasaur("pk-1", "ana", 20).withHp(0));
        t.give(t.bulbasaur("pk-2", "ana", 20).withHp(3));
        t.play(List.of(new GameEvent.TrainerHpChanged("ana", -4, "a fall")));
        PokemonSheet hurt = t.pokemon("pk-2");
        t.play(List.of(new GameEvent.PokemonSet(hurt.withMoves(List.of(new MoveSlot("tackle", "Tackle", 1, 35, 0, "normal"), hurt.moves().get(1))))));
        assertTrue(t.trainer("ana").hp() < t.trainer("ana").maxHp());
        t.play(ShopDesk.heal(t.state(), t.rules, "ana", null));
        assertEquals(t.maxHp(t.pokemon("pk-1")), t.pokemon("pk-1").hp(), "a fainted Pokémon is healed too");
        assertEquals(t.maxHp(t.pokemon("pk-2")), t.pokemon("pk-2").hp());
        assertEquals(35, t.pokemon("pk-2").moves().get(0).pp());
        assertEquals(t.trainer("ana").maxHp(), t.trainer("ana").hp());
        assertEquals(3000, t.trainer("ana").money());
    }

    @Test
    void aHealerCanChargeAndIsRefusedWhenYouCannotPay() {
        t.give(t.bulbasaur("pk-1", "ana", 20).withHp(1));
        t.play(ShopDesk.heal(t.state(), t.rules, "ana", 400));
        assertEquals(2600, t.trainer("ana").money());
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withPrices(0.5, 3000, 5000, 1000));
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withHp(1))));
        assertTrue(refused(() -> ShopDesk.heal(t.state(), t.rules, "ana", null)).getMessage().contains("needs 5000"));
        assertEquals(1, t.pokemon("pk-1").hp(), "nothing was healed");
    }

    // ===================================================================================================================== settings

    @Test
    void theEconomySettingsAreReadFromTheirKeys() {
        EconomySettings s = EconomySettings.fromValues(Map.ofEntries(
                Map.entry("bag.limit.slotsPerPocket", "12"),
                Map.entry("bag.limit.stackSize", "99"),
                Map.entry("items.ivCandy.step", "2"),
                Map.entry("economy.sellRatio", "0.4"),
                Map.entry("economy.prices.tm", "2500"),
                Map.entry("economy.healer.price", "100"),
                Map.entry("economy.tutor.price", "800"),
                Map.entry("economy.merchant.restockRounds", "6"),
                Map.entry("economy.trade.requireApproval", "true"),
                Map.entry("economy.trade.location", "anywhere"),
                Map.entry("economy.currency.symbol", "$")));
        assertEquals(new EconomySettings(12, 99, 2, 0.4, 2500, 100, 800, 6, true, EconomySettings.TradeLocation.ANYWHERE, Map.of(), "$"), s);
        assertEquals(EconomySettings.defaults(), EconomySettings.fromValues(Map.of()));
        assertEquals(0, EconomySettings.defaults().slotsPerPocket());
        assertEquals(1000, EconomySettings.defaults().tutorPrice());
        assertEquals(0.5, EconomySettings.defaults().sellRatio());
        assertThrows(IllegalArgumentException.class, () -> EconomySettings.fromValues(Map.of("economy.sellRatio", "1.5")));
        assertThrows(IllegalArgumentException.class, () -> EconomySettings.fromValues(Map.of("economy.trade.location", "moon")));
        assertThrows(IllegalArgumentException.class, () -> EconomySettings.fromValues(Map.of("bag.limit.stackSize", "many")));
    }
}
