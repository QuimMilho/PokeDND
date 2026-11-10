/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ProgressFixtures.MOVES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.RULES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.rule;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Candidate;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Context;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Trigger;
import net.quimmilho.pokednd.core.game.ProgressFixtures.Scene;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.importer.Evolutions;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.StatBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 49: evolution (chapter 5.4). The conditions are checked one by one against hand-written cases (the real data is run through the
 * same code in {@link EvolutionMethodsRealDataTest}), and the prompt that follows a level-up is answered in each way.
 */
class EvolutionTest {

    private Scene scene;
    private SpeciesBook book;
    private static final StatBlock STATS = StatBlock.all(50);

    @BeforeEach
    void setUp() {
        scene = new Scene();
        book = scene.book;
    }

    private SpeciesBook.Species species(String id, String evolutions) {
        SpeciesBook.Species sp = SpeciesBook.Species.simple(id, id, List.of(PokemonType.NORMAL), STATS, net.quimmilho.pokednd.core.rules.Growth.MEDIUM_FAST, 60);
        return evolutions.isEmpty() ? sp : sp.withEvolutions(java.util.Arrays.stream(evolutions.split("; ")).map(t -> rule(id, t)).toList());
    }

    private PokemonSheet mon(String species, int level) {
        return sheet(SpeciesBook.of(List.of(species(species, ""))), "pk-1", "ana", species, level, "tackle");
    }

    private List<Candidate> candidates(SpeciesBook.Species sp, PokemonSheet s, Trigger trigger, Context ctx) {
        return EvolutionCheck.candidates(s, sp, STATS, trigger, ctx, ProgressSettings.defaults());
    }

    private static List<String> targets(List<Candidate> list) {
        return list.stream().map(c -> c.rule().to()).toList();
    }

    // ================================================================================================================ the methods

    @Test
    void aLevelEvolutionStartsOnALevelUpAtTheLevel() {
        SpeciesBook.Species sp = species("charmander", "Charmeleon (Level 16)");
        assertEquals(List.of("charmeleon"), targets(candidates(sp, mon("charmander", 16), Trigger.LEVEL_UP, Context.none())));
        assertEquals(List.of(), candidates(sp, mon("charmander", 15), Trigger.LEVEL_UP, Context.none()));
        assertEquals(List.of(), candidates(sp, mon("charmander", 16), Trigger.USE_ITEM, Context.none().withUsedItem("fire_stone")), "an item does not make it start");
        assertEquals(List.of(), candidates(sp, mon("charmander", 16), Trigger.TRADE, Context.none()), "neither does a trade");
    }

    @Test
    void anItemEvolutionStartsWhenThatItemIsUsed() {
        SpeciesBook.Species sp = species("pikachu", "Raichu (Use Thunder Stone)");
        assertEquals(List.of("raichu"), targets(candidates(sp, mon("pikachu", 5), Trigger.USE_ITEM, Context.none().withUsedItem("thunder_stone"))));
        assertEquals(List.of("raichu"), targets(candidates(sp, mon("pikachu", 5), Trigger.USE_ITEM, Context.none().withUsedItem("Thunder Stone"))), "any spelling");
        assertEquals(List.of(), candidates(sp, mon("pikachu", 5), Trigger.USE_ITEM, Context.none().withUsedItem("fire_stone")));
        assertEquals(List.of(), candidates(sp, mon("pikachu", 99), Trigger.LEVEL_UP, Context.none()), "no level makes it evolve");
    }

    @Test
    void aTradeEvolutionStartsOnATradeAndAHeldItemIsUsedUp() {
        SpeciesBook.Species sp = species("onix", "Steelix (Trade, Holding Metal Coat)");
        PokemonSheet plain = mon("onix", 20);
        assertEquals(List.of(), candidates(sp, plain, Trigger.TRADE, Context.none()), "it must hold the item");
        PokemonSheet holding = plain.withHeldItem("metal_coat");
        List<Candidate> list = candidates(sp, holding, Trigger.TRADE, Context.none());
        assertEquals(List.of("steelix"), targets(list));
        assertEquals(List.of(), candidates(sp, holding, Trigger.LEVEL_UP, Context.none()));
        SpeciesBook b = SpeciesBook.of(List.of(sp, SpeciesBook.Species.simple("steelix", "Steelix", List.of(PokemonType.STEEL), STATS, net.quimmilho.pokednd.core.rules.Growth.MEDIUM_FAST, 100)));
        PokemonSheet evolved = EvolutionCheck.evolve(holding, sp, b.species("steelix").orElseThrow(), list.get(0).rule());
        assertEquals("steelix", evolved.speciesId());
        assertNull(evolved.heldItem(), "the held item is consumed (chapter 5.4)");
    }

