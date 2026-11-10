/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ProgressFixtures.MOVES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.RULES;
import static net.quimmilho.pokednd.core.game.ProgressFixtures.rule;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.ItemRules;
import net.quimmilho.pokednd.core.importer.Learnsets;
import net.quimmilho.pokednd.core.rules.Growth;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.StatBlock;
import net.quimmilho.pokednd.core.trainer.Trainer;

/** Items, species and a small table for the tests of phase 50 (bag, items, money, shops, trading). */
final class ItemFixtures {

    static final Actor ADMIN = Actor.admin("u-admin");
    static final Actor PLAYER = Actor.player("u-ana");
    static final long NOW = 1_000_000;

    private ItemFixtures() {
    }

    // ===================================================================================================================== items

    /** An item written the way the importer writes it: the handler and the price come from {@link ItemRules}. */
    static ItemEntry item(String id, String name, String category) {
        return new ItemEntry(id, name, category, List.of(), "", Optional.empty(), EffectClass.AUTOMATIC, id, List.of(), ItemRules.handler(id, category),
                JsonNodeFactory.instance.objectNode(), ItemRules.buyPrice(id, category), ItemRules.sellPrice(ItemRules.buyPrice(id, category)),
                ItemRules.forSale(id, category), Optional.empty(), Optional.empty());
    }

    /** A TM, as the importer generates it. */
    static ItemEntry tm(String moveId, String moveName) {
        Optional<Integer> buy = ItemRules.buyPrice("tm_" + moveId, "tm");
        return new ItemEntry("tm_" + moveId, "TM " + moveName, "tm", List.of(), "", Optional.of("Teaches " + moveName + " to a Pokémon."), EffectClass.AUTOMATIC,
                "tm", List.of(), Optional.of(new ItemRules.Handler("tm", Optional.empty(), Optional.of(moveId))), JsonNodeFactory.instance.objectNode(), buy,
                ItemRules.sellPrice(buy), true, Optional.of(moveId), Optional.of("generated"));
    }

    static List<ItemEntry> items() {
        List<ItemEntry> list = new ArrayList<>();
        for (String id : List.of("potion", "super_potion", "hyper_potion", "max_potion", "full_restore", "revive", "max_revive", "antidote", "burn_heal",
                "paralyze_heal", "full_heal", "ether", "max_ether", "elixir", "max_elixir", "hp_up", "protein", "calcium", "pp_up", "pp_max", "herbal_tea")) {
            list.add(item(id, pretty(id), "medicine"));
        }
        for (String id : List.of("rare_candy", "exp_candy_m", "health_candy", "mighty_candy", "tough_candy", "smart_candy", "courage_candy", "quick_candy",
                "sickly_candy", "weak_candy", "brittle_candy", "numb_candy", "coward_candy", "slow_candy")) {
            list.add(item(id, pretty(id), "candy"));
        }
        list.add(item("adamant_mint", "Adamant Mint", "mints"));
        list.add(item("mint_leaf", "Mint Leaf", "mints"));
        list.add(item("thunder_stone", "Thunder Stone", "evolution"));
        list.add(item("fire_stone", "Fire Stone", "evolution"));
        list.add(item("oran_berry", "Oran Berry", "berries"));
        list.add(item("sitrus_berry", "Sitrus Berry", "berries"));
        list.add(item("lum_berry", "Lum Berry", "berries"));
        list.add(item("cheri_berry", "Cheri Berry", "berries"));
        list.add(item("pomeg_berry", "Pomeg Berry", "berries"));
        list.add(item("tamato_berry", "Tamato Berry", "berries"));
        list.add(item("leftovers", "Leftovers", "held_items"));
        list.add(item("choice_band", "Choice Band", "held_items"));
        list.add(item("x_attack", "X Attack", "battle_items"));
        list.add(item("dire_hit", "Dire Hit", "battle_items"));
        list.add(item("guard_spec", "Guard Spec", "battle_items"));
        list.add(item("poke_ball", "Poké Ball", "poke_balls"));
        list.add(item("great_ball", "Great Ball", "poke_balls"));
        list.add(item("master_ball", "Master Ball", "poke_balls"));
        list.add(item("fire_gem", "Fire Gem", "type_gems"));
        for (String move : List.of("ember", "scratch", "flamethrower", "watergun", "slash", "swift")) {
            list.add(tm(move, BattleReducer.pretty(move)));
        }
        return list;
    }

    private static String pretty(String id) {
        return BattleReducer.pretty(id);
    }

    static final MoveBook BOOK = MoveBook.of(Moves.all(), items());

    // ================================================================================================================== species

    static final StatBlock STATS = StatBlock.all(50);

    static SpeciesBook.Species plain(String id) {
        return SpeciesBook.Species.simple(id, BattleReducer.pretty(id), List.of(PokemonType.NORMAL), STATS, Growth.MEDIUM_FAST, 60);
    }

