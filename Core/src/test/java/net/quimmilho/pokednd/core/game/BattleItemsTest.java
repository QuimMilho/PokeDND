/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.ADMIN;
import static net.quimmilho.pokednd.core.game.Arena.Answers;
import static net.quimmilho.pokednd.core.game.Arena.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 50: items as a battle action (chapter 7.4, 8.8). Potions, status heals, Revive, Ether, X items, Dire Hit, Guard Spec and berries
 * work in a battle; an item that would do nothing is refused when it is chosen, and the others are not battle items at all.
 */
class BattleItemsTest {

    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);
    private static final MoveBook BOOK = ItemFixtures.BOOK;
    private static final List<PokemonType> FIRE = List.of(PokemonType.FIRE);
    private static final List<PokemonType> NORMAL = List.of(PokemonType.NORMAL);

    private Arena arena;

    @BeforeEach
    void setUp() {
        arena = new Arena(BOOK, "ana", "bo");
    }

    private Combatant charmander(int hp, String... moves) {
        return arena.mon("ana-1", "ana", "Charmander", 20, 50, FIRE, 30, 28, 32, 30, 60, moves).withHp(hp);
    }

    private Combatant rattata(String... moves) {
        return arena.mon("wild-1", "wild", "Rattata", 18, 46, NORMAL, 29, 30, 33, 31, 40, moves);
    }

    private void duel(Combatant ana, Combatant wild) {
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Rattata", "wild-1")),
                List.of(ana, wild));
    }

    private void bag(Object... pairs) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            items.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        arena.play(List.of(new GameEvent.TrainerSet("ana", Fixtures.trainer("Ana").withItems(items))));
    }

    private int count(String item) {
        return arena.state().trainers().get("ana").items().getOrDefault(item, 0);
    }

    /** Ana uses an item, the wild Pokémon waits, and the round is played. */
    private void useItem(Action action) {
        arena.round(ANA_FIRST);
        arena.declare("ana-1", action);
        arena.declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        arena.endRound();
    }

    private BattleRefusedException cannotChoose(Action action) {
        arena.round(ANA_FIRST);
        return assertThrows(BattleRefusedException.class, () -> BattleDesk.declare(arena.state(), ADMIN, "ana-1", action, NOW, BOOK));
    }

    // =================================================================================================================== healing

    @Test
    void aPotionHealsInABattleAndIsUsedUp() {
        duel(charmander(20), rattata("tackle"));
        useItem(Action.item("potion", "ana-1"));
        assertEquals(40, arena.mon("ana-1").hp());
        assertEquals(2, count("potion"));
        assertTrue(arena.said("Ana uses Potion on Charmander."), "the action line");
        assertTrue(arena.said("recovers 20 HP from the Potion"));
    }

    @Test
    void aPotionNeverGoesAboveTheMaximumAndASuperPotionHealsMore() {
        duel(charmander(45), rattata("tackle"));
        bag("super_potion", 1, "potion", 1);
        useItem(Action.item("super_potion", "ana-1"));
        assertEquals(50, arena.mon("ana-1").hp());
        assertTrue(arena.said("recovers 5 HP"));
        assertEquals(0, count("super_potion"));
    }

    @Test
    void anItemThatWouldDoNothingIsRefusedWhenChosenSoNoTurnIsLost() {
        duel(charmander(50), rattata("tackle"));
        BattleRefusedException e = cannotChoose(Action.item("potion", "ana-1"));
        assertTrue(e.getMessage().contains("no effect"), e.getMessage());
        assertEquals(3, count("potion"));
    }

    @Test
    void withoutTheItemDataAnUselessItemJustFailsAndStaysInTheBag() {
        duel(charmander(50), rattata("tackle"));
        useItem(Action.item("potion", "ana-1")); // the old way of declaring does not look at the item
        assertTrue(arena.said("have any effect on"), String.join(" | ", arena.lines()));
        assertEquals(3, count("potion"), "an item that did nothing is not used up");
        assertEquals(50, arena.mon("ana-1").hp());
    }

    @Test
    void aStatusHealCuresItsStatusInABattle() {
        Combatant poisoned = charmander(50);
        duel(poisoned.withCondition(poisoned.condition().withStatus(Status.POISON, 0)), rattata("tackle"));
        bag("antidote", 1, "burn_heal", 1, "full_heal", 1);
        assertTrue(cannotChoose(Action.item("burn_heal", "ana-1")).getMessage().contains("no effect"), "it is poisoned, not burned");
        arena.declare("ana-1", Action.item("antidote", "ana-1"));
        arena.declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        assertEquals(Status.NONE, arena.mon("ana-1").status());
        assertEquals(0, count("antidote"));
    }

    @Test
    void aFaintedPokemonInTheReserveCanBeRevivedButNotOneOnTheField() {
        Combatant reserve = arena.mon("ana-2", "ana", "Pidgey", 15, 40, NORMAL, 20, 20, 20, 20, 50, "tackle").withHp(0);
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of("ana-2")), Arena.wild("wild", "Rattata", "wild-1")),
                List.of(charmander(50), reserve, rattata("tackle")));
        bag("revive", 1);
        useItem(Action.item("revive", "ana-2"));
        assertEquals(20, arena.mon("ana-2").hp(), "a Revive brings back half of the 40 HP");
        assertEquals(0, count("revive"));
        assertTrue(arena.said("back on its feet"));
    }

    @Test
    void etherRestoresTheMovesPpAndNeedsToKnowWhichMove() {
        Combatant c = charmander(50, "ember", "tackle");
        List<MoveSlot> moves = new ArrayList<>(c.moves());
        MoveSlot ember = moves.get(0);
        moves.set(0, new MoveSlot(ember.moveId(), ember.name(), 5, ember.maxPp(), ember.priority(), ember.target()));
        duel(c.withMoves(moves), rattata("tackle"));
        bag("ether", 1);
        assertTrue(cannotChoose(Action.item("ether", "ana-1")).getMessage().contains("Choose the move"));
        arena.declare("ana-1", Action.item("ether", "ana-1", "ember"));
        arena.declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        assertEquals(15, arena.mon("ana-1").moves().get(0).pp());
        assertEquals(0, count("ether"));
    }

    @Test
    void aBerryIsEatenFromTheBagInABattle() {
        duel(charmander(20), rattata("tackle"));
        bag("oran_berry", 1);
        useItem(Action.item("oran_berry", "ana-1"));
        assertEquals(30, arena.mon("ana-1").hp());
        assertEquals(0, count("oran_berry"));
    }

    // ============================================================================================================ X items and others

    @Test
    void anXItemRaisesAStatTwoStagesUntilItIsAtTheTop() {
        duel(charmander(50), rattata("tackle"));
        bag("x_attack", 3);
        useItem(Action.item("x_attack", "ana-1"));
        assertEquals(2, arena.mon("ana-1").condition().stage("atk"));
        arena.play(List.of(new GameEvent.StageChanged("b1", "ana-1", "atk", 4, null)));
        assertEquals(6, arena.mon("ana-1").condition().stage("atk"));
        assertTrue(cannotChoose(Action.item("x_attack", "ana-1")).getMessage().contains("no effect"));
        assertEquals(2, count("x_attack"));
    }

    @Test
    void direHitRaisesTheCriticalChanceAndLastsUntilThePokemonLeaves() {
        duel(charmander(50, "ember"), rattata("tackle"));
        bag("dire_hit", 1);
        Answers hit = Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[]{4, 5}, "ana-1-side0", "fail");
        double plain = critWidth(hit);
        arena = new Arena(BOOK, "ana", "bo");
        duel(charmander(50, "ember"), rattata("tackle"));
        bag("dire_hit", 2);
        useItem(Action.item("dire_hit", "ana-1"));
        assertTrue(arena.mon("ana-1").condition().has(Volatiles.FOCUS_ENERGY));
        assertEquals(1, count("dire_hit"));
        assertTrue(cannotChoose(Action.item("dire_hit", "ana-1")).getMessage().contains("no effect"), "it is pumped already");
        arena.declare("ana-1", Action.move("ember", "wild-1"));
        arena.declare("wild-1", Action.skip());
        arena.resolve(hit);
        double pumped = critBand(hit);
        assertTrue(pumped > plain, "the critical chance is higher with Dire Hit: " + pumped + " against " + plain);
        assertEquals(1.0 / 6, pumped, 1e-9, "15 % (stage +2) is one face of a d6");
        assertEquals(1.0 / 20, plain, 1e-9, "5 % is one face of a d20");
    }

    private double critWidth(Answers hit) {
        arena.round(ANA_FIRST);
        arena.declare("ana-1", Action.move("ember", "wild-1"));
        arena.declare("wild-1", Action.skip());
        arena.resolve(hit);
        return critBand(hit);
    }

    /** The share of the faces that mean a critical hit on the die of the first hit roll that was asked. */
    private double critBand(Answers hit) {
        for (Map.Entry<String, GameState.RollRequest> e : arena.asked.entrySet()) {
            if (e.getKey().endsWith("ana-1-hit0")) {
                return e.getValue().bands().stream().filter(b -> b.label().equals("crit")).mapToInt(b -> b.to() - b.from() + 1).sum() / (double) e.getValue().dice().max();
            }
        }
        throw new AssertionError("no hit roll was asked: " + arena.asked.keySet());
    }

    @Test
    void guardSpecKeepsAFoeFromLoweringStats() {
        duel(charmander(50), rattata("growl"));
        bag("guard_spec", 1);
        useItem(Action.item("guard_spec", "ana-1"));
        assertTrue(arena.mon("ana-1").condition().has(Volatiles.GUARD_SPEC));
        arena.round(ANA_FIRST);
        arena.declare("ana-1", Action.skip());
        arena.declare("wild-1", Action.move("growl"));
        arena.resolve(Answers.of("wild-1-hit0", "hit"));
        assertEquals(0, arena.mon("ana-1").condition().stage("atk"), "Growl did not lower Attack");
        assertTrue(arena.said("protected by the mist"));
    }

    // ===================================================================================================== what is not a battle item

    @Test
    void onlySomeItemsWorkInABattle() {
        duel(charmander(20), rattata("tackle"));
        bag("rare_candy", 1, "tm_ember", 1, "protein", 1, "leftovers", 1, "herbal_tea", 1);
        for (String id : List.of("rare_candy", "tm_ember", "protein", "leftovers", "herbal_tea")) {
            arena.round(ANA_FIRST);
            assertThrows(BattleRefusedException.class, () -> BattleDesk.declare(arena.state(), ADMIN, "ana-1", Action.item(id, "ana-1"), NOW, BOOK), id);
            arena.declare("ana-1", Action.skip());
            arena.declare("wild-1", Action.skip());
            arena.resolve(Answers.none());
            arena.endRound();
        }
        assertTrue(BagDesk.usableInBattle(BOOK, "potion"));
        assertTrue(BagDesk.usableInBattle(BOOK, "x_attack"));
        assertTrue(BagDesk.usableInBattle(BOOK, "oran_berry"));
        assertTrue(BagDesk.usableInBattle(BOOK, "antidote"));
        assertFalse(BagDesk.usableInBattle(BOOK, "poke_ball"), "a ball is the BALL action");
        assertFalse(BagDesk.usableInBattle(BOOK, "rare_candy"));
        assertFalse(BagDesk.usableInBattle(BOOK, "tm_ember"));
        assertFalse(BagDesk.usableInBattle(BOOK, "fire_gem"));
        assertFalse(BagDesk.usableInBattle(BOOK, "nothing_like_it"));
    }
}
