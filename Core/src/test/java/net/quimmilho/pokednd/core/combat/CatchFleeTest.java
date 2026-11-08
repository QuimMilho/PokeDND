/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.quimmilho.pokednd.core.combat.CatchCalculator.Target;
import net.quimmilho.pokednd.core.combat.CatchCalculator.Throw;
import net.quimmilho.pokednd.core.combat.FleeCalculator.Attempt;
import net.quimmilho.pokednd.core.combat.FleeCalculator.Battle;
import net.quimmilho.pokednd.core.dice.DiceSettings;
import net.quimmilho.pokednd.core.dice.SmartDice;
import net.quimmilho.pokednd.core.dice.SmartDice.Kind;
import net.quimmilho.pokednd.core.rules.MajorStatus;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.Test;

class CatchFleeTest {

    private static final double PCT = 0.0005; // the examples are written to the nearest 0.1 %

    private static CatchCalculator.Odds odds(Throw t) {
        return CatchCalculator.odds(t);
    }

    // ------------------------------------------------------------------------- catching: the examples of 7.10

    @Test
    void bulbasaurAtSixOf46HpWithAPokeBallIsAD6NeedingASix() {
        CatchCalculator.Odds o = odds(Throw.wild(46, 6, 45, "poke_ball"));
        assertEquals(41.09, o.catchValue(), 0.01, "a = 41.1");
        assertEquals(0.161, o.chance(), PCT);
        assertEquals(6, o.roll().die());
        assertEquals(6, o.roll().need());
        assertEquals(Kind.ROLL, o.roll().kind());
    }

    @Test
    void asleepTheSameBulbasaurIs40PercentAD10NeedingSevenOrMore() {
        CatchCalculator.Odds o = odds(Throw.wild(46, 6, 45, "poke_ball").withStatus(MajorStatus.SLEEP));
        assertEquals(0.403, o.chance(), PCT);
        assertEquals(10, o.roll().die());
        assertEquals(7, o.roll().need());
        assertEquals(2.5, o.statusMultiplier());
    }

    @Test
    void withAnUltraBallInsteadItIs32PercentAD6NeedingFiveOrMore() {
        CatchCalculator.Odds o = odds(Throw.wild(46, 6, 45, "ultra_ball"));
        assertEquals(0.322, o.chance(), PCT);
        assertEquals(6, o.roll().die());
        assertEquals(5, o.roll().need());
    }

    @Test
    void aLegendaryAtOneHpWithAnUltraBallIs2PercentOnAD100() {
        CatchCalculator.Odds o = odds(Throw.wild(100, 1, 3, "ultra_ball"));
        assertEquals(0.0234, o.chance(), PCT);
        assertEquals(100, o.roll().die());
        assertEquals(99, o.roll().need());
        assertEquals(0.02, o.roll().realChance(), 1e-9);
    }

    @Test
    void aLegendaryAtFullHpWithAnUltraBallIsOnePercentNeedingAHundred() {
        CatchCalculator.Odds o = odds(Throw.wild(100, 100, 3, "ultra_ball"));
        assertEquals(0.0078, o.chance(), PCT);
        assertEquals(100, o.roll().die());
        assertEquals(100, o.roll().need());
        assertEquals(2.0, o.catchValue(), 1e-9);
    }

    @Test
    void thePidgeyExamplesOfChapter6_5() {
        // Catch rate 255. At full HP: 33 %.
        CatchCalculator.Odds full = odds(Throw.wild(50, 50, 255, "poke_ball"));
        assertEquals(1.0 / 3.0, full.chance(), 1e-9);
        assertEquals(6, full.roll().die());
        assertEquals(5, full.roll().need());
        // Nearly dead: 88 %.
        CatchCalculator.Odds low = odds(Throw.wild(50, 9, 255, "poke_ball"));
        assertEquals(0.88, low.chance(), 1e-9);
        assertEquals(8, low.roll().die());
        assertEquals(2, low.roll().need());
    }

