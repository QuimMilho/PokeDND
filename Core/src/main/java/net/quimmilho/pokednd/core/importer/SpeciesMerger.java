/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import net.quimmilho.pokednd.core.importer.Forms.FormRef;
import net.quimmilho.pokednd.core.importer.Raw.LearnsetRow;
import net.quimmilho.pokednd.core.importer.Raw.PokedexRow;
import net.quimmilho.pokednd.core.importer.Raw.SpeciesFile;
import net.quimmilho.pokednd.core.importer.Raw.Stats;

/**
 * Merges {@code pokedex.csv} and the Cobblemon species JSON into one species list (chapter 19.3, step 3) and maps the
 * form texts of the learnsets to form ids (chapter 19.4).
 *
 * <p>Rule (decided): start from the CSV row, fill the gaps from the JSON; if both say something different <b>the CSV
 * wins</b> and the difference is written to the conflicts report. The result does not depend on the order of anything: the
 * same inputs always give the same output.
 */
public final class SpeciesMerger {

    private SpeciesMerger() {
    }

    /**
     * One species, merged.
     *
     * @param dex            National Dex number
     * @param id             Cobblemon species id (the JSON file name, for example {@code mrmime})
     * @param name           name from the CSV
     * @param types          one or two types, lower case
     * @param baseStats      base stats
     * @param abilities      the normal abilities (one or two), as ids
     * @param hiddenAbility  the hidden ability, as an id
     * @param heightMetres   height in metres
     * @param weightKg       weight in kilograms
     * @param evYield        EVs given when defeated
     * @param baseExperience base experience value
     * @param catchRate      catch rate
     * @param growth         growth group, lower case with underscores (for example {@code medium_slow})
     * @param baseFriendship base friendship
     * @param color          Pokédex colour
     * @param malePercent    percentage of males; empty for a genderless species
     * @param eggCycles      egg cycles
     * @param eggGroups      egg groups, lower case
     * @param evolutionText  the CSV evolution text (parsed into rules in {@code evolutions.json})
     * @param implemented    Cobblemon marks the species as having a model
     * @param playable       the species is in the roster (its assets are complete)
     * @param labels         labels from the JSON (for example {@code gen1})
     * @param preEvolution   the species this one evolves from, if the JSON says so
     * @param baseScale      model scale from the JSON, if present
     * @param hitbox         hitbox from the JSON, if present
     * @param shoulderMountable can sit on a trainer's shoulder
     * @param behaviour      the JSON's {@code behaviour} object, as it is
     * @param drops          the JSON's {@code drops} object, as it is
     * @param features       the JSON's species features (for example {@code alolan})
     * @param forms          every form known for the species: playable ones first, then the ones only learnsets mention
     */
    public record MergedSpecies(
            int dex, String id, String name, List<String> types, Stats baseStats, List<String> abilities,
            Optional<String> hiddenAbility, double heightMetres, double weightKg, Stats evYield, int baseExperience,
            int catchRate, String growth, int baseFriendship, String color, Optional<Double> malePercent, int eggCycles,
            List<String> eggGroups, Optional<String> evolutionText, boolean implemented, boolean playable,
            List<String> labels, Optional<String> preEvolution, Optional<Double> baseScale, Optional<Hitbox> hitbox,
            boolean shoulderMountable, JsonNode behaviour, JsonNode drops, List<String> features, List<FormRef> forms) {
        public MergedSpecies {
            types = List.copyOf(types);
            abilities = List.copyOf(abilities);
            eggGroups = List.copyOf(eggGroups);
            labels = List.copyOf(labels);
            features = List.copyOf(features);
            forms = List.copyOf(forms);
        }
    }

    /**
     * A species' size in the world.
     *
     * @param width  width in blocks
     * @param height height in blocks
     * @param fixed  Cobblemon does not scale it with the model
     */
    public record Hitbox(double width, double height, boolean fixed) {
    }

    /**
     * A place where the CSV and the JSON (or the roster) disagree.
     *
     * @param dex     National Dex number
     * @param species species id
     * @param field   what differs
     * @param csv     the CSV value (this one is kept)
     * @param other   the other source's value
     * @param source  who disagrees: {@code json} or {@code roster}
     */
    public record Conflict(int dex, String species, String field, String csv, String other, String source) {
    }