    @Test
    void aTradeWithoutAHeldItemKeepsWhatItHolds() {
        SpeciesBook.Species sp = species("kadabra", "Alakazam (Trade)");
        PokemonSheet holding = mon("kadabra", 30).withHeldItem("oran_berry");
        List<Candidate> list = candidates(sp, holding, Trigger.TRADE, Context.none());
        assertEquals(List.of("alakazam"), targets(list));
        assertEquals("oran_berry", EvolutionCheck.evolve(holding, sp, SpeciesBook.Species.simple("alakazam", "Alakazam", List.of(), STATS,
                net.quimmilho.pokednd.core.rules.Growth.FAST, 100), list.get(0).rule()).heldItem());
    }

    // ============================================================================================================== the conditions

    @Test
    void friendshipTimeOfDayAndGender() {
        SpeciesBook.Species sp = species("eevee", "Espeon (Friendship >= 220, Day); Umbreon (Friendship >= 220, Night)");
        PokemonSheet happy = mon("eevee", 10).withFriendship(220);
        assertEquals(List.of("espeon"), targets(candidates(sp, happy, Trigger.LEVEL_UP, Context.none().withTime("day"))));
        assertEquals(List.of("umbreon"), targets(candidates(sp, happy, Trigger.LEVEL_UP, Context.none().withTime("night"))));
        assertEquals(List.of(), candidates(sp, happy, Trigger.LEVEL_UP, Context.none().withTime("dusk")));
        assertEquals(List.of(), candidates(sp, happy.withFriendship(219), Trigger.LEVEL_UP, Context.none().withTime("day")));
        assertEquals(List.of(), candidates(sp, happy, Trigger.LEVEL_UP, Context.none()), "the game does not say what time it is");

        SpeciesBook.Species kirlia = species("kirlia", "Gallade (Level 1, Gender Male)");
        assertEquals(List.of("gallade"), targets(candidates(kirlia, mon("kirlia", 5).withGender("M"), Trigger.LEVEL_UP, Context.none())));
        assertEquals(List.of(), candidates(kirlia, mon("kirlia", 5).withGender("F"), Trigger.LEVEL_UP, Context.none()));
    }

    @Test
    void theFriendshipNeededIsASetting() {
        SpeciesBook.Species sp = species("golbat", "Crobat (Friendship >= 220)");
        PokemonSheet s = mon("golbat", 30).withFriendship(200);
        assertEquals(List.of(), candidates(sp, s, Trigger.LEVEL_UP, Context.none()));
        ProgressSettings easy = ProgressSettings.defaults().withFriendship(200, List.of(5, 4, 3), 1, 10);
        assertEquals(List.of("crobat"), targets(EvolutionCheck.candidates(s, sp, STATS, Trigger.LEVEL_UP, Context.none(), easy)));
    }

