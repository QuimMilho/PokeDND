/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Condition;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Method;
import net.quimmilho.pokednd.core.importer.Evolutions;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.StatBlock;

/**
 * Evolution (chapter 5.4): which of a species' evolution rules a Pokémon fulfils now, and what happens when it evolves. The rules come
 * from the data ({@link Evolutions.Rule}); this class only checks them. A rule that needs something only the admin can judge (a full
 * moon, Gimmighoul's coins, an advancement, a condition the importer did not understand) is a candidate that {@link Candidate#needsAdmin
 * needs the admin}.
 *
 * <p>When a rule is looked at: a level-up rule when the Pokémon gains a level, and again only at a level above the one where its owner
 * said "not now"; a rule that counts something (a move used N times, N blocks travelled, N defeats) also whenever the count changes
 * ({@link Trigger#TICK}); a rule that uses an item when that item is used on the Pokémon; a trade rule when the Pokémon is traded.
 */
public final class EvolutionCheck {

    private EvolutionCheck() {
    }

    /** What makes the game look at the evolution rules. */
    public enum Trigger {
        /** The Pokémon gained a level. */
        LEVEL_UP,
        /** An item was used on it ({@link Context#usedItem()}). */
        USE_ITEM,
        /** It was traded. */
        TRADE,
        /** A count changed (a move used, blocks travelled, a defeat) or the admin asked. */
        TICK
    }

    /**
     * The facts around the Pokémon that some conditions read.
     *
     * @param timeOfDay    {@code day}, {@code dusk}, {@code night} or {@code dawn} (null if the game does not say)
     * @param weather      {@code raining} or {@code thundering}, or null for clear weather
     * @param regionTags   the tags of the place (chapter 8.2), for example {@code alola}
     * @param partySpecies the species ids of the owner's party
     * @param usedItem     the item used on the Pokémon, for {@link Trigger#USE_ITEM}
     * @param moveTypes    the type of a move by its id, for "knows a Fairy-type move"
     */
    public record Context(String timeOfDay, String weather, Set<String> regionTags, List<String> partySpecies, String usedItem,
                          Function<String, PokemonType> moveTypes) {
        public Context {
            regionTags = regionTags == null ? Set.of() : Set.copyOf(regionTags);
            partySpecies = partySpecies == null ? List.of() : List.copyOf(partySpecies);
            moveTypes = moveTypes == null ? id -> null : moveTypes;
        }

        /** Nothing is known about the time, the place or the party. */
        public static Context none() {
            return new Context(null, null, Set.of(), List.of(), null, null);
        }

        public Context withTime(String value) {
            return new Context(value, weather, regionTags, partySpecies, usedItem, moveTypes);
        }

        public Context withWeather(String value) {
            return new Context(timeOfDay, value, regionTags, partySpecies, usedItem, moveTypes);
        }

        public Context withTags(Set<String> value) {
            return new Context(timeOfDay, weather, value, partySpecies, usedItem, moveTypes);
        }

        public Context withParty(List<String> value) {
            return new Context(timeOfDay, weather, regionTags, value, usedItem, moveTypes);
        }

        public Context withUsedItem(String value) {
            return new Context(timeOfDay, weather, regionTags, partySpecies, value, moveTypes);
        }

        public Context withMoveTypes(Function<String, PokemonType> value) {
            return new Context(timeOfDay, weather, regionTags, partySpecies, usedItem, value);
        }
    }

    /**
     * A rule that the Pokémon fulfils.
     *
     * @param rule       the rule
     * @param needsAdmin true if some condition can only be judged by the admin: the rest holds, the admin has the last word
     */
    public record Candidate(Evolutions.Rule rule, boolean needsAdmin) {
    }

    private enum Check {
        YES, NO, ADMIN
    }

    /**
     * The rules this Pokémon fulfils at this trigger.
     *
     * @param stats the Pokémon's six stats now (for "Defence &gt; Attack")
     */
    public static List<Candidate> candidates(PokemonSheet sheet, SpeciesBook.Species species, StatBlock stats, Trigger trigger, Context ctx,
                                             ProgressSettings settings) {
        List<Candidate> list = new ArrayList<>();
        for (Evolutions.Rule rule : species.evolutions()) {
            if (!starts(rule, sheet, trigger, ctx)) {
                continue;
            }
            boolean ok = true;
            boolean admin = false;
            for (Condition c : rule.conditions()) {
                Check r = check(c, sheet, stats, ctx, settings);
                if (c.negated() && r != Check.ADMIN) {
                    r = r == Check.YES ? Check.NO : Check.YES;
                }
                if (r == Check.NO) {
                    ok = false;
                    break;
                }
                admin |= r == Check.ADMIN;
            }
            if (ok) {
                list.add(new Candidate(rule, admin));
            }
        }
        return list;
    }

    /** True if this rule is looked at for this trigger. */
    private static boolean starts(Evolutions.Rule rule, PokemonSheet sheet, Trigger trigger, Context ctx) {
        boolean counts = rule.conditions().stream().anyMatch(c -> switch (c.type()) {
            case USE_MOVE, TRAVEL_BLOCKS, DEFEAT -> true;
            default -> false;
        });
        return switch (rule.method()) {
            case LEVEL_UP -> trigger == Trigger.LEVEL_UP && sheet.declinedLevel() < sheet.level() || trigger == Trigger.TICK && counts;
            case USE_ITEM -> trigger == Trigger.USE_ITEM && ctx.usedItem() != null && Battle.squash(ctx.usedItem()).equals(Battle.squash(rule.item()));
            case TRADE -> trigger == Trigger.TRADE;
        };
    }

