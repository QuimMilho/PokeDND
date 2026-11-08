/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.SortedMap;
import java.util.Optional;

/**
 * Everything read from the raw files in {@code resources/}, cleaned and typed but not merged (chapter 19.3, steps 1 and 2).
 * Merging the CSV and the species JSON is the next phase.
 *
 * <p>Where a value can be missing the field is an {@link Optional}: a blank cell, or {@code —}, means "none".
 *
 * @param pokedex   {@code pokedex.csv}, in file order
 * @param attacks   {@code attack.csv}, in file order, without the rows that were skipped
 * @param items     {@code item.csv}, in file order (the Pokédex rows are still here; they are dropped when items are built)
 * @param learnsets {@code pokemon-attacks.csv}, in file order
 * @param tms       {@code tm.csv}, in file order
 * @param species   the species JSON files, by Dex number (sorted)
 * @param reports   one report per input, by file name
 */
public record Raw(
        List<PokedexRow> pokedex,
        List<Move> attacks,
        List<Item> items,
        List<LearnsetRow> learnsets,
        List<Tm> tms,
        SortedMap<Integer, SpeciesFile> species,
        SortedMap<String, FileReport> reports) {

    /** Six values: base stats, or EV yield. */
    public record Stats(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed) {
    }

    /**
     * A row of {@code pokedex.csv}.
     *
     * @param dex            National Dex number
     * @param name           name as written (for example {@code Mr. Mime})
     * @param type1          first type
     * @param type2          second type, if any
     * @param baseStats      the six base stats
     * @param total          the "Total" column as written (it is not recomputed)
     * @param ability1       first ability
     * @param ability2       second ability, if any
     * @param hiddenAbility  hidden ability, if any
     * @param heightFeet     height in feet
     * @param heightMetres   height in metres
     * @param weightPounds   weight in pounds
     * @param weightKg       weight in kilograms
     * @param evYield        EVs given when defeated
     * @param baseExperience base experience value
     * @param catchRate      catch rate
     * @param growth         growth group as written (for example {@code Medium Slow})
     * @param baseFriendship base friendship
     * @param color          Pokédex colour
     * @param malePercent    percentage of males; empty for a genderless species
     * @param eggCycles      egg cycles
     * @param eggGroup1      first egg group
     * @param eggGroup2      second egg group, if any
     * @param evolutionText  the free-form evolution text, if any (parsed in a later phase)
     */
    public record PokedexRow(
            int dex, String name, String type1, Optional<String> type2, Stats baseStats, int total,
            String ability1, Optional<String> ability2, Optional<String> hiddenAbility,
            double heightFeet, double heightMetres, double weightPounds, double weightKg,
            Stats evYield, int baseExperience, int catchRate, String growth, int baseFriendship, String color,
            Optional<Double> malePercent, int eggCycles, String eggGroup1, Optional<String> eggGroup2,
            Optional<String> evolutionText) {
    }

    /**
     * A row of {@code attack.csv}.
     *
     * @param id          Showdown-style id (for example {@code absorb})
     * @param category    Physical, Special or Status
     * @param accuracy    percent; empty when the move never misses ({@code —} in the file)
     * @param target      target as written (for example {@code allAdjacentFoes})
     */
    public record Move(
            String id, String name, String type, String category, int power, Optional<Integer> accuracy,
            int pp, int priority, String target, String description) {
    }

    /**
     * A row of {@code item.csv}.
     *
     * @param category category as written (for example {@code poke_balls}); {@code pokedexes} rows are not real items
     * @param icon     path of the icon inside {@code resources/}
     */
    public record Item(String id, String name, String category, String icon, Optional<String> description) {
    }

    /**
     * A row of {@code pokemon-attacks.csv}: one move learnt by one species or form.
     *
     * @param dex         National Dex number
     * @param species     species name as written
     * @param form        free-text form (for example {@code Alola}); empty for the base form
     * @param method      level, egg, tm, tutor, legacy, special or form_change
     * @param level       level, for the {@code level} method
     * @param implemented the species has assets in the game
     */
    public record LearnsetRow(
            int dex, String species, Optional<String> form, String moveId, String moveName, String method,
            Optional<Integer> level, boolean implemented) {
    }

    /**
     * A row of {@code tm.csv}.
     *
     * @param source {@code cobblemon} (defined by Cobblemon) or {@code generated} (made for this project)
     * @param obtain how Cobblemon hands it out ({@code default} or {@code unlockable}); empty for generated ones
     */
    public record Tm(String moveId, String name, String type, String source, Optional<String> obtain, String icon) {
    }

    /**
     * One species JSON file of Cobblemon ({@code data/cobblemon/species/**}). Only what the next phases need is typed;
     * {@code json} keeps the whole file for the rest (hitbox, behaviour, drops, forms, riding…).
     *
     * @param dex                National Dex number
     * @param name               species name as written
     * @param implemented        Cobblemon marks the species as having a model
     * @param primaryType        first type (lower case)
     * @param secondaryType      second type, if any
     * @param baseStats          base stats
     * @param evYield            EV yield
     * @param catchRate          catch rate
     * @param baseFriendship     base friendship
     * @param baseExperienceYield base experience yield
     * @param experienceGroup    growth group (for example {@code medium_slow})
     * @param eggCycles          egg cycles
     * @param eggGroups          egg groups (lower case)
     * @param maleRatio          share of males, 0 to 1; empty for genderless
     * @param height             height in decimetres (Cobblemon's unit)
     * @param weight             weight in hectograms (Cobblemon's unit)
     * @param abilities          abilities as written ({@code h:} marks the hidden one)
     * @param labels             labels (for example {@code gen1})
     * @param moves              moves as written ({@code 1:tackle}, {@code tm:cut}, {@code egg:curse})
     * @param evolutions         the {@code evolutions} array (an empty array when absent)
     * @param source             file path relative to {@code resources/}
     * @param json               the complete file
     */
    public record SpeciesFile(
            int dex, String name, boolean implemented, String primaryType, Optional<String> secondaryType,
            Stats baseStats, Stats evYield, int catchRate, int baseFriendship, int baseExperienceYield,
            String experienceGroup, int eggCycles, List<String> eggGroups, Optional<Double> maleRatio,
            int height, int weight, List<String> abilities, List<String> labels, List<String> moves,
            JsonNode evolutions, String source, JsonNode json) {
    }
}
