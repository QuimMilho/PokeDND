/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.quimmilho.pokednd.core.cards.SignatureMoves.MoveInfo;
import net.quimmilho.pokednd.core.cards.SignatureMoves.Result;
import net.quimmilho.pokednd.core.cards.SignatureMoves.Role;
import net.quimmilho.pokednd.core.cards.SignatureMoves.StageLevelCaps;
import net.quimmilho.pokednd.core.combat.MoveProfile.Category;
import net.quimmilho.pokednd.core.importer.Derived.Stage;
import net.quimmilho.pokednd.core.importer.Learnsets.LevelMove;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.Test;

class SignatureMovesTest {

    private static final Set<PokemonType> FIRE = Set.of(PokemonType.FIRE);

    private static MoveInfo attack(String id, PokemonType type, int power, int accuracy) {
        return new MoveInfo(id, type, Category.SPECIAL, power, Optional.of(accuracy), false, false, true);
    }

    private static MoveInfo drawback(MoveInfo m) {
        return new MoveInfo(m.id(), m.type(), m.category(), m.power(), m.accuracy(), false, true, true);
    }

    private static MoveInfo status(String id, PokemonType type, boolean effect) {
        return new MoveInfo(id, type, Category.STATUS, 0, Optional.empty(), effect, false, true);
    }

    private static Map<String, MoveInfo> table(MoveInfo... moves) {
        Map<String, MoveInfo> map = new HashMap<>();
        for (MoveInfo m : moves) {
            map.put(m.id(), m);
        }
        return map;
    }

