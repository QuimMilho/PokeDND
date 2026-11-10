/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.ItemFixtures.PLAYER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.importer.Bundle;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.Learnsets;
import net.quimmilho.pokednd.core.rules.Nature;
import net.quimmilho.pokednd.core.rules.Stat;
import net.quimmilho.pokednd.core.rules.StatBlock;
import net.quimmilho.pokednd.core.trainer.Trainer;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 50 on the real item data (chapter 8.8): every handler of {@code item.csv} is one that Core has code for, every one of the 391 TMs
 * teaches its move to a species that can learn it, the twelve IV candies move the IV they name, the nature mints name real natures and the
 * shop prices are those of the table. Skipped when {@code resources/} or {@code build/roster.json} are missing, unless
 * {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class ItemsRealDataTest {

    /** Every handler name that {@link BagDesk} (outside a battle), {@link BattleItems} or the ball action runs. */
    private static final Set<String> KNOWN_HANDLERS = Set.of("heal_hp", "heal_hp_full", "full_restore", "revive", "restore_pp", "restore_pp_full", "restore_pp_all",
            "restore_pp_all_full", "cure_status", "vitamin", "pp_up", "pp_max", "rare_candy", "exp_candy", "iv_candy", "nature_mint", "evolution_item", "berry", "tm",
            "held_item", "type_gem", "catch_ball", "x_stat", "dire_hit", "guard_spec", "none");

    private static Bundle bundle;
    private static MoveBook moves;
    private static SpeciesBook species;
    private static BagRules rules;

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
        moves = MoveBook.of(bundle.catalog().moves(), bundle.catalog().items());
        species = SpeciesBook.of(bundle);
        rules = BagRules.of(species, moves, TrainerRulebook.defaults());
    }

    private static List<ItemEntry> items() {
        return bundle.catalog().items();
    }

    /** A game with Ana, who has this bag, and a level-30 Pokémon of this species that knows these moves. */
    private static GameState game(String speciesId, Map<String, Integer> bag, List<String> known) {
        Trainer ana = Fixtures.trainer("Ana").withItems(bag);
        SpeciesBook.Species sp = species.species(speciesId).orElseThrow();
        List<Battle.MoveSlot> slots = known.stream().map(m -> Leveling.slot(moves, m)).toList();
        PokemonSheet p = PokemonSheet.of("pk-1", "ana", speciesId, 30, sp.growth().totalXp(30), 1, slots);
        p = p.withHp(BattleSetup.maxHp(p, sp, 30));
        return GameState.empty().withStatus(GameStatus.RUNNING).withTrainers(Map.of("ana", ana)).withPokemon(Map.of("pk-1", p));
    }

    @Test
    void everyItemHandlerOfTheDataIsOneThatCoreRuns() {
        Set<String> unknown = new TreeSet<>();
        Map<String, Integer> byHandler = new TreeMap<>();
        int textOnly = 0;
        for (ItemEntry i : items()) {
            if (i.handler().isEmpty()) {
                textOnly++;
                continue;
            }
            String name = i.handler().get().name();
            byHandler.merge(name, 1, Integer::sum);
            if (!KNOWN_HANDLERS.contains(name)) {
                unknown.add(i.id() + " -> " + name);
            }
        }
        assertEquals(Set.of(), unknown, "a handler without code: add it to BagDesk and to this list");
        System.out.println("[phase 50] item handlers: " + byHandler + ", text only: " + textOnly);
        assertEquals(48, byHandler.get("catch_ball"), "the 48 balls");
        assertEquals(12, byHandler.get("iv_candy"), "the 12 IV candies");
        assertEquals(391, byHandler.get("tm"), "the 391 TMs");
        assertEquals(12 + 6, byHandler.get("exp_candy") + byHandler.get("rare_candy") + byHandler.get("iv_candy"), "18 candies");
    }

    @Test
    void whatTheAppAsksForMatchesTheHandler() {
        for (ItemEntry i : items()) {
            BagDesk.Needs needs = BagDesk.needs(moves, i.id());
            String handler = i.handler().map(h -> h.name()).orElse("");
            switch (handler) {
                case "catch_ball", "x_stat", "dire_hit", "guard_spec" -> assertEquals(BagDesk.Needs.BATTLE_ONLY, needs, i.id());
                case "tm" -> assertEquals(BagDesk.Needs.TM, needs, i.id());
                case "held_item", "type_gem" -> assertEquals(BagDesk.Needs.HOLD, needs, i.id());
                case "" -> assertEquals(BagDesk.Needs.ADMIN, needs, i.id());
                case "none" -> assertEquals(BagDesk.Needs.NOTHING, needs, i.id());
                default -> assertTrue(needs == BagDesk.Needs.POKEMON || needs == BagDesk.Needs.POKEMON_AND_MOVE, i.id() + " " + needs);
            }
        }
    }

    @Test
    void everyTmTeachesItsMoveToASpeciesThatLearnsItByTm() {
        Map<String, String> firstSpecies = new LinkedHashMap<>();
        for (SpeciesBook.Species sp : species.all()) {
            Learnsets.Learnset ls = species.learnset(sp.id()).orElse(null);
            if (ls != null) {
                ls.tm().forEach(m -> firstSpecies.putIfAbsent(m, sp.id()));
            }
        }
        List<String> failed = new ArrayList<>();
        int tms = 0;
        for (ItemEntry i : items()) {
            if (!i.category().equals("tm")) {
                continue;
            }
            tms++;
            String move = i.handler().orElseThrow().detail().orElseThrow();
            String learner = firstSpecies.get(move);
            if (learner == null) {
                failed.add(i.id() + ": no species learns " + move + " by TM");
                continue;
            }
            try {
                GameState state = game(learner, Map.of(i.id(), 1), List.of());
                state = GameReducer.applyAll(state, BagDesk.use(state, rules, PLAYER, "ana", i.id(), "pk-1", BagDesk.Choice.none()));
                if (!state.pokemon().get("pk-1").moves().stream().anyMatch(m -> m.moveId().equals(move))) {
                    failed.add(i.id() + ": " + learner + " did not learn " + move);
                }
                if (!state.trainers().get("ana").items().isEmpty()) {
                    failed.add(i.id() + ": the TM was not used up");
                }
            } catch (RuntimeException e) {
                failed.add(i.id() + " on " + learner + ": " + e.getMessage());
            }
        }
        assertEquals(391, tms);
        assertEquals(List.of(), failed);
    }

    @Test
    void theTwelveIvCandiesMoveTheIvTheyName() {
        Map<String, Stat> raise = Map.of("health_candy", Stat.HP, "mighty_candy", Stat.ATTACK, "tough_candy", Stat.DEFENSE, "smart_candy", Stat.SPECIAL_ATTACK,
                "courage_candy", Stat.SPECIAL_DEFENSE, "quick_candy", Stat.SPEED);
        Map<String, Stat> lower = Map.of("sickly_candy", Stat.HP, "weak_candy", Stat.ATTACK, "brittle_candy", Stat.DEFENSE, "numb_candy", Stat.SPECIAL_ATTACK,
                "coward_candy", Stat.SPECIAL_DEFENSE, "slow_candy", Stat.SPEED);
        Map<String, Stat> all = new LinkedHashMap<>(raise);
        all.putAll(lower);
        for (Map.Entry<String, Stat> e : all.entrySet()) {
            int sign = raise.containsKey(e.getKey()) ? 1 : -1;
            GameState state = game("bulbasaur", Map.of(e.getKey(), 3), List.of("tackle"));
            state = GameReducer.apply(state, new GameEvent.PokemonSet(state.pokemon().get("pk-1").withIvs(StatBlock.all(15))));
            state = GameReducer.applyAll(state, BagDesk.use(state, rules, PLAYER, "ana", e.getKey(), "pk-1", BagDesk.Choice.none()));
            StatBlock ivs = state.pokemon().get("pk-1").ivs();
            for (Stat s : Stat.values()) {
                assertEquals(s == e.getValue() ? 15 + sign : 15, ivs.get(s), e.getKey() + " on " + s);
            }
            assertEquals(2, state.trainers().get("ana").items().get(e.getKey()), "one candy was used up");
        }
    }

    @Test
    void everyNatureMintNamesARealNature() {
        int mints = 0;
        for (ItemEntry i : items()) {
            if (i.handler().isPresent() && i.handler().get().name().equals("nature_mint")) {
                mints++;
                String nature = i.handler().get().detail().orElseThrow();
                assertTrue(Nature.find(nature).isPresent(), i.id() + " names the nature " + nature);
                GameState state = game("bulbasaur", Map.of(i.id(), 1), List.of("tackle"));
                state = GameReducer.applyAll(state, BagDesk.use(state, rules, PLAYER, "ana", i.id(), "pk-1", BagDesk.Choice.none()));
                assertEquals(nature, state.pokemon().get("pk-1").nature());
            }
        }
        assertEquals(21, mints, "the 21 nature mints");
    }

    @Test
    void theShopPricesAreThoseOfTheTableOfChapter8() {
        Map<String, Integer> expected = Map.ofEntries(Map.entry("poke_ball", 200), Map.entry("great_ball", 600), Map.entry("ultra_ball", 800),
                Map.entry("potion", 200), Map.entry("super_potion", 700), Map.entry("hyper_potion", 1500), Map.entry("max_potion", 2500),
                Map.entry("full_restore", 3000), Map.entry("revive", 1500), Map.entry("max_revive", 4000), Map.entry("full_heal", 400),
                Map.entry("ether", 1200), Map.entry("elixir", 3000), Map.entry("x_attack", 500), Map.entry("fire_stone", 2100), Map.entry("adamant_mint", 5000),
                Map.entry("leftovers", 5000), Map.entry("choice_band", 5000), Map.entry("oran_berry", 100), Map.entry("fire_gem", 2000));
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            assertEquals(e.getValue(), ShopDesk.tablePrice(rules, e.getKey()).orElse(-1), e.getKey());
        }
        String someTm = items().stream().filter(i -> i.category().equals("tm")).findFirst().orElseThrow().id();
        assertEquals(3000, ShopDesk.tablePrice(rules, someTm).orElse(-1), "every TM costs 3,000");
        for (String notForSale : List.of("rare_candy", "exp_candy_m", "master_ball")) {
            assertTrue(ShopDesk.tablePrice(rules, notForSale).isEmpty(), notForSale + " is not for sale");
        }
        // the sell price is half the buy price
        Merchant any = Merchant.of("m", "M", 0, 0, List.of());
        Trainer ana = Fixtures.trainer("Ana");
        for (ItemEntry i : items()) {
            var buy = ShopDesk.tablePrice(rules, i.id());
            if (buy.isPresent()) {
                assertEquals(buy.getAsInt() / 2, ShopDesk.sellPrice(rules, any, i.id(), ana).getAsInt(), i.id());
            }
        }
    }
}