    @Test
    void statsMovesAndHeldItems() {
        SpeciesBook.Species tyrogue = species("tyrogue", "Hitmonlee (Level 20, Attack > Defence); Hitmonchan (Level 20, Defence > Attack); Hitmontop (Level 20, Attack = Defence)");
        PokemonSheet s = mon("tyrogue", 20);
        assertEquals(List.of("hitmonlee"), targets(EvolutionCheck.candidates(s, tyrogue, new StatBlock(30, 60, 40, 20, 20, 20), Trigger.LEVEL_UP, Context.none(), ProgressSettings.defaults())));
        assertEquals(List.of("hitmonchan"), targets(EvolutionCheck.candidates(s, tyrogue, new StatBlock(30, 40, 60, 20, 20, 20), Trigger.LEVEL_UP, Context.none(), ProgressSettings.defaults())));
        assertEquals(List.of("hitmontop"), targets(EvolutionCheck.candidates(s, tyrogue, new StatBlock(30, 40, 40, 20, 20, 20), Trigger.LEVEL_UP, Context.none(), ProgressSettings.defaults())));

        SpeciesBook.Species lickitung = species("lickitung", "Lickilicky (Level 1, Knows Rollout)");
        assertEquals(List.of(), candidates(lickitung, mon("lickitung", 5), Trigger.LEVEL_UP, Context.none()));
        assertEquals(List.of("lickilicky"), targets(candidates(lickitung, mon("lickitung", 5).withMoves(List.of(ProgressFixtures.slot("rollout"))), Trigger.LEVEL_UP, Context.none())));

        SpeciesBook.Species eevee = species("eevee", "Sylveon (Friendship >= 220, Knows a Fairy-type move)");
        PokemonSheet charming = mon("eevee", 5).withFriendship(255).withMoves(List.of(ProgressFixtures.slot("tackle")));
        Context fairy = Context.none().withMoveTypes(id -> id.equals("tackle") ? PokemonType.FAIRY : null);
        assertEquals(List.of("sylveon"), targets(candidates(eevee, charming, Trigger.LEVEL_UP, fairy)));
        assertEquals(List.of(), candidates(eevee, charming, Trigger.LEVEL_UP, Context.none().withMoveTypes(id -> PokemonType.NORMAL)));

        SpeciesBook.Species poliwhirl = species("sneasel", "Weavile (Level 1, Holding Razor Claw, Night)");
        assertEquals(List.of("weavile"), targets(candidates(poliwhirl, mon("sneasel", 5).withHeldItem("razor_claw"), Trigger.LEVEL_UP, Context.none().withTime("night"))));
        assertEquals(List.of(), candidates(poliwhirl, mon("sneasel", 5).withHeldItem("razor_claw"), Trigger.LEVEL_UP, Context.none().withTime("day")));
    }

    @Test
    void placesPartyAndNegatedConditions() {
        SpeciesBook.Species sp = species("pikachu", "Raichu (Level 1, Biome Alola Biome); Pichu (Level 1, With Mantyke in party); Pikachu (Level 1, Without Skarmory in party)");
        assertEquals(List.of("raichu"), targets(candidates(sp, mon("pikachu", 5), Trigger.LEVEL_UP, Context.none().withTags(Set.of("alola_biome"))).stream()
                .filter(c -> c.rule().to().equals("raichu")).toList()));
        assertEquals(List.of("pichu"), targets(candidates(sp, mon("pikachu", 5), Trigger.LEVEL_UP, Context.none().withParty(List.of("mantyke", "skarmory"))).stream()
                .filter(c -> c.rule().to().equals("pichu")).toList()));
        // "Without Skarmory": fulfilled while there is none in the party
        assertTrue(targets(candidates(sp, mon("pikachu", 5), Trigger.LEVEL_UP, Context.none().withParty(List.of("mantyke")))).contains("pikachu"));
        assertFalse(targets(candidates(sp, mon("pikachu", 5), Trigger.LEVEL_UP, Context.none().withParty(List.of("skarmory")))).contains("pikachu"));
    }

    @Test
    void thingsThatAreCountedAreLookedAtWhenTheCountChanges() {
        SpeciesBook.Species primeape = species("primeape", "Annihilape (Level 1, Use Rage Fist x20)");
        PokemonSheet s = mon("primeape", 35).withCount("use:ragefist", 19);
        assertEquals(List.of(), candidates(primeape, s, Trigger.TICK, Context.none()));
        assertEquals(List.of("annihilape"), targets(candidates(primeape, s.withCount("use:ragefist", 1), Trigger.TICK, Context.none())));

        SpeciesBook.Species farfetchd = species("farfetchd", "Sirfetchd (Travel 1000 blocks)");
        assertEquals(List.of(), candidates(farfetchd, mon("farfetchd", 10).withTravelled(999), Trigger.TICK, Context.none()));
        assertEquals(List.of("sirfetchd"), targets(candidates(farfetchd, mon("farfetchd", 10).withTravelled(1000), Trigger.TICK, Context.none())));
    }

