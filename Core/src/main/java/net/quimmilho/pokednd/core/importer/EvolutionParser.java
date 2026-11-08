/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the free-text evolution column of {@code pokedex.csv} into rules (chapter 5.4).
 *
 * <p>The text is made by {@code tools/build-csv.mjs}, for example
 * {@code Vaporeon (Use Water Stone); Espeon (Friendship >= 160, Day)}. Each part before a {@code ;} is one rule:
 * a target, and in brackets the things that must be true. The first thing says how it starts: {@code Trade},
 * {@code Use <item>}, or (anything else) a level-up.
 *
 * <p>Nothing is guessed: a condition that is not understood becomes an {@link Type#UNKNOWN} condition and its rule is
 * handled as "ask the admin" ({@code pokemon.evolution.unknownFallback}).
 */
public final class EvolutionParser {

    /** How an evolution starts. */
    public enum Method {
        /** Triggers when the Pokémon gains a level (every other condition must also hold). */
        LEVEL_UP,
        /** The owner uses an item on the Pokémon. */
        USE_ITEM,
        /** Triggers when the Pokémon is traded. */
        TRADE
    }

    /** What is checked. */
    public enum Type {
        LEVEL, FRIENDSHIP, TIME_OF_DAY, HOLDING_ITEM, KNOWS_MOVE, KNOWS_MOVE_TYPE, USE_MOVE, TRAVEL_BLOCKS, BIOME, STRUCTURE,
        WEATHER, MOON_PHASE, PARTY_MEMBER, STAT_COMPARE, GENDER, PROPERTY, ADVANCEMENT, DEFEAT, NICKNAME,
        /** Not understood. */
        UNKNOWN
    }

    /** Who can check a condition. */
    public enum Handling {
        /** The game can check it alone. */
        AUTOMATIC,
        /** Needs a region tag (chapter 8.2), for example {@code biome:alola}. */
        REGION_TAG,
        /** The game cannot check it; the app asks the admin to confirm. */
        ADMIN,
        /** Not understood; handled as "ask the admin". */
        UNKNOWN
    }

    /**
     * One thing that must be true.
     *
     * @param type     what is checked
     * @param value    the main value as an id or short text (item id, move id, time of day, biome…); null if the type has none
     * @param amount   a number (level, friendship, blocks, times, minimum of a range); null if the type has none
     * @param max      the upper end of a range (Gimmighoul's Netherite), otherwise null
     * @param other    a second value (the lower stat, the property value, a held item…); null if none
     * @param negated  true for "Not (…)"
     * @param handling who can check it
     * @param text     the condition as written
     */
    public record Condition(Type type, String value, Integer amount, Integer max, String other, boolean negated,
                            Handling handling, String text) {
    }

    /**
     * One evolution rule as written in the text.
     *
     * @param target     the target species' name as written
     * @param method     how it starts
     * @param item       the item name for {@link Method#USE_ITEM}, otherwise null
     * @param conditions the other conditions, in the order written
     * @param text       the whole part as written
     */
    public record ParsedRule(String target, Method method, String item, List<Condition> conditions, String text) {
        public ParsedRule {
            conditions = List.copyOf(conditions);
        }

        /** The weakest handling among the conditions: unknown beats admin beats region tag beats automatic. */
        public Handling handling() {
            Handling worst = Handling.AUTOMATIC;
            for (Condition c : conditions) {
                if (c.handling().ordinal() > worst.ordinal()) {
                    worst = c.handling();
                }
            }
            return worst;
        }
    }

    /** A part of the text that could not be taken apart at all (no target and brackets). */
    public record Unparseable(String text, String reason) {
    }

    private static final Pattern RULE = Pattern.compile("^(.+?) \\((.*)\\)$");
    private static final Pattern LEVEL = Pattern.compile("^Level (\\d+)$");
    private static final Pattern FRIENDSHIP = Pattern.compile("^Friendship >= (\\d+)$");
    private static final Pattern TIME = Pattern.compile("^(Day|Night|Dusk|Dawn)$");
    private static final Pattern HOLDING = Pattern.compile("^Holding (.+)$");
    private static final Pattern KNOWS_TYPE = Pattern.compile("^Knows a ([A-Za-z]+)-type move$");
    private static final Pattern KNOWS = Pattern.compile("^Knows (.+)$");
    private static final Pattern USE_MOVE = Pattern.compile("^Use (.+) x(\\d+)$");
    private static final Pattern USE = Pattern.compile("^Use (.+)$");
    private static final Pattern TRAVEL = Pattern.compile("^Travel (\\d+) blocks$");
    private static final Pattern NOT = Pattern.compile("^Not \\((.+)\\)$");
    private static final Pattern BIOME_IS = Pattern.compile("^Biome Is (.+)$");
    private static final Pattern BIOME = Pattern.compile("^Biome (.+)$");
    private static final Pattern STRUCTURE = Pattern.compile("^Structure (.+)$");
    private static final Pattern MOON = Pattern.compile("^Moon (.+)$");
    private static final Pattern PARTY = Pattern.compile("^(With|Without) (.+) in party$");
    private static final Pattern STAT = Pattern.compile("^(Attack|Defence|Defense|Special Attack|Special Defence|Special Defense|Speed|HP) ([<>=]) "
            + "(Attack|Defence|Defense|Special Attack|Special Defence|Special Defense|Speed|HP)$");
    private static final Pattern GENDER = Pattern.compile("^Gender (Male|Female)$");
    private static final Pattern COCOON = Pattern.compile("^Wurmple Cocoon Species (Silcoon|Cascoon)$");
    private static final Pattern NATURE = Pattern.compile("^Toxel Nature ([A-Za-z]+)$");
    private static final Pattern COINS = Pattern.compile("^Gimmighoul Gimmighoul Coins (\\d+)$");
    private static final Pattern NETHERITE_RANGE = Pattern.compile("^Gimmighoul Netherite (\\d+)-(\\d+)$");
    private static final Pattern NETHERITE = Pattern.compile("^Gimmighoul Gimmighoul Netherite (\\d+)$");
    private static final Pattern ADVANCEMENT = Pattern.compile("^Advancement (.+)$");
    private static final Pattern DEFEAT = Pattern.compile("^Defeat (\\d+)x (\\S+?)(?: held_item=(\\S+))?$");
    private static final Pattern NICKNAME = Pattern.compile("^Nickname (.+)$");

    private EvolutionParser() {
    }

    /** What parsing one cell gave. */
    public record Result(List<ParsedRule> rules, List<Unparseable> unparseable) {
    }

    /** Parses the whole evolution cell of one species. An empty text gives no rules. */
    public static Result parse(String cell) {
        List<ParsedRule> rules = new ArrayList<>();
        List<Unparseable> bad = new ArrayList<>();
        if (cell == null || cell.isBlank()) {
            return new Result(rules, bad);
        }
        for (String part : cell.split("; ")) {
            Matcher m = RULE.matcher(part.strip());
            if (!m.matches()) {
                bad.add(new Unparseable(part, "no \"Target (conditions)\" shape"));
                continue;
            }
            List<String> things = splitTop(m.group(2));
            Method method = Method.LEVEL_UP;
            String item = null;
            int from = 0;
            if (!things.isEmpty()) {
                String first = things.get(0);
                if (first.equals("Trade")) {
                    method = Method.TRADE;
                    from = 1;
                } else if (USE.matcher(first).matches() && !USE_MOVE.matcher(first).matches()) {
                    method = Method.USE_ITEM;
                    item = first.substring("Use ".length());
                    from = 1;
                }
            }
            List<Condition> conditions = new ArrayList<>();
            for (int i = from; i < things.size(); i++) {
                conditions.add(condition(things.get(i)));
            }
            rules.add(new ParsedRule(m.group(1), method, item, conditions, part.strip()));
        }
        return new Result(rules, bad);
    }

    /** Splits at commas that are not inside brackets: {@code a, Not (b, c), d} gives three parts. */
    static List<String> splitTop(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0 && i + 1 < s.length() && s.charAt(i + 1) == ' ') {
                out.add(s.substring(start, i).strip());
                start = i + 2;
            }
        }
        String last = s.substring(start).strip();
        if (!last.isEmpty()) {
            out.add(last);
        }
        return out;
    }

    private static Condition c(Type type, String value, Integer amount, Integer max, String other, boolean negated,
                               Handling handling, String text) {
        return new Condition(type, value, amount, max, other, negated, handling, text);
    }

    /** Takes one condition apart. */
    static Condition condition(String text) {
        Matcher m;
        if ((m = NOT.matcher(text)).matches()) {
            Condition inner = condition(m.group(1));
            return c(inner.type(), inner.value(), inner.amount(), inner.max(), inner.other(), !inner.negated(), inner.handling(), text);
        }
        if ((m = LEVEL.matcher(text)).matches()) {
            return c(Type.LEVEL, null, Integer.valueOf(m.group(1)), null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = FRIENDSHIP.matcher(text)).matches()) {
            return c(Type.FRIENDSHIP, null, Integer.valueOf(m.group(1)), null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = TIME.matcher(text)).matches()) {
            return c(Type.TIME_OF_DAY, m.group(1).toLowerCase(Locale.ROOT), null, null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = HOLDING.matcher(text)).matches()) {
            return c(Type.HOLDING_ITEM, m.group(1), null, null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = KNOWS_TYPE.matcher(text)).matches()) {
            return c(Type.KNOWS_MOVE_TYPE, m.group(1).toLowerCase(Locale.ROOT), null, null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = KNOWS.matcher(text)).matches()) {
            return c(Type.KNOWS_MOVE, id(m.group(1)), null, null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = USE_MOVE.matcher(text)).matches()) {
            return c(Type.USE_MOVE, id(m.group(1)), Integer.valueOf(m.group(2)), null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = TRAVEL.matcher(text)).matches()) {
            return c(Type.TRAVEL_BLOCKS, null, Integer.valueOf(m.group(1)), null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = BIOME_IS.matcher(text)).matches()) {
            return c(Type.BIOME, tag(m.group(1)), null, null, null, false, Handling.REGION_TAG, text);
        }
        if ((m = BIOME.matcher(text)).matches()) {
            return c(Type.BIOME, tag(m.group(1)), null, null, null, false, Handling.REGION_TAG, text);
        }
        if ((m = STRUCTURE.matcher(text)).matches()) {
            return c(Type.STRUCTURE, tag(m.group(1)), null, null, null, false, Handling.REGION_TAG, text);
        }
        if (text.equals("Raining") || text.equals("Thundering")) {
            return c(Type.WEATHER, text.toLowerCase(Locale.ROOT), null, null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = MOON.matcher(text)).matches()) {
            return c(Type.MOON_PHASE, tag(m.group(1)), null, null, null, false, Handling.ADMIN, text);
        }
        if ((m = PARTY.matcher(text)).matches()) {
            return c(Type.PARTY_MEMBER, tag(m.group(2)), null, null, null, m.group(1).equals("Without"), Handling.AUTOMATIC, text);
        }
        if ((m = STAT.matcher(text)).matches()) {
            return c(Type.STAT_COMPARE, stat(m.group(1)), null, null, stat(m.group(3)) + m.group(2), false, Handling.AUTOMATIC, text);
        }
        if ((m = GENDER.matcher(text)).matches()) {
            return c(Type.GENDER, m.group(1).toLowerCase(Locale.ROOT), null, null, null, false, Handling.AUTOMATIC, text);
        }
        if ((m = COCOON.matcher(text)).matches()) {
            return c(Type.PROPERTY, "cocoon_species", null, null, m.group(1).toLowerCase(Locale.ROOT), false, Handling.AUTOMATIC, text);
        }
        if ((m = NATURE.matcher(text)).matches()) {
            return c(Type.PROPERTY, "nature", null, null, m.group(1).toLowerCase(Locale.ROOT), false, Handling.AUTOMATIC, text);
        }
        if ((m = COINS.matcher(text)).matches()) {
            return c(Type.PROPERTY, "gimmighoul_coins", Integer.valueOf(m.group(1)), null, null, false, Handling.ADMIN, text);
        }
        if ((m = NETHERITE_RANGE.matcher(text)).matches()) {
            return c(Type.PROPERTY, "gimmighoul_netherite", Integer.valueOf(m.group(1)), Integer.valueOf(m.group(2)), null, false,
                    Handling.ADMIN, text);
        }
        if ((m = NETHERITE.matcher(text)).matches()) {
            return c(Type.PROPERTY, "gimmighoul_netherite", Integer.valueOf(m.group(1)), Integer.valueOf(m.group(1)), null, false,
                    Handling.ADMIN, text);
        }
        if ((m = ADVANCEMENT.matcher(text)).matches()) {
            return c(Type.ADVANCEMENT, tag(m.group(1)), null, null, null, false, Handling.ADMIN, text);
        }
        if ((m = DEFEAT.matcher(text)).matches()) {
            return c(Type.DEFEAT, m.group(2), Integer.valueOf(m.group(1)), null, m.group(3), false, Handling.AUTOMATIC, text);
        }
        if ((m = NICKNAME.matcher(text)).matches()) {
            return c(Type.NICKNAME, m.group(1), null, null, null, false, Handling.AUTOMATIC, text);
        }
        return c(Type.UNKNOWN, text, null, null, null, false, Handling.UNKNOWN, text);
    }

    private static final Set<String> STATS = Set.of("hp", "attack", "defense", "special_attack", "special_defense", "speed");

    /** {@code Special Defence} becomes {@code special_defense}. */
    static String stat(String name) {
        String s = name.toLowerCase(Locale.ROOT).replace(' ', '_').replace("defence", "defense");
        return STATS.contains(s) ? s : name;
    }

    /** {@code Pikachu Alolabiome} becomes {@code pikachu_alolabiome}; slashes are kept. */
    static String tag(String text) {
        return text.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** {@code Ragefist} or {@code Dragon Pulse} becomes the move id {@code ragefist} / {@code dragonpulse}. */
    static String id(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** An optional wrapper for tests. */
    static Optional<Condition> single(String text) {
        Condition c = condition(text);
        return c.type() == Type.UNKNOWN ? Optional.empty() : Optional.of(c);
    }
}
