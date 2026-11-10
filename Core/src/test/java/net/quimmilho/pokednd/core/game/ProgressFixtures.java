/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.importer.EvolutionParser;
import net.quimmilho.pokednd.core.importer.Evolutions;
import net.quimmilho.pokednd.core.importer.Learnsets.LevelMove;
import net.quimmilho.pokednd.core.rules.Growth;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.StatBlock;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;

/** A few species and Pokémon for the tests of phase 49 (experience, levels, evolution, friendship, fainting and PvP). */
final class ProgressFixtures {

    static final MoveBook MOVES = MoveBook.of(Moves.all());
    static final TrainerRulebook RULES = TrainerRulebook.defaults();

    private ProgressFixtures() {
    }

    /** The evolution rule written the way the data writes it: {@code rule("bulbasaur", "Ivysaur (Level 16)")}. */
    static Evolutions.Rule rule(String from, String text) {
        EvolutionParser.ParsedRule p = EvolutionParser.parse(text).rules().get(0);
        return new Evolutions.Rule(0, from, 0, p.target().toLowerCase(), "", p.method(), p.item() == null ? null : Battle.squash(p.item()), p.conditions(), p.handling(), text);
    }

    static SpeciesBook.Species bulbasaur() {
        return new SpeciesBook.Species("bulbasaur", "Bulbasaur", List.of(PokemonType.GRASS, PokemonType.POISON), new StatBlock(45, 49, 49, 65, 65, 45), Growth.MEDIUM_SLOW, 64,
                new StatBlock(0, 0, 0, 1, 0, 0), 45, 50, List.of("overgrow"), "chlorophyll",
                List.of(new LevelMove(1, "tackle"), new LevelMove(3, "growl"), new LevelMove(11, "ember"), new LevelMove(11, "scratch")),
                List.of(rule("bulbasaur", "Ivysaur (Level 16)")));
    }

    static SpeciesBook.Species ivysaur() {
        return new SpeciesBook.Species("ivysaur", "Ivysaur", List.of(PokemonType.GRASS, PokemonType.POISON), new StatBlock(60, 62, 63, 80, 80, 60), Growth.MEDIUM_SLOW, 142,
                new StatBlock(0, 0, 0, 1, 1, 0), 45, 50, List.of("overgrow"), "chlorophyll", List.of(), List.of(rule("ivysaur", "Venusaur (Level 32)")));
    }

    static SpeciesBook.Species venusaur() {
        return SpeciesBook.Species.simple("venusaur", "Venusaur", List.of(PokemonType.GRASS, PokemonType.POISON), new StatBlock(80, 82, 83, 100, 100, 80), Growth.MEDIUM_SLOW, 236);
    }

    static SpeciesBook.Species pidgey() {
        return SpeciesBook.Species.simple("pidgey", "Pidgey", List.of(PokemonType.NORMAL, PokemonType.FLYING), new StatBlock(40, 45, 40, 35, 35, 56), Growth.MEDIUM_SLOW, 50)
                .withEvYield(new StatBlock(0, 0, 0, 0, 0, 1)).withAbilities(List.of("keeneye", "tangledfeet"), "bigpecks");
    }

    static SpeciesBook.Species rattata() {
        return SpeciesBook.Species.simple("rattata", "Rattata", List.of(PokemonType.NORMAL), new StatBlock(30, 56, 35, 25, 35, 72), Growth.MEDIUM_FAST, 51);
    }

    static SpeciesBook book(SpeciesBook.Species... more) {
        List<SpeciesBook.Species> all = new ArrayList<>(List.of(bulbasaur(), ivysaur(), venusaur(), pidgey(), rattata()));
        all.addAll(List.of(more));
        return SpeciesBook.of(all);
    }

    static MoveSlot slot(String id) {
        return MOVES.slot(id).orElseThrow();
    }

    /** A Pokémon with zero IVs and EVs and a neutral nature, at the XP its growth group needs for that level, at full HP. */
    static PokemonSheet sheet(SpeciesBook book, String id, String owner, String speciesId, int level, String... moves) {
        SpeciesBook.Species sp = book.species(speciesId).orElseThrow();
        List<MoveSlot> list = new ArrayList<>();
        for (String m : moves) {
            list.add(slot(m));
        }
        PokemonSheet s = PokemonSheet.of(id, owner, speciesId, level, sp.growth().totalXp(level), 1, list);
        s = new PokemonSheet(s.id(), s.ownerId(), s.speciesId(), sp.name(), s.level(), s.xp(), "hardy", s.ivs(), s.evs(), "N", false,
                sp.abilities().isEmpty() ? null : sp.abilities().get(0), null, s.moves(), 1, s.status(), s.friendship(), s.location(), null, 0, s.counters(), 0, null);
        return s.withHp(BattleSetup.maxHp(s, sp, level));
    }

    static Combatant combatant(PokemonSheet s, String participant, SpeciesBook book) {
        return BattleSetup.combatant(s, participant, book, MOVES);
    }

    /**
     * A game with trainer Ana (and Bo), a battle, and the calls that end and settle it. The Pokémon are real {@link PokemonSheet}s; the
     * battle fights with copies made by {@link BattleSetup}.
     */
    static final class Scene {
        final Arena arena = new Arena(MOVES, "ana", "bo");
        final SpeciesBook book;
        ProgressSettings settings = ProgressSettings.defaults();
        EvolutionCheck.Context context = EvolutionCheck.Context.none();

        Scene() {
            this(ProgressFixtures.book());
        }

        Scene(SpeciesBook book) {
            this.book = book;
        }

        PokemonSheet give(PokemonSheet sheet) {
            arena.play(List.of(new GameEvent.PokemonSet(sheet)));
            return sheet;
        }

        PokemonSheet pokemon(String id) {
            return arena.state().pokemon().get(id);
        }

        /** Ana (first Pokémon active, the rest in the reserve) against one wild Pokémon, which is given to the game as a wild sheet. */
        void wild(PokemonSheet wild, PokemonSheet... ana) {
            give(wild);
            List<Combatant> all = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (PokemonSheet p : ana) {
                give(p);
                all.add(combatant(p, "ana", book));
                ids.add(p.id());
            }
            all.add(combatant(wild, "wild", book));
            arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", ids.subList(0, 1), ids.subList(1, ids.size())),
                    Arena.wild("wild", wild.name(), wild.id())), all);
        }

        /** The battle's Pokémon is out of the fight: the admin sets its HP to 0. */
        void faint(String id) {
            arena.play(List.of(new GameEvent.CombatantSet("b1", arena.battle().combatant(id).withHp(0))));
        }

        /** The battle ends and its rewards are paid. */
        List<GameEvent> finish() {
            return finish(0);
        }

        List<GameEvent> finish(long wager) {
            List<GameEvent> end = BattleDesk.endIfOver(arena.state());
            if (end.isEmpty() && arena.battle().step() != Battle.Step.ENDED) {
                end = BattleDesk.end(arena.state(), Arena.ADMIN, "the test is over"); // no round was played: the admin ends it
            }
            if (!end.isEmpty()) {
                arena.play(end);
            }
            List<GameEvent> events = AfterBattle.settle(arena.state(), book, MOVES, RULES, settings, context, wager);
            arena.play(events);
            return events;
        }
    }
}
