/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.importer.ItemRules.Handler;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.MergedSpecies;

/**
 * The moves, abilities and items tables (chapter 19.3, steps 6 and 7; chapter 19.6). Each entry comes from the CSV, is
 * completed with the Showdown data, and is sorted into an effect class.
 *
 * <p>If the CSV and Showdown disagree on a number the CSV wins and the difference is reported.
 */
public final class Catalog {

    private Catalog() {
    }

    /** Who has to make an effect work (chapter 19.6). */
    public enum EffectClass {
        /** Plain data (or nothing to do): works without code. */
        AUTOMATIC,
        /** Needs code in Core. Until that code exists the admin applies it, like a manual one. */
        SCRIPTED,
        /** No effect data at all: the app shows the text and the admin applies it. */
        MANUAL
    }

    /**
     * A move.
     *
     * @param id          Showdown-style id
     * @param name        name from the CSV
     * @param type        type from the CSV (for example {@code Fire})
     * @param category    Physical, Special or Status
     * @param power       base power (0 for status moves)
     * @param accuracy    percent; empty when the move never misses
     * @param pp          power points
     * @param priority    priority, −7 to +5
     * @param target      target as written in the CSV
     * @param description description from the CSV
     * @param effectClass automatic, scripted or manual
     * @param reason      empty for ordinary moves; {@code z_max} for Z- and Max-moves (not used in the game); for scripted
     *                    moves why: the handlers and unsupported conditions
     * @param handlers    names of the Showdown code handlers (empty for automatic moves)
     * @param effect      the Showdown fields that describe the effect (flags, secondary, drain, recoil, heal, multihit…)
     */
    public record MoveEntry(String id, String name, String type, String category, int power, Optional<Integer> accuracy,
                            int pp, int priority, String target, String description, EffectClass effectClass,
                            List<String> reason, List<String> handlers, JsonNode effect) {
        public MoveEntry {
            reason = List.copyOf(reason);
            handlers = List.copyOf(handlers);
        }
    }

    /**
     * An ability.
     *
     * @param id          Showdown ability id
     * @param name        display name
     * @param description English description from the Cobblemon language file, if it has one
     * @param effectClass automatic (nothing to implement) or scripted; manual if Showdown does not know it
     * @param handlers    names of the Showdown code handlers
     */
    public record AbilityEntry(String id, String name, Optional<String> description, EffectClass effectClass, List<String> handlers) {
        public AbilityEntry {
            handlers = List.copyOf(handlers);
        }
    }

    /**
     * An item, or a TM.
     *
     * @param id          item id (for a TM: {@code tm_} and the move id)
     * @param name        display name
     * @param category    category ({@code tm} for TMs)
     * @param alsoIn      other categories the same id appears in (King's Rock, Shell Helmet)
     * @param icon        icon path inside {@code resources/}
     * @param description description, if any
     * @param effectClass automatic, scripted or manual
     * @param showdown    {@code no-script}, {@code scripted} or {@code no-data}: what the Showdown data says; {@code tm} for TMs
     * @param handlers    names of the Showdown code handlers
     * @param handler     the default effect by category (chapter 8.8); empty if the effect is not defined (text-only item)
     * @param effect      Showdown's plain-data fields for the item (fling power, boosts…)
     * @param buyPrice    default buy price in ₽; empty if not for sale or not in the price table
     * @param sellPrice   half the buy price
     * @param forSale     can be bought in a shop
     * @param move        for a TM: the move it teaches
     * @param tmSource    for a TM: {@code cobblemon} or {@code generated}
     */
    public record ItemEntry(String id, String name, String category, List<String> alsoIn, String icon, Optional<String> description,
                            EffectClass effectClass, String showdown, List<String> handlers, Optional<Handler> handler,
                            JsonNode effect, Optional<Integer> buyPrice, Optional<Integer> sellPrice, boolean forSale,
                            Optional<String> move, Optional<String> tmSource) {
        public ItemEntry {
            alsoIn = List.copyOf(alsoIn);
            handlers = List.copyOf(handlers);
        }
    }

    /**
     * A number that the CSV and Showdown disagree on.
     *
     * @param move     move id
     * @param field    what differs
     * @param csv      CSV value (kept)
     * @param showdown Showdown value
     */
    public record MoveConflict(String move, String field, String csv, String showdown) {
    }