    @Test
    void aWurmpleDecidesItsCocoonFromAValueKeptFromCreation() {
        SpeciesBook.Species wurmple = species("wurmple", "Silcoon (Level 7, Wurmple Cocoon Species Silcoon); Cascoon (Level 7, Wurmple Cocoon Species Cascoon)");
        PokemonSheet s = mon("wurmple", 7);
        PokemonSheet silcoon = new PokemonSheet(s.id(), s.ownerId(), s.speciesId(), s.name(), 7, s.xp(), s.nature(), s.ivs(), s.evs(), s.gender(), false, null, null, s.moves(),
                s.hp(), s.status(), s.friendship(), s.location(), null, 0, Map.of(), 0, "silcoon");
        assertEquals(List.of("silcoon"), targets(candidates(wurmple, silcoon, Trigger.LEVEL_UP, Context.none())));
    }

    @Test
    void conditionsOnlyTheAdminCanJudgeMakeACandidateThatNeedsTheAdmin() {
        SpeciesBook.Species sp = species("gimmighoul", "Gholdengo (Level 1, Gimmighoul Gimmighoul Coins 999)");
        List<Candidate> list = candidates(sp, mon("gimmighoul", 5), Trigger.LEVEL_UP, Context.none());
        assertEquals(1, list.size());
        assertTrue(list.get(0).needsAdmin());
        SpeciesBook.Species moon = species("clefairy", "Clefable (Level 1, Moon FULL MOON)");
        assertTrue(candidates(moon, mon("clefairy", 5), Trigger.LEVEL_UP, Context.none()).get(0).needsAdmin());
        // a condition the game can check still has to hold
        SpeciesBook.Species both = species("clefairy", "Clefable (Level 30, Moon FULL MOON)");
        assertEquals(List.of(), candidates(both, mon("clefairy", 5), Trigger.LEVEL_UP, Context.none()));
    }

    // ============================================================================================================== asking and answering

    private PokemonSheet nearSixteen() {
        return sheet(book, "pk-1", "ana", "bulbasaur", 15, "tackle", "growl").withGrowth(15, 2400, 30);
    }

    private void battleWon() {
        scene.wild(sheet(book, "w-1", null, "pidgey", 20, "tackle"), nearSixteen());
        scene.faint("w-1");
        scene.finish();
    }

    @Test
    void theOwnerIsAskedAndSaysYes() {
        battleWon();
        assertEquals(16, scene.pokemon("pk-1").level());
        Prompt prompt = scene.arena.state().prompts().get("evolve-pk-1-ivysaur");
        assertEquals(Prompt.Kind.EVOLVE, prompt.kind());
        assertEquals("ana", prompt.trainerId());
        assertEquals("bulbasaur", scene.pokemon("pk-1").speciesId(), "nothing changes before the answer");
        long xpBefore = scene.arena.state().trainers().get("ana").xp();
        scene.arena.play(ProgressDesk.answer(scene.arena.state(), Actor.player("u-ana"), prompt.id(), "yes", book, MOVES, RULES, scene.settings));
        PokemonSheet evolved = scene.pokemon("pk-1");
        assertEquals("ivysaur", evolved.speciesId());
        assertEquals("Ivysaur", evolved.name(), "the name follows the species");
        assertEquals("overgrow", evolved.ability());
        assertTrue(evolved.hp() > 30, "the higher maximum HP adds to the HP");
        assertEquals(xpBefore + 20, scene.arena.state().trainers().get("ana").xp(), "an evolution gives its trainer 20 XP (chapter 4.5)");
        assertTrue(scene.arena.state().prompts().isEmpty());
    }

