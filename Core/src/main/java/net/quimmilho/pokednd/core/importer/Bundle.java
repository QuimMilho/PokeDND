/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.quimmilho.pokednd.core.cards.SignatureOverrides;
import net.quimmilho.pokednd.core.importer.Catalog.AbilityEntry;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.importer.Evolutions.Rule;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Type;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Merged;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.MergedSpecies;
import net.quimmilho.pokednd.core.rules.Nature;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.TypeChart;

/**
 * The data bundle (chapter 19.2): everything the API and the mod need, built from {@code resources/} and the roster.
 *
 * <p>Building is <b>deterministic</b>: the same inputs give the same files, byte for byte, and {@code MANIFEST.json}
 * carries the hash of every input and output and one hash of the whole bundle. Writing a bundle first checks that no id
 * dangles (a learnset move that does not exist, an evolution result that does not exist, a roster species without data…)
 * and refuses to write if one does.
 *
 * @param roster      the playable species and forms
 * @param merged      merged species, conflicts and form table
 * @param evolutions  evolution rules and their cross-check
 * @param learnsets   what every form learns
 * @param catalog     moves, abilities and items
 * @param derived     stage badges, weaknesses and resistances, and the signature moves of every card
 * @param signature   how the signature moves were chosen (for the report and the tests)
 * @param overrides   the hand-written signature overrides that were applied (empty if none were given)
 * @param forms       types, abilities and weaknesses of each form (`forms.json`)
 * @param inputHashes SHA-256 of every input, by name
 */