    @Test
    void theChapter6_5RowForAsleepAtLowHpUsesTheRoundedFortyPercent() {
        // Chapter 6.5 writes the chance as 40 %: that is a d8 needing 6. The exact chance (40.3 %) is a d10, as in 7.10.
        SmartDice.Success rounded = SmartDice.success(0.40);
        assertEquals(8, rounded.die());
        assertEquals(6, rounded.need());
    }

    // ------------------------------------------------------------------------- catching: balls, status, edge cases

    @Test
    void aMasterBallAlwaysCatchesWhateverTheHpAndCatchRate() {
        for (int hp : new int[] {1, 50, 100}) {
            CatchCalculator.Odds o = odds(Throw.wild(100, hp, 3, "master_ball"));
            assertTrue(o.allowed());
            assertEquals(1.0, o.chance());
            assertEquals(Kind.ALWAYS, o.roll().kind());
            assertFalse(o.roll().choice().rolled(), "nobody rolls");
        }
    }

    @Test
    void aMasterBallStillCannotCatchABossOrAFaintedPokemon() {
        assertFalse(odds(Throw.wild(100, 50, 3, "master_ball").withTarget(Target.BOSS)).allowed());
        assertFalse(odds(Throw.wild(100, 0, 3, "master_ball")).allowed());
    }

    @Test
    void aFaintedPokemonCannotBeCaught() {
        CatchCalculator.Odds o = odds(Throw.wild(100, 0, 255, "ultra_ball"));
        assertFalse(o.allowed());
        assertEquals(0.0, o.chance());
        assertEquals(Kind.NEVER, o.roll().kind());
        assertTrue(o.reason().contains("fainted"));
    }

    @Test
    void fullHpIsTheHardestAndOneHpTheEasiest() {
        double full = odds(Throw.wild(80, 80, 120, "poke_ball")).chance();
        double half = odds(Throw.wild(80, 40, 120, "poke_ball")).chance();
        double one = odds(Throw.wild(80, 1, 120, "poke_ball")).chance();
        assertTrue(full < half && half < one, full + " < " + half + " < " + one);
        assertEquals(120.0 / 3.0 / 255.0, full, 1e-9, "full HP: a = catchRate / 3");
        assertEquals(odds(Throw.wild(80, 80, 120, "poke_ball")).chance(), odds(Throw.wild(80, 200, 120, "poke_ball")).chance(),
                "HP above the maximum counts as full");
    }

    @Test
    void theChanceNeverGoesAbove100PercentAndAnEasyCatchIsCertain() {
        CatchCalculator.Odds o = odds(Throw.wild(30, 1, 255, "ultra_ball").withStatus(MajorStatus.SLEEP));
        assertEquals(1.0, o.chance());
        assertEquals(Kind.ALWAYS, o.roll().kind());
        assertTrue(o.catchValue() > 255);
    }

    @Test
    void statusMultipliersAreTheOnesOfChapter7_10() {
        assertEquals(2.5, CatchCalculator.statusMultiplier(Optional.of(MajorStatus.SLEEP)));
        assertEquals(2.5, CatchCalculator.statusMultiplier(Optional.of(MajorStatus.FREEZE)));
        assertEquals(1.5, CatchCalculator.statusMultiplier(Optional.of(MajorStatus.PARALYSIS)));
        assertEquals(1.5, CatchCalculator.statusMultiplier(Optional.of(MajorStatus.POISON)));
        assertEquals(1.5, CatchCalculator.statusMultiplier(Optional.of(MajorStatus.BURN)));
        assertEquals(1.0, CatchCalculator.statusMultiplier(Optional.empty()));
    }

