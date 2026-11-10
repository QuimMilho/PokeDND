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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.ItemFixtures.Table;
import net.quimmilho.pokednd.core.rules.Growth;
import net.quimmilho.pokednd.core.rules.PokemonSettings;
import net.quimmilho.pokednd.core.rules.StatBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 50: the bag and the items in it (chapter 8.8), one scripted case per kind of item: medicine, PP items, vitamins, candies (the IV
 * candies stay within 0 to 31), mints, evolution stones, berries, TMs and held items, and the limits of the bag.
 */
class BagItemsTest {

    private Table t;

    @BeforeEach
    void setUp() {
        t = new Table();
    }

    private static PokemonSheet withPp(PokemonSheet p, String moveId, int pp) {
        List<MoveSlot> moves = new ArrayList<>();
        for (MoveSlot m : p.moves()) {
            moves.add(m.moveId().equals(moveId) ? new MoveSlot(m.moveId(), m.name(), pp, m.maxPp(), m.priority(), m.target()) : m);
        }
        return p.withMoves(moves);
    }

    private BattleRefusedException refused(Runnable r) {
        return assertThrows(BattleRefusedException.class, r::run);
    }

    // ================================================================================================================ medicine

    @Test
    void aPotionHealsAndIsUsedUpAndTheLogSaysSo() {
        PokemonSheet p = t.give(t.bulbasaur("pk-1", "ana", 30).withHp(5));
        int max = t.maxHp(p);
        List<GameEvent> events = BagDesk.use(t.state(), t.rules, PLAYER, "ana", "potion", "pk-1", BagDesk.Choice.none());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.GameNoted n && n.text().contains("Ana used Potion on Bulbasaur")), "one line for the log");
        t.play(events);
        assertEquals(25, t.pokemon("pk-1").hp());
        assertEquals(2, t.count("ana", "potion"));
        t.bag("ana", "potion", 1, "super_potion", 1, "hyper_potion", 1, "max_potion", 1);
        t.use("ana", "super_potion", "pk-1");
        assertEquals(Math.min(max, 85), t.pokemon("pk-1").hp());
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withHp(1))));
        t.use("ana", "hyper_potion", "pk-1");
        assertEquals(max, t.pokemon("pk-1").hp(), "never above the maximum");
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withHp(1))));
        t.use("ana", "max_potion", "pk-1");
        assertEquals(max, t.pokemon("pk-1").hp());
    }

    @Test
    void anItemThatWouldDoNothingIsRefusedAndStaysInTheBag() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        BattleRefusedException e = refused(() -> t.use("ana", "potion", "pk-1"));
        assertTrue(e.getMessage().contains("full HP"), e.getMessage());
        assertEquals(3, t.count("ana", "potion"));
        refused(() -> t.use("ana", "revive", "pk-1")); // not in the bag
        refused(() -> t.use("ana", "potion", "nobody"));
        t.give(t.bulbasaur("pk-2", "bo", 30).withHp(5));
        refused(() -> t.use("ana", "potion", "pk-2")); // it is Bo's
        refused(() -> t.use("ana", "potion", null));
    }

    @Test
    void aFaintedPokemonNeedsARevive() {
        PokemonSheet p = t.give(t.bulbasaur("pk-1", "ana", 30).withHp(0));
        int max = t.maxHp(p);
        assertTrue(refused(() -> t.use("ana", "potion", "pk-1")).getMessage().contains("Revive"));
        t.bag("ana", "revive", 1, "max_revive", 1);
        t.use("ana", "revive", "pk-1");
        assertEquals(max / 2, t.pokemon("pk-1").hp());
        refused(() -> t.use("ana", "revive", "pk-1")); // it has not fainted any more
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withHp(0))));
        t.use("ana", "max_revive", "pk-1");
        assertEquals(max, t.pokemon("pk-1").hp());
        assertEquals(0, t.count("ana", "revive") + t.count("ana", "max_revive"));
    }

    @Test
    void statusHealsCureTheirOwnStatusAndFullRestoreEverything() {
        t.give(t.bulbasaur("pk-1", "ana", 30).withStatus(Status.BURN));
        t.bag("ana", "antidote", 1, "burn_heal", 1, "full_heal", 1, "full_restore", 1);
        refused(() -> t.use("ana", "antidote", "pk-1"));
        t.use("ana", "burn_heal", "pk-1");
        assertEquals(Status.NONE, t.pokemon("pk-1").status());
        refused(() -> t.use("ana", "full_heal", "pk-1")); // no status: nothing to cure
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withStatus(Status.PARALYSIS).withHp(3))));
        t.use("ana", "full_heal", "pk-1");
        assertEquals(Status.NONE, t.pokemon("pk-1").status());
        assertEquals(3, t.pokemon("pk-1").hp(), "a Full Heal does not heal HP");
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withStatus(Status.TOXIC))));
        t.use("ana", "full_restore", "pk-1");
        assertEquals(t.maxHp(t.pokemon("pk-1")), t.pokemon("pk-1").hp());
        assertEquals(Status.NONE, t.pokemon("pk-1").status());
    }

    @Test
    void etherAndElixirRestorePp() {
        t.give(withPp(withPp(t.bulbasaur("pk-1", "ana", 30), "tackle", 3), "growl", 10));
        t.bag("ana", "ether", 1, "max_ether", 1, "elixir", 1);
        refused(() -> t.use("ana", "ether", "pk-1")); // which move?
        refused(() -> t.use("ana", "ether", "pk-1", BagDesk.Choice.move("ember"))); // it does not know it
        t.use("ana", "ether", "pk-1", BagDesk.Choice.move("tackle"));
        assertEquals(13, slot(t.pokemon("pk-1"), "tackle").pp(), "Ether restores 10");
        assertEquals(10, slot(t.pokemon("pk-1"), "growl").pp(), "and only for the move that was chosen");
        t.use("ana", "max_ether", "pk-1", BagDesk.Choice.move("tackle"));
        assertEquals(35, slot(t.pokemon("pk-1"), "tackle").pp(), "Max Ether restores it all");
        refused(() -> t.use("ana", "max_ether", "pk-1", BagDesk.Choice.move("tackle"))); // none left, and nothing to restore
        t.use("ana", "elixir", "pk-1");
        assertEquals(20, slot(t.pokemon("pk-1"), "growl").pp(), "an Elixir restores 10 PP of every move");
        assertEquals(0, t.count("ana", "elixir"));
    }

    private static MoveSlot slot(PokemonSheet p, String move) {
        return p.moves().stream().filter(m -> m.moveId().equals(move)).findFirst().orElseThrow();
    }

    // =============================================================================================================== vitamins, PP Up

    @Test
    void vitaminsAddEvsUpToTheCaps() {
        PokemonSheet p = t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "protein", 30, "calcium", 5, "hp_up", 1);
        t.use("ana", "protein", "pk-1");
        assertEquals(10, t.pokemon("pk-1").evs().attack());
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withEvs(new StatBlock(0, 250, 0, 0, 0, 0)))));
        t.use("ana", "protein", "pk-1");
        assertEquals(252, t.pokemon("pk-1").evs().attack(), "a stat stops at 252");
        refused(() -> t.use("ana", "protein", "pk-1"));
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withEvs(new StatBlock(252, 252, 4, 0, 0, 0)))));
        t.use("ana", "calcium", "pk-1");
        assertEquals(2, t.pokemon("pk-1").evs().specialAttack(), "only 2 more EVs fit in the total of 510");
        assertEquals(510, t.pokemon("pk-1").evs().total());
        refused(() -> t.use("ana", "calcium", "pk-1"));
        assertEquals(5 - 1, t.count("ana", "calcium"));
    }

    @Test
    void aVitaminForHpRaisesTheMaximumAndTheHpTogether() {
        PokemonSheet p = t.give(t.bulbasaur("pk-1", "ana", 100));
        int before = t.maxHp(p);
        t.bag("ana", "hp_up", 1);
        t.use("ana", "hp_up", "pk-1");
        int after = t.maxHp(t.pokemon("pk-1"));
        assertTrue(after > before, "10 EVs are 2 more HP at level 100");
        assertEquals(after, t.pokemon("pk-1").hp(), "the new HP is not left behind");
    }

    @Test
    void ppUpAddsAFifthOfTheBasePpThreeTimesAndPpMaxDoesItAllAtOnce() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "pp_up", 5, "pp_max", 2);
        refused(() -> t.use("ana", "pp_up", "pk-1")); // which move?
        t.use("ana", "pp_up", "pk-1", BagDesk.Choice.move("tackle"));
        assertEquals(35 + 7, slot(t.pokemon("pk-1"), "tackle").maxPp(), "Tackle has 35 PP, a fifth is 7");
        assertEquals(35 + 7, slot(t.pokemon("pk-1"), "tackle").pp());
        t.use("ana", "pp_up", "pk-1", BagDesk.Choice.move("tackle"));
        t.use("ana", "pp_up", "pk-1", BagDesk.Choice.move("tackle"));
        assertEquals(35 + 21, slot(t.pokemon("pk-1"), "tackle").maxPp());
        refused(() -> t.use("ana", "pp_up", "pk-1", BagDesk.Choice.move("tackle")));
        refused(() -> t.use("ana", "pp_max", "pk-1", BagDesk.Choice.move("tackle")));
        assertEquals(2, t.count("ana", "pp_up"), "the refused ones were not used up");
        t.use("ana", "pp_max", "pk-1", BagDesk.Choice.move("growl"));
        assertEquals(40 + 24, slot(t.pokemon("pk-1"), "growl").maxPp(), "PP Max: three boosts of 8");
    }

    // ============================================================================================================ candies and mints

    @Test
    void aRareCandyGivesALevelAndTheMovesOfThatLevel() {
        t.give(t.bulbasaur("pk-1", "ana", 10));
        t.bag("ana", "rare_candy", 3);
        t.use("ana", "rare_candy", "pk-1");
        PokemonSheet p = t.pokemon("pk-1");
        assertEquals(11, p.level());
        assertEquals(Growth.MEDIUM_SLOW.totalXp(11), p.xp());
        assertEquals(List.of("tackle", "growl", "ember", "scratch"), p.moves().stream().map(MoveSlot::moveId).toList(), "both level 11 moves fit");
        assertEquals(2, t.count("ana", "rare_candy"));
    }

    @Test
    void aRareCandyAtTheEvolutionLevelAsksTheOwnerAndTheLevelCapStopsIt() {
        t.give(t.bulbasaur("pk-1", "ana", 15));
        t.bag("ana", "rare_candy", 2);
        t.use("ana", "rare_candy", "pk-1");
        assertEquals(16, t.pokemon("pk-1").level());
        assertTrue(t.state().prompts().containsKey("evolve-pk-1-ivysaur"));
        t.rules = t.rules.withProgress(ProgressSettings.defaults().withPokemon(PokemonSettings.defaults().withLevelCap(16)));
        assertTrue(refused(() -> t.use("ana", "rare_candy", "pk-1")).getMessage().contains("level cap"));
        assertEquals(2 - 1, t.count("ana", "rare_candy"));
    }

    @Test
    void anExpCandyGivesExactlyItsXpWhateverTheXpMultiplierIs() {
        t.rules = t.rules.withProgress(ProgressSettings.defaults().withPokemon(PokemonSettings.defaults().withXpMultiplier(2.0)));
        t.give(t.bulbasaur("pk-1", "ana", 10));
        t.bag("ana", "exp_candy_m", 1);
        t.use("ana", "exp_candy_m", "pk-1");
        assertEquals(Growth.MEDIUM_SLOW.totalXp(10) + 3000, t.pokemon("pk-1").xp());
        assertEquals(Growth.MEDIUM_SLOW.levelFor(Growth.MEDIUM_SLOW.totalXp(10) + 3000, 100), t.pokemon("pk-1").level());
    }

    @Test
    void ivCandiesMoveOneIvAndStayWithinZeroAndThirtyOne() {
        t.give(t.bulbasaur("pk-1", "ana", 30).withIvs(new StatBlock(30, 0, 31, 15, 3, 28)));
        t.bag("ana", "health_candy", 5, "sickly_candy", 5, "weak_candy", 5, "tough_candy", 5, "quick_candy", 5, "coward_candy", 5);
        t.use("ana", "health_candy", "pk-1");
        assertEquals(31, t.pokemon("pk-1").ivs().hp());
        refused(() -> t.use("ana", "health_candy", "pk-1")); // at the top already
        assertEquals(4, t.count("ana", "health_candy"));
        refused(() -> t.use("ana", "weak_candy", "pk-1")); // attack IV is 0: it cannot go lower
        refused(() -> t.use("ana", "tough_candy", "pk-1")); // defense IV is 31
        t.use("ana", "sickly_candy", "pk-1");
        assertEquals(30, t.pokemon("pk-1").ivs().hp(), "a Sickly Candy lowers the HP IV by 1");
        t.use("ana", "quick_candy", "pk-1");
        assertEquals(29, t.pokemon("pk-1").ivs().speed());
        t.use("ana", "coward_candy", "pk-1");
        assertEquals(2, t.pokemon("pk-1").ivs().specialDefense());
    }

    @Test
    void theIvCandyStepIsASettingAndTheIvStillStopsAtTheEnds() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withIvCandyStep(5));
        t.give(t.bulbasaur("pk-1", "ana", 30).withIvs(new StatBlock(28, 3, 10, 10, 10, 10)));
        t.bag("ana", "health_candy", 2, "weak_candy", 2, "mighty_candy", 1);
        t.use("ana", "health_candy", "pk-1");
        assertEquals(31, t.pokemon("pk-1").ivs().hp(), "28 + 5 stops at 31");
        t.use("ana", "weak_candy", "pk-1");
        assertEquals(0, t.pokemon("pk-1").ivs().attack(), "3 - 5 stops at 0");
        t.use("ana", "mighty_candy", "pk-1");
        assertEquals(5, t.pokemon("pk-1").ivs().attack());
    }

    @Test
    void vitaminsAndFriendshipBerriesRaiseFriendshipAsInTheMainGames() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "protein", 5, "pomeg_berry", 3, "tamato_berry", 3);
        t.use("ana", "protein", "pk-1");
        assertEquals(70 + 5, t.pokemon("pk-1").friendship(), "a vitamin gives +5 below 100");
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withFriendship(150))));
        t.use("ana", "protein", "pk-1");
        assertEquals(153, t.pokemon("pk-1").friendship(), "+3 from 100");
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withFriendship(254))));
        t.use("ana", "protein", "pk-1");
        assertEquals(255, t.pokemon("pk-1").friendship(), "at most 255");
        // a Pomeg Berry lowers the HP EVs by 10 and gives friendship: +10, +5, +2
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withEvs(new StatBlock(25, 0, 0, 0, 0, 4)).withFriendship(70))));
        t.use("ana", "pomeg_berry", "pk-1");
        assertEquals(15, t.pokemon("pk-1").evs().hp());
        assertEquals(80, t.pokemon("pk-1").friendship());
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withFriendship(120))));
        t.use("ana", "pomeg_berry", "pk-1");
        assertEquals(5, t.pokemon("pk-1").evs().hp());
        assertEquals(125, t.pokemon("pk-1").friendship());
        t.use("ana", "pomeg_berry", "pk-1");
        assertEquals(0, t.pokemon("pk-1").evs().hp(), "the EVs stop at 0");
        assertEquals(130, t.pokemon("pk-1").friendship());
        t.use("ana", "tamato_berry", "pk-1");
        assertEquals(0, t.pokemon("pk-1").evs().speed(), "Speed was 4: it stops at 0");
        // nothing to lower and nothing to gain: refused and kept
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withFriendship(255))));
        refused(() -> t.use("ana", "tamato_berry", "pk-1"));
        assertEquals(2, t.count("ana", "tamato_berry"));
        assertFalse(BagDesk.usableInBattle(t.rules.moves(), "pomeg_berry"), "they cannot be eaten during a battle");
    }

    @Test
    void aClassFeatureMultipliesTheFriendshipAnItemGives() {
        t.becomes("ana", "coordinator", 6); // Crowd Favourite: friendship gain x1.5
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "protein", 1);
        t.use("ana", "protein", "pk-1");
        assertEquals(70 + 7, t.pokemon("pk-1").friendship(), "5 x 1.5 rounded down");
    }

    @Test
    void aNatureMintSetsTheNatureWhenNaturesAreOn() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "adamant_mint", 2, "mint_leaf", 1);
        t.use("ana", "adamant_mint", "pk-1");
        assertEquals("adamant", t.pokemon("pk-1").nature());
        refused(() -> t.use("ana", "adamant_mint", "pk-1"));
        refused(() -> t.use("ana", "mint_leaf", "pk-1")); // a mint plant does nothing
        t.rules = t.rules.withProgress(ProgressSettings.defaults().withPokemon(PokemonSettings.defaults().withNaturesEnabled(false)));
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withNature("hardy"))));
        refused(() -> t.use("ana", "adamant_mint", "pk-1"));
        assertEquals(1, t.count("ana", "adamant_mint"));
    }

    // =================================================================================================== stones, berries, TMs, held items

    @Test
    void anEvolutionStoneStartsTheEvolutionAndIsUsedUpOnlyWhenItWorks() {
        t.give(t.sheet("pk-1", "ana", "pikachu", 20, "tackle"));
        t.bag("ana", "thunder_stone", 1, "fire_stone", 1);
        assertTrue(refused(() -> t.use("ana", "fire_stone", "pk-1")).getMessage().contains("no effect"));
        assertEquals(1, t.count("ana", "fire_stone"));
        t.use("ana", "thunder_stone", "pk-1");
        assertTrue(t.state().prompts().containsKey("evolve-pk-1-raichu"), "the owner is asked");
        assertEquals(0, t.count("ana", "thunder_stone"));
        // answered yes
        t.play(ProgressDesk.answer(t.state(), PLAYER, "evolve-pk-1-raichu", "yes", t.rules.species(), ItemFixtures.moves(), ProgressFixtures.RULES, t.rules.progress()));
        assertEquals("raichu", t.pokemon("pk-1").speciesId());
    }

    @Test
    void anEvolutionStoneEvolvesAtOnceWhenTheGameIsSetToAutomatic() {
        t.rules = t.rules.withProgress(ProgressSettings.defaults().withEvolution(ProgressSettings.EvolutionMode.AUTOMATIC, ProgressSettings.UnknownFallback.ASK_ADMIN));
        t.give(t.sheet("pk-1", "ana", "pikachu", 20, "tackle"));
        t.bag("ana", "thunder_stone", 1);
        t.use("ana", "thunder_stone", "pk-1");
        assertEquals("raichu", t.pokemon("pk-1").speciesId());
        assertTrue(t.trainer("ana").xp() > 0, "an evolution gives trainer XP");
        assertEquals(0, t.count("ana", "thunder_stone"));
    }

    @Test
    void aBerryIsEatenFromTheBag() {
        t.give(t.bulbasaur("pk-1", "ana", 30).withHp(5));
        t.bag("ana", "oran_berry", 1, "sitrus_berry", 1, "lum_berry", 1, "cheri_berry", 1);
        t.use("ana", "oran_berry", "pk-1");
        assertEquals(15, t.pokemon("pk-1").hp(), "an Oran Berry gives 10 HP");
        t.use("ana", "sitrus_berry", "pk-1");
        assertEquals(15 + t.maxHp(t.pokemon("pk-1")) / 4, t.pokemon("pk-1").hp(), "a Sitrus Berry gives a quarter");
        refused(() -> t.use("ana", "cheri_berry", "pk-1")); // no paralysis
        t.play(List.of(new GameEvent.PokemonSet(t.pokemon("pk-1").withHp(t.maxHp(t.pokemon("pk-1"))).withStatus(Status.BURN))));
        t.use("ana", "lum_berry", "pk-1");
        assertEquals(Status.NONE, t.pokemon("pk-1").status());
    }

    @Test
    void aTmTeachesItsMoveAndAFullPokemonReplacesOne() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "tm_ember", 1, "tm_scratch", 1, "tm_slash", 1, "tm_watergun", 1, "tm_flamethrower", 1);
        t.use("ana", "tm_ember", "pk-1");
        assertEquals(List.of("tackle", "growl", "ember"), t.pokemon("pk-1").moves().stream().map(MoveSlot::moveId).toList());
        assertEquals(0, t.count("ana", "tm_ember"));
        refused(() -> t.use("ana", "tm_watergun", "pk-1")); // Bulbasaur learns it by tutor, not by TM
        t.bag("ana", "tm_ember", 1, "tm_scratch", 1, "tm_slash", 1, "tm_flamethrower", 1);
        refused(() -> t.use("ana", "tm_ember", "pk-1")); // knows it already
        t.use("ana", "tm_scratch", "pk-1");
        assertEquals(4, t.pokemon("pk-1").moves().size());
        // four moves: a move has to be forgotten
        assertTrue(refused(() -> t.use("ana", "tm_slash", "pk-1")).getMessage().contains("forget"));
        refused(() -> t.use("ana", "tm_slash", "pk-1", BagDesk.Choice.forgetting(4)));
        assertEquals(1, t.count("ana", "tm_slash"));
        t.use("ana", "tm_slash", "pk-1", BagDesk.Choice.forgetting(1));
        assertEquals(List.of("tackle", "slash", "ember", "scratch"), t.pokemon("pk-1").moves().stream().map(MoveSlot::moveId).toList(), "Growl was replaced");
        assertEquals(0, t.count("ana", "tm_slash"));
    }

    @Test
    void aTmIsRefusedWhenTheCardFreezesTheMovesOrTheDataHasNoList() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.give(t.sheet("pk-2", "ana", "venusaur", 30, "tackle"));
        t.bag("ana", "tm_ember", 2);
        refused(() -> t.use("ana", "tm_ember", "pk-2")); // the data has no TM list for Venusaur
        t.rules = t.rules.withProgress(ProgressSettings.defaults().withMovesFrozen(true));
        assertTrue(refused(() -> t.use("ana", "tm_ember", "pk-1")).getMessage().contains("frozen"));
        assertEquals(2, t.count("ana", "tm_ember"));
    }

    @Test
    void aHeldItemIsGivenToAPokemonAndTheOldOneGoesBack() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "leftovers", 1, "choice_band", 1, "potion", 1);
        t.play(BagDesk.hold(t.state(), t.rules, "ana", "pk-1", "leftovers"));
        assertEquals("leftovers", t.pokemon("pk-1").heldItem());
        assertEquals(0, t.count("ana", "leftovers"));
        t.play(BagDesk.hold(t.state(), t.rules, "ana", "pk-1", "choice_band"));
        assertEquals("choice_band", t.pokemon("pk-1").heldItem());
        assertEquals(1, t.count("ana", "leftovers"), "what it held went back in the bag");
        refused(() -> BagDesk.hold(t.state(), t.rules, "ana", "pk-1", "potion")); // a Potion cannot be held
        refused(() -> t.use("ana", "leftovers", "pk-1")); // it is held, not used
        t.play(BagDesk.unhold(t.state(), t.rules, "ana", "pk-1"));
        assertEquals(null, t.pokemon("pk-1").heldItem());
        assertEquals(1, t.count("ana", "choice_band"));
        refused(() -> BagDesk.unhold(t.state(), t.rules, "ana", "pk-1"));
    }

    @Test
    void anItemWithoutAnAutomaticEffectIsAppliedByTheAdminByHand() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "herbal_tea", 2);
        assertTrue(refused(() -> t.use("ana", "herbal_tea", "pk-1")).getMessage().contains("admin"));
        t.play(BagDesk.use(t.state(), t.rules, ADMIN, "ana", "herbal_tea", null, BagDesk.Choice.none()));
        assertEquals(1, t.count("ana", "herbal_tea"));
        assertEquals(BagDesk.Needs.ADMIN, BagDesk.needs(t.rules.moves(), "herbal_tea"));
    }

    @Test
    void ballsAndBattleItemsAreOnlyUsedInABattle() {
        t.give(t.bulbasaur("pk-1", "ana", 30));
        t.bag("ana", "poke_ball", 1, "x_attack", 1, "dire_hit", 1);
        assertTrue(refused(() -> t.use("ana", "poke_ball", "pk-1")).getMessage().contains("battle"));
        assertTrue(refused(() -> t.use("ana", "x_attack", "pk-1")).getMessage().contains("battle"));
        assertTrue(refused(() -> t.use("ana", "dire_hit", "pk-1")).getMessage().contains("battle"));
        assertEquals(BagDesk.Needs.BATTLE_ONLY, BagDesk.needs(t.rules.moves(), "x_attack"));
        assertEquals(BagDesk.Needs.TM, BagDesk.needs(t.rules.moves(), "tm_ember"));
        assertEquals(BagDesk.Needs.POKEMON_AND_MOVE, BagDesk.needs(t.rules.moves(), "ether"));
        assertEquals(BagDesk.Needs.HOLD, BagDesk.needs(t.rules.moves(), "leftovers"));
        assertEquals(BagDesk.Needs.POKEMON, BagDesk.needs(t.rules.moves(), "potion"));
    }

    // ======================================================================================================= limits, pockets, admin

    @Test
    void aPocketHasLimitedSlotsAndAStackHasALimitedSize() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withBagLimits(2, 5));
        t.bag("ana", "potion", 3, "super_potion", 1);
        // the medicine pocket holds two different items
        BattleRefusedException full = assertThrows(BattleRefusedException.class, () -> BagDesk.give(t.state(), t.rules, ADMIN, "ana", "revive", 1));
        assertTrue(full.getMessage().contains("pocket is full"), full.getMessage());
        t.play(BagDesk.give(t.state(), t.rules, ADMIN, "ana", "potion", 2)); // one more of what is already there fits
        assertEquals(5, t.count("ana", "potion"));
        assertTrue(assertThrows(BattleRefusedException.class, () -> BagDesk.give(t.state(), t.rules, ADMIN, "ana", "potion", 1)).getMessage().contains("stack"));
        t.play(BagDesk.give(t.state(), t.rules, ADMIN, "ana", "great_ball", 1)); // another pocket has its own slots
        // a player cannot give items
        assertThrows(BattleRefusedException.class, () -> BagDesk.give(t.state(), t.rules, PLAYER, "ana", "potion", 1));
        // an empty slot is free again
        t.play(BagDesk.take(t.state(), t.rules, "ana", "super_potion", 1));
        t.play(BagDesk.give(t.state(), t.rules, ADMIN, "ana", "revive", 1));
        assertEquals(1, t.count("ana", "revive"));
    }

    @Test
    void theHeavyPackFeatureAddsSlots() {
        t.rules = t.rules.withEconomy(EconomySettings.defaults().withBagLimits(2, 0));
        t.bag("ana", "potion", 1, "super_potion", 1);
        assertThrows(BattleRefusedException.class, () -> BagDesk.give(t.state(), t.rules, ADMIN, "ana", "revive", 1));
        t.becomes("ana", "hiker", 6); // Heavy Pack: 10 more slots
        t.play(BagDesk.give(t.state(), t.rules, ADMIN, "ana", "revive", 1));
        assertEquals(1, t.count("ana", "revive"));
    }

    @Test
    void theBagIsShownInPockets() {
        t.bag("ana", "potion", 3, "poke_ball", 5, "tm_ember", 1, "mystery_thing", 2);
        Map<String, Map<String, Integer>> pockets = BagDesk.pockets(t.rules, t.trainer("ana"));
        assertEquals(List.of("medicine", "other", "poke_balls", "tm"), List.copyOf(pockets.keySet()));
        assertEquals(Map.of("potion", 3), pockets.get("medicine"));
        assertEquals(Map.of("tm_ember", 1), pockets.get("tm"));
        assertFalse(pockets.containsKey("berries"));
    }

    @Test
    void usingAnItemIsOneActionThatAnUndoTakesBackWhole() {
        t.give(t.bulbasaur("pk-1", "ana", 30).withHp(5));
        GameState before = t.state();
        t.use("ana", "potion", "pk-1");
        assertEquals(25, t.pokemon("pk-1").hp());
        t.log.undo("u-admin", NOW, 1);
        assertEquals(before, t.state(), "the HP, the bag and the log line are all back");
        assertEquals(5, t.pokemon("pk-1").hp());
        assertEquals(3, t.count("ana", "potion"));
        t.log.redo("u-admin", NOW, 1);
        assertEquals(25, t.pokemon("pk-1").hp());
        // and the log can be rebuilt from what was stored
        GameLog again = GameLog.restore(t.log.settings(), t.log.entries(), t.log.snapshots(), t.log.audit());
        assertEquals(t.state(), again.state());
    }

    @Test
    void aPokemonInABattleThatIsNotSettledCannotBeTreatedOutsideIt() {
        PokemonSheet p = t.give(t.bulbasaur("pk-1", "ana", 30).withHp(5));
        Arena arena = new Arena(t.rules.moves(), "ana", "bo");
        arena.play(List.of(new GameEvent.PokemonSet(p)));
        PokemonSheet wild = ProgressFixtures.sheet(t.rules.species(), "w-1", null, "rattata", 5, "tackle");
        arena.play(List.of(new GameEvent.PokemonSet(wild)));
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("pk-1"), List.of()), Arena.wild("wild", "Rattata", "w-1")),
                List.of(ProgressFixtures.combatant(p, "ana", t.rules.species()), ProgressFixtures.combatant(wild, "wild", t.rules.species())));
        BattleRefusedException e = assertThrows(BattleRefusedException.class,
                () -> BagDesk.use(arena.state(), t.rules, PLAYER, "ana", "potion", "pk-1", BagDesk.Choice.none()));
        assertTrue(e.getMessage().contains("battle"), e.getMessage());
    }
}
