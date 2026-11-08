/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.quimmilho.pokednd.core.dice.Dice;

/**
 * The power table (chapter 7.6, setting {@code combat.damage.powerTable}): which damage dice a move's power turns into.
 * It is data. {@link #defaults()} is the table of the specification and {@link #withRow} makes an edited copy for a game
 * that wants other dice. A table never changes after it is made.
 *
 * <p>Each row starts at its {@code fromPower} and lasts until the next row starts; the last row has no end.
 */
public final class PowerTable {

    /**
     * One row of the table.
     *
     * @param fromPower the lowest move power this row covers
     * @param dice      the damage dice for those moves
     */
    public record Row(int fromPower, Dice dice) {
        public Row {
            if (fromPower < 1) {
                throw new IllegalArgumentException("A power table row starts at 1 or more, not " + fromPower);
            }
        }
    }

    private static volatile PowerTable defaults;

    private final List<Row> rows;

    private PowerTable(List<Row> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The power table needs at least one row");
        }
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(Row::fromPower));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).fromPower() == sorted.get(i - 1).fromPower()) {
                throw new IllegalArgumentException("Two rows of the power table start at " + sorted.get(i).fromPower());
            }
        }
        if (sorted.get(0).fromPower() != 1) {
            throw new IllegalArgumentException("The first row of the power table must start at power 1, not " + sorted.get(0).fromPower());
        }
        this.rows = List.copyOf(sorted);
    }

    /** A table from rows (any order). The first row must start at power 1. */
    public static PowerTable of(List<Row> rows) {
        return new PowerTable(rows);
    }

    /** The table of chapter 7.6, read from {@code rules/power-table.json}. */
    public static PowerTable defaults() {
        PowerTable table = defaults;
        if (table == null) {
            synchronized (PowerTable.class) {
                if (defaults == null) {
                    try (InputStream in = PowerTable.class.getResourceAsStream("/rules/power-table.json")) {
                        if (in == null) {
                            throw new IllegalStateException("rules/power-table.json is missing from Core");
                        }
                        defaults = fromJson(new ObjectMapper().readTree(in));
                    } catch (IOException e) {
                        throw new IllegalStateException("Cannot read rules/power-table.json: " + e.getMessage(), e);
                    }
                }
                table = defaults;
            }
        }
        return table;
    }

    /** Reads {@code {"rows": [{"from": 1, "dice": "1d4"}, ...]}}. */
    public static PowerTable fromJson(JsonNode root) {
        List<Row> rows = new ArrayList<>();
        for (JsonNode r : root.path("rows")) {
            String text = r.path("dice").asText("");
            Dice dice = Dice.parse(text)
                    .orElseThrow(() -> new IllegalArgumentException("Power table: \"" + text + "\" is not dice like 2d8"));
            rows.add(new Row(r.path("from").asInt(0), dice));
        }
        return new PowerTable(rows);
    }

    /** Reads the table from JSON text. */
    public static PowerTable fromJson(String json) {
        try {
            return fromJson(new ObjectMapper().readTree(json));
        } catch (IOException e) {
            throw new IllegalArgumentException("The power table is not valid JSON: " + e.getMessage(), e);
        }
    }

    /** The table as JSON, the form a game stores in its settings. */
    public String toJson() {
        ObjectNode root = new ObjectMapper().createObjectNode();
        ArrayNode array = root.putArray("rows");
        for (Row r : rows) {
            array.addObject().put("from", r.fromPower()).put("dice", r.dice().toString());
        }
        return root.toString();
    }

    /** All rows, lowest power first. */
    public List<Row> rows() {
        return rows;
    }

    /** The damage dice for a move power. Power 0 or less (status moves, fixed damage) has no dice. */
    public Optional<Dice> diceFor(int power) {
        if (power < 1) {
            return Optional.empty();
        }
        Dice found = rows.get(0).dice();
        for (Row r : rows) {
            if (r.fromPower() <= power) {
                found = r.dice();
            } else {
                break;
            }
        }
        return Optional.of(found);
    }

    /** A copy where the moves from this power up (until the next row) roll other dice. An existing row at that power is replaced. */
    public PowerTable withRow(int fromPower, Dice dice) {
        List<Row> copy = new ArrayList<>(rows);
        copy.removeIf(r -> r.fromPower() == fromPower);
        copy.add(new Row(fromPower, dice));
        return new PowerTable(copy);
    }

    /** The highest power that uses the row at this index, or empty for the last row. */
    public Optional<Integer> upTo(int rowIndex) {
        return rowIndex + 1 < rows.size() ? Optional.of(rows.get(rowIndex + 1).fromPower() - 1) : Optional.empty();
    }

    /** The average dice total multiplied by the factor: what the row is worth as a power. 2d8 with factor 5 is 45. */
    public static double effectivePower(Dice dice, int powerFactor) {
        return (dice.min() + dice.max()) / 2.0 * powerFactor;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PowerTable other && rows.equals(other.rows);
    }

    @Override
    public int hashCode() {
        return rows.hashCode();
    }

    @Override
    public String toString() {
        return "PowerTable" + rows;
    }
}