    @Test
    void theCommonBallsHaveTheMultipliersOfChapter7_10() {
        BallTable balls = BallTable.defaults();
        BallTable.Context first = BallTable.Context.firstRound(List.of(PokemonType.NORMAL));
        assertEquals(1.0, balls.multiplier("poke_ball", first));
        assertEquals(1.0, balls.multiplier("premier_ball", first));
        assertEquals(1.5, balls.multiplier("great_ball", first));
        assertEquals(2.0, balls.multiplier("ultra_ball", first));
        assertTrue(balls.alwaysCatches("master_ball"));
        assertFalse(balls.alwaysCatches("ultra_ball"));
        // Ancient balls match their regular counterparts.
        assertEquals(1.0, balls.multiplier("ancient_poke_ball", first));
        assertEquals(1.5, balls.multiplier("ancient_great_ball", first));
        assertEquals(2.0, balls.multiplier("ancient_ultra_ball", first));
    }

    @Test
    void theConditionalBallsCheckTheirCondition() {
        BallTable balls = BallTable.defaults();
        BallTable.Context water = new BallTable.Context(1, false, false, List.of(PokemonType.WATER, PokemonType.FLYING));
        BallTable.Context plain = new BallTable.Context(1, false, false, List.of(PokemonType.NORMAL));
        BallTable.Context bug = new BallTable.Context(3, false, false, List.of(PokemonType.BUG));
        assertEquals(3.5, balls.multiplier("net_ball", water));
        assertEquals(3.5, balls.multiplier("net_ball", bug));
        assertEquals(1.0, balls.multiplier("net_ball", plain));

        assertEquals(5.0, balls.multiplier("quick_ball", plain), "first round");
        assertEquals(1.0, balls.multiplier("quick_ball", new BallTable.Context(2, false, false, List.of())));

        assertEquals(3.0, balls.multiplier("dusk_ball", new BallTable.Context(1, true, false, List.of())));
        assertEquals(1.0, balls.multiplier("dusk_ball", plain));

        assertEquals(3.5, balls.multiplier("repeat_ball", new BallTable.Context(1, false, true, List.of())));
        assertEquals(1.0, balls.multiplier("repeat_ball", plain));

        assertEquals(1.3, balls.multiplier("timer_ball", plain), 1e-9);
        assertEquals(1.0 + 0.3 * 5, balls.multiplier("timer_ball", new BallTable.Context(5, false, false, List.of())), 1e-9);
        assertEquals(4.0, balls.multiplier("timer_ball", new BallTable.Context(40, false, false, List.of())), "capped at 4");
    }

    @Test
    void aQuickBallOnTheFirstRoundBeatsAnUltraBallForTheSameTarget() {
        double quick = odds(Throw.wild(60, 60, 45, "quick_ball")).chance();
        double ultra = odds(Throw.wild(60, 60, 45, "ultra_ball")).chance();
        assertTrue(quick > ultra);
        BallTable.Context later = new BallTable.Context(4, false, false, List.of());
        assertTrue(odds(Throw.wild(60, 60, 45, "quick_ball").withContext(later)).chance()
                < odds(Throw.wild(60, 60, 45, "poke_ball")).chance() + 1e-12, "x1 after the first round");
    }

    @Test
    void anUnknownBallCountsAsPlainAndAnEditedBallChangesTheOdds() {
        BallTable.Context first = BallTable.Context.firstRound(List.of());
        assertEquals(1.0, BallTable.defaults().multiplier("mystery_ball", first));
        BallTable edited = BallTable.defaults()
                .with(new BallTable.Rule("love_ball", BallTable.Kind.FLAT, 8.0, true, "edited"));
        assertEquals(8.0, edited.multiplier("love_ball", first));
        assertEquals(1.0, BallTable.defaults().multiplier("love_ball", first), "the default table is not changed");
    }

