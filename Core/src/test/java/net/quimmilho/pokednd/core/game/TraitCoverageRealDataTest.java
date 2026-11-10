/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.importer.Bundle;
import net.quimmilho.pokednd.core.importer.Catalog.AbilityEntry;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 47 on the real data (chapter 19.6): the lists of abilities and held items that Core runs match the code and the coverage report,
 * and every ability and every item of the game can be on the field in a battle without an error; the ones Core does not run leave one
 * task for the admin. Skipped when {@code resources/} or {@code build/roster.json} are missing, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class TraitCoverageRealDataTest {

    private static Bundle bundle;
    private static MoveBook book;
    private static Path coverageJson;

    @BeforeAll
    static void load() {
        Path resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        Path roster = Path.of(System.getProperty("pokednd.roster", "../build/roster.json"));
        boolean there = Files.isRegularFile(resources.resolve("pokedex.csv")) && Files.isRegularFile(roster);
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ or build/roster.json is missing but POKEDND_REQUIRE_RESOURCES=true");
        }
        assumeTrue(there, "resources/ or build/roster.json is not built");
        bundle = Bundle.build(resources, roster);
        book = MoveBook.of(bundle.catalog().moves(), bundle.catalog().items(), bundle.catalog().abilities());
        coverageJson = roster.resolveSibling("reports/coverage.json");
    }

    @Test
    void theListsOfAbilitiesAndItemsMatchTheCodeAndTheData() {
        MoveBook.Implemented list = MoveBook.implementedEffects();
        Map<String, AbilityEntry> abilities = bundle.catalog().abilities().stream().collect(Collectors.toMap(AbilityEntry::id, a -> a));
        for (String id : list.abilities()) {
            assertTrue(abilities.containsKey(id), id + " is an ability of the data");
            assertEquals(EffectClass.SCRIPTED, abilities.get(id).effectClass(), id + " is scripted in the data");
            assertEquals(MoveBook.Handling.IMPLEMENTED, book.abilityHandling(id), id);
        }
        for (String id : list.abilitiesNoEffect()) {
            if (abilities.containsKey(id)) {
                assertEquals(EffectClass.SCRIPTED, abilities.get(id).effectClass(), id + " only matters in weather, so it is scripted");
            }
            assertEquals(MoveBook.Handling.AUTOMATIC, book.abilityHandling(id), id);
        }
        assertEquals(AbilityScripts.implemented(), list.abilities(), "the code and the list agree");
        assertEquals(25, list.abilities().size());

        Map<String, ItemEntry> items = bundle.catalog().items().stream().collect(Collectors.toMap(ItemEntry::id, i -> i, (a, b) -> a));
        for (String id : list.items()) {
            assertTrue(items.containsKey(id), id + " is an item of the data");
            assertEquals(MoveBook.Handling.IMPLEMENTED, book.itemHandling(id), id);
        }
        assertEquals(ItemScripts.implemented(), list.items().stream().map(Battle::squash).collect(Collectors.toSet()), "the code and the list agree");
    }

    @Test
    void theNumbersOfTheCoverageReportAreTheOnesOfTheCode() throws IOException {
        assumeTrue(Files.isRegularFile(coverageJson), "build/reports/coverage.json is not built (node tools/effect-coverage.mjs)");
        MoveBook.Implemented list = MoveBook.implementedEffects();
        JsonNode report = new ObjectMapper().readTree(Files.readString(coverageJson));
        Set<String> reported = new TreeSet<>();
        report.get("implementedAbilities").forEach(n -> reported.add(n.asText()));
        assertEquals(new TreeSet<>(list.abilities()), reported, "all 25 are used by playable species");
        Set<String> weather = new TreeSet<>();
        report.get("noEffectAbilities").forEach(n -> weather.add(n.asText()));
        assertTrue(list.abilitiesNoEffect().containsAll(weather));
        Set<String> items = new TreeSet<>();
        report.get("implementedItems").forEach(n -> items.add(n.asText()));
        assertEquals(new TreeSet<>(list.items()), items);

        JsonNode ab = report.get("summary").get("abilities");
        assertEquals(25, ab.get("implemented").asInt());
        assertEquals(ab.get("slotsAutomatic").asInt() + ab.get("slotsImplemented").asInt() + ab.get("slotsNoEffect").asInt(), ab.get("slotsCovered").asInt());
        double covered = ab.get("slotsCovered").asDouble() / ab.get("slots").asDouble();
        assertTrue(covered > 0.44 && covered < 0.48, "the species-ability slots that work without the admin: " + covered + " (about 46 %, chapter 19.6)");
        assertEquals(list.items().size(), report.get("summary").get("items").get("implemented").asInt());
    }

    /** Every ability and every item of the game on a Pokémon in a battle, with digital dice: no error, a log that replays, nothing left open. */
    @Test
    void everyAbilityAndEveryItemCanBeOnTheFieldWithoutAnError() {
        int abilities = 0;
        int items = 0;
        for (AbilityEntry a : bundle.catalog().abilities()) {
            Arena arena = battle(a.id(), null, "ability " + a.id());
            MoveBook.Handling h = book.abilityHandling(a.id());
            long tasks = arena.battle().tasks().stream().filter(t -> t.moveId().equals("ability:" + a.id())).count();
            assertEquals(h == MoveBook.Handling.MANUAL ? 1 : 0, tasks, a.id() + " is " + h);
            abilities++;
        }
        for (ItemEntry item : bundle.catalog().items()) {
            Arena arena = battle("sturdy", item.id(), "item " + item.id());
            MoveBook.Handling h = book.itemHandling(item.id());
            long tasks = arena.battle().tasks().stream().filter(t -> t.moveId().equals("item:" + Battle.squash(item.id()))).count();
            assertEquals(h == MoveBook.Handling.MANUAL ? 1 : 0, tasks, item.id() + " is " + h);
            items++;
        }
        assertTrue(abilities > 200, "abilities used: " + abilities);
        assertTrue(items > 380, "items used: " + items);
    }

    /** Ana's Charmander (with this ability and item) and a wild Rattata fight one round; the foe is a Charmander with Intimidate. */
    private static Arena battle(String ability, String item, String what) {
        try {
            Arena arena = new Arena(book, RollSettings.defaults().withSystemRolls(RollSettings.SystemRolls.AUTO, RollSettings.SystemRolls.AUTO), "ana");
            Combatant ana = arena.mon("ana-1", "ana", "Charmander", 30, 90, List.of(PokemonType.FIRE), 60, 50, 64, 55, 60, "ember", "tackle");
            Combatant ally = arena.mon("ana-2", "ana", "Squirtle", 30, 90, List.of(PokemonType.WATER), 50, 60, 50, 60, 40, "tackle");
            Combatant wild = arena.mon("wild-1", "wild", "Rattata", 30, 90, List.of(PokemonType.NORMAL), 55, 50, 40, 50, 40, "tackle");
            ana = Arena.withAbility(ana, ability);
            if (item != null) {
                ana = Arena.holding(ana, item);
            }
            arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                    List.of(Arena.trainer("ana", List.of("ana-1"), List.of("ana-2")), Arena.wild("wild", "Rattata", "wild-1")), List.of(ana, ally, wild));
            arena.play(BattleDesk.openRound(arena.state(), arena.rolls, Map.of(), Arena.NOW, arena.seeds));
            for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(arena.state()))) {
                arena.play(RollDesk.rollDigital(arena.state(), arena.rolls, ADMIN, r.requestId(), 11));
            }
            arena.play(BattleDesk.setOrder(arena.state(), Arena.NOW, arena.seeds));
            arena.declare("ana-1", Action.move("ember", "wild-1"));
            arena.declare("wild-1", Action.move("tackle", "ana-1"));
            arena.play(BattleDesk.startResolving(arena.state()));
            long digital = 100;
            int guard = 0;
            while (!arena.battle().queue().isEmpty()) {
                assertTrue(++guard < 100, "the round does not move on");
                List<GameEvent> events = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
                if (!events.isEmpty()) {
                    arena.play(events);
                }
                for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(arena.state()))) {
                    arena.play(RollDesk.rollDigital(arena.state(), arena.rolls, ADMIN, r.requestId(), digital++));
                }
            }
            arena.play(BattleDesk.endRound(arena.state(), arena.seeds));
            assertEquals(arena.log.replay(), arena.state(), "the log replays");
            assertEquals(List.of(), BattleDesk.awaiting(arena.state()), "no roll is left open");
            return arena;
        } catch (RuntimeException | AssertionError e) {
            throw new AssertionError(what + " failed: " + e, e);
        }
    }
}