    /**
     * One form text of one species as the learnsets use it.
     *
     * @param dex     National Dex number
     * @param species species id
     * @param text    the form text as written
     * @param form    the form id, or empty when the text is unmapped
     * @param rows    learnset rows that use it
     */
    public record FormUse(int dex, String species, String text, Optional<FormRef> form, int rows) {
    }

    /**
     * The result of merging.
     *
     * @param species   merged species, by Dex number
     * @param conflicts every disagreement, sorted
     * @param formUses  every (species, form text) pair of the learnsets, sorted, with its form id
     * @param unmapped  form texts that are not reviewed values or are unsupported, with the rows that use them
     */
    public record Merged(List<MergedSpecies> species, List<Conflict> conflicts, List<FormUse> formUses,
                         TreeMap<String, Integer> unmapped) {
    }

    /** Merges the species and maps the forms. */
    public static Merged merge(Raw raw, Roster roster) {
        Map<Integer, SpeciesFile> json = raw.species();
        List<Conflict> conflicts = new ArrayList<>();

        // The forms the learnsets use, by species.
        Map<Integer, String> idByDex = new TreeMap<>();
        json.forEach((dex, s) -> idByDex.put(dex, idOf(s)));
        record Key(int dex, String text) {
        }
        TreeMap<String, Integer> rowsPerKey = new TreeMap<>();
        Map<String, Key> keys = new TreeMap<>();
        TreeMap<String, Integer> unmapped = new TreeMap<>();
        for (LearnsetRow row : raw.learnsets()) {
            String text = row.form().orElse("");
            String k = String.format(Locale.ROOT, "%05d|%s", row.dex(), text);
            rowsPerKey.merge(k, 1, Integer::sum);
            keys.putIfAbsent(k, new Key(row.dex(), text));
        }
        List<FormUse> formUses = new ArrayList<>();
        Map<Integer, List<FormRef>> learnsetForms = new TreeMap<>();
        for (Map.Entry<String, Key> e : keys.entrySet()) {
            Key key = e.getValue();
            int rows = rowsPerKey.get(e.getKey());
            String speciesId = idByDex.getOrDefault(key.dex(), "dex" + key.dex());
            Optional<FormRef> form = Forms.map(speciesId, key.dex(), key.text(), roster);
            if (form.isEmpty()) {
                unmapped.merge(key.text(), rows, Integer::sum);
            } else {
                learnsetForms.computeIfAbsent(key.dex(), d -> new ArrayList<>()).add(form.get());
            }
            formUses.add(new FormUse(key.dex(), speciesId, key.text(), form, rows));
        }

        List<MergedSpecies> merged = new ArrayList<>();
        for (PokedexRow csv : raw.pokedex()) {
            SpeciesFile js = json.get(csv.dex());
            if (js == null) {
                conflicts.add(new Conflict(csv.dex(), "dex" + csv.dex(), "species file", csv.name(), "(no JSON file)", "json"));
                continue;
            }
            String id = idOf(js);
            compare(conflicts, csv, id, js);
            Roster.Entry rosterEntry = roster.species().get(csv.dex());
            boolean playable = rosterEntry != null;
            if (playable && !js.implemented()) {
                conflicts.add(new Conflict(csv.dex(), id, "implemented", "(in the roster)", "false", "roster"));
            }
            merged.add(mergeOne(csv, js, id, playable, forms(id, rosterEntry, learnsetForms.get(csv.dex()))));
        }
        merged.sort(Comparator.comparingInt(MergedSpecies::dex));
        for (SpeciesFile js : json.values()) {
            if (raw.pokedex().stream().noneMatch(r -> r.dex() == js.dex())) {
                conflicts.add(new Conflict(js.dex(), idOf(js), "species row", "(no CSV row)", js.name(), "json"));
            }
        }
        conflicts.sort(Comparator.comparingInt(Conflict::dex).thenComparing(Conflict::field).thenComparing(Conflict::source));
        formUses.sort(Comparator.comparingInt(FormUse::dex).thenComparing(FormUse::text));
        return new Merged(List.copyOf(merged), List.copyOf(conflicts), List.copyOf(formUses), unmapped);
    }