    private static Check check(Condition c, PokemonSheet sheet, StatBlock stats, Context ctx, ProgressSettings settings) {
        return switch (c.type()) {
            case LEVEL -> yes(sheet.level() >= c.amount());
            case FRIENDSHIP -> yes(sheet.friendship() >= settings.friendshipEvolveAt());
            case TIME_OF_DAY -> yes(c.value().equals(ctx.timeOfDay()));
            case HOLDING_ITEM -> yes(sheet.heldItem() != null && itemKey(sheet.heldItem()).equals(itemKey(c.value())));
            case KNOWS_MOVE -> yes(sheet.moves().stream().anyMatch(m -> Battle.squash(m.moveId()).equals(Battle.squash(c.value()))));
            case KNOWS_MOVE_TYPE -> {
                PokemonType type = PokemonType.parse(c.value()).orElse(null);
                yield yes(type != null && sheet.moves().stream().map(MoveSlot::moveId).anyMatch(id -> type == ctx.moveTypes().apply(id)));
            }
            case USE_MOVE -> yes(sheet.count("use:" + Battle.squash(c.value())) >= c.amount());
            case TRAVEL_BLOCKS -> yes(sheet.travelledBlocks() >= c.amount());
            case BIOME, STRUCTURE -> yes(ctx.regionTags().contains(c.value()));
            case WEATHER -> yes(c.value().equals(ctx.weather()));
            case MOON_PHASE, ADVANCEMENT, UNKNOWN -> Check.ADMIN;
            case PARTY_MEMBER -> yes(ctx.partySpecies().stream().anyMatch(s -> Battle.squash(s).equals(Battle.squash(c.value()))));
            case STAT_COMPARE -> yes(compare(stats, c));
            case GENDER -> yes(sheet.gender().equals(c.value().equals("male") ? "M" : "F"));
            case PROPERTY -> switch (c.value()) {
                case "cocoon_species" -> yes(c.other().equals(sheet.hidden()));
                case "nature" -> yes(c.other().equalsIgnoreCase(sheet.nature()));
                default -> Check.ADMIN; // Gimmighoul's coins and Netherite
            };
            case DEFEAT -> yes(sheet.count("defeat:" + c.value() + (c.other() == null ? "" : ":" + itemKey(c.other()))) >= c.amount());
            case NICKNAME -> yes(c.value().equalsIgnoreCase(sheet.name()));
        };
    }

    private static Check yes(boolean condition) {
        return condition ? Check.YES : Check.NO;
    }

    /** {@code razor_claw}, {@code Razor Claw} and {@code cobblemon:razor_claw} all give {@code razorclaw}. */
    static String itemKey(String id) {
        int colon = id.indexOf(':');
        return Battle.squash(colon < 0 ? id : id.substring(colon + 1));
    }

    /** {@code defense} with {@code attack>} is "Defense &gt; Attack". */
    private static boolean compare(StatBlock stats, Condition c) {
        String other = c.other();
        char op = other.charAt(other.length() - 1);
        int a = stat(stats, c.value());
        int b = stat(stats, other.substring(0, other.length() - 1));
        return switch (op) {
            case '>' -> a > b;
            case '<' -> a < b;
            default -> a == b;
        };
    }

    private static int stat(StatBlock stats, String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "hp" -> stats.hp();
            case "attack" -> stats.attack();
            case "defense" -> stats.defense();
            case "special_attack" -> stats.specialAttack();
            case "special_defense" -> stats.specialDefense();
            case "speed" -> stats.speed();
            default -> throw new IllegalArgumentException("There is no stat " + name);
        };
    }

    /**
     * True if the rule uses up the item the Pokémon holds when it evolves: a trade rule that says "holding" an item (chapter 5.4:
     * the held item is consumed).
     */
    public static boolean consumesHeldItem(Evolutions.Rule rule) {
        return rule.method() == Method.TRADE && rule.conditions().stream().anyMatch(c -> c.type() == net.quimmilho.pokednd.core.importer.EvolutionParser.Type.HOLDING_ITEM && !c.negated());
    }

    // ================================================================================================================ evolving

    /**
     * The Pokémon after it evolves along {@code rule}: the new species with its name (a nickname stays), the ability of the same place in
     * the list, the HP that the higher maximum adds, and no declined level. A held item that the rule consumes is gone.
     */
    public static PokemonSheet evolve(PokemonSheet sheet, SpeciesBook.Species from, SpeciesBook.Species to, Evolutions.Rule rule) {
        String name = sheet.name().equalsIgnoreCase(from.name()) || sheet.name().equalsIgnoreCase(from.id()) ? to.name() : sheet.name();
        String ability = sheet.ability();
        int index = ability == null ? -1 : from.abilities().indexOf(ability);
        if (index >= 0) {
            ability = index < to.abilities().size() ? to.abilities().get(index) : to.abilities().isEmpty() ? ability : to.abilities().get(0);
        } else if (ability != null && ability.equals(from.hiddenAbility()) && to.hiddenAbility() != null) {
            ability = to.hiddenAbility();
        }
        int oldMax = BattleSetup.maxHp(sheet, from, sheet.level());
        int newMax = BattleSetup.maxHp(sheet, to, sheet.level());
        int hp = sheet.fainted() ? 0 : Math.max(1, Math.min(newMax, sheet.hp() + (newMax - oldMax)));
        PokemonSheet evolved = sheet.evolvedTo(to.id(), name, ability, hp);
        return consumesHeldItem(rule) ? evolved.withHeldItem(null) : evolved;
    }
}