    @Test
    void handlingBonusMultipliesTheChanceAndNegativeModifiersCountOnlyIfAllowed() {
        Throw base = Throw.wild(100, 50, 100, "poke_ball");
        double plain = odds(base).chance();
        assertEquals(plain * 1.25, odds(base.withHandling(5)).chance(), 1e-9, "x(1 + 0.05 x 5)");
        assertEquals(plain, odds(base.withHandling(-4)).chance(), 1e-9, "never below x1 by default");
        CatchCalculator.Settings penalty = CatchCalculator.Settings.defaults().withAllowPenalty(true);
        assertEquals(plain * 0.8, CatchCalculator.odds(base.withHandling(-4), penalty, BallTable.defaults(), DiceSettings.defaults())
                .chance(), 1e-9);
        CatchCalculator.Settings strong = CatchCalculator.Settings.defaults().withBonusPerPoint(0.1);
        assertEquals(plain * 1.2, CatchCalculator.odds(base.withHandling(2), strong, BallTable.defaults(), DiceSettings.defaults())
                .chance(), 1e-9);
        assertEquals(plain * 1.1, odds(base.withOtherMultiplier(1.1)).chance(), 1e-9, "class feature");
    }

    @Test
    void catchingRulesFromTheSettings() {
        Throw wild = Throw.wild(100, 50, 100, "poke_ball");
        CatchCalculator.Settings defaults = CatchCalculator.Settings.defaults();
        BallTable balls = BallTable.defaults();
        DiceSettings dice = DiceSettings.defaults();
        assertFalse(CatchCalculator.odds(wild.withTarget(Target.BOSS), defaults, balls, dice).allowed());
        assertFalse(CatchCalculator.odds(wild.withTarget(Target.TRAINER_OWNED), defaults, balls, dice).allowed());
        assertTrue(CatchCalculator.odds(wild.withTarget(Target.TRAINER_OWNED), defaults.withAllowTrainerOwned(true), balls, dice)
                .allowed());
        CatchCalculator.Odds off = CatchCalculator.odds(wild, defaults.withEnabled(false), balls, dice);
        assertFalse(off.allowed());
        assertFalse(off.reason().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Throw.wild(0, 0, 45, "poke_ball"));
        assertThrows(IllegalArgumentException.class, () -> Throw.wild(10, 5, 300, "poke_ball"));
    }

    // ------------------------------------------------------------------------- the ball table covers every ball

