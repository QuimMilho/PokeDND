/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Condition;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Handling;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Method;
import net.quimmilho.pokednd.core.importer.EvolutionParser.ParsedRule;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Type;
import net.quimmilho.pokednd.core.importer.Raw.PokedexRow;
import net.quimmilho.pokednd.core.importer.Raw.SpeciesFile;

/**
 * Builds the normalised evolution rules of every species ({@code evolutions.json}, chapter 19.3, step 4): the CSV text
 * is parsed ({@link EvolutionParser}) and every rule is cross-checked against the {@code evolutions} list of the
 * species JSON. The JSON also tells which form the target takes (Alolan Raichu, Vivillon's wing pattern…), which the
 * CSV text loses.
 */
public final class Evolutions {

    private Evolutions() {
    }

    /**
     * One evolution rule.
     *
     * @param fromDex      National Dex number of the species that evolves
     * @param from         its species id
     * @param toDex        National Dex number of the result
     * @param to           the result's species id
     * @param toForm       what the JSON adds to the result, as written (for example {@code alolan} or {@code tree=oak});
     *                     empty when the result is the base form
     * @param method       how it starts
     * @param item         for {@link Method#USE_ITEM}: the item id
     * @param conditions   the other conditions
     * @param handling     the weakest handling among the conditions
     * @param text         the rule as written in the CSV
     */
    public record Rule(int fromDex, String from, int toDex, String to, String toForm, Method method, String item,
                       List<Condition> conditions, Handling handling, String text) {
        public Rule {
            conditions = List.copyOf(conditions);
        }
    }

    /**
     * A disagreement between the CSV text and the species JSON.
     *
     * @param dex     National Dex number of the species that evolves
     * @param species its id
     * @param index   which rule (1-based), or 0 for the whole list
     * @param problem what differs, in plain words
     */
    public record Mismatch(int dex, String species, int index, String problem) {
    }

    /**
     * A reference to an item or move that does not exist in the data.
     *
     * @param dex   National Dex number of the species that evolves
     * @param kind  {@code item} or {@code move} or {@code species}
     * @param name  what was written
     */
    public record Unresolved(int dex, String kind, String name) {
    }

    /**
     * A rule that has a condition nobody understands, or a text that cannot be taken apart. It is not lost: it is handled
     * as "ask the admin".
     *
     * @param dex    National Dex number
     * @param text   the part as written
     * @param reason why
     */
    public record Unparsed(int dex, String text, String reason) {
    }

    /** Everything the build produced. */
    public record Built(List<Rule> rules, List<Mismatch> mismatches, List<Unparsed> unparseable, List<Unresolved> unresolved,
                        int stringsRead) {
    }

