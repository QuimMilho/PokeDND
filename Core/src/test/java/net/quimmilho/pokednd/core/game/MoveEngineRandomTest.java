/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.Test;

/**
 * Battles with random Pokémon and random moves, played to the end with digital dice. Whatever the dice show, the engine must never throw
 * (a move it cannot run becomes a task), never leave a roll open, keep every number in range, and give the same state again when the log
 * is replayed (this is what undo and the server rely on).
 */
class MoveEngineRandomTest {

    private static final int GAMES = 250;
    private static final PokemonType[] TYPES = PokemonType.values();

    @Test
    void randomBattlesNeverBreakTheEngineAndAlwaysReplay() {
        int battlesEnded = 0;
        int tasks = 0;
        int moves = 0;
        for (long seed = 0; seed < GAMES; seed++) {
            Played played = play(seed);
            battlesEnded += played.ended ? 1 : 0;
            tasks += played.tasks;
            moves += played.moves;
        }
        assertTrue(battlesEnded > GAMES / 2, "most battles reach an end: " + battlesEnded);
        assertTrue(moves > 2000, "the games use many moves: " + moves);
        assertTrue(tasks > 0, "some moves are left to the admin");
    }

    private record Played(boolean ended, int tasks, int moves) {
    }

    private Played play(long seed) {
        Random random = new Random(seed);
        List<MoveEntry> entries = Moves.all();
        MoveBook book = MoveBook.of(entries, List.of(Moves.berry("oran_berry", 80, "Poison")));
        Arena arena = new Arena(book, RollSettings.defaults().withSystemRolls(RollSettings.SystemRolls.AUTO, RollSettings.SystemRolls.AUTO), "ana", "bo");

        List<Combatant> mons = new ArrayList<>();
        List<String> anaTeam = new ArrayList<>();
        List<String> boTeam = new ArrayList<>();
        List<String> wildTeam = new ArrayList<>();
        for (int i = 0; i < 2 + random.nextInt(2); i++) {
            mons.add(mon(arena, random, "ana-" + i, "ana"));
            anaTeam.add("ana-" + i);
        }
        boolean twoTrainers = random.nextBoolean();
        if (twoTrainers) {
            mons.add(mon(arena, random, "bo-0", "bo"));
            boTeam.add("bo-0");
        }
        for (int i = 0; i < 1 + random.nextInt(3); i++) {
            mons.add(mon(arena, random, "wild-" + i, "wild"));
            wildTeam.add("wild-" + i);
        }
        List<Participant> participants = new ArrayList<>();
        participants.add(Arena.trainer("ana", anaTeam.subList(0, 1), anaTeam.subList(1, anaTeam.size())));
        if (twoTrainers) {
            participants.add(Arena.trainer("bo", boTeam, List.of()));
        }
        participants.add(Arena.wildWithReserve("wild", "Wild", wildTeam.subList(0, 1), wildTeam.subList(1, wildTeam.size())));
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), participants, mons);

        int usedMoves = 0;
        for (int round = 1; round <= 40 && !arena.battle().over(); round++) {
            replaceFainted(arena);
            if (arena.battle().over()) {
                break;
            }
            arena.play(BattleDesk.openRound(arena.state(), arena.rolls, java.util.Map.of(), Arena.NOW, arena.seeds));
            long digital = seed * 1000 + round * 7;
            for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(arena.state()))) {
                arena.play(RollDesk.rollDigital(arena.state(), arena.rolls, ADMIN, r.requestId(), digital++));
            }
            arena.play(BattleDesk.setOrder(arena.state(), Arena.NOW, arena.seeds));
            for (Combatant c : arena.battle().standing()) {
                Action a = choose(random, arena.battle(), c);
                usedMoves += a.kind() == Battle.ActionKind.MOVE ? 1 : 0;
                arena.declare(c.id(), a);
            }
            arena.play(BattleDesk.startResolving(arena.state()));
            int guard = 0;
            while (!arena.battle().queue().isEmpty() || !arena.battle().mustSwitch().isEmpty()) {
                assertTrue(++guard < 400, "seed " + seed + ": the round does not move on");
                if (!arena.battle().mustSwitch().isEmpty()) {
                    switchOut(arena);
                    continue;
                }
                List<GameEvent> events = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
                assertTrue(!events.isEmpty() || !BattleDesk.awaiting(arena.state()).isEmpty(), "seed " + seed + ": nothing happens and nothing is asked; queue "
                        + arena.battle().queue() + ", pending " + arena.state().pendingRolls().keySet() + ", last line: " + arena.lastLine());
                if (!events.isEmpty()) {
                    arena.play(events);
                }
                for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(arena.state()))) {
                    arena.play(RollDesk.rollDigital(arena.state(), arena.rolls, ADMIN, r.requestId(), digital++));
                }
            }
            arena.play(BattleDesk.endRound(arena.state()));
            check(arena, seed);
        }
        List<GameEvent> end = BattleDesk.endIfOver(arena.state());
        if (!end.isEmpty()) {
            arena.play(end);
        }
        check(arena, seed);
        assertEquals(arena.log.replay(), arena.state(), "seed " + seed + ": the log replays to the same state");
        assertEquals(List.of(), BattleDesk.awaiting(arena.state()), "seed " + seed + ": no roll is left open");
        return new Played(arena.battle().step() == Battle.Step.ENDED, arena.battle().tasks().size(), usedMoves);
    }

    private static void check(Arena arena, long seed) {
        for (Combatant c : arena.battle().combatants().values()) {
            assertTrue(c.hp() >= 0 && c.hp() <= c.maxHp(), "seed " + seed + ": HP in range for " + c.id());
            for (String stat : Battle.STAGE_IDS) {
                assertTrue(Math.abs(c.condition().stage(stat)) <= 6, "seed " + seed + ": stages stay within 6");
            }
            if (c.fainted()) {
                assertEquals(Battle.Status.NONE, c.status(), "seed " + seed + ": a fainted Pokémon has no status");
            }
        }
    }

    private static void switchOut(Arena arena) {
        Battle b = arena.battle();
        String out = b.mustSwitch().get(0);
        Participant p = b.ownerOf(b.combatant(out));
        String in = p.reserve().stream().filter(id -> !b.combatant(id).fainted()).findFirst().orElseThrow();
        arena.play(BattleDesk.sendReplacement(arena.state(), ADMIN, p.id(), in));
    }

    private static void replaceFainted(Arena arena) {
        for (String participantId : new ArrayList<>(arena.battle().participants().keySet())) {
            for (int i = 0; i < 3; i++) {
                Battle b = arena.battle();
                Participant p = b.participants().get(participantId);
                boolean down = p.active().stream().anyMatch(id -> b.combatant(id).fainted());
                String in = p.reserve().stream().filter(r -> !b.combatant(r).fainted()).findFirst().orElse(null);
                if (down && in != null) {
                    arena.play(BattleDesk.sendReplacement(arena.state(), ADMIN, participantId, in));
                }
            }
        }
    }

    private static Combatant mon(Arena arena, Random random, String id, String owner) {
        List<MoveEntry> all = Moves.all();
        java.util.LinkedHashSet<String> chosen = new java.util.LinkedHashSet<>();
        for (int i = 0, n = 1 + random.nextInt(4); i < n; i++) {
            chosen.add(all.get(random.nextInt(all.size())).id());
        }
        String[] ids = chosen.toArray(String[]::new);
        PokemonType first = TYPES[random.nextInt(TYPES.length)];
        List<PokemonType> types = random.nextBoolean() ? List.of(first) : List.of(first, TYPES[random.nextInt(TYPES.length)]);
        if (types.size() == 2 && types.get(0) == types.get(1)) {
            types = List.of(first);
        }
        Combatant c = arena.mon(id, owner, "Mon " + id, 5 + random.nextInt(60), 10 + random.nextInt(120), types, 10 + random.nextInt(100),
                10 + random.nextInt(100), 10 + random.nextInt(100), 10 + random.nextInt(100), 10 + random.nextInt(120), ids);
        String gender = new String[] {"M", "F", "N"}[random.nextInt(3)];
        Combatant withBuild = c.withBuild(c.build().withGender(gender).withFriendship(random.nextInt(256)).withHiddenPower(TYPES[random.nextInt(TYPES.length)]));
        return random.nextInt(3) == 0 ? withBuild.withCondition(withBuild.condition().withHeldItem("oran_berry")) : withBuild;
    }

    private static Action choose(Random random, Battle b, Combatant c) {
        List<Action> options = new ArrayList<>();
        options.add(Action.skip());
        Battle.Participant owner = b.ownerOf(c);
        owner.reserve().stream().filter(id -> !b.combatant(id).fainted()).forEach(id -> options.add(Action.switchTo(id)));
        for (Battle.MoveSlot m : c.moves()) {
            if (m.pp() <= 0) {
                continue;
            }
            TargetKind kind = m.targetKind();
            if (kind.choosesOne()) {
                List<String> legal = b.legalTargets(c, kind);
                if (!legal.isEmpty()) {
                    options.add(Action.move(m.moveId(), legal.get(random.nextInt(legal.size()))));
                }
            } else {
                options.add(Action.move(m.moveId()));
            }
        }
        if (c.outOfPp()) {
            List<String> foes = b.legalTargets(c, TargetKind.FOE);
            if (!foes.isEmpty()) {
                options.add(Action.struggle(foes.get(0)));
            }
        }
        // moves are the common case
        Action pick = options.get(random.nextInt(options.size()));
        return options.size() > 2 && pick.kind() != Battle.ActionKind.MOVE && random.nextInt(3) != 0 ? options.get(2 + random.nextInt(options.size() - 2)) : pick;
    }
}