    @Test
    void theBallTableCoversAll48BallsOfTheRepository() throws IOException {
        Path resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        Path folder = resources.resolve("icons/items/poke_balls");
        boolean there = Files.isDirectory(folder);
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ is missing but POKEDND_REQUIRE_RESOURCES=true");
        }
        assumeTrue(there, "resources/ is not built");
        TreeSet<String> ids = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".png"))
                    .forEach(n -> ids.add(n.substring(0, n.length() - 4)));
        }
        assertEquals(48, ids.size());
        BallTable table = BallTable.defaults();
        for (String id : ids) {
            assertTrue(table.contains(id), "no rule for " + id);
        }
        assertEquals(ids, new TreeSet<>(table.all().stream().map(BallTable.Rule::id).toList()),
                "the table lists exactly the balls of the repository");
    }

    @Test
    void everyBallThatIsNotInTheCommonSetIsMarkedSpecial() {
        BallTable table = BallTable.defaults();
        List<String> common = List.of("poke_ball", "premier_ball", "great_ball", "ultra_ball", "master_ball", "quick_ball", "net_ball",
                "dusk_ball", "timer_ball", "repeat_ball", "ancient_poke_ball", "ancient_great_ball", "ancient_ultra_ball");
        for (BallTable.Rule r : table.all()) {
            assertEquals(!common.contains(r.id()), r.special(), r.id());
            assertFalse(r.note().isBlank(), r.id());
        }
        assertEquals(48, table.all().size());
    }

    // ------------------------------------------------------------------------- fleeing: the examples of 7.11

    @Test
    void speed60AgainstEightyIs49PercentOnTheFirstTryAD4NeedingThreeOrMore() {
        FleeCalculator.Odds o = FleeCalculator.odds(Attempt.wild(60, 80, 1));
        assertEquals(126, o.f(), "floor(60 x 128 / 80) + 30");
        assertEquals(0.492, o.chance(), PCT);
        assertEquals(4, o.roll().die());
        assertEquals(3, o.roll().need());
        assertEquals(0.50, o.roll().realChance(), 1e-9);
    }

    @Test
    void theSecondTryIs61PercentAD8NeedingFourOrMore() {
        FleeCalculator.Odds o = FleeCalculator.odds(Attempt.wild(60, 80, 2));
        assertEquals(156, o.f());
        assertEquals(0.609, o.chance(), PCT);
        assertEquals(8, o.roll().die());
        assertEquals(4, o.roll().need());
        assertEquals(0.625, o.roll().realChance(), 1e-9);
    }

    @Test
    void ifYourSpeedIsAtLeastTheOpponentsYouAlwaysEscape() {
        for (Attempt a : List.of(Attempt.wild(80, 80, 1), Attempt.wild(120, 80, 1), Attempt.wild(1, 0, 1))) {
            FleeCalculator.Odds o = FleeCalculator.odds(a);
            assertEquals(1.0, o.chance());
            assertEquals(Kind.ALWAYS, o.roll().kind());
        }
    }

    @Test
    void everyFailedTryMakesTheNextOneBetterUntilItIsCertain() {
        double previous = 0;
        for (int attempt = 1; attempt <= 12; attempt++) {
            double chance = FleeCalculator.odds(Attempt.wild(20, 100, attempt)).chance();
            assertTrue(chance >= previous, "attempt " + attempt);
            previous = chance;
        }
        assertEquals(1.0, previous, "after enough tries a slow Pokémon escapes");
    }

    @Test
    void theDexBonusAddsPercentagePointsAndTheChanceStaysBetween0And100() {
        Attempt base = Attempt.wild(60, 80, 1);
        double plain = FleeCalculator.odds(base).chance();
        assertEquals(plain + 0.06, FleeCalculator.odds(base.withDex(3)).chance(), 1e-9, "+2 pp per DEX point");
        assertEquals(1.0, FleeCalculator.odds(base.withDex(50)).chance());
        assertEquals(0.0, FleeCalculator.odds(Attempt.wild(1, 255, 1).withDex(-50)).chance());
        FleeCalculator.Settings settings = FleeCalculator.Settings.defaults().withBonusPpPerPoint(1);
        assertEquals(plain + 0.03, FleeCalculator.odds(base.withDex(3), settings, DiceSettings.defaults()).chance(), 1e-9);
    }

    @Test
    void fleeingFromTrainersAndBossesFollowsTheSettings() {
        Attempt wild = Attempt.wild(60, 80, 1);
        FleeCalculator.Settings defaults = FleeCalculator.Settings.defaults();
        DiceSettings dice = DiceSettings.defaults();
        assertTrue(FleeCalculator.odds(wild, defaults, dice).allowed());
        FleeCalculator.Odds trainer = FleeCalculator.odds(wild.inBattle(Battle.TRAINER), defaults, dice);
        assertFalse(trainer.allowed());
        assertEquals(Kind.NEVER, trainer.roll().kind());
        assertTrue(FleeCalculator.odds(wild.inBattle(Battle.TRAINER), defaults.withAllowTrainerBattles(true), dice).allowed());
        assertTrue(FleeCalculator.odds(wild.inBattle(Battle.BOSS), defaults, dice).allowed());
        assertFalse(FleeCalculator.odds(wild.inBattle(Battle.BOSS), defaults.withAllowBoss(false), dice).allowed());
        assertThrows(IllegalArgumentException.class, () -> Attempt.wild(60, 80, 0));
    }

    @Test
    void bothCalculatorsReturnTheDieAndTheBandsForTheScreen() {
        SmartDice.Success success = odds(Throw.wild(46, 6, 45, "poke_ball")).roll();
        assertEquals(2, success.choice().bands().size());
        assertEquals("6", success.choice().bands().get(1).range());
        assertTrue(success.choice().explanation().contains("d6"));
        assertTrue(FleeCalculator.odds(Attempt.wild(60, 80, 1)).roll().choice().explanation().contains("d4"));
    }
}