    @Test
    void aNicknameStaysAndSayingNotNowWaitsForTheNextLevel() {
        PokemonSheet named = new PokemonSheet("pk-1", "ana", "bulbasaur", "Bulby", 15, 2400, "hardy", StatBlock.all(0), StatBlock.all(0), "N", false, "overgrow", null,
                List.of(ProgressFixtures.slot("tackle")), 30, Battle.Status.NONE, 70, PokemonSheet.Location.PARTY, null, 0, Map.of(), 0, null);
        scene.wild(sheet(book, "w-1", null, "pidgey", 20, "tackle"), named);
        scene.faint("w-1");
        scene.finish();
        scene.arena.play(ProgressDesk.answer(scene.arena.state(), Actor.player("u-ana"), "evolve-pk-1-ivysaur", "no", book, MOVES, RULES, scene.settings));
        assertEquals("bulbasaur", scene.pokemon("pk-1").speciesId());
        assertEquals(16, scene.pokemon("pk-1").declinedLevel());
        SpeciesBook.Species bulbasaur = book.species("bulbasaur").orElseThrow();
        assertEquals(List.of(), EvolutionCheck.candidates(scene.pokemon("pk-1"), bulbasaur, STATS, Trigger.LEVEL_UP, Context.none(), scene.settings), "not at the same level");
        assertEquals(List.of("ivysaur"), targets(EvolutionCheck.candidates(scene.pokemon("pk-1").withGrowth(17, 3200, 30), bulbasaur, STATS, Trigger.LEVEL_UP, Context.none(), scene.settings)),
                "the next level offers it again");
        // and a nickname stays when it evolves
        PokemonSheet evolved = EvolutionCheck.evolve(scene.pokemon("pk-1"), bulbasaur, book.species("ivysaur").orElseThrow(), bulbasaur.evolutions().get(0));
        assertEquals("Bulby", evolved.name());
        assertEquals(0, evolved.declinedLevel());
    }

    @Test
    void automaticModeEvolvesAtOnce() {
        scene.settings = scene.settings.withEvolution(ProgressSettings.EvolutionMode.AUTOMATIC, ProgressSettings.UnknownFallback.ASK_ADMIN);
        battleWon();
        assertEquals("ivysaur", scene.pokemon("pk-1").speciesId());
        assertTrue(scene.arena.state().prompts().isEmpty());
        // a level-20 wild Pokémon gives 20 trainer XP and the evolution 20 more (chapter 4.5)
        assertEquals(40, scene.arena.state().trainers().get("ana").xp());
    }

    @Test
    void askAdminModeLeavesTheAnswerToTheAdmin() {
        scene.settings = scene.settings.withEvolution(ProgressSettings.EvolutionMode.ASK_ADMIN, ProgressSettings.UnknownFallback.ASK_ADMIN);
        battleWon();
        Prompt prompt = scene.arena.state().prompts().get("evolve-pk-1-ivysaur");
        assertEquals(Prompt.Kind.ADMIN_EVOLVE, prompt.kind());
        assertThrows(BattleRefusedException.class, () -> ProgressDesk.answer(scene.arena.state(), Actor.player("u-ana"), prompt.id(), "yes", book, MOVES, RULES, scene.settings));
        scene.arena.play(ProgressDesk.answer(scene.arena.state(), Actor.admin("u-admin"), prompt.id(), "yes", book, MOVES, RULES, scene.settings));
        assertEquals("ivysaur", scene.pokemon("pk-1").speciesId());
    }

    @Test
    void anEvolutionOnlyTheAdminCanJudgeFollowsTheFallbackSetting() {
        SpeciesBook moonBook = SpeciesBook.of(List.of(species("clefairy", "Clefable (Level 5, Moon FULL MOON)"),
                SpeciesBook.Species.simple("clefable", "Clefable", List.of(PokemonType.FAIRY), STATS, net.quimmilho.pokednd.core.rules.Growth.MEDIUM_FAST, 100),
                ProgressFixtures.pidgey()));
        Scene s = new Scene(moonBook);
        s.wild(sheet(moonBook, "w-1", null, "pidgey", 20, "tackle"), sheet(moonBook, "pk-1", "ana", "clefairy", 4, "tackle").withGrowth(4, 60, 20));
        s.faint("w-1");
        s.finish();
        assertEquals(Prompt.Kind.ADMIN_EVOLVE, s.arena.state().prompts().get("evolve-pk-1-clefable").kind(), "pokemon.evolution.unknownFallback is askAdmin");

        Scene player = new Scene(moonBook);
        player.settings = player.settings.withEvolution(ProgressSettings.EvolutionMode.ASK_PLAYER, ProgressSettings.UnknownFallback.ASK_PLAYER);
        player.wild(sheet(moonBook, "w-1", null, "pidgey", 20, "tackle"), sheet(moonBook, "pk-1", "ana", "clefairy", 4, "tackle").withGrowth(4, 60, 20));
        player.faint("w-1");
        player.finish();
        assertEquals(Prompt.Kind.EVOLVE, player.arena.state().prompts().get("evolve-pk-1-clefable").kind());

        Scene skip = new Scene(moonBook);
        skip.settings = skip.settings.withEvolution(ProgressSettings.EvolutionMode.ASK_PLAYER, ProgressSettings.UnknownFallback.SKIP);
        skip.wild(sheet(moonBook, "w-1", null, "pidgey", 20, "tackle"), sheet(moonBook, "pk-1", "ana", "clefairy", 4, "tackle").withGrowth(4, 60, 20));
        skip.faint("w-1");
        skip.finish();
        assertTrue(skip.arena.state().prompts().isEmpty());
    }