public record Bundle(Roster roster, Merged merged, Evolutions.Built evolutions, Learnsets learnsets, Catalog.Built catalog,
                     Derived derived, Signature signature, SignatureOverrides overrides, FormDetails forms,
                     Map<String, String> inputHashes) {

    /** Changes when the shape of the bundle files changes. */
    public static final int FORMAT = 1;

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /** Reads the inputs and builds everything, without signature-move overrides. See {@link #build(Path, Path, Path)}. */
    public static Bundle build(Path resources, Path rosterFile) {
        return build(resources, rosterFile, null);
    }

    /**
     * Reads the inputs and builds everything. It does not check for dangling ids: call {@link #validate()} or {@link #write}.
     *
     * @param overridesFile {@code cards/signature-overrides.json}, or null for no overrides
     */
    public static Bundle build(Path resources, Path rosterFile, Path overridesFile) {
        Raw raw = RawReader.read(resources);
        Roster roster = Roster.read(rosterFile);
        Merged merged = SpeciesMerger.merge(raw, roster);
        Evolutions.Built evolutions = Evolutions.build(raw);
        Learnsets learnsets = Learnsets.build(raw, merged);
        Showdown showdown = Showdown.load();
        Catalog.Built catalog = Catalog.build(raw, merged.species(), showdown, Catalog.english(resources));
        Derived plain = Derived.build(merged.species(), evolutions.rules(), TypeChart.standard());
        SignatureOverrides overrides = overridesFile == null ? SignatureOverrides.none() : SignatureOverrides.read(overridesFile);
        Signature signature = Signature.build(raw, roster, merged, learnsets, catalog, plain, overrides);
        FormDetails forms = FormDetails.build(raw, roster, merged, TypeChart.standard());
        Map<String, String> hashes = inputHashes(resources, rosterFile);
        if (overridesFile != null) {
            hashes.put("cards/signature-overrides.json", sha256(overridesFile));
        }
        return new Bundle(roster, merged, evolutions, learnsets, catalog, plain.withSignature(signature), signature, overrides, forms, hashes);
    }

    // ---------------------------------------------------------------- validation

    /** Every dangling id and other inconsistency, in plain words. Empty when the bundle is sound. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        Set<Integer> dexes = new TreeSet<>();
        Map<String, MergedSpecies> speciesById = new TreeMap<>();
        for (MergedSpecies s : merged.species()) {
            dexes.add(s.dex());
            speciesById.put(s.id(), s);
        }
        Set<String> moves = new TreeSet<>();
        catalog.moves().forEach(m -> moves.add(m.id()));
        Set<String> abilities = new TreeSet<>();
        catalog.abilities().forEach(a -> abilities.add(a.id()));
        Set<String> items = new TreeSet<>();
        catalog.items().forEach(i -> items.add(i.id()));

        for (Roster.Entry r : roster.species().values()) {
            MergedSpecies s = speciesById.get(r.id());
            if (s == null) {
                problems.add("roster species " + r.id() + " (Dex " + r.dex() + ") has no species data");
                continue;
            }
            if (!s.playable()) {
                problems.add("roster species " + r.id() + " is not marked playable in the species list");
            }
            for (Roster.Form f : r.forms()) {
                if (s.forms().stream().noneMatch(x -> x.id().equals(f.id()))) {
                    problems.add("roster form " + f.id() + " is missing from the forms of " + r.id());
                }
            }
        }
        for (MergedSpecies s : merged.species()) {
            for (String a : s.abilities()) {
                if (!abilities.contains(a)) {
                    problems.add(s.id() + " has the ability " + a + ", which is not in the abilities table");
                }
            }
            s.hiddenAbility().ifPresent(a -> {
                if (!abilities.contains(a)) {
                    problems.add(s.id() + " has the hidden ability " + a + ", which is not in the abilities table");
                }
            });
        }
        learnsets.bySpecies().forEach((species, forms) -> {
            MergedSpecies s = speciesById.get(species);
            if (s == null) {
                problems.add("learnsets: species " + species + " does not exist");
                return;
            }
            forms.forEach((formId, l) -> {
                if (s.forms().stream().noneMatch(f -> f.id().equals(formId))) {
                    problems.add("learnsets: form " + formId + " is not a form of " + species);
                }
            });
        });
        for (String move : learnsets.allMoves()) {
            if (!moves.contains(move)) {
                problems.add("a learnset move does not exist: " + move);
            }
        }
        for (Rule r : evolutions.rules()) {
            if (!dexes.contains(r.fromDex())) {
                problems.add("evolution from Dex " + r.fromDex() + ": no such species");
            }
            if (!dexes.contains(r.toDex())) {
                problems.add("evolution result \"" + r.to() + "\" of " + r.from() + " does not exist");
            }
            if (r.item() != null && !r.item().contains(":") && !items.contains(r.item())) {
                problems.add("evolution " + r.from() + " -> " + r.to() + " uses the item " + r.item() + ", which does not exist");
            }
            for (EvolutionParser.Condition c : r.conditions()) {
                if (c.type() == Type.HOLDING_ITEM && !c.value().contains(":") && !items.contains(c.value())) {
                    problems.add("evolution " + r.from() + " -> " + r.to() + " needs the held item " + c.value() + ", which does not exist");
                }
                if ((c.type() == Type.KNOWS_MOVE || c.type() == Type.USE_MOVE) && !moves.contains(c.value())) {
                    problems.add("evolution " + r.from() + " -> " + r.to() + " needs the move " + c.value() + ", which does not exist");
                }
            }
        }
        Set<String> natures = new TreeSet<>();
        Nature.all().forEach(n -> natures.add(n.id()));
        for (ItemEntry i : catalog.items()) {
            i.move().ifPresent(m -> {
                if (!moves.contains(m)) {
                    problems.add("the TM " + i.id() + " teaches " + m + ", which does not exist");
                }
            });
            i.handler().ifPresent(h -> {
                if (h.name().equals("nature_mint") && !natures.contains(h.detail().orElse(""))) {
                    problems.add("the item " + i.id() + " names a nature that does not exist");
                }
                if (h.name().equals("type_gem") && PokemonType.parse(h.detail().orElse("")).isEmpty()) {
                    problems.add("the item " + i.id() + " names a type that does not exist");
                }
            });
        }
        catalog.missingAbilities().forEach(a -> problems.add("Showdown has no ability " + a));
        problems.addAll(signature.problems());
        derived.species().keySet().forEach(id -> {
            if (!speciesById.containsKey(id)) {
                problems.add("derived values for unknown species " + id);
            }
        });
        return problems;
    }

    // ---------------------------------------------------------------- writing

    private static byte[] json(Object value) {
        try {
            return (JSON.writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new ImportException("Cannot write JSON: " + e.getMessage(), e);
        }
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** All files of the bundle by relative path, {@code MANIFEST.json} last. Does not validate. */
    public Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("species.json", json(Map.of("species", merged.species())));
        files.put("evolutions.json", json(Map.of("rules", evolutions.rules())));
        files.put("learnsets.json", json(Map.of("learnsets", learnsets.bySpecies())));
        files.put("moves.json", json(Map.of("moves", catalog.moves())));
        files.put("abilities.json", json(Map.of("abilities", catalog.abilities())));
        files.put("items.json", json(Map.of("items", catalog.items())));
        files.put("natures.json", json(Map.of("natures", Nature.all())));
        files.put("typechart.json", json(typeChart()));
        files.put("roster.json", json(roster));
        files.put("derived.json", json(derived));
        files.put("forms.json", json(Map.of("forms", forms.bySpecies())));
        files.put("reports/conflicts.md", text(Reports.conflicts(merged.conflicts())));
        files.put("reports/forms.md", text(Reports.forms(merged.formUses(), merged.unmapped())));
        files.put("reports/evolutions.md", text(Evolutions.report(evolutions)));
        files.put("reports/coverage.md", text(Catalog.coverageReport(catalog)));
        files.put("reports/signature.md", text(signature.report(overrides)));
        files.put("reports/bundle.md", text(summary()));
        files.put("MANIFEST.json", manifest(files));
        return files;
    }

    /** Validates, then writes every file into {@code dir}. Throws {@link ImportException} if an id dangles. */
    public void write(Path dir) {
        List<String> problems = validate();
        if (!problems.isEmpty()) {
            throw new ImportException("The bundle has " + problems.size() + " problem(s):\n  - "
                    + String.join("\n  - ", problems.subList(0, Math.min(50, problems.size())))
                    + (problems.size() > 50 ? "\n  … and " + (problems.size() - 50) + " more" : ""));
        }
        try {
            for (Map.Entry<String, byte[]> e : files().entrySet()) {
                Path p = dir.resolve(e.getKey());
                Files.createDirectories(p.getParent());
                Files.write(p, e.getValue());
            }
        } catch (IOException e) {
            throw new ImportException("Cannot write the bundle to " + dir + ": " + e.getMessage(), e);
        }
    }

    /** The SHA-256 of the whole bundle: every output file's name and hash, sorted, without the manifest itself. */
    public String outputHash() {
        return outputHash(outputHashes(files()));
    }

    private static TreeMap<String, String> outputHashes(Map<String, byte[]> files) {
        TreeMap<String, String> out = new TreeMap<>();
        files.forEach((name, bytes) -> {
            if (!name.equals("MANIFEST.json")) {
                out.put(name, sha256(bytes));
            }
        });
        return out;
    }

    private static String outputHash(Map<String, String> hashes) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(hashes).forEach((name, h) -> sb.append(name).append('\t').append(h).append('\n'));
        return sha256(text(sb.toString()));
    }

    private byte[] manifest(Map<String, byte[]> files) {
        TreeMap<String, String> outputs = outputHashes(files);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bundleFormat", FORMAT);
        m.put("cobblemon", roster.cobblemon());
        m.put("inputs", new TreeMap<>(inputHashes));
        m.put("outputs", outputs);
        m.put("outputHash", outputHash(outputs));
        try {
            return (JSON.writerWithDefaultPrettyPrinter().writeValueAsString(m) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new ImportException("Cannot write MANIFEST.json: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> typeChart() {
        TypeChart chart = TypeChart.standard();
        TreeMap<String, Map<String, Double>> rows = new TreeMap<>();
        for (PokemonType a : PokemonType.values()) {
            TreeMap<String, Double> row = new TreeMap<>();
            for (PokemonType d : PokemonType.values()) {
                if (chart.multiplier(a, d) != 1.0) {
                    row.put(d.id(), chart.multiplier(a, d));
                }
            }
            rows.put(a.id(), row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("types", Stream.of(PokemonType.values()).map(PokemonType::id).toList());
        out.put("note", "attacking type, then defending type, then the multiplier; a pair that is not listed is x1");
        out.put("chart", rows);
        return out;
    }

    private String summary() {
        StringBuilder sb = new StringBuilder("# Data bundle\n\n| What | Count |\n|---|---:|\n");
        sb.append("| Species | ").append(merged.species().size()).append(" |\n");
        sb.append("| Playable species (roster) | ").append(roster.species().size()).append(" |\n");
        sb.append("| Forms in the roster | ").append(roster.species().values().stream().mapToInt(e -> e.forms().size()).sum()).append(" |\n");
        sb.append("| Evolution rules | ").append(evolutions.rules().size()).append(" |\n");
        sb.append("| Moves | ").append(catalog.moves().size()).append(" |\n");
        sb.append("| Abilities | ").append(catalog.abilities().size()).append(" |\n");
        sb.append("| Items (without TMs) | ").append(catalog.items().stream().filter(i -> !i.category().equals("tm")).count()).append(" |\n");
        sb.append("| TM items | ").append(catalog.items().stream().filter(i -> i.category().equals("tm")).count()).append(" |\n");
        sb.append("| Learnset rows read | ").append(learnsets.rows()).append(" |\n");
        sb.append("| Learnset rows left out (unsupported form) | ").append(learnsets.skipped()).append(" |\n");
        sb.append("| Conflicts between CSV and JSON | ").append(merged.conflicts().size()).append(" |\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- hashing

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new ImportException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, String> inputHashes(Path resources, Path rosterFile) {
        TreeMap<String, String> out = new TreeMap<>();
        for (String name : List.of("pokedex.csv", "attack.csv", "item.csv", "tm.csv", "pokemon-attacks.csv",
                "assets/cobblemon/lang/en_us.json")) {
            Path p = resources.resolve(name);
            if (Files.isRegularFile(p)) {
                out.put("resources/" + name, sha256(p));
            }
        }
        Path species = resources.resolve("data/cobblemon/species");
        try (Stream<Path> walk = Files.walk(species)) {
            StringBuilder sb = new StringBuilder();
            walk.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".json"))
                    .map(f -> species.relativize(f).toString().replace('\\', '/'))
                    .sorted()
                    .forEach(rel -> sb.append(rel).append('\t').append(sha256(species.resolve(rel))).append('\n'));
            out.put("resources/data/cobblemon/species/**", sha256(text(sb.toString())));
        } catch (IOException e) {
            throw new ImportException("Cannot list " + species + ": " + e.getMessage(), e);
        }
        out.put("roster.json", sha256(rosterFile));
        for (String name : List.of("moves", "abilities", "items", "natures", "typechart", "source")) {
            try (InputStream in = Bundle.class.getResourceAsStream("/showdown/" + name + ".json")) {
                if (in != null) {
                    out.put("showdown/" + name + ".json", sha256(in.readAllBytes()));
                }
            } catch (IOException e) {
                throw new ImportException("Cannot read showdown/" + name + ".json: " + e.getMessage(), e);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers for callers

    /** A copy with a different catalog (used by tests that break something on purpose). */
    public Bundle withCatalog(Catalog.Built other) {
        return new Bundle(roster, merged, evolutions, learnsets, other, derived, signature, overrides, forms, inputHashes);
    }

    /** The moves table without one move (used by tests that break something on purpose). */
    public Bundle withoutMove(String id) {
        List<MoveEntry> moves = catalog.moves().stream().filter(m -> !m.id().equals(id)).toList();
        Catalog.Built c = catalog;
        return withCatalog(new Catalog.Built(moves, c.abilities(), c.items(), c.moveConflicts(), c.moveRowsSkipped(),
                c.missingAbilities(), c.itemRowsUsed(), c.itemRowsDropped(), c.mergedItemIds(), c.tmMissingMoves()));
    }

    /** The abilities table without one ability. */
    public Bundle withoutAbility(String id) {
        List<AbilityEntry> abilities = catalog.abilities().stream().filter(a -> !a.id().equals(id)).toList();
        Catalog.Built c = catalog;
        return withCatalog(new Catalog.Built(c.moves(), abilities, c.items(), c.moveConflicts(), c.moveRowsSkipped(),
                c.missingAbilities(), c.itemRowsUsed(), c.itemRowsDropped(), c.mergedItemIds(), c.tmMissingMoves()));
    }
}
