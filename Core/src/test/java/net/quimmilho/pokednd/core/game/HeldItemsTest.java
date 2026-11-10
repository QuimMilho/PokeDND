/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.Answers;
import static net.quimmilho.pokednd.core.game.Arena.holding;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.Test;

/**
 * Phase 47: the held items that Core runs (chapter 7.9 and 8.8): Leftovers, the Choice items, Focus Sash, Life Orb, the 18 type gems and
 * the berries that heal or cure. The Pokémon and the numbers are those of {@link AbilitiesTest}.
 */
class HeldItemsTest {

    private static final List<PokemonType> FIRE = List.of(PokemonType.FIRE);
    private static final List<PokemonType> NORMAL = List.of(PokemonType.NORMAL);
    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);
    private static final int[] NINE = {4, 5};
    private static final Answers EMBER = Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE, "ana-1-side0", "fail");
    private static final Answers SCRATCH = Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE);

    private final MoveBook book = MoveBook.of(Moves.all(), List.of(new ItemEntry("eviolite", "Eviolite", "held_items", List.of(), "", Optional.empty(),
            EffectClass.SCRIPTED, "scripted", List.of("onModifyDef"), Optional.empty(), JsonNodeFactory.instance.objectNode(), Optional.empty(),
            Optional.empty(), false, Optional.empty(), Optional.empty())));
    private Arena arena = new Arena(book, "ana", "bo");

    // ------------------------------------------------------------------------------------------------------------------ scaffolding

    private Combatant charmander(String... moves) {
        return arena.mon("ana-1", "ana", "Charmander", 20, 50, FIRE, 30, 28, 32, 30, 60, moves);
    }

    private Combatant rattata(String... moves) {
        return arena.mon("wild-1", "wild", "Rattata", 18, 46, NORMAL, 29, 30, 33, 31, 40, moves);
    }

    private static Combatant hp(Combatant c, int hp) {
        return c.withHp(hp);
    }

    private static Combatant status(Combatant c, Status status, int turns) {
        return c.withCondition(c.condition().withStatus(status, turns));
    }

    private void duel(BattleSettings rules, Combatant ana, Combatant wild) {
        arena.start(Battle.Type.WILD, rules, List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Rattata", "wild-1")),
                List.of(ana, wild));
    }

    private void duel(Combatant ana, Combatant wild) {
        duel(BattleSettings.defaults(), ana, wild);
    }

    private void fresh() {
        arena = new Arena(book, "ana", "bo");
    }

    private void round(Action ana, Action wild, Answers answers) {
        arena.round(ANA_FIRST);
        arena.declare("ana-1", ana);
        arena.declare("wild-1", wild);
        arena.resolve(answers);
        if (arena.battle().mustSwitch().isEmpty()) {
            arena.endRound();
        }
    }

    private void skipRound() {
        round(Action.skip(), Action.skip(), Answers.none());
    }

    private Combatant ana() {
        return arena.mon("ana-1");
    }

    private Combatant foe() {
        return arena.mon("wild-1");
    }

    private static int damage(int level, int diceTotal, int attack, int defense, double multiplier) {
        long inner = (2L * level / 5 + 2) * diceTotal * 5 * attack / defense;
        int base = (int) (inner / 50) + 2;
        return (int) Math.floor(base * multiplier + 1e-9);
    }

    // ================================================================================================================= Leftovers

    @Test
    void leftoversHealASixteenthOfTheMaximumHpAtTheEndOfTheRound() {
        duel(holding(hp(charmander("tackle"), 30), "leftovers"), rattata("tackle"));
        skipRound();
        assertEquals(30 + 3, ana().hp(), "50 / 16, rounded down");
        assertEquals("leftovers", ana().condition().heldItem(), "Leftovers are not used up");
    }

    @Test
    void leftoversDoNothingAtFullHp() {
        duel(holding(charmander("tackle"), "leftovers"), rattata("tackle"));
        skipRound();
        assertEquals(50, ana().hp());
    }

    // ============================================================================================================== Choice items

    @Test
    void choiceBandRaisesPhysicalDamageAndLocksTheMove() {
        duel(holding(charmander("scratch", "tackle"), "choice_band"), rattata("tackle"));
        round(Action.move("scratch", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(damage(20, 9, 30, 30, 1.5), 46 - foe().hp());
        assertEquals(1, ana().condition().volatileValue(Volatiles.CHOICE_LOCK), "the first move of the list");
        arena.round(ANA_FIRST);
        assertThrows(BattleRefusedException.class, () -> arena.declare("ana-1", Action.move("tackle", "wild-1")), "locked into Scratch");
        arena.declare("ana-1", Action.move("scratch", "wild-1")); // the same move is fine
    }

    @Test
    void theChoiceLockEndsWhenThePokemonGoesBack() {
        Combatant second = arena.mon("ana-2", "ana", "Squirtle", 20, 60, List.of(PokemonType.WATER), 30, 28, 32, 30, 50, "tackle");
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of("ana-2")), Arena.wild("wild", "Rattata", "wild-1")),
                List.of(holding(charmander("scratch", "tackle"), "choice_band"), second, rattata("tackle")));
        round(Action.move("scratch", "wild-1"), Action.skip(), SCRATCH);
        assertTrue(ana().condition().has(Volatiles.CHOICE_LOCK));
        round(Action.switchTo("ana-2"), Action.skip(), Answers.none());
        assertFalse(arena.mon("ana-1").condition().has(Volatiles.CHOICE_LOCK));
    }

    @Test
    void choiceBandDoesNotChangeSpecialMoves() {
        duel(holding(charmander("ember"), "choice_band"), rattata("tackle"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5), 46 - foe().hp());
    }

    @Test
    void choiceSpecsRaiseSpecialDamage() {
        duel(holding(charmander("ember"), "choice_specs"), rattata("tackle"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5 * 1.5), 46 - foe().hp());
        assertEquals(1, ana().condition().volatileValue(Volatiles.CHOICE_LOCK));
    }

    @Test
    void choiceScarfRaisesTheSpeedForTheInitiative() {
        duel(holding(charmander("tackle"), "choice_scarf"), rattata("tackle"));
        arena.round(ANA_FIRST);
        Battle.InitiativeSlot slot = arena.battle().slots().stream().filter(s -> s.participantId().equals("ana")).findFirst().orElseThrow();
        assertEquals(90, slot.speed(), "60 x 1.5");
        assertEquals(4, slot.modifier(), "floor(90 / 20) instead of 3");
        assertEquals(60, ana().speed(), "the stat itself is not changed");
    }

    // =================================================================================================================== Focus Sash

    @Test
    void focusSashLeavesOneHpFromFullHpAndIsUsedUp() {
        duel(charmander("flamethrower"), holding(rattata("tackle"), "focus_sash"));
        round(Action.move("flamethrower", "wild-1"), Action.skip(),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {10, 10, 10}, "ana-1-side0", "fail"));
        assertEquals(1, foe().hp());
        assertNull(foe().condition().heldItem());
        assertTrue(arena.said("hung on using its Focus Sash"));
    }

    @Test
    void focusSashDoesNotHelpBelowFullHp() {
        duel(charmander("flamethrower"), holding(hp(rattata("tackle"), 40), "focus_sash"));
        round(Action.move("flamethrower", "wild-1"), Action.skip(),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {10, 10, 10}, "ana-1-side0", "fail"));
        assertEquals(0, foe().hp());
    }

    // ==================================================================================================================== Life Orb

    @Test
    void lifeOrbRaisesTheDamageAndCostsATenthOfTheHp() {
        duel(holding(charmander("ember"), "life_orb"), rattata("tackle"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5 * 1.3), 46 - foe().hp());
        assertEquals(50 - 5, ana().hp(), "a tenth of 50");
        assertTrue(arena.said("from its Life Orb"));
    }

    @Test
    void lifeOrbCostsNothingWhenTheMoveMisses() {
        duel(holding(charmander("firefang"), "life_orb"), rattata("tackle"));
        round(Action.move("firefang", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "miss")); // 95 % accuracy: there is a miss band
        assertEquals(50, ana().hp());
    }

    // ===================================================================================================================== gems

    @Test
    void aTypeGemRaisesTheFirstMoveOfItsTypeAndIsUsedUp() {
        duel(holding(charmander("ember"), "fire_gem"), rattata("tackle"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5 * 1.3), 46 - foe().hp());
        assertNull(ana().condition().heldItem());
        assertTrue(arena.said("boosted the power"));
        int after = foe().hp();
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5), after - foe().hp(), "the second Ember has no gem");
    }

    @Test
    void aGemOfAnotherTypeDoesNothingAndStays() {
        duel(holding(charmander("ember"), "water_gem"), rattata("tackle"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5), 46 - foe().hp());
        assertEquals("water_gem", ana().condition().heldItem());
    }

    @Test
    void thereIsAGemForEveryOneOfTheEighteenTypes() {
        for (PokemonType type : PokemonType.values()) {
            assertTrue(ItemScripts.knows(type.id() + "_gem"), type.id());
            assertEquals(type, ItemScripts.gemType(Battle.squash(type.id() + "_gem")));
        }
        assertEquals(18, PokemonType.values().length);
    }

    // ==================================================================================================================== berries

    @Test
    void anOranBerryHealsTenHpAtHalfHp() {
        duel(holding(hp(charmander("tackle"), 25), "oran_berry"), rattata("tackle"));
        skipRound();
        assertEquals(35, ana().hp());
        assertNull(ana().condition().heldItem(), "a berry is used up");
        assertTrue(arena.said("Charmander ate its Oran Berry!"));
    }

    @Test
    void aBerryWaitsUntilTheHpIsLowEnough() {
        duel(holding(hp(charmander("tackle"), 30), "oran_berry"), rattata("tackle"));
        skipRound();
        assertEquals(30, ana().hp());
        assertEquals("oran_berry", ana().condition().heldItem());
    }

    @Test
    void aSitrusBerryHealsAQuarterAtHalfHp() {
        duel(holding(hp(charmander("tackle"), 25), "sitrus_berry"), rattata("tackle"));
        skipRound();
        assertEquals(25 + 12, ana().hp());
    }

    @Test
    void figyWikiMagoAguavAndIapapaHealAThirdAtAQuarterOfTheHp() {
        for (String berry : List.of("figy_berry", "wiki_berry", "mago_berry", "aguav_berry", "iapapa_berry")) {
            fresh();
            duel(holding(hp(charmander("tackle"), 12), berry), rattata("tackle"));
            skipRound();
            assertEquals(12 + 16, ana().hp(), berry);
            assertNull(ana().condition().heldItem(), berry);
        }
        fresh();
        duel(holding(hp(charmander("tackle"), 13), "figy_berry"), rattata("tackle"));
        skipRound();
        assertEquals(13, ana().hp(), "13 of 50 is more than a quarter");
    }

    @Test
    void theCuringBerriesCureTheirStatus() {
        record Case(String berry, Status status) {
        }
        for (Case c : List.of(new Case("cheri_berry", Status.PARALYSIS), new Case("chesto_berry", Status.SLEEP), new Case("pecha_berry", Status.POISON),
                new Case("pecha_berry", Status.TOXIC), new Case("rawst_berry", Status.BURN), new Case("aspear_berry", Status.FREEZE),
                new Case("lum_berry", Status.PARALYSIS), new Case("lum_berry", Status.BURN))) {
            fresh();
            duel(holding(status(charmander("tackle"), c.status, c.status == Status.SLEEP ? 2 : 0), c.berry), rattata("tackle"));
            skipRound();
            assertEquals(Status.NONE, ana().status(), c.berry + " against " + c.status);
            assertNull(ana().condition().heldItem(), c.berry);
        }
    }

    @Test
    void aBerryOfTheWrongKindWaits() {
        duel(holding(status(charmander("tackle"), Status.BURN, 0), "cheri_berry"), rattata("tackle"));
        skipRound();
        assertEquals(Status.BURN, ana().status());
        assertEquals("cheri_berry", ana().condition().heldItem());
    }

    @Test
    void persimAndLumCureConfusion() {
        for (String berry : List.of("persim_berry", "lum_berry")) {
            fresh();
            Combatant dizzy = charmander("tackle");
            duel(holding(dizzy.withCondition(dizzy.condition().withVolatile(Volatiles.CONFUSION, 3)), berry), rattata("tackle"));
            skipRound();
            assertFalse(ana().condition().has(Volatiles.CONFUSION), berry);
            assertNull(ana().condition().heldItem(), berry);
        }
    }

    @Test
    void aBerryIsEatenRightAfterTheHitThatCallsForIt() {
        duel(charmander("flamethrower"), holding(rattata("tackle"), "oran_berry"));
        round(Action.move("flamethrower", "wild-1"), Action.skip(),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {5, 5, 5}, "ana-1-side0", "fail"));
        // 3d10 rolled 15 = power 75: 17 x 1.5 = 25 damage: 21 HP left, below half of 46, so the berry heals 10
        assertEquals(46 - 25 + 10, foe().hp());
        assertNull(foe().condition().heldItem());
    }

    // ======================================================================================================== tasks and the switch

    @Test
    void aScriptedHeldItemThatCoreDoesNotRunMakesOneTask() {
        duel(holding(charmander("tackle"), "eviolite"), rattata("tackle"));
        assertEquals(1, arena.battle().tasks().size());
        assertEquals("item:eviolite", arena.battle().tasks().get(0).moveId());
        assertTrue(arena.battle().tasks().get(0).text().contains("Eviolite"));
        assertEquals(MoveBook.Handling.MANUAL, book.itemHandling("eviolite"));
    }

    @Test
    void anItemThatCoreRunsNeverMakesATask() {
        duel(holding(charmander("tackle"), "leftovers"), holding(rattata("tackle"), "oran_berry"));
        assertEquals(List.of(), arena.battle().tasks());
        assertEquals(MoveBook.Handling.IMPLEMENTED, book.itemHandling("choice_band"));
    }

    @Test
    void withHeldItemsSwitchedOffBattlesRunWithoutAnyItemEffect() {
        BattleSettings off = BattleSettings.defaults().withTraits(true, false);
        // no Leftovers, no berry, no Life Orb, no gem, no Focus Sash, no Choice lock, no task
        duel(off, holding(hp(charmander("ember"), 30), "leftovers"), holding(rattata("tackle"), "focus_sash"));
        assertEquals(List.of(), arena.battle().tasks());
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(30, ana().hp());
        assertEquals(damage(20, 9, 32, 31, 1.5), 46 - foe().hp());

        fresh();
        duel(off, holding(charmander("flamethrower"), "choice_band"), holding(rattata("tackle"), "focus_sash"));
        round(Action.move("flamethrower", "wild-1"), Action.skip(),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {10, 10, 10}, "ana-1-side0", "fail"));
        assertEquals(0, foe().hp(), "the Focus Sash did nothing");
        assertFalse(ana().condition().has(Volatiles.CHOICE_LOCK));

        fresh();
        duel(off, holding(hp(charmander("tackle"), 10), "sitrus_berry"), rattata("tackle"));
        skipRound();
        assertEquals(10, ana().hp());
        assertEquals("sitrus_berry", ana().condition().heldItem(), "the berry stays");
    }

    @Test
    void theListOfImplementedItemsMatchesTheCode() {
        MoveBook.Implemented list = MoveBook.implementedEffects();
        assertEquals(ItemScripts.implemented(), list.items().stream().map(Battle::squash).collect(java.util.stream.Collectors.toSet()));
        assertEquals(6 + 18 + 7 + 7, list.items().size());
    }
}
