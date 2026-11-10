/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Candidate;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Context;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Trigger;
import net.quimmilho.pokednd.core.importer.Bundle;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Condition;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Method;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Type;
import net.quimmilho.pokednd.core.importer.Evolutions;
import net.quimmilho.pokednd.core.rules.Growth;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.StatBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 49 on the real data (chapter 5.4): every one of the evolution rules in the data (the three methods and every kind of condition
 * found there) is evolved by {@link EvolutionCheck} when a Pokémon fulfils it, and is not when any one of its conditions is not met.
 * Skipped when {@code resources/} or {@code build/roster.json} are missing, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class EvolutionMethodsRealDataTest {

    private static Bundle bundle;

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
    }

    /** What a rule needs in order to be fulfilled, built one condition at a time. */
    private static final class Scenario {
        PokemonSheet sheet = PokemonSheet.of("pk-1", "ana", "x", 1, 0, 10, List.of());
        String time;
        String weather;
        final Set<String> tags = new HashSet<>();
        final List<String> party = new ArrayList<>();
        final Map<String, PokemonType> moveTypes = new LinkedHashMap<>();
        StatBlock stats = StatBlock.all(50);
        final List<Battle.MoveSlot> moves = new ArrayList<>();
        final Map<String, Integer> counters = new LinkedHashMap<>();

        Context context() {
            return Context.none().withTime(time).withWeather(weather).withTags(tags).withParty(party).withMoveTypes(moveTypes::get);
        }

        PokemonSheet finished(String name) {
            PokemonSheet s = sheet.withMoves(moves);
            for (var e : counters.entrySet()) {
                s = s.withCount(e.getKey(), e.getValue());
            }
            return s;
        }
    }

    /** Makes one condition true (or false) in the scenario; false if that is not possible for this condition (it is the admin's). */
    private static boolean arrange(Scenario sc, Condition c, boolean passes) {
        boolean predicate = c.negated() ? !passes : passes;
        PokemonSheet s = sc.sheet;
        switch (c.type()) {
            case LEVEL -> {
                if (!predicate && c.amount() <= 1) {
                    return false;
                }
                sc.sheet = s.withGrowth(predicate ? c.amount() : c.amount() - 1, 0, s.hp());
            }
            case FRIENDSHIP -> sc.sheet = s.withFriendship(predicate ? 255 : 0);
            case TIME_OF_DAY -> sc.time = predicate ? c.value() : c.value().equals("day") ? "night" : "day";
            case HOLDING_ITEM -> sc.sheet = s.withHeldItem(predicate ? c.value() : null);
            case KNOWS_MOVE -> {
                if (predicate) {
                    sc.moves.add(new Battle.MoveSlot(c.value(), c.value(), 10, 10, 0, "normal"));
                }
            }
            case KNOWS_MOVE_TYPE -> {
                if (predicate) {
                    sc.moves.add(new Battle.MoveSlot("probe" + c.value(), "probe", 10, 10, 0, "normal"));
                    sc.moveTypes.put("probe" + c.value(), PokemonType.parse(c.value()).orElseThrow());
                }
            }
            case USE_MOVE -> sc.counters.put("use:" + c.value(), predicate ? c.amount() : c.amount() - 1);
            case TRAVEL_BLOCKS -> sc.sheet = s.withTravelled(predicate ? c.amount() : c.amount() - 1);
            case BIOME, STRUCTURE -> {
                if (predicate) {
                    sc.tags.add(c.value());
                }
            }
            case WEATHER -> sc.weather = predicate ? c.value() : null;
            case PARTY_MEMBER -> {
                if (predicate) {
                    sc.party.add(c.value());
                }
            }
            case STAT_COMPARE -> {
                String other = c.other().substring(0, c.other().length() - 1);
                char op = c.other().charAt(c.other().length() - 1);
                int a = 50;
                int b = 50;
                if (op == '>') {
                    a = predicate ? 80 : 20;
                } else if (op == '<') {
                    a = predicate ? 20 : 80;
                } else {
                    a = predicate ? 50 : 80;
                }
                sc.stats = with(with(StatBlock.all(50), c.value(), a), other, b);
            }
            case GENDER -> sc.sheet = s.withGender(predicate == c.value().equals("male") ? "M" : "F");
            case PROPERTY -> {
                if (c.value().equals("cocoon_species")) {
                    sc.sheet = new PokemonSheet(s.id(), s.ownerId(), s.speciesId(), s.name(), s.level(), s.xp(), s.nature(), s.ivs(), s.evs(), s.gender(), false, null, null,
                            s.moves(), s.hp(), s.status(), s.friendship(), s.location(), null, 0, s.counters(), s.travelledBlocks(), predicate ? c.other() : "none");
                } else if (c.value().equals("nature")) {
                    sc.sheet = new PokemonSheet(s.id(), s.ownerId(), s.speciesId(), s.name(), s.level(), s.xp(), predicate ? c.other() : c.other().equals("bold") ? "adamant" : "bold", s.ivs(), s.evs(), s.gender(), false,
                            null, null, s.moves(), s.hp(), s.status(), s.friendship(), s.location(), null, 0, s.counters(), s.travelledBlocks(), s.hidden());
                } else {
                    return false; // Gimmighoul's coins: the admin's
                }
            }
            case DEFEAT -> sc.counters.put("defeat:" + c.value() + (c.other() == null ? "" : ":" + EvolutionCheck.itemKey(c.other())), predicate ? c.amount() : c.amount() - 1);
            case NICKNAME -> sc.sheet = new PokemonSheet(s.id(), s.ownerId(), s.speciesId(), predicate ? c.value() : "zzz", s.level(), s.xp(), s.nature(), s.ivs(), s.evs(), s.gender(),
                    false, null, null, s.moves(), s.hp(), s.status(), s.friendship(), s.location(), null, 0, s.counters(), s.travelledBlocks(), s.hidden());
            case MOON_PHASE, ADVANCEMENT, UNKNOWN -> {
                return false;
            }
        }
        return true;
    }

    private static StatBlock with(StatBlock b, String stat, int value) {
        return switch (stat) {
            case "hp" -> new StatBlock(value, b.attack(), b.defense(), b.specialAttack(), b.specialDefense(), b.speed());
            case "attack" -> new StatBlock(b.hp(), value, b.defense(), b.specialAttack(), b.specialDefense(), b.speed());
            case "defense" -> new StatBlock(b.hp(), b.attack(), value, b.specialAttack(), b.specialDefense(), b.speed());
            case "special_attack" -> new StatBlock(b.hp(), b.attack(), b.defense(), value, b.specialDefense(), b.speed());
            case "special_defense" -> new StatBlock(b.hp(), b.attack(), b.defense(), b.specialAttack(), value, b.speed());
            default -> new StatBlock(b.hp(), b.attack(), b.defense(), b.specialAttack(), b.specialDefense(), value);
        };
    }

    private static boolean adminOnly(Condition c) {
        return switch (c.type()) {
            case MOON_PHASE, ADVANCEMENT, UNKNOWN -> true;
            case PROPERTY -> c.value().startsWith("gimmighoul");
            default -> false;
        };
    }

    private static Trigger trigger(Evolutions.Rule rule, Scenario sc) {
        return switch (rule.method()) {
            case LEVEL_UP -> Trigger.LEVEL_UP;
            case USE_ITEM -> Trigger.USE_ITEM;
            case TRADE -> Trigger.TRADE;
        };
    }

    /** The Pokémon of the scenario, the context, and whether the rule is among its candidates. */
    private static List<Candidate> run(Evolutions.Rule rule, Scenario sc) {
        SpeciesBook.Species sp = SpeciesBook.Species.simple(rule.from(), rule.from(), List.of(PokemonType.NORMAL), StatBlock.all(50), Growth.MEDIUM_FAST, 60)
                .withEvolutions(List.of(rule));
        Context ctx = sc.context();
        if (rule.method() == Method.USE_ITEM) {
            ctx = ctx.withUsedItem(rule.item());
        }
        PokemonSheet s = sc.finished(rule.from());
        return EvolutionCheck.candidates(s, sp, sc.stats, trigger(rule, sc), ctx, ProgressSettings.defaults());
    }

    @Test
    void everyRuleOfTheDataEvolvesWhenFulfilledAndNotWhenAConditionFails() {
        List<Evolutions.Rule> rules = bundle.evolutions().rules();
        assertTrue(rules.size() > 600, "the data has " + rules.size() + " evolution rules");
        Set<Method> methods = EnumSet.noneOf(Method.class);
        Set<Type> typesFulfilled = EnumSet.noneOf(Type.class);
        Set<Type> typesBroken = EnumSet.noneOf(Type.class);
        int adminRules = 0;
        for (Evolutions.Rule rule : rules) {
            methods.add(rule.method());
            // 1. everything holds
            Scenario ok = new Scenario();
            boolean admin = false;
            for (Condition c : rule.conditions()) {
                admin |= adminOnly(c);
                if (!adminOnly(c)) {
                    assertTrue(arrange(ok, c, true), rule.text());
                    typesFulfilled.add(c.type());
                }
            }
            List<Candidate> got = run(rule, ok);
            assertEquals(1, got.size(), "the rule is fulfilled: " + rule.from() + " -> " + rule.to() + " (" + rule.text() + ")");
            assertEquals(rule, got.get(0).rule());
            assertEquals(admin, got.get(0).needsAdmin(), rule.text());
            adminRules += admin ? 1 : 0;
            if (admin) {
                assertTrue(rule.handling() == net.quimmilho.pokednd.core.importer.EvolutionParser.Handling.ADMIN
                        || rule.handling() == net.quimmilho.pokednd.core.importer.EvolutionParser.Handling.UNKNOWN, "the importer marked it as the admin's: " + rule.text());
            }
            // 2. every condition that the game can check, broken on its own
            for (int broken = 0; broken < rule.conditions().size(); broken++) {
                Condition c = rule.conditions().get(broken);
                if (adminOnly(c)) {
                    continue;
                }
                Scenario sc = new Scenario();
                boolean possible = true;
                for (int i = 0; i < rule.conditions().size(); i++) {
                    Condition other = rule.conditions().get(i);
                    if (!adminOnly(other)) {
                        possible &= arrange(sc, other, i != broken);
                    }
                }
                if (!possible) {
                    continue; // it cannot be broken (a rule for level 1)
                }
                typesBroken.add(c.type());
                assertEquals(List.of(), run(rule, sc), "breaking " + c.text() + " stops " + rule.text());
            }
            // 3. the wrong trigger starts nothing
            for (Trigger wrong : Trigger.values()) {
                if (wrong == trigger(rule, ok) || wrong == Trigger.TICK && rule.conditions().stream().anyMatch(c -> c.type() == Type.USE_MOVE || c.type() == Type.TRAVEL_BLOCKS || c.type() == Type.DEFEAT)) {
                    continue;
                }
                SpeciesBook.Species sp = SpeciesBook.Species.simple(rule.from(), rule.from(), List.of(PokemonType.NORMAL), StatBlock.all(50), Growth.MEDIUM_FAST, 60)
                        .withEvolutions(List.of(rule));
                assertEquals(List.of(), EvolutionCheck.candidates(ok.finished(rule.from()), sp, ok.stats, wrong, ok.context().withUsedItem(rule.item()), ProgressSettings.defaults()),
                        "the trigger " + wrong + " does not start " + rule.text());
            }
        }
        assertEquals(EnumSet.allOf(Method.class), methods, "the three methods are in the data");
        Set<Type> inData = EnumSet.noneOf(Type.class);
        rules.forEach(r -> r.conditions().forEach(c -> inData.add(c.type())));
        Set<Type> checkable = EnumSet.copyOf(inData);
        checkable.removeAll(EnumSet.of(Type.MOON_PHASE, Type.ADVANCEMENT, Type.UNKNOWN));
        assertTrue(typesFulfilled.containsAll(checkable), "every kind of condition was fulfilled: " + typesFulfilled + " of " + checkable);
        Set<Type> mustBreak = EnumSet.copyOf(checkable);
        assertTrue(typesBroken.containsAll(mustBreak), "every kind of condition was broken: missing " + missing(mustBreak, typesBroken));
        assertEquals(11, adminRules, "rules only the admin can judge: " + adminRules);
        assertFalse(inData.contains(Type.UNKNOWN), "the importer understood every condition");
    }

    private static Set<Type> missing(Set<Type> all, Set<Type> have) {
        Set<Type> m = EnumSet.copyOf(all);
        m.removeAll(have);
        return m;
    }
}