    /** The species id: the JSON file name without {@code .json}. */
    static String idOf(SpeciesFile s) {
        String file = s.source().substring(s.source().lastIndexOf('/') + 1);
        return file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
    }

    /** Playable forms first (as the roster names them), then the forms only the learnsets mention; no duplicates. */
    private static List<FormRef> forms(String id, Roster.Entry rosterEntry, List<FormRef> fromLearnsets) {
        Map<String, FormRef> out = new LinkedHashMap<>();
        if (rosterEntry != null) {
            for (Roster.Form f : rosterEntry.forms()) {
                String slug = f.id().startsWith(id + "_") ? f.id().substring(id.length() + 1) : f.id();
                Forms.Kind kind = f.aspects().isEmpty() ? Forms.Kind.BASE : Forms.Kind.NAMED;
                out.put(f.id(), new FormRef(f.id(), "", slug, kind, true));
            }
        }
        if (fromLearnsets != null) {
            for (FormRef f : fromLearnsets) {
                if (f.playable() || !out.containsKey(f.id())) {
                    out.put(f.id(), f);
                }
            }
        }
        List<FormRef> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparing((FormRef f) -> f.kind() != Forms.Kind.BASE).thenComparing(f -> !f.playable())
                .thenComparing(FormRef::id));
        return list;
    }

    private static MergedSpecies mergeOne(PokedexRow csv, SpeciesFile js, String id, boolean playable, List<FormRef> forms) {
        List<String> types = new ArrayList<>();
        types.add(lower(csv.type1()));
        csv.type2().ifPresent(t -> types.add(lower(t)));
        List<String> abilities = new ArrayList<>();
        abilities.add(Forms.squash(csv.ability1()));
        csv.ability2().ifPresent(a -> abilities.add(Forms.squash(a)));
        List<String> eggGroups = new ArrayList<>();
        eggGroups.add(lower(csv.eggGroup1()));
        csv.eggGroup2().ifPresent(g -> eggGroups.add(lower(g)));
        JsonNode j = js.json();
        Optional<Hitbox> hitbox = j.has("hitbox")
                ? Optional.of(new Hitbox(j.get("hitbox").path("width").asDouble(), j.get("hitbox").path("height").asDouble(),
                        j.get("hitbox").path("fixed").asBoolean(false)))
                : Optional.empty();
        List<String> features = new ArrayList<>();
        j.path("features").forEach(f -> features.add(f.asText()));
        return new MergedSpecies(csv.dex(), id, csv.name(), types, csv.baseStats(), abilities,
                csv.hiddenAbility().map(Forms::squash), csv.heightMetres(), csv.weightKg(), csv.evYield(),
                csv.baseExperience(), csv.catchRate(), growth(csv.growth()), csv.baseFriendship(), csv.color(),
                csv.malePercent(), csv.eggCycles(), eggGroups, csv.evolutionText(), js.implemented(), playable,
                js.labels(), j.hasNonNull("preEvolution") ? Optional.of(j.get("preEvolution").asText()) : Optional.empty(),
                j.has("baseScale") ? Optional.of(j.get("baseScale").asDouble()) : Optional.empty(), hitbox,
                j.path("shoulderMountable").asBoolean(false), orEmpty(j.path("behaviour")), orEmpty(j.path("drops")), features,
                forms);
    }

    private static JsonNode orEmpty(JsonNode n) {
        return n instanceof MissingNode ? new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode() : n;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static String growth(String csv) {
        return lower(csv).replace(' ', '_');
    }

    // ---------------------------------------------------------------- comparing the two sources

    private static void compare(List<Conflict> out, PokedexRow csv, String id, SpeciesFile js) {
        int dex = csv.dex();
        diff(out, dex, id, "name", Forms.squash(csv.name()), Forms.squash(js.name()), csv.name(), js.name());
        List<String> jsTypes = new ArrayList<>();
        jsTypes.add(lower(js.primaryType()));
        js.secondaryType().ifPresent(t -> jsTypes.add(lower(t)));
        List<String> csvTypes = new ArrayList<>();
        csvTypes.add(lower(csv.type1()));
        csv.type2().ifPresent(t -> csvTypes.add(lower(t)));
        diff(out, dex, id, "types", csvTypes, jsTypes);
        compareStats(out, dex, id, "baseStats", csv.baseStats(), js.baseStats());
        compareStats(out, dex, id, "evYield", csv.evYield(), js.evYield());
        Stats b = csv.baseStats();
        int sum = b.hp() + b.attack() + b.defense() + b.specialAttack() + b.specialDefense() + b.speed();
        diff(out, dex, id, "total (CSV column vs sum of its own stats)", csv.total(), sum);
        diff(out, dex, id, "catchRate", csv.catchRate(), js.catchRate());
        diff(out, dex, id, "baseFriendship", csv.baseFriendship(), js.baseFriendship());
        diff(out, dex, id, "baseExperience", csv.baseExperience(), js.baseExperienceYield());
        diff(out, dex, id, "growth", growth(csv.growth()), js.experienceGroup());
        diff(out, dex, id, "eggCycles", csv.eggCycles(), js.eggCycles());
        List<String> csvEgg = new ArrayList<>();
        csvEgg.add(lower(csv.eggGroup1()));
        csv.eggGroup2().ifPresent(g -> csvEgg.add(lower(g)));
        List<String> jsEgg = js.eggGroups().stream().map(SpeciesMerger::lower).toList();
        diff(out, dex, id, "eggGroups", csvEgg, jsEgg);
        diff(out, dex, id, "height (decimetres)", (int) Math.round(csv.heightMetres() * 10), js.height());
        diff(out, dex, id, "weight (hectograms)", (int) Math.round(csv.weightKg() * 10), js.weight());
        String csvMale = csv.malePercent().map(SpeciesMerger::percent).orElse("genderless");
        String jsMale = js.maleRatio().map(r -> percent(r * 100)).orElse("genderless");
        diff(out, dex, id, "malePercent", csvMale, jsMale);

        List<String> jsNormal = new ArrayList<>();
        Optional<String> jsHidden = Optional.empty();
        for (String a : js.abilities()) {
            if (a.startsWith("h:")) {
                jsHidden = Optional.of(Forms.squash(a.substring(2)));
            } else {
                jsNormal.add(Forms.squash(a));
            }
        }
        List<String> csvNormal = new ArrayList<>();
        csvNormal.add(Forms.squash(csv.ability1()));
        csv.ability2().ifPresent(a -> csvNormal.add(Forms.squash(a)));
        diff(out, dex, id, "abilities", csvNormal, jsNormal);
        diff(out, dex, id, "hiddenAbility", csv.hiddenAbility().map(Forms::squash).orElse("none"), jsHidden.orElse("none"));
    }

    private static String percent(double p) {
        return String.format(Locale.ROOT, "%.1f", p);
    }

    private static void compareStats(List<Conflict> out, int dex, String id, String field, Stats csv, Stats js) {
        if (!csv.equals(js)) {
            out.add(new Conflict(dex, id, field, stats(csv), stats(js), "json"));
        }
    }

    private static String stats(Stats s) {
        return s.hp() + "/" + s.attack() + "/" + s.defense() + "/" + s.specialAttack() + "/" + s.specialDefense() + "/" + s.speed();
    }

    private static void diff(List<Conflict> out, int dex, String id, String field, Object csv, Object other) {
        diff(out, dex, id, field, csv, other, String.valueOf(csv), String.valueOf(other));
    }

    private static void diff(List<Conflict> out, int dex, String id, String field, Object csv, Object other, String csvShown,
                             String otherShown) {
        if (!csv.equals(other)) {
            out.add(new Conflict(dex, id, field, csvShown, otherShown, "json"));
        }
    }

    /** Every form text found in the learnsets, sorted, for tests and reports. */
    public static TreeSet<String> formTexts(Raw raw) {
        TreeSet<String> out = new TreeSet<>();
        for (LearnsetRow row : raw.learnsets()) {
            out.add(row.form().orElse(""));
        }
        return out;
    }
}
