/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.quimmilho.pokednd.core.importer.FileReport.Issue;
import net.quimmilho.pokednd.core.importer.FileReport.Kind;
import net.quimmilho.pokednd.core.importer.Raw.Item;
import net.quimmilho.pokednd.core.importer.Raw.LearnsetRow;
import net.quimmilho.pokednd.core.importer.Raw.Move;
import net.quimmilho.pokednd.core.importer.Raw.PokedexRow;
import net.quimmilho.pokednd.core.importer.Raw.SpeciesFile;
import net.quimmilho.pokednd.core.importer.Raw.Stats;
import net.quimmilho.pokednd.core.importer.Raw.Tm;

/**
 * Reads the raw files of {@code resources/} into clean, typed records (chapter 19.3, steps 1 and 2).
 *
 * <p>A bad row never stops the import: it is skipped and written to the file's {@link FileReport} with the reason.
 * A missing file or a missing column does stop it ({@link ImportException}), because then nothing can be trusted.
 * Reading is deterministic: the same files always give equal results.
 */
public final class RawReader {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern GENDER = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)% M / ([0-9]+(?:\\.[0-9]+)?)% F$");
    private static final String SPECIES_DIR = "data/cobblemon/species";

    private RawReader() {
    }

    /** What reading one file gave. */
    public record Result<T>(List<T> records, FileReport report) {
    }

    /** Reads all inputs from the resources folder. */
    public static Raw read(Path resources) {
        Result<PokedexRow> pokedex = readPokedex(resources);
        Result<Move> attacks = readAttacks(resources);
        Result<Item> items = readItems(resources);
        Result<LearnsetRow> learnsets = readLearnsets(resources);
        Result<Tm> tms = readTms(resources);
        SpeciesResult species = readSpecies(resources);
        TreeMap<String, FileReport> reports = new TreeMap<>();
        for (FileReport r : List.of(pokedex.report(), attacks.report(), items.report(), learnsets.report(), tms.report(), species.report())) {
            reports.put(r.file(), r);
        }
        return new Raw(pokedex.records(), attacks.records(), items.records(), learnsets.records(), tms.records(),
                Collections.unmodifiableSortedMap(species.byDex()), Collections.unmodifiableSortedMap(reports));
    }

    // ---------------------------------------------------------------- CSV plumbing

    /** A row that cannot be used. The message says why, in plain words. */
    private static final class BadRow extends Exception {
        private static final long serialVersionUID = 1L;

        BadRow(String message) {
            super(message);
        }
    }

    /** Reads the cells of one row by column name. */
    private static final class Cells {
        private final Csv.Table table;
        private final Csv.Row row;
        private final List<Issue> issues;

        Cells(Csv.Table table, Csv.Row row, List<Issue> issues) {
            this.table = table;
            this.row = row;
            this.issues = issues;
        }

        /** The first cell, to say which row a message is about. */
        String label() {
            return row.cells().get(0).strip();
        }

        boolean has(String column) {
            return table.indexOf(column) >= 0;
        }

        /** The cell, trimmed; "" when blank. */
        String raw(String column) {
            return row.cells().get(table.indexOf(column)).strip();
        }

        /** A cell that must not be blank. */
        String text(String column) throws BadRow {
            String s = raw(column);
            if (s.isEmpty()) {
                throw new BadRow(column + " is blank");
            }
            return s;
        }

        /** A cell that may be blank or {@code —}, both meaning "none". */
        Optional<String> optional(String column) {
            String s = raw(column);
            return s.isEmpty() || s.equals("—") ? Optional.empty() : Optional.of(s);
        }

        int integer(String column) throws BadRow {
            String s = text(column);
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new BadRow(column + " \"" + s + "\" is not a whole number");
            }
        }

        double decimal(String column) throws BadRow {
            String s = text(column);
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                throw new BadRow(column + " \"" + s + "\" is not a number");
            }
        }

        Optional<Integer> optionalInteger(String column) throws BadRow {
            Optional<String> s = optional(column);
            if (s.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(Integer.parseInt(s.get()));
            } catch (NumberFormatException e) {
                throw new BadRow(column + " \"" + s.get() + "\" is not a whole number");
            }
        }

        void note(String message) {
            issues.add(new Issue(Kind.FIXED, row.number(), label() + ": " + message));
        }
    }

    @FunctionalInterface
    private interface RowParser<T> {
        T parse(Cells cells) throws BadRow;
    }

    private static <T> Result<T> table(Path resources, String file, List<String> columns, RowParser<T> parser) {
        Path path = resources.resolve(file);
        if (!Files.isRegularFile(path)) {
            throw new ImportException("Missing input file " + path + " (rebuild resources/ with: node tools/extract.mjs)");
        }
        Csv.Table table = Csv.read(path);
        for (String column : columns) {
            if (table.indexOf(column) < 0) {
                throw new ImportException(file + ": column \"" + column + "\" is missing (found: " + table.header() + ")");
            }
        }
        List<Issue> issues = new ArrayList<>();
        if (table.hadBom()) {
            issues.add(new Issue(Kind.FIXED, 0, "the file starts with a byte-order mark; it was removed"));
        }
        for (String fix : table.headerFixes()) {
            issues.add(new Issue(Kind.FIXED, 0, fix));
        }
        if (table.blankLines() > 0) {
            issues.add(new Issue(Kind.FIXED, 0, table.blankLines() + " blank line(s) skipped"));
        }
        List<T> records = new ArrayList<>(table.rows().size());
        for (Csv.Row row : table.rows()) {
            Cells cells = new Cells(table, row, issues);
            if (row.cells().size() != table.header().size()) {
                issues.add(new Issue(Kind.SKIPPED, row.number(), cells.label() + ": has " + row.cells().size()
                        + " cells but the header has " + table.header().size()));
                continue;
            }
            try {
                records.add(parser.parse(cells));
            } catch (BadRow e) {
                issues.add(new Issue(Kind.SKIPPED, row.number(), cells.label() + ": " + e.getMessage()));
            }
        }
        return new Result<>(List.copyOf(records), new FileReport(file, table.rows().size(), records.size(), issues));
    }

    // ---------------------------------------------------------------- the CSV files

    /** Reads {@code pokedex.csv}: splits the combined {@code Type} text, turns blank or {@code —} into "none". */
    public static Result<PokedexRow> readPokedex(Path resources) {
        return table(resources, "pokedex.csv", List.of(
                "National Dex", "Pokémon Name", "Type", "HP", "Attack", "Defense", "Special Attack", "Special Defense", "Speed",
                "Total", "Ability I", "Ability II", "Hidden Ability", "Height (ft)", "Height (m)", "Weight (lbs)", "Weight (kg)",
                "HP EVs", "Attack EVs", "Defense EVs", "Special Attack EVs", "Special Defense EVs", "Speed EVs",
                "Experience Value", "Catch Rate", "Experience Growth", "Base Friendship", "Pokedex Color", "Gender Ratio",
                "Egg Cycles", "Egg Group I", "Egg Group II", "Evolution Method"), c -> {
            List<String> types = new ArrayList<>();
            for (String t : c.text("Type").split("/")) {
                String name = t.strip();
                if (!name.isEmpty() && !name.equals("—")) {
                    types.add(name);
                }
            }
            if (types.isEmpty() || types.size() > 2) {
                throw new BadRow("Type \"" + c.raw("Type") + "\" does not hold one or two types");
            }
            // The file also has "Type I" and "Type II" columns; they must agree with the combined text.
            if (c.has("Type I") && c.has("Type II")) {
                String second = types.size() > 1 ? types.get(1) : "";
                if (!c.raw("Type I").equals(types.get(0)) || !c.raw("Type II").replace("—", "").equals(second)) {
                    c.note("Type \"" + c.raw("Type") + "\" differs from Type I/II (\"" + c.raw("Type I") + "\", \"" + c.raw("Type II")
                            + "\"); the combined text was used");
                }
            }
            Optional<Double> male;
            String gender = c.text("Gender Ratio");
            if (gender.equalsIgnoreCase("Genderless")) {
                male = Optional.empty();
            } else {
                Matcher m = GENDER.matcher(gender);
                if (!m.matches()) {
                    throw new BadRow("Gender Ratio \"" + gender + "\" is not like \"87.5% M / 12.5% F\" or \"Genderless\"");
                }
                male = Optional.of(Double.parseDouble(m.group(1)));
            }
            return new PokedexRow(
                    c.integer("National Dex"), c.text("Pokémon Name"), types.get(0),
                    types.size() > 1 ? Optional.of(types.get(1)) : Optional.empty(),
                    new Stats(c.integer("HP"), c.integer("Attack"), c.integer("Defense"), c.integer("Special Attack"),
                            c.integer("Special Defense"), c.integer("Speed")),
                    c.integer("Total"), c.text("Ability I"), c.optional("Ability II"), c.optional("Hidden Ability"),
                    c.decimal("Height (ft)"), c.decimal("Height (m)"), c.decimal("Weight (lbs)"), c.decimal("Weight (kg)"),
                    new Stats(c.integer("HP EVs"), c.integer("Attack EVs"), c.integer("Defense EVs"), c.integer("Special Attack EVs"),
                            c.integer("Special Defense EVs"), c.integer("Speed EVs")),
                    c.integer("Experience Value"), c.integer("Catch Rate"), c.text("Experience Growth"), c.integer("Base Friendship"),
                    c.text("Pokedex Color"), male, c.integer("Egg Cycles"), c.text("Egg Group I"), c.optional("Egg Group II"),
                    c.optional("Evolution Method"));
        });
    }

    /**
     * Reads {@code attack.csv}. Accuracy {@code —} means the move never misses (empty). A row without type, category or
     * numbers (one such row exists: {@code nihillight}) is skipped.
     */
    public static Result<Move> readAttacks(Path resources) {
        return table(resources, "attack.csv", List.of(
                "move_id", "name", "type", "category", "power", "accuracy", "pp", "priority", "target", "description"), c -> {
            List<String> blank = new ArrayList<>();
            for (String column : List.of("type", "category", "power", "accuracy", "pp", "priority", "target")) {
                if (c.raw(column).isEmpty()) {
                    blank.add(column);
                }
            }
            if (!blank.isEmpty()) {
                throw new BadRow("no " + String.join(", ", blank) + " (the move has no stats)");
            }
            return new Move(c.text("move_id"), c.text("name"), c.text("type"), c.text("category"), c.integer("power"),
                    c.optionalInteger("accuracy"), c.integer("pp"), c.integer("priority"), c.text("target"), c.raw("description"));
        });
    }

    /** Reads {@code item.csv}. */
    public static Result<Item> readItems(Path resources) {
        return table(resources, "item.csv", List.of("item_id", "name", "category", "icon", "description"),
                c -> new Item(c.text("item_id"), c.text("name"), c.text("category"), c.text("icon"), c.optional("description")));
    }

    /** Reads {@code pokemon-attacks.csv}. */
    public static Result<LearnsetRow> readLearnsets(Path resources) {
        return table(resources, "pokemon-attacks.csv", List.of("dex", "pokemon", "form", "move_id", "move", "method", "level", "implemented"), c -> {
            String implemented = c.text("implemented");
            if (!implemented.equals("true") && !implemented.equals("false")) {
                throw new BadRow("implemented \"" + implemented + "\" is not true or false");
            }
            return new LearnsetRow(c.integer("dex"), c.text("pokemon"), c.optional("form"), c.text("move_id"), c.text("move"),
                    c.text("method"), c.optionalInteger("level"), Boolean.parseBoolean(implemented));
        });
    }

    /** Reads {@code tm.csv}. */
    public static Result<Tm> readTms(Path resources) {
        return table(resources, "tm.csv", List.of("move_id", "name", "type", "source", "obtain", "icon"),
                c -> new Tm(c.text("move_id"), c.text("name"), c.text("type"), c.text("source"), c.optional("obtain"), c.text("icon")));
    }

    // ---------------------------------------------------------------- the species JSON files

    /** The species files by Dex number, and what happened while reading them. */
    public record SpeciesResult(TreeMap<Integer, SpeciesFile> byDex, FileReport report) {
    }

    /** Reads every JSON file under {@code data/cobblemon/species/} and indexes it by National Dex number. */
    public static SpeciesResult readSpecies(Path resources) {
        Path root = resources.resolve(SPECIES_DIR);
        if (!Files.isDirectory(root)) {
            throw new ImportException("Missing folder " + root + " (rebuild resources/ with: node tools/extract.mjs)");
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                    .sorted(java.util.Comparator.comparing(p -> relative(resources, p)))
                    .toList();
        } catch (IOException e) {
            throw new ImportException("Cannot list " + root + ": " + e.getMessage(), e);
        }
        TreeMap<Integer, SpeciesFile> byDex = new TreeMap<>();
        List<Issue> issues = new ArrayList<>();
        int number = 0;
        for (Path file : files) {
            number++;
            String rel = relative(resources, file);
            try {
                SpeciesFile s = species(JSON.readTree(Files.readString(file)), rel);
                SpeciesFile earlier = byDex.putIfAbsent(s.dex(), s);
                if (earlier != null) {
                    issues.add(new Issue(Kind.SKIPPED, number, rel + ": Dex " + s.dex() + " is already used by " + earlier.source()));
                }
            } catch (BadRow e) {
                issues.add(new Issue(Kind.SKIPPED, number, rel + ": " + e.getMessage()));
            } catch (IOException e) {
                issues.add(new Issue(Kind.SKIPPED, number, rel + ": not valid JSON (" + e.getMessage().lines().findFirst().orElse("") + ")"));
            }
        }
        return new SpeciesResult(byDex, new FileReport(SPECIES_DIR, files.size(), byDex.size(), issues));
    }

    private static String relative(Path resources, Path file) {
        return resources.relativize(file).toString().replace('\\', '/');
    }

    private static SpeciesFile species(JsonNode j, String source) throws BadRow {
        if (!j.isObject()) {
            throw new BadRow("not a JSON object");
        }
        int dex = (int) number(j, "nationalPokedexNumber"); // first, so a broken file is reported by its Dex number
        List<String> eggGroups = strings(j, "eggGroups");
        double male = number(j, "maleRatio");
        JsonNode second = j.get("secondaryType");
        return new SpeciesFile(
                dex, string(j, "name"), j.path("implemented").asBoolean(false),
                string(j, "primaryType"), second != null && second.isTextual() ? Optional.of(second.asText()) : Optional.empty(),
                stats(j, "baseStats"), stats(j, "evYield"), (int) number(j, "catchRate"), (int) number(j, "baseFriendship"),
                (int) number(j, "baseExperienceYield"), string(j, "experienceGroup"), (int) number(j, "eggCycles"), eggGroups,
                male < 0 ? Optional.empty() : Optional.of(male), (int) number(j, "height"), (int) number(j, "weight"),
                strings(j, "abilities"), strings(j, "labels"), strings(j, "moves"),
                j.has("evolutions") ? j.get("evolutions") : JSON.createArrayNode(), source, j);
    }

    private static String string(JsonNode j, String key) throws BadRow {
        JsonNode n = j.get(key);
        if (n == null || !n.isTextual() || n.asText().isBlank()) {
            throw new BadRow("\"" + key + "\" is missing or not text");
        }
        return n.asText();
    }

    private static double number(JsonNode j, String key) throws BadRow {
        JsonNode n = j.get(key);
        if (n == null || !n.isNumber()) {
            throw new BadRow("\"" + key + "\" is missing or not a number");
        }
        return n.asDouble();
    }

    private static List<String> strings(JsonNode j, String key) throws BadRow {
        JsonNode n = j.get(key);
        if (n == null) {
            return List.of();
        }
        if (!n.isArray()) {
            throw new BadRow("\"" + key + "\" is not a list");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode e : n) {
            out.add(e.asText());
        }
        return List.copyOf(out);
    }

    private static Stats stats(JsonNode j, String key) throws BadRow {
        JsonNode s = j.get(key);
        if (s == null || !s.isObject()) {
            throw new BadRow("\"" + key + "\" is missing");
        }
        return new Stats((int) number(s, "hp"), (int) number(s, "attack"), (int) number(s, "defence"),
                (int) number(s, "special_attack"), (int) number(s, "special_defence"), (int) number(s, "speed"));
    }
}