    /** Parses and cross-checks all evolution texts. */
    public static Built build(Raw raw) {
        Map<String, String> itemIds = new HashMap<>();
        for (Raw.Item i : raw.items()) {
            itemIds.putIfAbsent(Forms.squash(i.name()), i.id());
        }
        TreeSet<String> moveIds = new TreeSet<>();
        raw.attacks().forEach(m -> moveIds.add(m.id()));
        Map<String, Integer> dexByName = new HashMap<>();
        for (PokedexRow row : raw.pokedex()) {
            dexByName.putIfAbsent(row.name(), row.dex());
        }
        Map<String, Integer> dexById = new HashMap<>();
        raw.species().forEach((dex, s) -> dexById.put(SpeciesMerger.idOf(s), dex));

        List<Rule> rules = new ArrayList<>();
        List<Mismatch> mismatches = new ArrayList<>();
        List<Unparsed> unparseable = new ArrayList<>();
        List<Unresolved> unresolved = new ArrayList<>();
        int strings = 0;
        for (PokedexRow row : raw.pokedex()) {
            if (row.evolutionText().isEmpty()) {
                continue;
            }
            strings++;
            SpeciesFile js = raw.species().get(row.dex());
            String fromId = js == null ? "dex" + row.dex() : SpeciesMerger.idOf(js);
            EvolutionParser.Result parsed = EvolutionParser.parse(row.evolutionText().get());
            parsed.unparseable().forEach(u -> unparseable.add(new Unparsed(row.dex(), u.text(), u.reason())));

            JsonNode jsonEvos = js == null ? null : js.evolutions();
            int jsonCount = jsonEvos == null ? 0 : jsonEvos.size();
            if (jsonCount != parsed.rules().size() + parsed.unparseable().size()) {
                mismatches.add(new Mismatch(row.dex(), fromId, 0, "the CSV text has " + parsed.rules().size()
                        + " rules but the JSON has " + jsonCount));
            }
            int index = 0;
            for (ParsedRule p : parsed.rules()) {
                index++;
                JsonNode j = jsonEvos != null && index <= jsonCount ? jsonEvos.get(index - 1) : null;

                Integer toDex = dexByName.get(p.target());
                if (toDex == null) {
                    unresolved.add(new Unresolved(row.dex(), "species", p.target()));
                    toDex = 0;
                }
                String toId = toDex == 0 ? p.target() : SpeciesMerger.idOf(raw.species().get(toDex));
                String toForm = "";
                if (j != null) {
                    String result = j.path("result").asText("");
                    String[] words = result.split(" ", 2);
                    if (toDex != 0 && !dexById.getOrDefault(words[0], -1).equals(toDex)) {
                        mismatches.add(new Mismatch(row.dex(), fromId, index, "the text says " + p.target() + " but the JSON result is \""
                                + result + "\""));
                    }
                    toForm = words.length > 1 ? words[1] : "";
                    checkVariant(mismatches, row.dex(), fromId, index, p, j);
                }

                String itemId = null;
                if (p.item() != null) {
                    itemId = itemIds.get(Forms.squash(p.item()));
                    if (itemId == null) {
                        unresolved.add(new Unresolved(row.dex(), "item", p.item()));
                        itemId = Forms.squash(p.item());
                    }
                    if (j != null) {
                        String want = j.path("requiredContext").asText("").replaceAll("^[^:]*:", "");
                        if (!want.equals(itemId)) {
                            mismatches.add(new Mismatch(row.dex(), fromId, index, "the text uses item " + itemId + " but the JSON says "
                                    + want));
                        }
                    }
                }
                List<Condition> conditions = new ArrayList<>();
                for (Condition c : p.conditions()) {
                    conditions.add(resolve(c, row.dex(), itemIds, moveIds, unresolved, j == null ? null : j.path("requirements")));
                    if (c.type() == Type.UNKNOWN) {
                        unparseable.add(new Unparsed(row.dex(), p.text(), "condition \"" + c.text() + "\" is not understood"));
                    }
                }
                if (j != null && conditions.size() != j.path("requirements").size()) {
                    mismatches.add(new Mismatch(row.dex(), fromId, index, "the text has " + conditions.size()
                            + " conditions but the JSON has " + j.path("requirements").size() + " requirements"));
                }
                if (j != null) {
                    checkNumbers(mismatches, row.dex(), fromId, index, conditions, j.path("requirements"));
                }
                Handling handling = new ParsedRule(p.target(), p.method(), itemId, conditions, p.text()).handling();
                rules.add(new Rule(row.dex(), fromId, toDex, toId, toForm, p.method(), itemId, conditions, handling, p.text()));
            }
        }
        rules.sort(Comparator.comparingInt(Rule::fromDex));
        return new Built(List.copyOf(rules), List.copyOf(mismatches), List.copyOf(unparseable), List.copyOf(unresolved), strings);
    }

    private static Condition resolve(Condition c, int dex, Map<String, String> itemIds, TreeSet<String> moveIds,
                                     List<Unresolved> unresolved, JsonNode requirements) {
        if (c.type() == Type.HOLDING_ITEM) {
            String id = itemIds.get(Forms.squash(c.value()));
            if (id == null) {
                id = external(c.value(), requirements); // a Minecraft item such as an oak sapling is not in item.csv
            }
            if (id == null) {
                unresolved.add(new Unresolved(dex, "item", c.value()));
                id = Forms.squash(c.value());
            }
            return new Condition(c.type(), id, c.amount(), c.max(), c.other(), c.negated(), c.handling(), c.text());
        }
        if ((c.type() == Type.KNOWS_MOVE || c.type() == Type.USE_MOVE) && !moveIds.contains(c.value())) {
            unresolved.add(new Unresolved(dex, "move", c.value()));
        }
        return c;
    }

    /** The JSON's own id (for example {@code minecraft:oak_sapling}) of a held item that item.csv does not have. */
    private static String external(String name, JsonNode requirements) {
        if (requirements == null) {
            return null;
        }
        for (JsonNode r : requirements) {
            if (r.path("variant").asText().equals("held_item")) {
                String raw = r.path("itemCondition").asText("");
                if (Forms.squash(raw.replaceAll("^#?[^:]*:", "")).equals(Forms.squash(name))) {
                    return raw;
                }
            }
        }
        return null;
    }