    /** Bulbasaur (and the others of {@link ProgressFixtures}), Pikachu with a stone, Kadabra with a trade, and a Pokémon with a held-item trade. */
    static SpeciesBook species() {
        SpeciesBook.Species pikachu = plain("pikachu").withEvolutions(List.of(rule("pikachu", "Raichu (Use Thunder Stone)")));
        SpeciesBook.Species kadabra = plain("kadabra").withEvolutions(List.of(rule("kadabra", "Alakazam (Trade)")));
        SpeciesBook book = ProgressFixtures.book(pikachu, plain("raichu"), kadabra, plain("alakazam"));
        Learnsets.Learnset bulbasaur = new Learnsets.Learnset(List.of(), List.of("scratch"), List.of("ember", "scratch", "flamethrower", "slash"),
                List.of("watergun", "swift"), List.of("swift"), List.of(), List.of());
        Learnsets.Learnset any = new Learnsets.Learnset(List.of(), List.of(), List.of("ember", "scratch", "slash"), List.of("watergun"), List.of(), List.of(), List.of());
        return book.withLearnsets(Map.of("bulbasaur", bulbasaur, "pidgey", any, "pikachu", any, "kadabra", any, "rattata", any));
    }

    static BagRules rules() {
        return BagRules.of(species(), BOOK, RULES);
    }

    // ==================================================================================================================== a table

    /** A game with trainers Ana and Bo (3000 ₽, 5 Poké Balls and 3 potions each), played through a {@link GameLog}. */
    static final class Table {
        final GameLog log = new GameLog(GameLog.Settings.defaults());
        BagRules rules = rules();

        Table(String... trainers) {
            List<GameEvent> setup = new ArrayList<>();
            setup.add(new GameEvent.StatusChanged(GameStatus.RUNNING));
            for (String id : trainers) {
                setup.add(new GameEvent.TrainerSet(id, Fixtures.trainer(id.substring(0, 1).toUpperCase() + id.substring(1))));
            }
            log.append("system", 1, setup);
        }

        Table() {
            this("ana", "bo");
        }

        GameState state() {
            return log.state();
        }

        List<GameEvent> play(List<GameEvent> events) {
            log.append("u-test", NOW, events);
            return events;
        }

        Trainer trainer(String id) {
            return state().trainers().get(id);
        }

        int count(String trainerId, String itemId) {
            return trainer(trainerId).items().getOrDefault(itemId, 0);
        }

        PokemonSheet pokemon(String id) {
            return state().pokemon().get(id);
        }

        PokemonSheet give(PokemonSheet sheet) {
            play(List.of(new GameEvent.PokemonSet(sheet)));
            return sheet;
        }

        /** A Bulbasaur of this owner and level that knows Tackle and Growl, at full HP. */
        PokemonSheet bulbasaur(String id, String owner, int level) {
            return ProgressFixtures.sheet(rules.species(), id, owner, "bulbasaur", level, "tackle", "growl");
        }

        PokemonSheet sheet(String id, String owner, String species, int level, String... moves) {
            return ProgressFixtures.sheet(rules.species(), id, owner, species, level, moves);
        }

        int maxHp(PokemonSheet p) {
            return BattleSetup.maxHp(p, rules.species().species(p.speciesId()).orElseThrow(), p.level());
        }

        /** The trainer's bag changes to this: item, count, item, count… */
        void bag(String trainerId, Object... pairs) {
            Map<String, Integer> items = new java.util.LinkedHashMap<>();
            for (int i = 0; i < pairs.length; i += 2) {
                items.put((String) pairs[i], (Integer) pairs[i + 1]);
            }
            play(List.of(new GameEvent.TrainerSet(trainerId, trainer(trainerId).withItems(items))));
        }

        void money(String trainerId, long money) {
            play(List.of(new GameEvent.TrainerSet(trainerId, trainer(trainerId).withMoney(money))));
        }

        /** The trainer becomes a trainer of this class and level. */
        void becomes(String trainerId, String classId, int level) {
            Trainer t = trainer(trainerId);
            play(List.of(new GameEvent.TrainerSet(trainerId,
                    new Trainer(t.name(), classId, level, t.xp(), t.scores(), t.skills(), t.maxHp(), t.hp(), t.money(), t.items()))));
        }

        void nextRound() {
            play(List.of(new GameEvent.RoundAdvanced(state().round() + 1)));
        }

        void use(String trainerId, String itemId, String pokemonId) {
            play(BagDesk.use(state(), rules, PLAYER, trainerId, itemId, pokemonId, BagDesk.Choice.none()));
        }

        void use(String trainerId, String itemId, String pokemonId, BagDesk.Choice choice) {
            play(BagDesk.use(state(), rules, PLAYER, trainerId, itemId, pokemonId, choice));
        }
    }

    static MoveBook moves() {
        return MOVES;
    }
}