    /**
     * Everything built.
     *
     * @param moves            all usable moves, sorted by id
     * @param abilities        every ability some species has, sorted by id
     * @param items            usable items (duplicates merged), then the TMs; sorted by id within each part
     * @param moveConflicts    CSV against Showdown
     * @param moveRowsSkipped  moves in the CSV that were left out (no stats), with the reason
     * @param missingAbilities ability ids a species has that Showdown does not know
     * @param itemRowsUsed     rows of {@code item.csv} used (the Pokédex rows are dropped)
     * @param itemRowsDropped  the Pokédex rows dropped
     * @param mergedItemIds    ids that appeared in two categories
     * @param tmMissingMoves   TMs whose move is not in the moves table
     */
    public record Built(List<MoveEntry> moves, List<AbilityEntry> abilities, List<ItemEntry> items, List<MoveConflict> moveConflicts,
                        List<String> moveRowsSkipped, List<String> missingAbilities, int itemRowsUsed, int itemRowsDropped,
                        List<String> mergedItemIds, List<String> tmMissingMoves) {
    }

    // ---------------------------------------------------------------- the English language file

    /** Reads the English Cobblemon language file; an empty map if it is not there. */
    public static Map<String, String> english(Path resources) {
        Path file = resources.resolve("assets/cobblemon/lang/en_us.json");
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(file));
            Map<String, String> out = new TreeMap<>();
            root.fields().forEachRemaining(e -> {
                if (e.getValue().isTextual()) {
                    out.put(e.getKey(), e.getValue().asText());
                }
            });
            return out;
        } catch (IOException e) {
            throw new ImportException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- building

    private static final Set<String> MOVE_BASE_FIELDS = Set.of("num", "name", "type", "category", "basePower", "accuracy", "pp",
            "priority", "target", "handlers");
    private static final Set<String> ITEM_BASE_FIELDS = Set.of("num", "name", "gen", "handlers");

    /** Builds all three tables. */
    public static Built build(Raw raw, List<MergedSpecies> species, Showdown sd, Map<String, String> english) {
        List<MoveConflict> conflicts = new ArrayList<>();
        List<MoveEntry> moves = moves(raw, sd, conflicts);
        Set<String> moveIds = new TreeSet<>();
        moves.forEach(m -> moveIds.add(m.id()));

        List<String> missing = new ArrayList<>();
        List<AbilityEntry> abilities = abilities(species, sd, english, missing);

        List<String> merged = new ArrayList<>();
        int[] counts = new int[2];
        List<ItemEntry> items = new ArrayList<>(items(raw, sd, merged, counts));
        List<String> tmMissing = new ArrayList<>();
        items.addAll(tms(raw, moveIds, tmMissing));

        List<String> skipped = new ArrayList<>();
        FileReport attackReport = raw.reports().get("attack.csv");
        if (attackReport != null) {
            attackReport.skipped().forEach(i -> skipped.add(i.message()));
        }
        conflicts.sort(Comparator.comparing(MoveConflict::move).thenComparing(MoveConflict::field));
        return new Built(List.copyOf(moves), List.copyOf(abilities), List.copyOf(items), List.copyOf(conflicts), List.copyOf(skipped),
                List.copyOf(missing), counts[0], counts[1], List.copyOf(merged), List.copyOf(tmMissing));
    }

    // ---- moves

    private static List<MoveEntry> moves(Raw raw, Showdown sd, List<MoveConflict> conflicts) {
        TreeMap<String, Raw.Move> byId = new TreeMap<>();
        for (Raw.Move m : raw.attacks()) {
            byId.putIfAbsent(m.id(), m);
        }
        List<MoveEntry> out = new ArrayList<>();
        for (Raw.Move m : byId.values()) {
            JsonNode s = sd.moves().get(m.id());
            EffectClass cls;
            List<String> reason = new ArrayList<>();
            List<String> handlers = new ArrayList<>();
            ObjectNode effect = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            if (s == null) {
                cls = EffectClass.MANUAL;
                reason.add("no_data");
            } else {
                compare(conflicts, m, s);
                s.path("handlers").forEach(h -> handlers.add(h.asText()));
                s.fields().forEachRemaining(e -> {
                    if (!MOVE_BASE_FIELDS.contains(e.getKey())) {
                        effect.set(e.getKey(), e.getValue());
                    }
                });
                if (s.path("isZ").asBoolean(false) || s.path("isMax").asBoolean(false)
                        || s.path("isZ").isTextual() || s.path("isMax").isTextual()) {
                    cls = EffectClass.MANUAL;
                    reason.add("z_max");
                } else {
                    List<String> unsupported = unsupportedConditions(s);
                    if (!handlers.isEmpty() || !unsupported.isEmpty()) {
                        cls = EffectClass.SCRIPTED;
                        reason.addAll(handlers);
                        reason.addAll(unsupported);
                    } else {
                        cls = EffectClass.AUTOMATIC;
                    }
                }
            }
            out.add(new MoveEntry(m.id(), m.name(), m.type(), m.category(), m.power(), m.accuracy(), m.pp(), m.priority(),
                    m.target(), m.description(), cls, reason, handlers, effect));
        }
        return out;
    }

    /** Conditions a move applies that PokeDnD does not implement itself (the six major statuses, confusion and flinch are native). */
    private static List<String> unsupportedConditions(JsonNode move) {
        TreeSet<String> refs = new TreeSet<>();
        collect(refs, move);
        collect(refs, move.path("self"));
        JsonNode secondary = move.path("secondary");
        collect(refs, secondary);
        collect(refs, secondary.path("self"));
        for (JsonNode s : move.path("secondaries")) {
            collect(refs, s);
            collect(refs, s.path("self"));
        }
        refs.remove("volatileStatus:confusion");
        refs.remove("volatileStatus:flinch");
        return new ArrayList<>(refs);
    }

    private static void collect(Set<String> refs, JsonNode o) {
        for (String k : List.of("volatileStatus", "sideCondition", "slotCondition", "pseudoWeather")) {
            if (o.path(k).isTextual()) {
                refs.add(k + ":" + o.get(k).asText());
            }
        }
    }

    private static void compare(List<MoveConflict> out, Raw.Move m, JsonNode s) {
        text(out, m.id(), "type", m.type(), s.path("type").asText(""));
        text(out, m.id(), "category", m.category(), s.path("category").asText(""));
        number(out, m.id(), "power", m.power(), s.path("basePower").asInt(0));
        String showdownAccuracy = s.path("accuracy").isBoolean() ? "never misses" : String.valueOf(s.path("accuracy").asInt(0));
        String csvAccuracy = m.accuracy().map(String::valueOf).orElse("never misses");
        if (!csvAccuracy.equals(showdownAccuracy)) {
            out.add(new MoveConflict(m.id(), "accuracy", csvAccuracy, showdownAccuracy));
        }
        number(out, m.id(), "pp", m.pp(), s.path("pp").asInt(0));
        number(out, m.id(), "priority", m.priority(), s.path("priority").asInt(0));
        text(out, m.id(), "target", m.target(), s.path("target").asText(""));
    }

    private static void text(List<MoveConflict> out, String id, String field, String csv, String showdown) {
        if (!csv.equalsIgnoreCase(showdown)) {
            out.add(new MoveConflict(id, field, csv, showdown));
        }
    }

    private static void number(List<MoveConflict> out, String id, String field, int csv, int showdown) {
        if (csv != showdown) {
            out.add(new MoveConflict(id, field, String.valueOf(csv), String.valueOf(showdown)));
        }
    }

    // ---- abilities

    private static List<AbilityEntry> abilities(List<MergedSpecies> species, Showdown sd, Map<String, String> english,
                                                List<String> missing) {
        TreeSet<String> ids = new TreeSet<>();
        for (MergedSpecies s : species) {
            ids.addAll(s.abilities());
            s.hiddenAbility().ifPresent(ids::add);
        }
        List<AbilityEntry> out = new ArrayList<>();
        for (String id : ids) {
            JsonNode a = sd.abilities().get(id);
            Optional<String> description = Optional.ofNullable(english.get("cobblemon.ability." + id + ".desc"));
            if (a == null) {
                missing.add(id);
                out.add(new AbilityEntry(id, english.getOrDefault("cobblemon.ability." + id, id), description, EffectClass.MANUAL, List.of()));
                continue;
            }
            List<String> handlers = new ArrayList<>();
            a.path("handlers").forEach(h -> handlers.add(h.asText()));
            out.add(new AbilityEntry(id, english.getOrDefault("cobblemon.ability." + id, a.path("name").asText(id)), description,
                    handlers.isEmpty() ? EffectClass.AUTOMATIC : EffectClass.SCRIPTED, handlers));
        }
        return out;
    }

    // ---- items

    private static List<ItemEntry> items(Raw raw, Showdown sd, List<String> mergedIds, int[] counts) {
        // The same id can sit in two categories (King's Rock and Shell Helmet are held items and evolution items): one item,
        // filed under held_items, with the other category in alsoIn.
        Map<String, List<Raw.Item>> rowsById = new LinkedHashMap<>();
        for (Raw.Item i : raw.items()) {
            if (i.category().equals("pokedexes")) {
                counts[1]++;
                continue;
            }
            counts[0]++;
            rowsById.computeIfAbsent(i.id(), k -> new ArrayList<>()).add(i);
        }
        Map<String, Raw.Item> first = new LinkedHashMap<>();
        Map<String, List<String>> also = new TreeMap<>();
        for (Map.Entry<String, List<Raw.Item>> e : rowsById.entrySet()) {
            List<Raw.Item> rows = e.getValue();
            Raw.Item primary = rows.stream().filter(r -> r.category().equals("held_items")).findFirst().orElse(rows.get(0));
            first.put(e.getKey(), primary);
            if (rows.size() > 1) {
                mergedIds.add(e.getKey());
                also.put(e.getKey(), rows.stream().map(Raw.Item::category).filter(c -> !c.equals(primary.category())).sorted().toList());
            }
        }
        List<ItemEntry> out = new ArrayList<>();
        for (Raw.Item i : first.values()) {
            JsonNode s = sd.items().get(Forms.squash(i.id()));
            List<String> handlers = new ArrayList<>();
            ObjectNode effect = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            String showdown;
            if (s == null) {
                showdown = "no-data";
            } else {
                s.path("handlers").forEach(h -> handlers.add(h.asText()));
                s.fields().forEachRemaining(e -> {
                    if (!ITEM_BASE_FIELDS.contains(e.getKey())) {
                        effect.set(e.getKey(), e.getValue());
                    }
                });
                showdown = handlers.isEmpty() ? "no-script" : "scripted";
            }
            Optional<Handler> handler = ItemRules.handler(i.id(), i.category());
            EffectClass cls = switch (showdown) {
                case "scripted" -> EffectClass.SCRIPTED;
                case "no-script" -> EffectClass.AUTOMATIC;
                default -> handler.isPresent() ? EffectClass.AUTOMATIC : EffectClass.MANUAL;
            };
            boolean forSale = ItemRules.forSale(i.id(), i.category());
            Optional<Integer> buy = ItemRules.buyPrice(i.id(), i.category());
            out.add(new ItemEntry(i.id(), i.name(), i.category(), also.getOrDefault(i.id(), List.of()), i.icon(), i.description(), cls,
                    showdown, handlers, handler, effect, buy, ItemRules.sellPrice(buy), forSale, Optional.empty(), Optional.empty()));
        }
        out.sort(Comparator.comparing(ItemEntry::id));
        return out;
    }

    private static List<ItemEntry> tms(Raw raw, Set<String> moveIds, List<String> missing) {
        List<ItemEntry> out = new ArrayList<>();
        for (Raw.Tm tm : raw.tms()) {
            if (!moveIds.contains(tm.moveId())) {
                missing.add(tm.moveId());
            }
            Optional<Integer> buy = ItemRules.buyPrice("tm_" + tm.moveId(), "tm");
            out.add(new ItemEntry("tm_" + tm.moveId(), "TM " + tm.name(), "tm", List.of(), tm.icon(),
                    Optional.of("Teaches " + tm.name() + " to a Pokémon."), EffectClass.AUTOMATIC, "tm", List.of(),
                    Optional.of(new Handler("tm", Optional.empty(), Optional.of(tm.moveId()))),
                    new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(), buy, ItemRules.sellPrice(buy), true,
                    Optional.of(tm.moveId()), Optional.of(tm.source())));
        }
        out.sort(Comparator.comparing(ItemEntry::id));
        return out;
    }

    // ---------------------------------------------------------------- the report

    /** {@code reports/coverage.md}: how many moves, abilities and items are automatic, scripted or manual. */
    public static String coverageReport(Built b) {
        StringBuilder sb = new StringBuilder("# Effect coverage\n\n");
        sb.append("How much of the data can work without code (chapter 19.6). *Scripted* needs code in Core; until it exists the admin ")
                .append("applies it by hand. *Manual* has no effect data at all.\n\n");
        sb.append("## Moves\n\n").append(counts(b.moves().stream().map(MoveEntry::effectClass).toList()));
        long zMax = b.moves().stream().filter(m -> m.reason().contains("z_max")).count();
        sb.append("\nOf the manual moves, ").append(zMax).append(" are Z- and Max-moves (not used in the game). ")
                .append("Left out of the table: ").append(b.moveRowsSkipped().isEmpty() ? "none" : String.join("; ", b.moveRowsSkipped()))
                .append(".\n\n");
        sb.append("## Abilities\n\n").append(counts(b.abilities().stream().map(AbilityEntry::effectClass).toList())).append('\n');
        sb.append("## Items\n\nItem rows used: ").append(b.itemRowsUsed()).append(" (").append(b.itemRowsDropped())
                .append(" Pokédex rows dropped); ids that appear in two categories and were merged: ")
                .append(b.mergedItemIds().isEmpty() ? "none" : String.join(", ", b.mergedItemIds())).append(".\n\n");
        List<ItemEntry> usable = b.items().stream().filter(i -> !i.category().equals("tm")).toList();
        sb.append(counts(usable.stream().map(ItemEntry::effectClass).toList()));
        long tm = b.items().stream().filter(i -> i.category().equals("tm")).count();
        sb.append("\nPlus ").append(tm).append(" TM items.\n\n");
        sb.append("### Items without a default price\n\n");
        List<String> noPrice = usable.stream().filter(i -> i.forSale() && i.buyPrice().isEmpty()).map(ItemEntry::id).toList();
        sb.append(noPrice.isEmpty() ? "None.\n" : "The price table of chapter 8.8 does not mention these; the admin sets a price: "
                + String.join(", ", noPrice) + ".\n");
        sb.append("\n### Items that are text-only (no effect defined)\n\n");
        List<String> manual = usable.stream().filter(i -> i.effectClass() == EffectClass.MANUAL).map(ItemEntry::id).toList();
        sb.append(manual.isEmpty() ? "None.\n" : String.join(", ", manual) + ".\n");
        if (!b.moveConflicts().isEmpty()) {
            sb.append("\n## Moves: CSV against Showdown\n\nThe CSV value is kept.\n\n| Move | Field | CSV | Showdown |\n|---|---|---|---|\n");
            b.moveConflicts().forEach(c -> sb.append("| ").append(c.move()).append(" | ").append(c.field()).append(" | ")
                    .append(c.csv()).append(" | ").append(c.showdown()).append(" |\n"));
        }
        if (!b.missingAbilities().isEmpty()) {
            sb.append("\n## Abilities Showdown does not know\n\n").append(String.join(", ", b.missingAbilities())).append(".\n");
        }
        return sb.toString();
    }

    private static String counts(List<EffectClass> classes) {
        TreeMap<EffectClass, Integer> n = new TreeMap<>();
        for (EffectClass c : EffectClass.values()) {
            n.put(c, 0);
        }
        classes.forEach(c -> n.merge(c, 1, Integer::sum));
        StringBuilder sb = new StringBuilder("| Class | Count | Share |\n|---|---:|---:|\n");
        n.forEach((c, v) -> sb.append("| ").append(c.name().toLowerCase()).append(" | ").append(v).append(" | ")
                .append(classes.isEmpty() ? "-" : String.format(java.util.Locale.ROOT, "%.1f %%", 100.0 * v / classes.size()))
                .append(" |\n"));
        return sb.toString();
    }
}