    private static List<LevelMove> learns(Object... pairs) {
        List<LevelMove> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(new LevelMove((Integer) pairs[i], (String) pairs[i + 1]));
        }
        return list;
    }

    // ------------------------------------------------------------------------------------------ the score

    @Test
    void aDamagingMoveScoresHalfItsPowerUpTo120() {
        assertEquals(30 + 10, SignatureMoves.score(attack("a", PokemonType.WATER, 60, 100), FIRE), "60 / 2 and +10 accuracy");
        assertEquals(60 + 10, SignatureMoves.score(attack("a", PokemonType.WATER, 200, 100), FIRE), "capped at 120 / 2");
        assertEquals(60 + 10, SignatureMoves.score(attack("a", PokemonType.WATER, 120, 100), FIRE));
        assertEquals(30 + 10, SignatureMoves.score(attack("a", PokemonType.WATER, 60, 100), FIRE));
    }

    @Test
    void sameTypeAttackBonusIs25AndTheAccuracyBonusNeeds90OrMore() {
        assertEquals(30 + 25 + 10, SignatureMoves.score(attack("a", PokemonType.FIRE, 60, 90), FIRE));
        assertEquals(30 + 25, SignatureMoves.score(attack("a", PokemonType.FIRE, 60, 89), FIRE), "89 is not enough");
        MoveInfo neverMisses = new MoveInfo("swift", PokemonType.NORMAL, Category.SPECIAL, 60, Optional.empty(), false, false, true);
        assertEquals(30 + 10, SignatureMoves.score(neverMisses, FIRE), "a move that never misses counts as accurate");
    }

    @Test
    void aStatusMoveScores20IfItInflictsAStatusOrChangesStatsAndOtherwise5() {
        assertEquals(20, SignatureMoves.score(status("swordsdance", PokemonType.NORMAL, true), FIRE));
        assertEquals(5, SignatureMoves.score(status("recover", PokemonType.NORMAL, false), FIRE));
        assertEquals(20, SignatureMoves.score(status("willowisp", PokemonType.FIRE, true), FIRE), "no STAB bonus for status moves");
    }

    @Test
    void aDrawbackCosts15() {
        MoveInfo hyperBeam = drawback(attack("hyperbeam", PokemonType.NORMAL, 150, 90));
        assertEquals(60 + 10 - 15, SignatureMoves.score(hyperBeam, FIRE));
    }

    @Test
    void aDamagingMoveWithoutAPowerNumberScoresAsPower50() {
        MoveInfo seismicToss = new MoveInfo("seismictoss", PokemonType.FIGHTING, Category.PHYSICAL, 0, Optional.of(100), false, false, true);
        assertEquals(25 + 10, SignatureMoves.score(seismicToss, FIRE));
    }

    // ------------------------------------------------------------------------------------------ the pick order

    @Test
    void thePicksFollowTheOrderOwnTypeThenOtherTypeThenTheBestRest() {
        Map<String, MoveInfo> moves = table(
                attack("ember", PokemonType.FIRE, 40, 100),          // 20 + 25 + 10 = 55
                attack("flamethrower", PokemonType.FIRE, 90, 100),   // 45 + 25 + 10 = 80
                attack("slash", PokemonType.NORMAL, 70, 100),        // 35 + 10 = 45
                attack("dragonclaw", PokemonType.DRAGON, 80, 100),   // 40 + 10 = 50
                status("swordsdance", PokemonType.NORMAL, true));    // 20
        List<LevelMove> level = learns(1, "ember", 10, "flamethrower", 20, "slash", 30, "dragonclaw", 40, "swordsdance");
        Result r = SignatureMoves.select(level, FIRE, 100, moves);
        assertEquals(List.of("flamethrower", "dragonclaw", "ember", "slash"), r.moves());
        assertEquals(Role.OWN_TYPE, r.picks().get(0).role());
        assertEquals(Role.OTHER_TYPE, r.picks().get(1).role());
        assertEquals(Role.BEST_REMAINING, r.picks().get(2).role());
        assertEquals(80.0, r.picks().get(0).score());
        assertFalse(r.capLifted());
    }

    @Test
    void atMostTwoMovesOfTheSameType() {
        Map<String, MoveInfo> moves = table(
                attack("a", PokemonType.FIRE, 90, 100), attack("b", PokemonType.FIRE, 80, 100),
                attack("c", PokemonType.FIRE, 70, 100), attack("d", PokemonType.FIRE, 60, 100),
                attack("e", PokemonType.WATER, 40, 100), attack("f", PokemonType.GRASS, 30, 100));
        Result r = SignatureMoves.select(learns(1, "a", 2, "b", 3, "c", 4, "d", 5, "e", 6, "f"), FIRE, 100, moves);
        // a (own type), e (best of another type is water? no: the best damaging non-fire is e), then the best rest.
        assertEquals(List.of("a", "e", "b", "f"), r.moves(), "only two fire moves: a and b; c and d wait");
    }

    @Test
    void theTypeLimitIsRelaxedRatherThanLeavingAPlaceEmpty() {
        Map<String, MoveInfo> moves = table(
                attack("a", PokemonType.FIRE, 90, 100), attack("b", PokemonType.FIRE, 80, 100), attack("c", PokemonType.FIRE, 70, 100),
                attack("d", PokemonType.FIRE, 60, 100));
        Result r = SignatureMoves.select(learns(1, "a", 2, "b", 3, "c", 4, "d"), FIRE, 100, moves);
        assertEquals(4, r.moves().size());
        assertEquals(Role.FILLER, r.picks().get(3).role());
    }

    @Test
    void withoutAnOwnTypeAttackTheBestDamagingMoveComesFirst() {
        Map<String, MoveInfo> moves = table(
                attack("surf", PokemonType.WATER, 90, 100), attack("tackle", PokemonType.NORMAL, 40, 100),
                status("growl", PokemonType.NORMAL, true), status("rest", PokemonType.PSYCHIC, false));
        Result r = SignatureMoves.select(learns(1, "surf", 1, "tackle", 1, "growl", 1, "rest"), FIRE, 100, moves);
        assertEquals(List.of("surf", "tackle", "growl", "rest"), r.moves());
        assertEquals(Role.OTHER_TYPE, r.picks().get(0).role(), "nothing of the own type, so it counts as another type");
    }

    @Test
    void tiesGoToTheLaterLevelThenToTheMoveId() {
        Map<String, MoveInfo> moves = table(attack("x", PokemonType.WATER, 60, 100), attack("y", PokemonType.GRASS, 60, 100),
                attack("z", PokemonType.ICE, 60, 100), attack("w", PokemonType.ROCK, 60, 100));
        Result r = SignatureMoves.select(learns(5, "x", 9, "y", 9, "z", 5, "w"), FIRE, 100, moves);
        assertEquals(List.of("y", "z", "w", "x"), r.moves(), "level 9 first (y before z by id), then level 5 (w before x)");
    }

    // ------------------------------------------------------------------------------------------ candidates and caps

    @Test
    void uselessMovesRepeatsAndUnknownMovesAreLeftOut() {
        Map<String, MoveInfo> moves = table(attack("ember", PokemonType.FIRE, 40, 100), status("splash", PokemonType.NORMAL, false),
                status("celebrate", PokemonType.NORMAL, false), status("struggle", PokemonType.NORMAL, false),
                new MoveInfo("zmove", PokemonType.FIRE, Category.PHYSICAL, 190, Optional.empty(), false, false, false));
        Result r = SignatureMoves.select(learns(1, "splash", 1, "celebrate", 1, "struggle", 1, "ember", 5, "ember", 9, "zmove",
                9, "nosuchmove"), FIRE, 100, moves);
        assertEquals(List.of("ember"), r.moves());
        assertEquals(1, r.candidates());
        assertEquals(1, r.picks().get(0).level(), "the first level it is learnt at");
    }

    @Test
    void movesAboveTheLevelCapAreIgnoredUnlessTooFewRemain() {
        Map<String, MoveInfo> moves = table(attack("a", PokemonType.FIRE, 40, 100), attack("b", PokemonType.WATER, 40, 100),
                attack("c", PokemonType.GRASS, 40, 100), attack("d", PokemonType.ICE, 40, 100),
                attack("late", PokemonType.ROCK, 150, 100));
        List<LevelMove> level = learns(1, "a", 5, "b", 10, "c", 20, "d", 60, "late");
        Result capped = SignatureMoves.select(level, FIRE, 25, moves);
        assertFalse(capped.moves().contains("late"));
        assertFalse(capped.capLifted());
        assertEquals(25, capped.levelCap());

        Result lifted = SignatureMoves.select(level, FIRE, 10, moves);
        assertTrue(lifted.capLifted(), "only three moves are learnt by level 10");
        assertEquals(4, lifted.moves().size());
        assertTrue(lifted.moves().contains("late"));
    }

    @Test
    void aFormWithFewerThanFourUsableMovesGetsFewer() {
        Map<String, MoveInfo> moves = table(attack("tackle", PokemonType.NORMAL, 40, 100), status("harden", PokemonType.NORMAL, true));
        Result r = SignatureMoves.select(learns(1, "tackle", 1, "harden"), Set.of(PokemonType.BUG), 100, moves);
        assertEquals(List.of("tackle", "harden"), r.moves());
        assertTrue(SignatureMoves.select(List.of(), FIRE, 100, moves).picks().isEmpty());
    }

    @Test
    void thePicksAreAlwaysDistinct() {
        Map<String, MoveInfo> moves = table(attack("a", PokemonType.FIRE, 40, 100), attack("b", PokemonType.FIRE, 30, 100),
                attack("c", PokemonType.WATER, 20, 100), attack("d", PokemonType.GRASS, 20, 100), attack("e", PokemonType.ICE, 20, 100));
        Result r = SignatureMoves.select(learns(1, "a", 1, "a", 1, "b", 1, "c", 1, "d", 1, "e"), FIRE, 100, moves);
        assertEquals(4, r.moves().size());
        assertEquals(4, Set.copyOf(r.moves()).size());
    }

    // ------------------------------------------------------------------------------------------ stage caps

    @Test
    void theLevelCapDependsOnTheStage() {
        StageLevelCaps caps = StageLevelCaps.defaults();
        assertEquals(25, caps.capFor(Stage.BASIC, true), "a Basic Pokémon that evolves");
        assertEquals(40, caps.capFor(Stage.STAGE_1, true));
        assertEquals(100, caps.capFor(Stage.STAGE_2, true));
        assertEquals(100, caps.capFor(Stage.BASIC, false), "a Pokémon that does not evolve");
        assertEquals(new StageLevelCaps(20, 30, 90), StageLevelCaps.parse("20, 30,90"));
        assertEquals(StageLevelCaps.defaults(), StageLevelCaps.parse("25,40,100"));
        assertThrows(IllegalArgumentException.class, () -> StageLevelCaps.parse("25,40"));
        assertThrows(IllegalArgumentException.class, () -> StageLevelCaps.parse("a,b,c"));
    }
}
