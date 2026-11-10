/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ProgressFixtures.MOVES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.RULES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.quimmilho.pokednd.core.game.ProgressFixtures.Scene;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 49: fainting and healing (chapter 5.7), friendship (5.8), whiting out (7.15) and catching (7.10) after a battle. A fainted
 * Pokémon stays fainted until a healer restores it or a Revive brings it back.
 */
class FaintingHealingTest {

    private Scene scene;
    private SpeciesBook book;

    @BeforeEach
    void setUp() {
        scene = new Scene();
        book = scene.book;
    }

    private PokemonSheet bulbasaur(String id, int level) {
        return sheet(book, id, "ana", "bulbasaur", level, "tackle", "growl");
    }

    private PokemonSheet wild(String species, int level) {
        return sheet(book, "w-1", null, species, level, "tackle");
    }

    private void faintAndSettle(String... faint) {
        for (String id : faint) {
            scene.faint(id);
        }
        scene.finish();
    }

    // ============================================================================================================ fainting

    @Test
    void aPokemonThatFaintedStaysFaintedAfterTheBattleAndLosesFriendship() {
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10), bulbasaur("pk-2", 10));
        faintAndSettle("pk-1", "w-1");
        PokemonSheet fainted = scene.pokemon("pk-1");
        assertEquals(0, fainted.hp(), "the HP stays 0 until it is healed or revived");
        assertEquals(70 - 1, fainted.friendship(), "-1 for fainting (pokemon.friendship.faintLoss)");
        assertEquals(560, fainted.xp(), "and it gets no experience");
        assertTrue(scene.pokemon("pk-2").hp() > 0);
        assertEquals(70, scene.pokemon("pk-2").friendship());
    }

    @Test
    void theFriendshipLostForFaintingIsASetting() {
        scene.settings = scene.settings.withFriendship(220, List.of(5, 4, 3), 5, 10);
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10), bulbasaur("pk-2", 10));
        faintAndSettle("pk-1", "w-1");
        assertEquals(65, scene.pokemon("pk-1").friendship());
        // never below 0
        scene = new Scene();
        scene.settings = scene.settings.withFriendship(220, List.of(5, 4, 3), 5, 10);
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10).withFriendship(2), bulbasaur("pk-2", 10));
        faintAndSettle("pk-1", "w-1");
        assertEquals(0, scene.pokemon("pk-1").friendship());
    }

    @Test
    void aFaintedPokemonCannotBeHealedWithAPotionButARevivesBringsItBack() {
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10), bulbasaur("pk-2", 10));
        faintAndSettle("pk-1", "w-1");
        assertThrows(BattleRefusedException.class, () -> ProgressDesk.heal(scene.arena.state(), book, "pk-1", 20), "a potion does not wake a fainted Pokémon");
        int max = BattleSetup.maxHp(scene.pokemon("pk-1"), book.species("bulbasaur").orElseThrow(), 10);
        scene.arena.play(ProgressDesk.revive(scene.arena.state(), book, "pk-1", 50));
        assertEquals(max / 2, scene.pokemon("pk-1").hp(), "a Revive restores half");
        assertThrows(BattleRefusedException.class, () -> ProgressDesk.revive(scene.arena.state(), book, "pk-1", 50), "it has not fainted any more");
        scene.arena.play(ProgressDesk.heal(scene.arena.state(), book, "pk-1", 1000));
        assertEquals(max, scene.pokemon("pk-1").hp(), "a potion never goes above the maximum");
    }

    @Test
    void aMaxReviveRestoresEverythingAndAReviveAtLeastOneHp() {
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10), bulbasaur("pk-2", 10));
        faintAndSettle("pk-1", "w-1");
        int max = BattleSetup.maxHp(scene.pokemon("pk-1"), book.species("bulbasaur").orElseThrow(), 10);
        scene.arena.play(ProgressDesk.revive(scene.arena.state(), book, "pk-1", 100));
        assertEquals(max, scene.pokemon("pk-1").hp());
        assertThrows(BattleRefusedException.class, () -> ProgressDesk.revive(scene.arena.state(), book, "pk-2", 0));
    }

    @Test
    void theHealerRestoresHpPpAndStatusOfTheWholePartyEvenTheFainted() {
        PokemonSheet one = bulbasaur("pk-1", 10).withMoves(List.of(ProgressFixtures.slot("tackle"), ProgressFixtures.slot("growl")));
        scene.wild(wild("pidgey", 5), one, bulbasaur("pk-2", 10));
        // in the battle pk-2 is hurt and poisoned, and pk-1 used PP
        Battle.Combatant pk1 = scene.arena.battle().combatant("pk-1");
        List<Battle.MoveSlot> spent = List.of(pk1.moves().get(0).spend().spend(), pk1.moves().get(1));
        scene.arena.play(List.of(new GameEvent.CombatantSet("b1", pk1.withMoves(spent))));
        scene.arena.play(List.of(new GameEvent.CombatantSet("b1", scene.arena.battle().combatant("pk-2").withHp(3)
                .withCondition(scene.arena.battle().combatant("pk-2").condition().withStatus(Battle.Status.POISON, 0)))));
        faintAndSettle("w-1");
        assertEquals(33, scene.pokemon("pk-1").moves().get(0).pp(), "35 PP, two used");
        assertEquals(Battle.Status.POISON, scene.pokemon("pk-2").status(), "a status stays after the battle");
        assertEquals(3, scene.pokemon("pk-2").hp());

        scene.arena.play(ProgressDesk.healParty(scene.arena.state(), book, "ana"));
        int max2 = BattleSetup.maxHp(scene.pokemon("pk-2"), book.species("bulbasaur").orElseThrow(), 10);
        assertEquals(max2, scene.pokemon("pk-2").hp());
        assertEquals(Battle.Status.NONE, scene.pokemon("pk-2").status());
        assertEquals(35, scene.pokemon("pk-1").moves().get(0).pp(), "the PP are back");
    }

    @Test
    void aHeldItemThatWasUsedInTheBattleIsGoneAfterIt() {
        PokemonSheet holder = bulbasaur("pk-1", 10).withHeldItem("oran_berry");
        scene.wild(wild("pidgey", 5), holder);
        scene.arena.play(List.of(new GameEvent.HeldItemSet("b1", "pk-1", null, "It ate its berry.")));
        faintAndSettle("w-1");
        assertNull(scene.pokemon("pk-1").heldItem());
    }

    // ================================================================================================================= whiteout

    @Test
    void whenEveryPokemonFaintsTheTrainerWhitesOutLosesMoneyAndIsHealed() {
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10), bulbasaur("pk-2", 10));
        faintAndSettle("pk-1", "pk-2");
        assertEquals(3000 - 300, scene.arena.state().trainers().get("ana").money(), "10 % lost (combat.whiteout.moneyLossPct)");
        assertTrue(scene.pokemon("pk-1").hp() > 0 && scene.pokemon("pk-2").hp() > 0, "the team is healed");
        Prompt prompt = scene.arena.state().prompts().get("whiteout-b1-ana");
        assertEquals(Prompt.Kind.WHITEOUT, prompt.kind());
        scene.arena.play(ProgressDesk.answer(scene.arena.state(), Actor.player("u-ana"), prompt.id(), "ok", book, MOVES, RULES, scene.settings));
        assertTrue(scene.arena.state().prompts().isEmpty());
    }

    @Test
    void aTrainerWithOnePokemonLeftDoesNotWhiteOut() {
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10), bulbasaur("pk-2", 10));
        faintAndSettle("pk-1", "w-1");
        assertEquals(3000, scene.arena.state().trainers().get("ana").money());
        assertEquals(0, scene.pokemon("pk-1").hp(), "and a fainted one stays fainted");
    }

    // ================================================================================================================== catching

    private void ballAt(int hp) {
        scene.arena.play(List.of(new GameEvent.CombatantSet("b1", scene.arena.battle().combatant("w-1").withHp(hp))));
        scene.arena.play(List.of(new GameEvent.BallThrown("b1", "w-1", "ana", "great_ball", 3, true)));
    }

    @Test
    void aCaughtPokemonJoinsThePartyWithTheHpItHadAndGivesNoExperience() {
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10));
        ballAt(4);
        scene.finish();
        PokemonSheet caught = scene.pokemon("w-1");
        assertEquals("ana", caught.ownerId());
        assertEquals(PokemonSheet.Location.PARTY, caught.location());
        assertEquals("great_ball", caught.ball());
        assertEquals(4, caught.hp(), "it keeps the HP it had when the ball hit");
        assertEquals(560, scene.pokemon("pk-1").xp(), "a caught Pokémon is not defeated: no experience");
        assertEquals(10, scene.arena.state().trainers().get("ana").xp(), "catching gives the trainer 10 XP (chapter 4.5)");
    }

    @Test
    void aCaughtPokemonGoesToAPcBoxWhenThePartyIsFull() {
        scene.settings = scene.settings.withPartySize(1);
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10));
        ballAt(4);
        scene.finish();
        assertEquals(PokemonSheet.Location.PC, scene.pokemon("w-1").location());
    }

    @Test
    void aBattleSetUpByHandStillMakesASheetForTheCaughtPokemon() {
        PokemonSheet mine = bulbasaur("pk-1", 10);
        scene.give(mine);
        PokemonSheet pidgey = wild("pidgey", 5); // the game has no sheet for it
        scene.arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("pk-1"), List.of()), Arena.wild("wild", "Pidgey", "w-1")),
                List.of(ProgressFixtures.combatant(mine, "ana", book), ProgressFixtures.combatant(pidgey, "wild", book)));
        scene.arena.play(List.of(new GameEvent.BallThrown("b1", "w-1", "ana", "poke_ball", 3, true)));
        scene.finish();
        PokemonSheet made = scene.pokemon("w-1");
        assertEquals("pidgey", made.speciesId());
        assertEquals("ana", made.ownerId());
        assertEquals(5, made.level());
        assertEquals(book.species("pidgey").orElseThrow().growth().totalXp(5), made.xp());
    }

    @Test
    void theWildPokemonThatWasNotCaughtGoesAwayAndOtherWildOnesStayCaughtOnly() {
        scene.wild(wild("pidgey", 5), bulbasaur("pk-1", 10));
        scene.give(sheet(book, "w-9", null, "rattata", 3, "tackle")); // another wild Pokémon that is not in this battle
        scene.faint("w-1");
        scene.finish();
        assertNull(scene.pokemon("w-1"), "the one that was fought has gone");
        assertFalse(scene.pokemon("w-9") == null, "a wild Pokémon elsewhere is not touched");
    }
}
