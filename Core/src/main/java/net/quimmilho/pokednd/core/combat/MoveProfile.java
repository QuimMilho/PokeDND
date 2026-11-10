/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Optional;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * What the damage and hit calculators need to know about a move, read from the imported move data (chapters 7.5, 7.6).
 *
 * @param id          the move id
 * @param type        the move's type
 * @param category    physical, special or status
 * @param power       base power; 0 or 1 for fixed-damage, one-hit-KO and variable-power moves
 * @param accuracy    percent; empty when the move never misses (the dash)
 * @param critStage   0 for most moves, 1 for high-crit moves (Showdown crit ratio 2), and so on
 * @param alwaysCrit  true for moves that always crit (Frost Breath)
 * @param ohko        true for the one-hit-KO moves
 * @param fixedDamage the damage that skips the dice (Seismic Toss, Dragon Rage…), if any
 */
public record MoveProfile(String id, PokemonType type, Category category, int power, Optional<Integer> accuracy, int critStage,
                          boolean alwaysCrit, boolean ohko, Optional<FixedDamage> fixedDamage) {

    /** Physical, special or status. */
    public enum Category {
        PHYSICAL, SPECIAL, STATUS;

        /** Reads {@code Physical}, {@code special}… */
        public static Category parse(String text) {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Damage that does not use the dice.
     *
     * @param amount the damage, or 0 when the damage is the user's level
     * @param level  true if the damage equals the user's level (Seismic Toss, Night Shade)
     */
    public record FixedDamage(int amount, boolean level) {

        /** The damage for an attacker of this level. */
        public int damage(int attackerLevel) {
            return level ? attackerLevel : amount;
        }
    }

    /** Reads a move from the bundle. */
    public static MoveProfile from(MoveEntry move) {
        JsonNode effect = move.effect();
        int critRatio = effect == null ? 1 : effect.path("critRatio").asInt(1);
        boolean alwaysCrit = effect != null && effect.path("willCrit").asBoolean(false);
        boolean ohko = effect != null && effect.path("ohko").asBoolean(false);
        Optional<FixedDamage> fixed = Optional.empty();
        if (effect != null && effect.has("damage")) {
            JsonNode d = effect.get("damage");
            fixed = Optional.of(d.isNumber() ? new FixedDamage(d.asInt(), false) : new FixedDamage(0, true));
        }
        PokemonType type = PokemonType.parse(move.type())
                .orElseThrow(() -> new IllegalArgumentException(move.id() + ": unknown type " + move.type()));
        return new MoveProfile(move.id(), type, Category.parse(move.category()), move.power(), move.accuracy(),
                Math.max(0, critRatio - 1), alwaysCrit, ohko, fixed);
    }

    /** A copy of another type. */
    public MoveProfile withType(PokemonType value) {
        return new MoveProfile(id, value, category, power, accuracy, critStage, alwaysCrit, ohko, fixedDamage);
    }

    /** A copy of another category. */
    public MoveProfile withCategory(Category value) {
        return new MoveProfile(id, type, value, power, accuracy, critStage, alwaysCrit, ohko, fixedDamage);
    }

    /** A copy with another base power. */
    public MoveProfile withPower(int value) {
        return new MoveProfile(id, type, category, value, accuracy, critStage, alwaysCrit, ohko, fixedDamage);
    }

    /** A copy with this fixed damage (empty for none). */
    public MoveProfile withFixedDamage(Optional<FixedDamage> value) {
        return new MoveProfile(id, type, category, power, accuracy, critStage, alwaysCrit, ohko, value);
    }

    /** True for physical and special moves. */
    public boolean damaging() {
        return category != Category.STATUS;
    }

    /** True if the damage comes from dice and the formula (not fixed damage, not one-hit KO, and a power the table covers). */
    public boolean usesDamageDice() {
        return damaging() && !ohko && fixedDamage.isEmpty() && power >= 1;
    }

    /** The damage dice of this move; empty for status, fixed-damage and one-hit-KO moves. */
    public Optional<Dice> damageDice(CombatSettings settings) {
        return usesDamageDice() ? settings.powerTable().diceFor(power) : Optional.empty();
    }

    /** The hit input for a normal case: no stages, no bonuses. */
    public HitRoll.Input normalHitInput() {
        return new HitRoll.Input(accuracy, 0, 0, 0, 0, critStage, alwaysCrit);
    }

    /**
     * The hit and crit bands for a normal case (as printed on a card). A status move that never misses has nothing to roll;
     * a status move with an accuracy has a miss band and no crit.
     */
    public HitRoll.Odds normalHit(CombatSettings settings) {
        if (ohko) {
            return HitRoll.oneHitKo(50, 50, net.quimmilho.pokednd.core.dice.DiceSettings.defaults());
        }
        HitRoll.Input input = normalHitInput();
        if (!damaging()) {
            input = input.withCritStage(0).withAlwaysCrit(false);
            return HitRoll.compute(input, settings.withCritChancesPct(java.util.List.of(0, 0, 0, 0)));
        }
        return HitRoll.compute(input, settings);
    }
}