    // ===================================================================================================================== hooks

    @Test
    void aStoneAndATradeAreLookedAtByTheHooksOfLaterPhases() {
        SpeciesBook.Species pikachu = species("pikachu", "Raichu (Use Thunder Stone)");
        SpeciesBook b = SpeciesBook.of(List.of(pikachu, SpeciesBook.Species.simple("raichu", "Raichu", List.of(PokemonType.ELECTRIC), STATS,
                net.quimmilho.pokednd.core.rules.Growth.MEDIUM_FAST, 100)));
        Scene s = new Scene(b);
        s.give(sheet(b, "pk-1", "ana", "pikachu", 10, "tackle"));
        List<GameEvent> events = ProgressDesk.check(s.arena.state(), b, MOVES, RULES, s.settings, "pk-1", Trigger.USE_ITEM, Context.none().withUsedItem("thunder_stone"));
        s.arena.play(events);
        assertTrue(s.arena.state().prompts().containsKey("evolve-pk-1-raichu"));
        assertEquals(List.of(), ProgressDesk.check(s.arena.state(), b, MOVES, RULES, s.settings, "pk-1", Trigger.USE_ITEM, Context.none().withUsedItem("fire_stone")));
        assertEquals(List.of(), ProgressDesk.check(s.arena.state(), b, MOVES, RULES, s.settings, "pk-1", Trigger.USE_ITEM, Context.none().withUsedItem("thunder_stone")),
                "the same question is not asked twice");
    }

    @Test
    void usingAMoveAndTravellingAreCounted() {
        SpeciesBook.Species primeape = species("primeape", "Annihilape (Level 1, Use Rage Fist x2)");
        SpeciesBook.Species farfetchd = species("farfetchd", "Sirfetchd (Travel 16 blocks)");
        SpeciesBook b = SpeciesBook.of(List.of(primeape, farfetchd, SpeciesBook.Species.simple("annihilape", "Annihilape", List.of(), STATS, net.quimmilho.pokednd.core.rules.Growth.FAST, 100),
                SpeciesBook.Species.simple("sirfetchd", "Sirfetchd", List.of(), STATS, net.quimmilho.pokednd.core.rules.Growth.FAST, 100)));
        Scene s = new Scene(b);
        s.give(sheet(b, "pk-1", "ana", "primeape", 30, "tackle"));
        s.arena.play(ProgressDesk.moveUsed(s.arena.state(), b, MOVES, RULES, s.settings, "pk-1", "ragefist"));
        assertEquals(1, s.pokemon("pk-1").count("use:ragefist"));
        assertTrue(s.arena.state().prompts().isEmpty());
        s.arena.play(ProgressDesk.moveUsed(s.arena.state(), b, MOVES, RULES, s.settings, "pk-1", "ragefist"));
        assertTrue(s.arena.state().prompts().containsKey("evolve-pk-1-annihilape"), "twice is what the rule asks");

        s.give(sheet(b, "pk-2", "ana", "farfetchd", 10, "tackle"));
        s.arena.play(ProgressDesk.travelled(s.arena.state(), b, MOVES, RULES, s.settings, "pk-2", 1)); // 8 blocks
        assertEquals(8, s.pokemon("pk-2").travelledBlocks());
        assertFalse(s.arena.state().prompts().containsKey("evolve-pk-2-sirfetchd"));
        s.arena.play(ProgressDesk.travelled(s.arena.state(), b, MOVES, RULES, s.settings, "pk-2", 1)); // 16 blocks
        assertTrue(s.arena.state().prompts().containsKey("evolve-pk-2-sirfetchd"));
    }
}