    private static void checkVariant(List<Mismatch> out, int dex, String id, int index, ParsedRule p, JsonNode j) {
        String variant = j.path("variant").asText("");
        Method expected = switch (variant) {
            case "item_interact" -> Method.USE_ITEM;
            case "trade" -> Method.TRADE;
            case "level_up" -> Method.LEVEL_UP;
            default -> null;
        };
        if (expected != p.method()) {
            out.add(new Mismatch(dex, id, index, "the text says " + p.method() + " but the JSON variant is \"" + variant + "\""));
        }
    }

    /** The level and friendship numbers in the text must be the ones in the JSON. */
    private static void checkNumbers(List<Mismatch> out, int dex, String id, int index, List<Condition> conditions, JsonNode requirements) {
        for (Condition c : conditions) {
            int want = -1;
            for (JsonNode r : requirements) {
                String variant = r.path("variant").asText();
                if (c.type() == Type.LEVEL && variant.equals("level")) {
                    want = r.has("minLevel") ? r.get("minLevel").asInt() : r.path("amount").asInt();
                } else if (c.type() == Type.FRIENDSHIP && variant.equals("friendship")) {
                    want = r.path("amount").asInt();
                } else if (c.type() == Type.TRAVEL_BLOCKS && variant.equals("blocks_traveled")) {
                    want = r.path("amount").asInt();
                }
            }
            if ((c.type() == Type.LEVEL || c.type() == Type.FRIENDSHIP || c.type() == Type.TRAVEL_BLOCKS) && want != c.amount()) {
                out.add(new Mismatch(dex, id, index, c.type() + " is " + c.amount() + " in the text but " + want + " in the JSON"));
            }
        }
    }

    /** {@code reports/evolutions.md}. */
    public static String report(Built b) {
        StringBuilder sb = new StringBuilder("# Evolutions\n\n");
        sb.append("Evolution texts read: ").append(b.stringsRead()).append(". Rules: ").append(b.rules().size()).append(".\n\n");
        TreeMap<String, Integer> byMethod = new TreeMap<>();
        TreeMap<String, Integer> byHandling = new TreeMap<>();
        TreeMap<String, Integer> byCondition = new TreeMap<>();
        for (Rule r : b.rules()) {
            byMethod.merge(r.method().name(), 1, Integer::sum);
            byHandling.merge(r.handling().name(), 1, Integer::sum);
            for (Condition c : r.conditions()) {
                byCondition.merge(c.type().name(), 1, Integer::sum);
            }
        }
        table(sb, "Rules by how they start", "Method", byMethod);
        table(sb, "Rules by who can check them", "Handling", byHandling);
        table(sb, "Conditions by kind", "Kind", byCondition);

        sb.append("## Not understood\n\nThese are handled as \"ask the admin\" (`pokemon.evolution.unknownFallback`).\n\n");
        if (b.unparseable().isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("| Dex | Text | Reason |\n|---:|---|---|\n");
            b.unparseable().forEach(u -> sb.append("| ").append(u.dex()).append(" | ").append(u.text().replace("|", "\\|"))
                    .append(" | ").append(u.reason().replace("|", "\\|")).append(" |\n"));
            sb.append('\n');
        }
        sb.append("## Differences from the species JSON\n\n");
        if (b.mismatches().isEmpty()) {
            sb.append("None: every rule agrees with the `evolutions` list of its species file.\n\n");
        } else {
            sb.append("| Dex | Species | Rule | Problem |\n|---:|---|---:|---|\n");
            b.mismatches().forEach(m -> sb.append("| ").append(m.dex()).append(" | ").append(m.species()).append(" | ")
                    .append(m.index()).append(" | ").append(m.problem().replace("|", "\\|")).append(" |\n"));
            sb.append('\n');
        }
        sb.append("## Names that match nothing\n\n");
        if (b.unresolved().isEmpty()) {
            sb.append("None.\n");
        } else {
            sb.append("| Dex | Kind | Name |\n|---:|---|---|\n");
            b.unresolved().forEach(u -> sb.append("| ").append(u.dex()).append(" | ").append(u.kind()).append(" | ")
                    .append(u.name().replace("|", "\\|")).append(" |\n"));
        }
        return sb.toString();
    }

    private static void table(StringBuilder sb, String title, String head, Map<String, Integer> counts) {
        sb.append("## ").append(title).append("\n\n| ").append(head).append(" | Count |\n|---|---:|\n");
        counts.forEach((k, v) -> sb.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        sb.append('\n');
    }
}
