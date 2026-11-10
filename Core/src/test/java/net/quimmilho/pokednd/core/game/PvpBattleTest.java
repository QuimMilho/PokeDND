/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ProgressFixtures.MOVES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.RULES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.rules.Experience;
import net.quimmilho.pokednd.core.rules.PokemonSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 49: PvP and NPC trainer battles (chapter 7.14). Two players fight with their own dice: every roll is made by the player it
 * belongs to. Players cannot run away from each other, but they can surrender; a PvP battle pays a prize only if the admin set a wager.
 */
class PvpBattleTest {

    private static final Actor ANA = Actor.player("u-ana");
    private static final Actor BO = Actor.player("u-bo");

    private Arena arena;
    private final SpeciesBook book = ProgressFixtures.book();
    private ProgressSettings settings = ProgressSettings.defaults();
    private final Set<String> rollers = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        arena = new Arena(MOVES, RollSettings.defaults().withSystemRolls(RollSettings.SystemRolls.AUTO, RollSettings.SystemRolls.AUTO), "ana", "bo");
    }

    private PokemonSheet give(PokemonSheet s) {
        arena.play(List.of(new GameEvent.PokemonSet(s)));
        return s;
    }

    /** Ana's level-20 Bulbasaur against Bo's level-8 Pidgey (a weak one, so the fight is short). */
    private void startPvp(BattleSettings rules) {
        PokemonSheet mine = give(sheet(book, "pk-a", "ana", "bulbasaur", 20, "tackle", "growl"));
        PokemonSheet his = give(sheet(book, "pk-b", "bo", "pidgey", 8, "tackle"));
        arena.start(Battle.Type.PVP, rules,
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("pk-a"), List.of()),
                        new Participant("bo", Kind.TRAINER, 1, "Bo", "bo", "u-bo", List.of("pk-b"), List.of())),
                List.of(ProgressFixtures.combatant(mine, "ana", book), ProgressFixtures.combatant(his, "bo", book)));
    }

    /** Every open roll is made by the person it belongs to: Ana rolls Ana's, Bo rolls Bo's. */
    private void everybodyRolls(long seed) {
        long n = seed;
        for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(arena.state()))) {
            assertEquals(GameState.Roller.PLAYER, r.roller(), "in PvP every roll is a player's");
            assertTrue(r.rollerUserId().equals("u-ana") || r.rollerUserId().equals("u-bo"), r.requestId());
            rollers.add(r.rollerUserId());
            arena.play(RollDesk.rollDigital(arena.state(), arena.rolls, Actor.player(r.rollerUserId()), r.requestId(), n++));
        }
    }

    /** Plays rounds in which both use Tackle on each other until one side has nobody left. */
    private int fightToTheEnd() {
        int rounds = 0;
        while (!arena.battle().over() && rounds < 40) {
            rounds++;
            arena.play(BattleDesk.openRound(arena.state(), arena.rolls, Map.of(), Arena.NOW, arena.seeds));
            everybodyRolls(rounds * 100L);
            arena.play(BattleDesk.setOrder(arena.state(), Arena.NOW, arena.seeds));
            arena.declare("pk-a", Action.move("tackle", "pk-b"));
            arena.declare("pk-b", Action.move("tackle", "pk-a"));
            arena.play(BattleDesk.startResolving(arena.state()));
            int guard = 0;
            while (!arena.battle().queue().isEmpty()) {
                assertTrue(++guard < 100, "the round does not move on");
                List<GameEvent> events = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
                if (!events.isEmpty()) {
                    arena.play(events);
                }
                everybodyRolls(rounds * 1000L + guard);
            }
            arena.play(BattleDesk.endRound(arena.state(), arena.seeds));
        }
        return rounds;
    }

    private List<GameEvent> settle(long wager) {
        List<GameEvent> end = BattleDesk.endIfOver(arena.state());
        if (!end.isEmpty()) {
            arena.play(end);
        }
        List<GameEvent> events = AfterBattle.settle(arena.state(), book, MOVES, RULES, settings, EvolutionCheck.Context.none(), wager);
        arena.play(events);
        return events;
    }

    // ====================================================================================================================== PvP

    @Test
    void aPvpBattleRunsToTheEndWithTheRollsOfBothPlayers() {
        startPvp(BattleSettings.defaults());
        int rounds = fightToTheEnd();
        assertTrue(arena.battle().over(), "after " + rounds + " rounds");
        assertEquals(Set.of("u-ana", "u-bo"), rollers, "both players rolled");
        assertEquals(arena.log.replay(), arena.state(), "the log replays to the same state");
        assertEquals(List.of(), BattleDesk.awaiting(arena.state()), "no roll is left open");
        settle(0);
        assertTrue(arena.battle().settled());
        // the stronger Pokémon won, and only the winner's Pokémon got XP: a level-8 Pidgey (XP value 50) is worth floor(...) to a level-20 Bulbasaur
        PokemonSheet winner = arena.state().pokemon().get("pk-a");
        int expected = Experience.reward(50, 8, 20, true, false, false, PokemonSettings.defaults());
        assertEquals(book.species("bulbasaur").orElseThrow().growth().totalXp(20) + expected, winner.xp(), "a PvP opponent's Pokémon gives plain XP (not the NPC bonus)");
        assertTrue(arena.state().pokemon().get("pk-b").hp() > 0, "the loser's team fainted, so the whiteout healed it (chapter 7.15)");
    }

    @Test
    void thePlayerWhoWonGetsTrainerXpFiveTimesTheOpponentsHighestLevel() {
        startPvp(BattleSettings.defaults());
        fightToTheEnd();
        settle(0);
        assertEquals(5 * 8, arena.state().trainers().get("ana").xp(), "chapter 4.5: 5 x the opponent's highest level");
        assertEquals(0, arena.state().trainers().get("bo").xp());
    }

    @Test
    void thereIsNoPrizeUnlessTheAdminSetsAWager() {
        startPvp(BattleSettings.defaults());
        fightToTheEnd();
        settle(500); // a wager is ignored while combat.pvp.wagerAllowed is off
        assertEquals(3000, arena.state().trainers().get("ana").money());
        // the loser whited out (every Pokémon fainted): 10 % of the money
        assertEquals(2700, arena.state().trainers().get("bo").money());
    }

    @Test
    void withAWagerTheLoserPaysTheWinner() {
        startPvp(BattleSettings.defaults().withPrizes(new BattleSettings.Prizes(40, true, true)));
        fightToTheEnd();
        settle(500);
        assertEquals(3500, arena.state().trainers().get("ana").money());
        assertEquals((3000 - 500) - (3000 - 500) / 10, arena.state().trainers().get("bo").money(), "he paid 500 and then whited out");
    }

    @Test
    void aWagerNeverTakesMoreThanTheLoserHas() {
        startPvp(BattleSettings.defaults().withPrizes(new BattleSettings.Prizes(40, true, true)));
        fightToTheEnd();
        settle(10_000);
        assertEquals(6000, arena.state().trainers().get("ana").money(), "all 3000 of Bo's");
    }

    @Test
    void playersCannotRunAwayFromEachOtherEvenIfTheGameAllowsTrainerBattles() {
        startPvp(BattleSettings.defaults().withFlee(true, true));
        arena.round(Map.of("ana", 12, "bo", 5));
        assertThrows(BattleRefusedException.class, () -> arena.declare("pk-a", Action.flee()));
    }

    @Test
    void aPlayerCanSurrender() {
        startPvp(BattleSettings.defaults());
        arena.round(Map.of("ana", 12, "bo", 5));
        assertThrows(BattleRefusedException.class, () -> BattleDesk.surrender(arena.state(), ANA, "bo"), "only for yourself");
        arena.play(BattleDesk.surrender(arena.state(), BO, "bo"));
        assertEquals("surrendered", arena.battle().left().get("bo"));
        assertTrue(arena.said("Bo surrenders."));
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(Battle.Step.ENDED, arena.battle().step());
        assertThrows(BattleRefusedException.class, () -> BattleDesk.surrender(arena.state(), BO, "bo"), "the battle is over");
        settle(0);
        assertEquals(3000, arena.state().trainers().get("bo").money(), "a surrender is not a whiteout while the team is fine");
        assertEquals(book.species("bulbasaur").orElseThrow().growth().totalXp(20), arena.state().pokemon().get("pk-a").xp(), "nothing was defeated");
    }

    @Test
    void thereIsNoSurrenderFromAWildBattle() {
        PokemonSheet mine = give(sheet(book, "pk-a", "ana", "bulbasaur", 20, "tackle"));
        PokemonSheet wild = give(sheet(book, "w-1", null, "pidgey", 8, "tackle"));
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("pk-a"), List.of()),
                Arena.wild("wild", "Pidgey", "w-1")), List.of(ProgressFixtures.combatant(mine, "ana", book), ProgressFixtures.combatant(wild, "wild", book)));
        arena.round(Map.of("ana", 12, "wild", 5));
        assertThrows(BattleRefusedException.class, () -> BattleDesk.surrender(arena.state(), ANA, "ana"), "run away from a wild Pokémon instead");
    }

    @Test
    void pvpCanBeSwitchedOff() {
        PokemonSheet mine = give(sheet(book, "pk-a", "ana", "bulbasaur", 20, "tackle"));
        PokemonSheet his = give(sheet(book, "pk-b", "bo", "pidgey", 8, "tackle"));
        assertThrows(BattleRefusedException.class, () -> BattleDesk.start(arena.state(), Arena.ADMIN, "b2", Battle.Type.PVP,
                BattleSettings.defaults().withPrizes(new BattleSettings.Prizes(40, false, false)),
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("pk-a"), List.of()),
                        new Participant("bo", Kind.TRAINER, 1, "Bo", "bo", "u-bo", List.of("pk-b"), List.of())),
                List.of(ProgressFixtures.combatant(mine, "ana", book), ProgressFixtures.combatant(his, "bo", book))));
    }

    // ============================================================================================================== NPC trainers

    @Test
    void anNpcTrainerPaysThePrizeOfItsHighestLevelTimesFortyAndSplitsItBetweenTheTrainers() {
        PokemonSheet a = give(sheet(book, "pk-a", "ana", "bulbasaur", 20, "tackle"));
        PokemonSheet b = give(sheet(book, "pk-b", "bo", "bulbasaur", 20, "tackle"));
        PokemonSheet low = sheet(book, "n-1", null, "pidgey", 9, "tackle");
        PokemonSheet high = sheet(book, "n-2", null, "rattata", 14, "tackle");
        arena.start(Battle.Type.NPC_TRAINER, BattleSettings.defaults(),
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("pk-a"), List.of()), new Participant("bo", Kind.TRAINER, 0, "Bo", "bo", "u-bo", List.of("pk-b"), List.of()),
                        new Participant("rival", Kind.NPC, 1, "Rival", null, null, List.of("n-1"), List.of("n-2"))),
                List.of(ProgressFixtures.combatant(a, "ana", book), ProgressFixtures.combatant(b, "bo", book), ProgressFixtures.combatant(low, "rival", book),
                        ProgressFixtures.combatant(high, "rival", book)));
        arena.play(List.of(new GameEvent.CombatantSet("b1", arena.battle().combatant("n-1").withHp(0)), new GameEvent.CombatantSet("b1", arena.battle().combatant("n-2").withHp(0))));
        arena.play(BattleDesk.end(arena.state(), Arena.ADMIN, "test"));
        arena.play(AfterBattle.settle(arena.state(), book, MOVES, RULES, settings, EvolutionCheck.Context.none(), 0));
        // 14 x 40 = 560 ₽, shared: 280 each
        assertEquals(3280, arena.state().trainers().get("ana").money());
        assertEquals(3280, arena.state().trainers().get("bo").money());
        // each trainer who took part gets the whole 5 x 14 trainer XP
        assertEquals(70, arena.state().trainers().get("ana").xp());
        assertEquals(70, arena.state().trainers().get("bo").xp());
    }

    @Test
    void thePrizePerLevelIsASetting() {
        PokemonSheet a = give(sheet(book, "pk-a", "ana", "bulbasaur", 20, "tackle"));
        PokemonSheet n = sheet(book, "n-1", null, "pidgey", 10, "tackle");
        arena.start(Battle.Type.NPC_TRAINER, BattleSettings.defaults().withPrizes(new BattleSettings.Prizes(100, true, false)),
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("pk-a"), List.of()),
                        new Participant("rival", Kind.NPC, 1, "Rival", null, null, List.of("n-1"), List.of())),
                List.of(ProgressFixtures.combatant(a, "ana", book), ProgressFixtures.combatant(n, "rival", book)));
        arena.play(List.of(new GameEvent.CombatantSet("b1", arena.battle().combatant("n-1").withHp(0))));
        arena.play(BattleDesk.end(arena.state(), Arena.ADMIN, "test"));
        arena.play(AfterBattle.settle(arena.state(), book, MOVES, RULES, settings, EvolutionCheck.Context.none(), 0));
        assertEquals(4000, arena.state().trainers().get("ana").money());
        assertFalse(arena.state().trainers().get("ana").money() == 3000 + 400);
    }

    @Test
    void anNpcThatWonPaysNothing() {
        PokemonSheet a = give(sheet(book, "pk-a", "ana", "bulbasaur", 20, "tackle"));
        PokemonSheet n = sheet(book, "n-1", null, "pidgey", 10, "tackle");
        arena.start(Battle.Type.NPC_TRAINER, BattleSettings.defaults(),
                List.of(new Participant("ana", Kind.TRAINER, 0, "Ana", "ana", "u-ana", List.of("pk-a"), List.of()),
                        new Participant("rival", Kind.NPC, 1, "Rival", null, null, List.of("n-1"), List.of())),
                List.of(ProgressFixtures.combatant(a, "ana", book), ProgressFixtures.combatant(n, "rival", book)));
        arena.play(List.of(new GameEvent.CombatantSet("b1", arena.battle().combatant("pk-a").withHp(0))));
        arena.play(BattleDesk.end(arena.state(), Arena.ADMIN, "test"));
        arena.play(AfterBattle.settle(arena.state(), book, MOVES, RULES, settings, EvolutionCheck.Context.none(), 0));
        assertEquals(2700, arena.state().trainers().get("ana").money(), "she whited out and won no prize");
    }
}
