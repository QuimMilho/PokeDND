/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.Answers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.importer.Catalog.AbilityEntry;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.Test;

/**
 * Phase 47: the 25 abilities that Core runs (chapter 7.9), one test for each, and the switch that turns them all off. The two Pokémon
 * are the ones of {@link MoveEffectsTest}; the damage is worked out by hand from the formula of chapter 7.6:
 *
 * <pre>
 *   Ana's Charmander, level 20, Fire.    HP 50, Atk 30, Def 28, Sp.Atk 32, Sp.Def 30, Speed 60 (+3)
 *   wild Rattata,     level 18, Normal.  HP 46, Atk 29, Def 30, Sp.Atk 33, Sp.Def 31, Speed 40 (+2)
 *   Ember (2d8, rolled 9 = power 45) from Charmander to Rattata: floor(floor(10 x 45 x 32 / 31) / 50) + 2 = 11, x1.5 (same type) = 16
 * </pre>
 */
class AbilitiesTest {

    private static final List<PokemonType> FIRE = List.of(PokemonType.FIRE);
    private static final List<PokemonType> NORMAL = List.of(PokemonType.NORMAL);
    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);
    private static final int[] NINE = {4, 5};
    private static final Answers EMBER = Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE, "ana-1-side0", "fail");
    private static final Answers SCRATCH = Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE);

    private final MoveBook book = MoveBook.of(Moves.all(), List.of(), List.of(
            new AbilityEntry("moldbreaker", "Mold Breaker", Optional.of("Moves ignore the abilities of the target."), EffectClass.SCRIPTED, List.of("onStart")),
            new AbilityEntry("swiftswim", "Swift Swim", Optional.of("Speed doubles in rain."), EffectClass.SCRIPTED, List.of("onModifySpe")),
            new AbilityEntry("sturdy", "Sturdy", Optional.empty(), EffectClass.SCRIPTED, List.of("onDamage"))));
    private Arena arena = new Arena(book, "ana", "bo");

    // ------------------------------------------------------------------------------------------------------------------ scaffolding

    private Combatant charmander(String... moves) {
        return arena.mon("ana-1", "ana", "Charmander", 20, 50, FIRE, 30, 28, 32, 30, 60, moves);
    }

    private Combatant rattata(String... moves) {
        return arena.mon("wild-1", "wild", "Rattata", 18, 46, NORMAL, 29, 30, 33, 31, 40, moves);
    }

    private static Combatant with(Combatant c, String ability) {
        return Arena.withAbility(c, ability);
    }

    private static Combatant hp(Combatant c, int hp) {
        return c.withHp(hp);
    }

    private static Combatant types(Combatant c, PokemonType... types) {
        return c.withBuild(c.build().withTypes(List.of(types)));
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

    private void duelWithReserve(Combatant ana, Combatant reserve, Combatant wild) {
        arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of("ana-2")), Arena.wild("wild", "Rattata", "wild-1")), List.of(ana, reserve, wild));
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

    private Combatant ana() {
        return arena.mon("ana-1");
    }

    private Combatant foe() {
        return arena.mon("wild-1");
    }

    /** The damage of a move by the formula of chapter 7.6. */
    private static int damage(int level, int diceTotal, int attack, int defense, double multiplier) {
        long inner = (2L * level / 5 + 2) * diceTotal * 5 * attack / defense;
        int base = (int) (inner / 50) + 2;
        return (int) Math.floor(base * multiplier + 1e-9);
    }

    private GameEvent.RollRequested firstRoll() {
        arena.play(BattleDesk.startResolving(arena.state()));
        List<GameEvent> events = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
        arena.play(events);
        return (GameEvent.RollRequested) events.get(0);
    }

    // ============================================================================================================== the 25 abilities

    @Test
    void sturdyLetsAPokemonAtFullHpSurviveWithOneHp() {
        duel(charmander("flamethrower"), with(rattata("tackle"), "sturdy"));
        round(Action.move("flamethrower", "wild-1"), Action.skip(),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {10, 10, 10}, "ana-1-side0", "fail"));
        // 3d10 rolled 30 = power 150: 32 x 1.5 = 48 damage, more than the 46 HP
        assertEquals(1, foe().hp());
        assertTrue(arena.said("Rattata endured the hit with Sturdy!"));
    }

    @Test
    void sturdyDoesNotHelpBelowFullHp() {
        duel(charmander("flamethrower"), hp(with(rattata("tackle"), "sturdy"), 40));
        round(Action.move("flamethrower", "wild-1"), Action.skip(),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {10, 10, 10}, "ana-1-side0", "fail"));
        assertEquals(0, foe().hp());
    }

    @Test
    void sturdyMakesAPokemonImmuneToOneHitKnockOutMoves() {
        duel(charmander("fissure"), with(rattata("tackle"), "sturdy"));
        round(Action.move("fissure", "wild-1"), Action.skip(), Answers.none()); // no hit roll is asked
        assertEquals(46, foe().hp());
        assertTrue(arena.said("thanks to its Sturdy"));
    }

    @Test
    void keenEyeIgnoresTheEvasionOfTheTarget() {
        Combatant evasive = rattata("tackle");
        duel(with(charmander("tackle"), "keeneye"), evasive.withCondition(evasive.condition().withStage("evasion", 3)));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("tackle", "wild-1")).declare("wild-1", Action.skip());
        assertEquals(List.of("hit", "crit"), firstRoll().bands().stream().map(GameState.Band::label).toList(), "evasion +3 would have made a miss band");
    }

    @Test
    void withoutKeenEyeEvasionMakesTheMoveMiss() {
        Combatant evasive = rattata("tackle");
        duel(charmander("tackle"), evasive.withCondition(evasive.condition().withStage("evasion", 3)));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("tackle", "wild-1")).declare("wild-1", Action.skip());
        assertTrue(firstRoll().bands().stream().anyMatch(b -> b.label().equals("miss")));
    }

    @Test
    void keenEyeKeepsItsAccuracyFromBeingLowered() {
        duel(with(charmander("tackle"), "keeneye"), rattata("sandattack"));
        round(Action.skip(), Action.move("sandattack", "ana-1"), Answers.none());
        assertEquals(0, ana().condition().stage("accuracy"));
        assertTrue(arena.said("Keen Eye keeps its accuracy from being lowered"));
    }

    @Test
    void intimidateLowersTheAttackOfTheFoesWhenTheBattleStarts() {
        duel(with(charmander("tackle"), "intimidate"), rattata("tackle"));
        assertEquals(-1, foe().condition().stage("atk"));
        assertEquals(0, ana().condition().stage("atk"));
        assertTrue(arena.said("Charmander's Intimidate") || arena.said("'s Intimidate cuts the Attack of wild Rattata"));
    }

    @Test
    void intimidateActsAgainWhenThePokemonIsSwitchedIn() {
        Combatant second = arena.mon("ana-2", "ana", "Gyarados", 20, 60, List.of(PokemonType.WATER), 30, 28, 32, 30, 50, "tackle");
        duelWithReserve(charmander("tackle"), with(second, "intimidate"), rattata("tackle"));
        assertEquals(0, foe().condition().stage("atk"));
        round(Action.switchTo("ana-2"), Action.skip(), Answers.none());
        assertEquals(-1, foe().condition().stage("atk"));
    }

    @Test
    void innerFocusOwnTempoAndObliviousBlockIntimidate() {
        for (String ability : List.of("innerfocus", "owntempo", "oblivious")) {
            arena = new Arena(book, "ana", "bo");
            duel(with(charmander("tackle"), "intimidate"), with(rattata("tackle"), ability));
            assertEquals(0, foe().condition().stage("atk"), ability);
        }
    }

    @Test
    void innerFocusPreventsFlinching() {
        duel(charmander("bite"), with(rattata("tackle"), "innerfocus"));
        round(Action.move("bite", "wild-1"), Action.move("tackle", "ana-1"), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE, "wild-1-hit0", "hit", "wild-1-dmg0", NINE));
        assertFalse(arena.asked.keySet().stream().anyMatch(id -> id.endsWith("side0")), "the chance of a flinch is not even rolled");
        assertFalse(arena.said("flinched"));
    }

    @Test
    void withoutInnerFocusTheFlinchChanceIsRolled() {
        duel(charmander("bite"), rattata("tackle"));
        round(Action.move("bite", "wild-1"), Action.move("tackle", "ana-1"),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE, "ana-1-side0", "success", "wild-1-hit0", "hit", "wild-1-dmg0", NINE));
        assertTrue(arena.asked.keySet().stream().anyMatch(id -> id.endsWith("side0")));
        assertTrue(arena.said("flinched"));
    }

    @Test
    void blazeOvergrowTorrentAndSwarmBoostTheirTypeAtAThirdOfTheHp() {
        record Case(String ability, String move, PokemonType type, Answers answers) {
        }
        List<Case> cases = List.of(
                new Case("blaze", "ember", PokemonType.FIRE, EMBER),
                new Case("overgrow", "absorb", PokemonType.GRASS, Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE)),
                new Case("torrent", "watergun", PokemonType.WATER, Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE)),
                new Case("swarm", "bugbuzz", PokemonType.BUG, Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE)));
        for (Case c : cases) {
            int power = c.move.equals("absorb") ? 4 : 9; // Absorb is 1d6
            Answers answers = c.move.equals("absorb") ? Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {4}) : c.answers;
            // at a third of the HP (16 of 50): x1.5; the Pokémon has the type of its move, so the same-type bonus is the same
            arena = new Arena(book, "ana", "bo");
            duel(hp(with(types(charmander(c.move), c.type), c.ability), 16), rattata("tackle"));
            round(Action.move(c.move, "wild-1"), Action.skip(), answers);
            int boosted = 46 - foe().hp();
            arena = new Arena(book, "ana", "bo");
            duel(hp(with(types(charmander(c.move), c.type), c.ability), 17), rattata("tackle"));
            round(Action.move(c.move, "wild-1"), Action.skip(), answers);
            int normal = 46 - foe().hp();
            assertEquals(damage(20, power, 32, 31, 1.5 * 1.5), boosted, c.ability + " at 16 HP");
            assertEquals(damage(20, power, 32, 31, 1.5), normal, c.ability + " at 17 HP");
        }
    }

    @Test
    void aPinchAbilityOnlyBoostsMovesOfItsOwnType() {
        duel(hp(with(charmander("tackle"), "blaze"), 10), rattata("tackle"));
        round(Action.move("tackle", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(damage(20, 9, 30, 30, 1.0), 46 - foe().hp());
    }

    @Test
    void sheerForceTradesTheSecondaryEffectForPower() {
        duel(with(charmander("ember"), "sheerforce"), rattata("tackle"));
        round(Action.move("ember", "wild-1"), Action.skip(), SCRATCH); // no chance roll is asked: the burn is gone
        assertEquals(damage(20, 9, 32, 31, 1.5 * 1.3), 46 - foe().hp());
        assertFalse(arena.asked.keySet().stream().anyMatch(id -> id.endsWith("side0")));
    }

    @Test
    void sheerForceDoesNotChangeAMoveWithoutSecondaryEffects() {
        duel(with(charmander("tackle"), "sheerforce"), rattata("tackle"));
        round(Action.move("tackle", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(damage(20, 9, 30, 30, 1.0), 46 - foe().hp());
    }

    @Test
    void ownTempoCannotBeConfused() {
        duel(charmander("confuseray"), with(rattata("tackle"), "owntempo"));
        round(Action.move("confuseray", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        assertFalse(foe().condition().has(Volatiles.CONFUSION));
        assertTrue(arena.said("But it failed!"));
    }

    @Test
    void friskTellsWhatTheFoeHolds() {
        duel(with(charmander("tackle"), "frisk"), Arena.holding(rattata("tackle"), "oran_berry"));
        assertTrue(arena.said("frisks wild Rattata and finds its Oran Berry!"));
    }

    @Test
    void thickFatHalvesFireAndIceDamage() {
        duel(charmander("ember"), with(rattata("tackle"), "thickfat"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5 * 0.5), 46 - foe().hp());
    }

    @Test
    void gluttonyEatsAHealingBerryAtHalfHpInsteadOfAQuarter() {
        String figy = "figy_berry";
        duel(with(Arena.holding(hp(charmander("tackle"), 25), figy), "gluttony"), rattata("tackle"));
        round(Action.skip(), Action.skip(), Answers.none());
        assertEquals(25 + 16, ana().hp(), "a third of 50, rounded down");
        assertNull(ana().condition().heldItem());
        arena = new Arena(book, "ana", "bo");
        duel(Arena.holding(hp(charmander("tackle"), 25), figy), rattata("tackle"));
        round(Action.skip(), Action.skip(), Answers.none());
        assertEquals(25, ana().hp(), "without Gluttony the berry waits for a quarter of the HP");
        assertEquals(figy, ana().condition().heldItem());
    }

    @Test
    void infiltratorPassesASubstitute() {
        Combatant decoy = rattata("tackle");
        duel(with(charmander("tackle"), "infiltrator"), decoy.withCondition(decoy.condition().withVolatile(Volatiles.SUBSTITUTE, 20)));
        round(Action.move("tackle", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(46 - damage(20, 9, 30, 30, 1.0), foe().hp());
        assertEquals(20, foe().condition().volatileValue(Volatiles.SUBSTITUTE), "the substitute is untouched");
    }

    @Test
    void withoutInfiltratorTheSubstituteTakesTheHit() {
        Combatant decoy = rattata("tackle");
        duel(charmander("tackle"), decoy.withCondition(decoy.condition().withVolatile(Volatiles.SUBSTITUTE, 20)));
        round(Action.move("tackle", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(46, foe().hp());
        assertEquals(20 - damage(20, 9, 30, 30, 1.0), foe().condition().volatileValue(Volatiles.SUBSTITUTE));
    }

    @Test
    void obliviousCannotBeAttracted() {
        Combatant male = charmander("attract").withBuild(charmander("attract").build().withGender("M"));
        Combatant female = rattata("tackle").withBuild(rattata("tackle").build().withGender("F"));
        duel(male, with(female, "oblivious"));
        round(Action.move("attract", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        assertFalse(foe().condition().has(Volatiles.ATTRACT));
        assertTrue(arena.said("Oblivious keeps it from falling in love"));
    }

    @Test
    void shedSkinCuresAStatusAtTheEndOfTheRoundThreeTimesInTen() {
        Combatant sick = status(with(rattata("tackle"), "shedskin"), Status.PARALYSIS, 0);
        duel(charmander("tackle"), sick);
        arena.round(ANA_FIRST).declare("ana-1", Action.skip()).declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        arena.play(BattleDesk.endRound(arena.state(), () -> 2L)); // 2 of 10: cured
        assertEquals(Status.NONE, foe().status());
        assertTrue(arena.said("Shed Skin cured its status!"));
    }

    @Test
    void shedSkinDoesNothingOnTheOtherSevenInTen() {
        Combatant sick = status(with(rattata("tackle"), "shedskin"), Status.PARALYSIS, 0);
        duel(charmander("tackle"), sick);
        arena.round(ANA_FIRST).declare("ana-1", Action.skip()).declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        arena.play(BattleDesk.endRound(arena.state(), () -> 3L)); // 3 of 10: not cured
        assertEquals(Status.PARALYSIS, foe().status());
        assertEquals(1, arena.state().draws().stream().filter(d -> d.purpose().endsWith("shedskin")).count(), "the draw is kept in the log");
    }

    @Test
    void waterAbsorbIsImmuneToWaterAndHealsAQuarter() {
        duel(charmander("watergun"), hp(with(rattata("tackle"), "waterabsorb"), 20));
        round(Action.move("watergun", "wild-1"), Action.skip(), Answers.none()); // no hit roll
        assertEquals(20 + 11, foe().hp(), "a quarter of 46");
        assertTrue(arena.said("Water Absorb made the attack useless"));
    }

    @Test
    void flashFireIsImmuneToFireAndPowersUpItsOwnFireMoves() {
        duel(charmander("ember"), with(rattata("ember"), "flashfire"));
        round(Action.move("ember", "wild-1"), Action.skip(), Answers.none());
        assertEquals(46, foe().hp());
        assertTrue(foe().condition().has(Volatiles.FLASH_FIRE));
        // later the foe's Fire move is x1.5 (Rattata is not a Fire type: no same-type bonus; 33 Sp.Atk against 30 Sp.Def)
        arena.round(Map.of("ana", 3, "wild", 18)).declare("ana-1", Action.skip()).declare("wild-1", Action.move("ember", "ana-1"));
        arena.resolve(Answers.of("wild-1-hit0", "hit", "wild-1-dmg0", NINE, "wild-1-side0", "fail"));
        // Fire against Fire is not very effective (x0.5)
        assertEquals(50 - damage(18, 9, 33, 30, 1.5 * 0.5), ana().hp());
    }

    @Test
    void gutsRaisesTheAttackWithAStatusAndIgnoresTheBurn() {
        duel(status(with(charmander("scratch"), "guts"), Status.BURN, 0), rattata("tackle"));
        round(Action.move("scratch", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(damage(20, 9, 30, 30, 1.5), 46 - foe().hp(), "x1.5 and no burn penalty");
        arena = new Arena(book, "ana", "bo");
        duel(status(charmander("scratch"), Status.BURN, 0), rattata("tackle"));
        round(Action.move("scratch", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(damage(20, 9, 30, 30, 0.5), 46 - foe().hp(), "without Guts the burn halves it");
    }

    @Test
    void unnerveKeepsTheFoeFromEatingBerries() {
        duel(Arena.holding(hp(charmander("tackle"), 20), "sitrus_berry"), with(rattata("tackle"), "unnerve"));
        round(Action.skip(), Action.skip(), Answers.none());
        assertEquals(20, ana().hp());
        assertEquals("sitrus_berry", ana().condition().heldItem());
        arena = new Arena(book, "ana", "bo");
        duel(Arena.holding(hp(charmander("tackle"), 20), "sitrus_berry"), rattata("tackle"));
        round(Action.skip(), Action.skip(), Answers.none());
        assertEquals(20 + 12, ana().hp());
    }

    @Test
    void hustleBoostsPhysicalMovesButMakesThemLessAccurate() {
        duel(with(charmander("scratch"), "hustle"), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("scratch", "wild-1")).declare("wild-1", Action.skip());
        GameEvent.RollRequested hit = firstRoll();
        assertTrue(hit.bands().stream().anyMatch(b -> b.label().equals("miss")), "a 100 % move becomes 80 %");
        arena.play(RollDesk.enter(arena.state(), Arena.ADMIN, hit.requestId(), List.of(hit.bands().stream().filter(b -> b.label().equals("hit")).findFirst().orElseThrow().from()), null));
        arena.keepResolving(Answers.of("ana-1-dmg0", NINE));
        assertEquals(damage(20, 9, 30, 30, 1.5), 46 - foe().hp());
    }

    @Test
    void regeneratorHealsAThirdWhenThePokemonGoesBack() {
        Combatant second = arena.mon("ana-2", "ana", "Squirtle", 20, 60, List.of(PokemonType.WATER), 30, 28, 32, 30, 50, "tackle");
        duelWithReserve(hp(with(charmander("tackle"), "regenerator"), 20), second, rattata("tackle"));
        round(Action.switchTo("ana-2"), Action.skip(), Answers.none());
        assertEquals(20 + 16, arena.mon("ana-1").hp(), "a third of 50");
    }

    @Test
    void pressureMakesAMoveCostOneMorePp() {
        duel(charmander("ember"), with(rattata("tackle"), "pressure"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(25 - 2, ana().moves().get(0).pp());
    }

    @Test
    void weakArmorLowersDefenseAndRaisesSpeedWhenHitByAPhysicalMove() {
        duel(charmander("tackle"), with(rattata("tackle"), "weakarmor"));
        round(Action.move("tackle", "wild-1"), Action.skip(), SCRATCH);
        assertEquals(-1, foe().condition().stage("def"));
        assertEquals(2, foe().condition().stage("spe"));
    }

    @Test
    void weakArmorIgnoresSpecialMoves() {
        duel(charmander("ember"), with(rattata("tackle"), "weakarmor"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(0, foe().condition().stage("def"));
    }

    @Test
    void dampStopsSelfDestructingMoves() {
        duel(charmander("explosion"), with(rattata("tackle"), "damp"));
        round(Action.move("explosion"), Action.skip(), Answers.none());
        assertEquals(50, ana().hp(), "Charmander did not blow itself up");
        assertEquals(46, foe().hp());
        assertTrue(arena.said("Damp prevents"));
    }

    // =================================================================================== weather abilities, tasks and the switch

    @Test
    void anAbilityThatOnlyMattersInWeatherDoesNothingAndNeverMakesATask() {
        duel(with(charmander("tackle"), "swiftswim"), rattata("tackle"));
        assertEquals(List.of(), arena.battle().tasks());
        assertEquals(MoveBook.Handling.AUTOMATIC, book.abilityHandling("swiftswim"));
    }

    @Test
    void aScriptedAbilityThatCoreDoesNotRunMakesOneTaskForTheAdmin() {
        Combatant second = arena.mon("ana-2", "ana", "Squirtle", 20, 60, List.of(PokemonType.WATER), 30, 28, 32, 30, 50, "tackle");
        duelWithReserve(with(charmander("tackle"), "moldbreaker"), second, rattata("tackle"));
        assertEquals(1, arena.battle().tasks().size());
        assertEquals("ability:moldbreaker", arena.battle().tasks().get(0).moveId());
        assertTrue(arena.battle().tasks().get(0).text().contains("Mold Breaker"));
        // it leaves and comes back: the task is not made twice
        round(Action.switchTo("ana-2"), Action.skip(), Answers.none());
        arena.round(ANA_FIRST).declare("ana-2", Action.switchTo("ana-1")).declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        assertEquals(1, arena.battle().tasks().size());
    }

    @Test
    void anAbilityThatCoreRunsNeverMakesATask() {
        duel(with(charmander("tackle"), "sturdy"), rattata("tackle"));
        assertEquals(List.of(), arena.battle().tasks());
        assertEquals(MoveBook.Handling.IMPLEMENTED, book.abilityHandling("sturdy"));
    }

    @Test
    void withAbilitiesSwitchedOffBattlesRunWithoutAnyAbilityEffect() {
        BattleSettings off = BattleSettings.defaults().withTraits(false, true);
        // Intimidate does not act, Sturdy does not save, Thick Fat does not halve, Pressure costs one PP, Blaze does not boost
        duel(off, with(charmander("flamethrower"), "intimidate"), with(rattata("tackle"), "sturdy"));
        assertEquals(0, foe().condition().stage("atk"));
        round(Action.move("flamethrower", "wild-1"), Action.skip(),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {10, 10, 10}, "ana-1-side0", "fail"));
        assertEquals(0, foe().hp(), "Sturdy did nothing");
        assertEquals(15 - 1, ana().moves().get(0).pp());

        arena = new Arena(book, "ana", "bo");
        duel(off, hp(with(charmander("ember"), "blaze"), 16), with(rattata("tackle"), "thickfat"));
        round(Action.move("ember", "wild-1"), Action.skip(), EMBER);
        assertEquals(damage(20, 9, 32, 31, 1.5), 46 - foe().hp(), "neither Blaze nor Thick Fat counted");

        arena = new Arena(book, "ana", "bo");
        duel(off, with(charmander("tackle"), "moldbreaker"), rattata("tackle"));
        assertEquals(List.of(), arena.battle().tasks(), "no tasks for abilities that are off");
    }

    @Test
    void theListOfImplementedAbilitiesMatchesTheCode() {
        MoveBook.Implemented list = MoveBook.implementedEffects();
        assertEquals(AbilityScripts.implemented(), list.abilities());
        assertEquals(AbilityScripts.noEffectIds(), list.abilitiesNoEffect());
        assertEquals(25, list.abilities().size());
        for (String ability : list.abilities()) {
            assertTrue(AbilityScripts.knows(ability), ability);
        }
    }
}
