/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ProgressFixtures.RULES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.ProgressFixtures.Scene;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.rules.Growth;
import net.quimmilho.pokednd.core.rules.PokemonSettings;
import net.quimmilho.pokednd.core.rules.StatBlock;
import net.quimmilho.pokednd.core.trainer.Trainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 49: Pokémon XP, EVs and level-ups after a battle (chapters 5.3, 5.5, 5.8 and 7.15). The worked example of the specification:
 *
 * <pre>
 *   A level-12 wild Pidgey (XP value 50) is defeated.  A level-10 participant gets 140 XP, a team-mate who sat out gets 70;
 *   if the Pidgey had belonged to an NPC trainer the participant would get 210.
 * </pre>
 */
class LevelUpTest {

    private Scene scene;
    private SpeciesBook book;

    @BeforeEach
    void setUp() {
        scene = new Scene();
        book = scene.book;
    }

    private PokemonSheet bulbasaur(String id, int level, String... moves) {
        return sheet(book, id, "ana", "bulbasaur", level, moves);
    }

    private PokemonSheet wildPidgey(int level) {
        return sheet(book, "w-1", null, "pidgey", level, "tackle");
    }

    // ===================================================================================================================== XP

    @Test
    void theWorkedExampleOfTheSpecification() {
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle"), bulbasaur("pk-2", 10, "tackle"));
        scene.faint("w-1");
        scene.finish();
        assertEquals(560 + 140, scene.pokemon("pk-1").xp(), "the participant: 140 XP");
        assertEquals(560 + 70, scene.pokemon("pk-2").xp(), "the team-mate who sat out: half");
        assertEquals(10, scene.pokemon("pk-1").level());
        assertTrue(scene.arena.battle().settled());
    }

