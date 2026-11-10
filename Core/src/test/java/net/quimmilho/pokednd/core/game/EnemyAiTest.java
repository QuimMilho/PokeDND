/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.ActionKind;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.trainer.KoMode;
import org.junit.jupiter.api.Test;

/**
 * Phase 48: the enemy AI (chapter 7.13): how it scores moves, what each level does, when it aims at a trainer, when an NPC switches, and
 * the property test that it never chooses something that the rules of declaring refuse.
 */
class EnemyAiTest {

    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);

    private Arena arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");

    private Combatant charmander(String... moves) {
        return arena.mon("ana-1", "ana", "Charmander", 20, 50, List.of(PokemonType.FIRE), 30, 28, 32, 30, 60, moves);
    }

    private Combatant bulbasaur(String owner, String id, String... moves) {
        return arena.mon(id, owner, "Bulbasaur", 18, 46, List.of(PokemonType.GRASS), 29, 30, 33, 31, 40, moves);
    }

    private void wildDuel(BattleSettings rules, Combatant wild) {
        arena.start(Battle.Type.WILD, rules, List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Bulbasaur", "wild-1")), List.of(charmander("ember", "tackle"), wild));
        arena.round(ANA_FIRST);
    }

    private static BattleSettings ai(BattleSettings.AiLevel level, int trainerPct) {
        return BattleSettings.defaults().withEnemy(BattleSettings.Enemy.defaults().withAiLevel(level).withTargetTrainerPct(trainerPct));
    }

    /** Small seeds make Random's first numbers alike; the game's seeds are spread out, so the tests spread theirs too. */
    private static Random mixed(long seed) {
        return new Random(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
    }

    private Action chosen(long seed) {
        return EnemyAi.choose(arena.state(), arena.battle(), arena.mon("wild-1"), arena.book, mixed(seed));
    }

    // ================================================================================================================== scoring

    @Test
    void theHardAiAlwaysUsesTheBestMoveAndTheSuperEffectiveOneBeatsAStrongerOne() {
        // against a Fire type: Water Gun (power 40, super effective x2) is worth more than Tackle (40, x1) and Flamethrower (90, not very effective)
        Combatant wild = arena.mon("wild-1", "wild", "Squirtle", 18, 46, List.of(PokemonType.WATER), 29, 30, 33, 31, 40, "tackle", "watergun", "flamethrower");
        wildDuel(ai(BattleSettings.AiLevel.HARD, 0), wild);
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(Action.move("watergun", "ana-1"), chosen(seed), "seed " + seed);
        }
    }

    @Test
    void aMoveThatFinishesTheTargetIsWorthMore() {
        // Ana's Charmander has 5 HP: the weaker Tackle already finishes it, and so does Ember; the 2x Water Gun finishes it too, but a move that cannot
        // hurt it (Fire vs Fire is half) is never preferred
        Combatant wild = arena.mon("wild-1", "wild", "Squirtle", 18, 46, List.of(PokemonType.WATER), 29, 30, 33, 31, 40, "tackle", "ember");
        wildDuel(ai(BattleSettings.AiLevel.HARD, 0), wild);
        arena.play(List.of(new GameEvent.CombatantSet("b1", arena.mon("ana-1").withHp(5))));
        assertEquals("tackle", chosen(1).moveId());
    }

    @Test
    void aStatusMoveOnlyHelpsWhenTheTargetHasNone() {
        Combatant wild = arena.mon("wild-1", "wild", "Bulbasaur", 18, 46, List.of(PokemonType.GRASS), 29, 30, 33, 31, 40, "poisonpowder");
        wildDuel(ai(BattleSettings.AiLevel.HARD, 0), wild);
        assertEquals("poisonpowder", chosen(1).moveId(), "the only move");
        // with the target already poisoned the move is worth nothing, but it is still the only legal move
        arena.play(List.of(new GameEvent.CombatantSet("b1", arena.mon("ana-1").withCondition(arena.mon("ana-1").condition().withStatus(Battle.Status.POISON, 0)))));
        assertEquals("poisonpowder", chosen(1).moveId());
    }

    @Test
    void theEasyAiPlaysAnyLegalMoveAndTheNormalAiMostlyTheBestOnes() {
        Combatant wild = arena.mon("wild-1", "wild", "Squirtle", 18, 46, List.of(PokemonType.WATER), 29, 30, 33, 31, 40, "tackle", "watergun", "flamethrower");
        wildDuel(ai(BattleSettings.AiLevel.EASY, 0), wild);
        Set<String> easy = new LinkedHashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            easy.add(chosen(seed).moveId());
        }
        assertEquals(Set.of("tackle", "watergun", "flamethrower"), easy, "easy tries everything");

        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
        wildDuel(ai(BattleSettings.AiLevel.NORMAL, 0),
                arena.mon("wild-1", "wild", "Squirtle", 18, 46, List.of(PokemonType.WATER), 29, 30, 33, 31, 40, "tackle", "watergun", "flamethrower"));
        Map<String, Integer> normal = new HashMap<>();
        for (long seed = 0; seed < 400; seed++) {
            normal.merge(chosen(seed).moveId(), 1, Integer::sum);
        }
        assertTrue(normal.get("watergun") > 250, "normal usually plays the best move: " + normal);
        assertFalse(normal.containsKey("flamethrower"), "a move far below the best one is never played: " + normal);
    }

    @Test
    void withNoPpLeftTheAiUsesStruggleAndNeverARefusedMove() {
        Combatant wild = arena.mon("wild-1", "wild", "Bulbasaur", 18, 46, List.of(PokemonType.GRASS), 29, 30, 33, 31, 40, "tackle");
        Battle.MoveSlot tackle = wild.moves().get(0);
        wildDuel(BattleSettings.defaults(), wild.withMoves(List.of(new Battle.MoveSlot(tackle.moveId(), tackle.name(), 0, tackle.maxPp(), tackle.priority(), tackle.target()))));
        assertTrue(arena.mon("wild-1").outOfPp());
        assertEquals(Action.struggle("ana-1"), chosen(1));
        arena.play(BattleDesk.declareEnemies(arena.state(), arena.book, arena.seeds)); // and the rules of declaring accept it
    }

    // =========================================================================================================== who the AI chooses for

    @Test
    void theAiChoosesForEnemiesOnlyAndKeepsWhatTheAdminChose() {
        Combatant wild = bulbasaur("wild", "wild-1", "tackle", "vinewhip");
        wildDuel(BattleSettings.defaults(), wild);
        List<GameEvent> events = BattleDesk.declareEnemies(arena.state(), arena.book, arena.seeds);
        assertEquals(1, events.size());
        assertEquals("wild-1", ((GameEvent.ActionDeclared) events.get(0)).actorId());
        arena.play(events);
        assertFalse(arena.battle().declared().containsKey("ana-1"), "a player's Pokémon is never chosen for");
        // the admin takes over: declaring again replaces the AI's choice, and declaring first keeps it
        arena.declare("wild-1", Action.skip());
        assertEquals(List.of(), BattleDesk.declareEnemies(arena.state(), arena.book, arena.seeds));
        assertEquals(Action.skip(), arena.battle().declared().get("wild-1"));
    }

    @Test
    void theSameSeedsGiveTheSameChoices() {
        wildDuel(ai(BattleSettings.AiLevel.NORMAL, 50), bulbasaur("wild", "wild-1", "tackle", "watergun", "ember"));
        arena.play(List.of(new GameEvent.TrainerSet("ana", Fixtures.trainerAt("Ana", 5, 33, 33))));
        List<GameEvent> first = BattleDesk.declareEnemies(arena.state(), arena.book, net.quimmilho.pokednd.core.game.DigitalRoller.seeds(RollSettings.RngSource.SEEDED, 99));
        List<GameEvent> second = BattleDesk.declareEnemies(arena.state(), arena.book, net.quimmilho.pokednd.core.game.DigitalRoller.seeds(RollSettings.RngSource.SEEDED, 99));
        assertEquals(first, second);
    }

    // ============================================================================================================ aiming at a trainer

    @Test
    void aroundOneActionInTenIsAimedAtTheTrainer() {
        wildDuel(BattleSettings.defaults(), bulbasaur("wild", "wild-1", "tackle"));
        int trainers = 0;
        for (long seed = 0; seed < 2000; seed++) {
            Action a = chosen(seed);
            trainers += Battle.isTrainerTarget(a.targets().get(0)) ? 1 : 0;
        }
        assertTrue(trainers > 140 && trainers < 260, "combat.ai.targetTrainerPct is 10: " + trainers + " of 2000");
    }

    @Test
    void thePercentageIsASettingAndZeroNeverAimsAtATrainer() {
        wildDuel(ai(BattleSettings.AiLevel.NORMAL, 100), bulbasaur("wild", "wild-1", "tackle"));
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(Action.move("tackle", Battle.trainerTarget("ana")), chosen(seed));
        }
        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
        wildDuel(ai(BattleSettings.AiLevel.NORMAL, 0), bulbasaur("wild", "wild-1", "tackle"));
        for (long seed = 0; seed < 200; seed++) {
            assertEquals(Action.move("tackle", "ana-1"), chosen(seed));
        }
    }

    @Test
    void anEnemyDoesNotAimAtATrainerWhoIsAlreadyDown() {
        wildDuel(ai(BattleSettings.AiLevel.NORMAL, 100), bulbasaur("wild", "wild-1", "tackle"));
        arena.play(List.of(new GameEvent.TrainerSet("ana", Fixtures.trainerAt("Ana", 5, 33, 0))));
        assertEquals(Action.move("tackle", "ana-1"), chosen(1));
    }

    @Test
    void aStatusMoveIsNeverAimedAtATrainer() {
        wildDuel(ai(BattleSettings.AiLevel.NORMAL, 100), arena.mon("wild-1", "wild", "Bulbasaur", 18, 46, List.of(PokemonType.GRASS), 29, 30, 33, 31, 40, "poisonpowder"));
        assertEquals(Action.move("poisonpowder", "ana-1"), chosen(1));
    }

    // ===================================================================================================================== NPCs

    private Combatant mon(String id, String owner, String name, PokemonType type, int speed, String... moves) {
        return arena.mon(id, owner, name, 18, 46, List.of(type), 29, 30, 33, 31, speed, moves);
    }

    private void npcDuel(BattleSettings.AiLevel level) {
        Participant rival = new Participant("rival", Kind.NPC, 1, "Rival", null, null, List.of("rival-1"), List.of("rival-2"));
        arena.start(Battle.Type.NPC_TRAINER, ai(level, 0), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), rival),
                List.of(arena.mon("ana-1", "ana", "Pikachu", 20, 50, List.of(PokemonType.ELECTRIC), 30, 28, 32, 30, 60, "tackle"),
                        mon("rival-1", "rival", "Squirtle", PokemonType.WATER, 40, "tackle"), mon("rival-2", "rival", "Diglett", PokemonType.GROUND, 40, "tackle")));
        arena.round(Map.of("ana", 12, "rival", 5));
    }

    private Action npcChoice(long seed) {
        return EnemyAi.choose(arena.state(), arena.battle(), arena.mon("rival-1"), arena.book, mixed(seed));
    }

    @Test
    void anNpcOnHardSwitchesOutOfABadMatchup() {
        npcDuel(BattleSettings.AiLevel.HARD);
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(Action.switchTo("rival-2"), npcChoice(seed), "a Water Pokémon against an Electric one, with a Ground Pokémon in the reserve");
        }
    }

    @Test
    void anNpcOnNormalSwitchesAboutHalfOfTheTimeAndOnEasyNever() {
        npcDuel(BattleSettings.AiLevel.NORMAL);
        long switches = java.util.stream.LongStream.range(0, 400).filter(seed -> npcChoice(seed).kind() == ActionKind.SWITCH).count();
        assertTrue(switches > 140 && switches < 260, "about half: " + switches);
        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
        npcDuel(BattleSettings.AiLevel.EASY);
        assertEquals(0, java.util.stream.LongStream.range(0, 200).filter(seed -> npcChoice(seed).kind() == ActionKind.SWITCH).count());
    }

    @Test
    void anNpcStaysInWhenItCanHitBackSuperEffectively() {
        Participant rival = new Participant("rival", Kind.NPC, 1, "Rival", null, null, List.of("rival-1"), List.of("rival-2"));
        arena.start(Battle.Type.NPC_TRAINER, ai(BattleSettings.AiLevel.HARD, 0), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), rival),
                List.of(arena.mon("ana-1", "ana", "Charmander", 20, 50, List.of(PokemonType.WATER), 30, 28, 32, 30, 60, "tackle"),
                        mon("rival-1", "rival", "Pikachu", PokemonType.ELECTRIC, 40, "tackle", "thunderwave", "watergun"), mon("rival-2", "rival", "Diglett", PokemonType.GROUND, 40, "tackle")));
        arena.round(Map.of("ana", 12, "rival", 5));
        assertEquals("rival-1", arena.mon("rival-1").id());
        // Pikachu is not threatened by a Water type (Water vs Electric is half), so it just fights
        assertEquals(ActionKind.MOVE, npcChoice(1).kind());
    }

    @Test
    void aWildPokemonAndABossNeverSwitchEvenWithAReserve() {
        Participant wild = Arena.wildWithReserve("wild", "Wild", List.of("wild-1"), List.of("wild-2"));
        arena.start(Battle.Type.WILD, ai(BattleSettings.AiLevel.HARD, 0), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), wild),
                List.of(arena.mon("ana-1", "ana", "Pikachu", 20, 50, List.of(PokemonType.ELECTRIC), 30, 28, 32, 30, 60, "tackle"),
                        mon("wild-1", "wild", "Squirtle", PokemonType.WATER, 40, "tackle"), mon("wild-2", "wild", "Diglett", PokemonType.GROUND, 40, "tackle")));
        arena.round(ANA_FIRST);
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(ActionKind.MOVE, chosen(seed).kind());
        }
    }

    // ============================================================================================================== the property test

    /** A move list of the test moves that every Pokémon of the random games draws from. */
    private static final List<MoveEntry> MOVES = Moves.all();

    @Test
    void theAiNeverChoosesAnImpossibleActionInRandomBattles() {
        Totals totals = new Totals();
        for (long seed = 0; seed < 250; seed++) {
            playRandom(seed, totals);
        }
        assertTrue(totals.aiActions > 1500, "the AI chose many actions: " + totals.aiActions);
        assertTrue(totals.struggles > 0, "some enemies had no PP left");
        assertTrue(totals.switches > 0, "some NPCs switched");
        assertTrue(totals.trainerTargets > 20, "some enemies aimed at a trainer: " + totals.trainerTargets);
        assertTrue(totals.knockOuts > 0, "some trainers were knocked out");
        assertTrue(totals.settled > 50, "many battles were settled: " + totals.settled);
        assertTrue(totals.fled > 0 && totals.caught > 0, "some trainers fled (" + totals.fled + ") and some Pokémon were caught (" + totals.caught + ")");
    }

    private static final class Totals {
        int aiActions;
        int struggles;
        int switches;
        int trainerTargets;
        int knockOuts;
        int fled;
        int caught;
        int settled;
    }

    private void playRandom(long seed, Totals totals) {
        Random random = new Random(seed);
        Arena a = new Arena(MoveBook.of(Moves.all()), RollSettings.defaults().withSystemRolls(RollSettings.SystemRolls.AUTO, RollSettings.SystemRolls.AUTO), "ana", "bo");
        for (String id : List.of("ana", "bo")) {
            int maxHp = 8 + random.nextInt(40);
            a.play(List.of(new GameEvent.TrainerSet(id, Fixtures.trainerAt(id, 1 + random.nextInt(12), maxHp, 1 + random.nextInt(maxHp)))));
        }
        List<Combatant> mons = new ArrayList<>();
        List<Participant> participants = new ArrayList<>();
        participants.add(team(a, random, mons, "ana", Kind.TRAINER, 0, 1 + random.nextInt(2)));
        if (random.nextBoolean()) {
            participants.add(team(a, random, mons, "bo", Kind.TRAINER, 0, 1 + random.nextInt(2)));
        }
        boolean npc = random.nextBoolean();
        participants.add(team(a, random, mons, npc ? "rival" : "wild", npc ? Kind.NPC : Kind.WILD, 1, 1 + random.nextInt(3)));
        BattleSettings rules = BattleSettings.defaults().withPpEnabled(random.nextInt(5) != 0)
                .withEnemy(new BattleSettings.Enemy(BattleSettings.AiLevel.values()[random.nextInt(3)], random.nextInt(101), 0.25, 20, 4, 8))
                .withKnockOut(new BattleSettings.KnockOutRules(KoMode.values()[random.nextInt(3)], random.nextBoolean(), 10));
        a.start(random.nextBoolean() ? Battle.Type.WILD : Battle.Type.MIXED, rules, participants, mons);

        long digital = seed * 1000;
        for (int round = 1; round <= 30 && !a.battle().over(); round++) {
            replaceFainted(a);
            if (a.battle().over()) {
                break;
            }
            a.play(BattleDesk.openRound(a.state(), a.rolls, Map.of(), Arena.NOW, a.seeds));
            for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(a.state()))) {
                a.play(RollDesk.rollDigital(a.state(), a.rolls, ADMIN, r.requestId(), digital++));
            }
            a.play(BattleDesk.setOrder(a.state(), Arena.NOW, a.seeds));
            for (Combatant c : a.battle().standing()) {
                if (a.battle().ownerOf(c).kind() == Kind.TRAINER) {
                    a.declare(c.id(), playerChoice(random, a.state(), a.battle(), c));
                }
            }
            // the AI: whatever it chooses must be accepted by the rules of declaring
            List<GameEvent> declared = BattleDesk.declareEnemies(a.state(), a.book, a.seeds);
            for (GameEvent e : declared) {
                Action action = ((GameEvent.ActionDeclared) e).action();
                totals.aiActions++;
                totals.struggles += action.kind() == ActionKind.MOVE && Battle.MoveSlot.STRUGGLE.equals(action.moveId()) ? 1 : 0;
                totals.switches += action.kind() == ActionKind.SWITCH ? 1 : 0;
                totals.trainerTargets += action.kind() == ActionKind.MOVE && !action.targets().isEmpty() && Battle.isTrainerTarget(action.targets().get(0)) ? 1 : 0;
            }
            if (!declared.isEmpty()) {
                a.play(declared);
            }
            a.play(BattleDesk.startResolving(a.state()));
            int guard = 0;
            while (!a.battle().queue().isEmpty() || !a.battle().mustSwitch().isEmpty()) {
                assertTrue(++guard < 400, "seed " + seed + ": the round does not move on");
                if (!a.battle().mustSwitch().isEmpty()) {
                    Battle b = a.battle();
                    Participant p = b.ownerOf(b.combatant(b.mustSwitch().get(0)));
                    a.play(BattleDesk.sendReplacement(a.state(), ADMIN, p.id(), p.reserve().stream().filter(id -> !b.combatant(id).fainted()).findFirst().orElseThrow()));
                    continue;
                }
                List<GameEvent> events = BattleDesk.resolveNext(a.state(), a.book, a.rolls, Arena.NOW, a.seeds);
                if (!events.isEmpty()) {
                    a.play(events);
                }
                for (GameState.RollRequest r : new ArrayList<>(BattleDesk.awaiting(a.state()))) {
                    a.play(RollDesk.rollDigital(a.state(), a.rolls, ADMIN, r.requestId(), digital++));
                }
            }
            a.play(BattleDesk.endRound(a.state(), a.seeds));
            for (var t : a.state().trainers().values()) {
                assertTrue(t.hp() >= 0 && t.hp() <= t.maxHp(), "seed " + seed + ": the trainer's HP is in range");
            }
            for (String left : a.battle().left().keySet()) {
                for (String id : concat(a.battle().participants().get(left))) {
                    assertFalse(a.battle().queue().contains(id), "seed " + seed + ": " + id + " left but is still in the queue");
                }
            }
        }
        List<GameEvent> end = BattleDesk.endIfOver(a.state());
        if (!end.isEmpty()) {
            a.play(end);
        }
        totals.knockOuts += (int) a.battle().left().values().stream().filter("knockedOut"::equals).count();
        totals.fled += (int) a.battle().left().values().stream().filter("fled"::equals).count();
        totals.caught += a.battle().caught().size();
        if (a.battle().step() == Battle.Step.ENDED) {
            // the rewards of a random battle are paid without an error (the Pokémon of the game are ad hoc, so there is little XP to pay)
            a.play(AfterBattle.settle(a.state(), ProgressFixtures.book(), a.book, ProgressFixtures.RULES, ProgressSettings.defaults(), EvolutionCheck.Context.none(), 0));
            assertTrue(a.battle().settled(), "seed " + seed);
            totals.settled++;
        }
        assertEquals(a.log.replay(), a.state(), "seed " + seed + ": the log replays to the same state");
        assertEquals(List.of(), BattleDesk.awaiting(a.state()), "seed " + seed + ": no roll is left open");
    }

    private static List<String> concat(Participant p) {
        List<String> all = new ArrayList<>(p.active());
        all.addAll(p.reserve());
        return all;
    }

    private Participant team(Arena a, Random random, List<Combatant> mons, String id, Kind kind, int side, int size) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String monId = id + "-" + i;
            ids.add(monId);
            LinkedHashSet<String> chosen = new LinkedHashSet<>();
            for (int k = 0, n = 1 + random.nextInt(4); k < n; k++) {
                chosen.add(MOVES.get(random.nextInt(MOVES.size())).id());
            }
            PokemonType type = PokemonType.values()[random.nextInt(PokemonType.values().length)];
            Combatant c = a.mon(monId, id, "Mon " + monId, 5 + random.nextInt(50), 10 + random.nextInt(100), List.of(type), 10 + random.nextInt(100),
                    10 + random.nextInt(100), 10 + random.nextInt(100), 10 + random.nextInt(100), 10 + random.nextInt(100), chosen.toArray(String[]::new));
            if (random.nextInt(4) == 0) {
                c = c.withMoves(c.moves().stream().map(m -> random.nextInt(2) == 0 ? new Battle.MoveSlot(m.moveId(), m.name(), 0, m.maxPp(), m.priority(), m.target()) : m).toList());
            }
            if (random.nextInt(5) == 0) {
                c = Arena.holding(c, "choice_band");
            }
            mons.add(c.withBuild(c.build().withSpecies("species" + monId, 3 + random.nextInt(250))));
        }
        return switch (kind) {
            case TRAINER -> new Participant(id, kind, side, id, id, "u-" + id, ids.subList(0, 1), ids.subList(1, ids.size()));
            case NPC -> new Participant(id, kind, side, id, null, null, ids.subList(0, 1), ids.subList(1, ids.size()));
            case WILD -> new Participant(id, kind, side, id, null, null, ids.subList(0, 1), ids.subList(1, ids.size()));
        };
    }

    private void replaceFainted(Arena a) {
        for (String participantId : new ArrayList<>(a.battle().participants().keySet())) {
            for (int i = 0; i < 3; i++) {
                Battle b = a.battle();
                Participant p = b.participants().get(participantId);
                if (b.left().containsKey(participantId)) {
                    break;
                }
                boolean down = p.active().stream().anyMatch(id -> b.combatant(id).fainted());
                String in = p.reserve().stream().filter(r -> !b.combatant(r).fainted()).findFirst().orElse(null);
                if (down && in != null) {
                    a.play(BattleDesk.sendReplacement(a.state(), ADMIN, participantId, in));
                }
            }
        }
    }

    /** A legal action of a player's Pokémon: a move, a switch, a ball at a wild Pokémon, running away, or nothing. */
    private Action playerChoice(Random random, GameState state, Battle b, Combatant c) {
        List<Action> options = new ArrayList<>();
        options.add(Action.skip());
        Participant owner = b.ownerOf(c);
        owner.reserve().stream().filter(id -> !b.combatant(id).fainted()).forEach(id -> options.add(Action.switchTo(id)));
        String locked = b.choiceLockedMove(c);
        for (Battle.MoveSlot m : c.moves()) {
            if (b.rules().ppEnabled() && m.pp() <= 0 || locked != null && !m.moveId().equals(locked)) {
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
        if (b.rules().ppEnabled() && c.outOfPp()) {
            List<String> foes = b.legalTargets(c, TargetKind.FOE);
            if (!foes.isEmpty()) {
                options.add(Action.struggle(foes.get(0)));
            }
        }
        List<Combatant> catchable = b.standing().stream().filter(t -> b.ownerOf(t).kind() == Kind.WILD && !t.boss() && b.sideOf(t) != owner.side()).toList();
        if (!catchable.isEmpty() && state.trainers().get(owner.trainerId()).items().getOrDefault("poke_ball", 0) > 0) {
            options.add(Action.ball("poke_ball", catchable.get(0).id()));
            options.add(Action.ball("poke_ball", catchable.get(0).id()));
        }
        boolean trainerFoe = b.participants().values().stream().anyMatch(p -> p.side() != owner.side() && p.kind() != Kind.WILD && !b.out(p));
        if (!trainerFoe) {
            options.add(Action.flee());
        }
        return options.get(random.nextInt(options.size()));
    }
}
