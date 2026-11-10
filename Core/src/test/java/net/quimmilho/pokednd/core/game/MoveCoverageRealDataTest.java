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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.importer.Bundle;
import net.quimmilho.pokednd.core.importer.Catalog;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 46 on the real move data (chapter 19.6): the list of moves that Core runs matches the coverage report, every move of the game
 * can be used in a battle without an error, and a move that Core does not run becomes a task for the admin. Skipped when
 * {@code resources/} or {@code build/roster.json} are missing, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class MoveCoverageRealDataTest {

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
        book = MoveBook.of(bundle.catalog().moves(), bundle.catalog().items());
        coverageJson = roster.resolveSibling("reports/coverage.json");
    }

    @Test
    void everyMoveOnTheImplementedListIsAScriptedMoveThatCoreHasCodeFor() {
        MoveBook.Implemented list = MoveBook.implementedEffects();
        Set<String> scripted = new TreeSet<>();
        for (MoveEntry m : bundle.catalog().moves()) {
            if (m.effectClass() == EffectClass.SCRIPTED) {
                scripted.add(m.id());
            }
        }
        for (String id : list.moves()) {
            assertTrue(scripted.contains(id), id + " is a scripted move in the data");
            assertTrue(MoveScripts.knows(id) || book.handling(book.entry(id).orElseThrow()) == MoveBook.Handling.IMPLEMENTED,
                    id + " has code in MoveScripts or runs through a shared condition");
        }
        // and the other way round: nothing in the code is missing from the list
        for (String id : scripted) {
            if (MoveScripts.knows(id)) {
                assertTrue(list.moves().contains(id), id + " has code in MoveScripts but is not on the list");
            }
        }
        assertEquals(26, list.moves().size(), "the 25 most-learned scripted moves of the first measurement, and Struggle");
    }

    @Test
    void theCountsOfWhoRunsWhatAreTheOnesOfTheCoverageReport() throws IOException {
        Map<MoveBook.Handling, Integer> counts = new EnumMap<>(MoveBook.Handling.class);
        List<String> implemented = new ArrayList<>();
        for (MoveEntry m : bundle.catalog().moves()) {
            MoveBook.Handling h = book.handling(m);
            counts.merge(h, 1, Integer::sum);
            if (h == MoveBook.Handling.IMPLEMENTED) {
                implemented.add(m.id());
            }
        }
        assertEquals(445, counts.get(MoveBook.Handling.AUTOMATIC));
        assertEquals(34, counts.get(MoveBook.Handling.IMPLEMENTED), "26 written in Java (with Struggle) and 8 more that only set the recharge condition");
        assertEquals(363, counts.get(MoveBook.Handling.PARTIAL));
        assertEquals(87, counts.get(MoveBook.Handling.MANUAL), "the Z- and Max-moves");

        assumeTrue(Files.isRegularFile(coverageJson), "build/reports/coverage.json is not built (node tools/effect-coverage.mjs)");
        JsonNode report = new ObjectMapper().readTree(Files.readString(coverageJson));
        List<String> reported = new ArrayList<>();
        report.get("implementedMoves").forEach(n -> reported.add(n.asText()));
        implemented.sort(null);
        assertEquals(implemented, reported, "the report lists the same moves as the code");
        JsonNode summary = report.get("summary");
        assertEquals(34, summary.get("moves").get("implemented").asInt());
        assertEquals(363, summary.get("moves").get("still scripted").asInt());
        JsonNode rows = summary.get("learnsetRowsPlayable");
        double covered = rows.get("withImplemented").asDouble() / rows.get("total").asDouble();
        assertTrue(covered >= 0.70 && covered < 0.74, "the learnset rows covered by automatic and implemented moves: " + covered + " (about 71 %, chapter 19.6)");
    }

    /** Every move of the game is used once in a battle, with digital dice: no error, a log that replays, and nothing left open. */
    @Test
    void everyMoveOfTheGameCanBeUsedWithoutAnError() {
        int used = 0;
        int tasks = 0;
        for (MoveEntry m : bundle.catalog().moves()) {
            if (m.id().equals(Battle.MoveSlot.STRUGGLE)) {
                continue; // it cannot be chosen: a Pokémon uses it when no move has PP left (see MoveEffectsTest)
            }
            try {
                Arena arena = new Arena(book, RollSettings.defaults().withSystemRolls(RollSettings.SystemRolls.AUTO, RollSettings.SystemRolls.AUTO), "ana", "bo");
                Combatant ana = arena.mon("ana-1", "ana", "Charmander", 30, 90, List.of(PokemonType.FIRE), 60, 50, 64, 55, 60, m.id(), "tackle");
                Combatant ally = arena.mon("bo-1", "bo", "Squirtle", 30, 90, List.of(PokemonType.WATER), 50, 60, 50, 60, 40, "tackle");
                Combatant wild = arena.mon("wild-1", "wild", "Rattata", 30, 90, List.of(PokemonType.NORMAL), 55, 50, 40, 50, 40, "tackle");
                arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                        List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.trainer("bo", List.of("bo-1"), List.of()),
                                Arena.wildWithReserve("wild", "Rattata", List.of("wild-1"), List.of("wild-2"))),
                        List.of(ana.withCondition(ana.condition().withHeldItem("oran_berry")), ally, wild,
                                arena.mon("wild-2", "wild", "Pidgey", 30, 90, List.of(PokemonType.NORMAL, PokemonType.FLYING), 50, 50, 50, 50, 70, "tackle")));
                arena.play(BattleDesk.openRound(arena.state(), arena.rolls, Map.of(), Arena.NOW, arena.seeds));
                for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(arena.state()))) {
                    arena.play(RollDesk.rollDigital(arena.state(), arena.rolls, ADMIN, r.requestId(), 11));
                }
                arena.play(BattleDesk.setOrder(arena.state(), Arena.NOW, arena.seeds));
                Battle b = arena.battle();
                Battle.MoveSlot slot = b.combatant("ana-1").moves().get(0);
                TargetKind kind = slot.targetKind();
                Action action;
                if (kind.choosesOne()) {
                    List<String> legal = b.legalTargets(b.combatant("ana-1"), kind);
                    action = Action.move(slot.moveId(), legal.get(0));
                } else {
                    action = Action.move(slot.moveId());
                }
                arena.declare("ana-1", action);
                arena.declare("bo-1", Action.skip());
                arena.declare("wild-1", Action.move("tackle", "ana-1"));
                arena.play(BattleDesk.startResolving(arena.state()));
                long digital = 100;
                int guard = 0;
                while (!arena.battle().queue().isEmpty() || !arena.battle().mustSwitch().isEmpty()) {
                    assertTrue(++guard < 100, "the round does not move on");
                    if (!arena.battle().mustSwitch().isEmpty()) {
                        Battle now = arena.battle();
                        String out = now.mustSwitch().get(0);
                        Battle.Participant p = now.ownerOf(now.combatant(out));
                        arena.play(BattleDesk.sendReplacement(arena.state(), ADMIN, p.id(), p.reserve().get(0)));
                        continue;
                    }
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
                used++;
                tasks += arena.battle().tasks().size();
                if (book.handling(m) == MoveBook.Handling.MANUAL || book.handling(m) == MoveBook.Handling.PARTIAL) {
                    assertEquals(1, arena.battle().tasks().stream().filter(t -> t.moveId().equals(m.id())).count(), m.id() + " leaves a task for the admin");
                } else {
                    assertEquals(0, arena.battle().tasks().stream().filter(t -> t.moveId().equals(m.id())).count(), m.id() + " needs no task");
                }
            } catch (RuntimeException | AssertionError e) {
                throw new AssertionError("the move " + m.id() + " (" + book.handling(m) + ") failed: " + e, e);
            }
        }
        assertTrue(used > 900, "all moves were used: " + used);
        assertTrue(tasks > 400, "the moves Core does not run left tasks: " + tasks);
        assertTrue(bundle.catalog().moves().stream().anyMatch(m -> m.effectClass() == EffectClass.MANUAL), Catalog.class.getSimpleName());
    }
}