    @Test
    void thePokemonOfAnNpcTrainerGiveHalfAgainAsMuch() {
        PokemonSheet one = bulbasaur("pk-1", 10, "tackle");
        scene.give(one);
        scene.arena.start(Battle.Type.NPC_TRAINER, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("pk-1"), List.of()), new Battle.Participant("rival", Battle.Kind.NPC, 1, "Rival", null, null, List.of("w-1"), List.of())),
                List.of(ProgressFixtures.combatant(one, "ana", book), ProgressFixtures.combatant(wildPidgey(12), "rival", book)));
        scene.faint("w-1");
        scene.finish();
        assertEquals(560 + 210, scene.pokemon("pk-1").xp());
        // the prize: the highest level of the opponent x 40 ₽ (chapter 7.14), and 5 x that level as trainer XP (chapter 4.5)
        assertEquals(3000 + 12 * 40, scene.arena.state().trainers().get("ana").money());
        assertEquals(60, scene.arena.state().trainers().get("ana").xp());
    }

    @Test
    void theExperienceShareIsASetting() {
        scene.settings = scene.settings.withPokemon(PokemonSettings.defaults().withXpShare(PokemonSettings.XpShare.PARTICIPANTS_ONLY));
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle"), bulbasaur("pk-2", 10, "tackle"));
        scene.faint("w-1");
        scene.finish();
        assertEquals(560 + 140, scene.pokemon("pk-1").xp());
        assertEquals(560, scene.pokemon("pk-2").xp(), "nothing for the one that sat out");

        scene = new Scene();
        scene.settings = scene.settings.withPokemon(PokemonSettings.defaults().withXpShare(PokemonSettings.XpShare.ALL).withXpMultiplier(2.0));
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle"), bulbasaur("pk-2", 10, "tackle"));
        scene.faint("w-1");
        scene.finish();
        assertEquals(560 + 280, scene.pokemon("pk-2").xp(), "everybody gets the full share, doubled by pokemon.xp.multiplier");
    }

    @Test
    void aLuckyEggMakesItAHalfMore() {
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle").withHeldItem("lucky_egg"));
        scene.faint("w-1");
        scene.finish();
        assertEquals(560 + 210, scene.pokemon("pk-1").xp());
    }

    @Test
    void aFaintedPokemonGetsNoExperience() {
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle"), bulbasaur("pk-2", 10, "tackle"));
        scene.arena.play(List.of(new GameEvent.CombatantSet("b1", scene.arena.battle().combatant("pk-2").withHp(0))));
        scene.faint("w-1");
        scene.finish();
        assertEquals(560, scene.pokemon("pk-2").xp());
        assertEquals(0, scene.pokemon("pk-2").hp(), "and it stays fainted");
    }

    @Test
    void theDefeatedPokemonGivesItsEvsToThePokemonThatFought() {
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle"), bulbasaur("pk-2", 10, "tackle"));
        scene.faint("w-1");
        scene.finish();
        assertEquals(new StatBlock(0, 0, 0, 0, 0, 1), scene.pokemon("pk-1").evs(), "a Pidgey gives 1 Speed EV");
        assertEquals(StatBlock.all(0), scene.pokemon("pk-2").evs(), "the one that sat out gets none");
    }

    @Test
    void theWildPokemonDespawnsAndTheRewardsArePaidOnlyOnce() {
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle"));
        scene.faint("w-1");
        scene.finish();
        assertNull(scene.pokemon("w-1"));
        assertEquals(12, scene.arena.state().trainers().get("ana").xp(), "a wild Pokémon gives its level as trainer XP");
        assertThrows(BattleRefusedException.class, () -> AfterBattle.settle(scene.arena.state(), book, ProgressFixtures.MOVES, RULES, scene.settings,
                EvolutionCheck.Context.none(), 0));
    }

    @Test
    void aTrainerWhoRanAwayGetsNothing() {
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle"));
        scene.arena.play(List.of(new GameEvent.ParticipantLeft("b1", "ana", "fled")));
        scene.faint("w-1");
        scene.finish();
        assertEquals(560, scene.pokemon("pk-1").xp());
        assertEquals(0, scene.arena.state().trainers().get("ana").xp());
    }

    // ================================================================================================================= levels

    @Test
    void aLevelBringsHpFriendshipAndTheMovesOfThatLevel() {
        PokemonSheet near = bulbasaur("pk-1", 10, "tackle", "growl").withGrowth(10, 730, 1);
        near = near.withHp(BattleSetup.maxHp(near, book.species("bulbasaur").orElseThrow(), 10));
        scene.wild(wildPidgey(12), near);
        scene.faint("w-1");
        scene.finish();
        PokemonSheet after = scene.pokemon("pk-1");
        assertEquals(11, after.level());
        assertEquals(730 + 140, after.xp());
        int before = BattleSetup.maxHp(near, book.species("bulbasaur").orElseThrow(), 10);
        int now = BattleSetup.maxHp(near, book.species("bulbasaur").orElseThrow(), 11);
        assertEquals(now, after.hp(), "the HP grows with the maximum: " + before + " to " + now);
        assertEquals(75, after.friendship(), "+5 below friendship 100 (chapter 5.8)");
        assertEquals(List.of("tackle", "growl", "ember", "scratch"), after.moves().stream().map(Battle.MoveSlot::moveId).toList(), "both level-11 moves");
        assertTrue(scene.arena.state().prompts().isEmpty());
    }

    @Test
    void withFourMovesAKnownLevelMoveIsAPromptThatForgetsOrSkips() {
        PokemonSheet full = bulbasaur("pk-1", 10, "tackle", "growl", "watergun", "slash").withGrowth(10, 730, 30);
        scene.wild(wildPidgey(12), full);
        scene.faint("w-1");
        scene.finish();
        assertEquals(List.of("learn-pk-1-L11-ember", "learn-pk-1-L11-scratch"), List.copyOf(scene.arena.state().prompts().keySet()));
        Prompt ember = scene.arena.state().prompts().get("learn-pk-1-L11-ember");
        assertEquals(Prompt.Kind.LEARN_MOVE, ember.kind());
        assertTrue(ember.text().contains("knows 4 moves"));
        // forget Growl (place 1) to learn Ember
        scene.arena.play(ProgressDesk.answer(scene.arena.state(), Actor.player("u-ana"), "learn-pk-1-L11-ember", "1", book, ProgressFixtures.MOVES, RULES, scene.settings));
        assertEquals(List.of("tackle", "ember", "watergun", "slash"), scene.pokemon("pk-1").moves().stream().map(Battle.MoveSlot::moveId).toList());
        // and not learn Scratch
        scene.arena.play(ProgressDesk.answer(scene.arena.state(), Actor.player("u-ana"), "learn-pk-1-L11-scratch", "skip", book, ProgressFixtures.MOVES, RULES, scene.settings));
        assertEquals(List.of("tackle", "ember", "watergun", "slash"), scene.pokemon("pk-1").moves().stream().map(Battle.MoveSlot::moveId).toList());
        assertTrue(scene.arena.state().prompts().isEmpty());
        assertThrows(BattleRefusedException.class, () -> ProgressDesk.answer(scene.arena.state(), Actor.player("u-ana"), "learn-pk-1-L11-ember", "1", book,
                ProgressFixtures.MOVES, RULES, scene.settings), "answered prompts are gone");
    }

    @Test
    void thePhysicalCardsFreezeTheMoves() {
        scene.settings = scene.settings.withMovesFrozen(true);
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle", "growl").withGrowth(10, 730, 30));
        scene.faint("w-1");
        scene.finish();
        assertEquals(11, scene.pokemon("pk-1").level());
        assertEquals(2, scene.pokemon("pk-1").moves().size(), "chapter 5.5: moves are frozen to the four on the card");
        assertTrue(scene.arena.state().prompts().isEmpty());
    }

    @Test
    void severalLevelsAtOnceCountEachOne() {
        // 140 x 15 = 2100 XP in one go from level 10 (560 XP): the total of 2660 is level 16 (it needs 2535), six levels
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle", "growl"));
        scene.faint("w-1");
        scene.settings = scene.settings.withPokemon(PokemonSettings.defaults().withXpMultiplier(15.0));
        scene.finish();
        PokemonSheet after = scene.pokemon("pk-1");
        assertEquals(16, after.level());
        assertEquals(6, after.level() - 10);
        assertEquals(100, after.friendship(), "+5 for each of the six levels (it is below 100 until the last one)");
        assertTrue(scene.arena.state().prompts().containsKey("evolve-pk-1-ivysaur"), "level 16 is ready to evolve");
    }

    @Test
    void theLevelCapIsASetting() {
        scene.settings = scene.settings.withPokemon(PokemonSettings.defaults().withLevelCap(10));
        scene.wild(wildPidgey(40), bulbasaur("pk-1", 9, "tackle", "growl"));
        scene.faint("w-1");
        scene.finish();
        assertEquals(10, scene.pokemon("pk-1").level(), "it stops at the cap");
        assertEquals(Growth.MEDIUM_SLOW.totalXp(10), scene.pokemon("pk-1").xp(), "and keeps no more XP than the cap needs");
    }

    @Test
    void aBreederDoublesTheFriendshipGainOfALevel() {
        Trainer ana = scene.arena.state().trainers().get("ana");
        scene.arena.play(List.of(new GameEvent.TrainerSet("ana", new Trainer(ana.name(), "breeder", ana.level(), ana.xp(), ana.scores(), ana.skills(), ana.maxHp(), ana.hp(),
                ana.money(), ana.items()))));
        scene.wild(wildPidgey(12), bulbasaur("pk-1", 10, "tackle", "growl").withGrowth(10, 730, 30));
        scene.faint("w-1");
        scene.finish();
        assertEquals(70 + 10, scene.pokemon("pk-1").friendship(), "Gentle Hands: friendship gain x2");
    }

    @Test
    void theGainPerLevelFollowsTheBandsOfFriendship() {
        ProgressSettings s = ProgressSettings.defaults();
        assertEquals(5, Friendship.gainPerLevel(s, 99));
        assertEquals(4, Friendship.gainPerLevel(s, 100));
        assertEquals(4, Friendship.gainPerLevel(s, 199));
        assertEquals(3, Friendship.gainPerLevel(s, 200));
        // 198 -> +4 -> 202 -> +3 -> 205 -> +3 -> 208: each level counts on its own
        assertEquals(208, Friendship.afterLevelUps(s, 198, 3, 1.0));
        assertEquals(255, Friendship.afterLevelUps(s, 250, 5, 1.0), "never above 255");
    }

    @Test
    void theSettingsAreReadFromTheirKeys() {
        ProgressSettings s = ProgressSettings.fromValues(Map.of("pokemon.level.cap", "50", "pokemon.xp.share", "participantsOnly", "pokemon.evolution.mode", "automatic",
                "pokemon.friendship.levelUpGain", "10,8,6", "pokemon.moves.max", "3", "cards.mode", "physical"));
        assertEquals(50, s.pokemon().levelCap());
        assertEquals(ProgressSettings.EvolutionMode.AUTOMATIC, s.evolutionMode());
        assertEquals(List.of(10, 8, 6), s.levelUpGain());
        assertEquals(3, s.movesMax());
        assertTrue(s.movesFrozen());
        assertFalse(ProgressSettings.defaults().movesFrozen());
    }
}
