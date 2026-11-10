/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;

/**
 * A merchant NPC's shop (chapter 8.8): what it sells, whether it buys, how much dearer or cheaper it is than the price table, and when it
 * fills its shelves again. The stock is part of the game state because it changes while the game is played. Where the merchant stands
 * belongs to the NPC (phase 52); the rules only need its id.
 *
 * @param id              the id of the merchant (also the id of its NPC)
 * @param name            the name for the screen
 * @param sells           true if players can buy from it
 * @param buys            true if players can sell to it
 * @param priceMultiplier what its buy prices are multiplied by (1.0 = the table, 1.5 = half again as dear)
 * @param restockRounds   rounds between two restocks, 0 for never ({@code economy.merchant.restockRounds} is the default)
 * @param lastRestock     the round of the last restock (or of the day the merchant was set up)
 * @param stock           the shelves, in the order the merchant shows them
 */
public record Merchant(String id, String name, boolean sells, boolean buys, double priceMultiplier, int restockRounds, int lastRestock,
                       List<StockLine> stock) {

    /**
     * One shelf of a merchant.
     *
     * @param itemId    the item
     * @param price     the merchant's own buy price in ₽, or null to use the price table
     * @param quantity  how many are left; {@link #UNLIMITED} for a shelf that is never empty
     * @param restockTo how many there are again after a restock (ignored when the shelf is unlimited)
     */
    public record StockLine(String itemId, Integer price, int quantity, int restockTo) {

        /** The quantity of a shelf that never runs out. */
        public static final int UNLIMITED = -1;

        public StockLine {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("A shelf needs an item");
            }
            if (price != null && price < 0) {
                throw new IllegalArgumentException("A price cannot be negative");
            }
            if (quantity < UNLIMITED || restockTo < 0 && quantity != UNLIMITED) {
                throw new IllegalArgumentException("A quantity is 0 or more, or unlimited (-1)");
            }
        }

        /** A shelf that never runs out. */
        public static StockLine unlimited(String itemId) {
            return new StockLine(itemId, null, UNLIMITED, 0);
        }

        /** A shelf with {@code count} items that is filled up to {@code count} again at a restock. */
        public static StockLine of(String itemId, int count) {
            return new StockLine(itemId, null, count, count);
        }

        public StockLine withPrice(Integer value) {
            return new StockLine(itemId, value, quantity, restockTo);
        }

        public StockLine withQuantity(int value) {
            return new StockLine(itemId, price, value, restockTo);
        }

        public boolean endless() {
            return quantity == UNLIMITED;
        }
    }

    public Merchant {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A merchant needs an id");
        }
        name = name == null || name.isBlank() ? id : name;
        stock = stock == null ? List.of() : List.copyOf(stock);
        if (priceMultiplier < 0) {
            throw new IllegalArgumentException("A price multiplier cannot be negative");
        }
        if (restockRounds < 0 || lastRestock < 0) {
            throw new IllegalArgumentException("Restock rounds cannot be negative");
        }
    }

    /** A merchant that sells and buys at the prices of the table. */
    public static Merchant of(String id, String name, int restockRounds, int round, List<StockLine> stock) {
        return new Merchant(id, name, true, true, 1.0, restockRounds, round, stock);
    }

    /** The shelf of an item (any spelling of the id), or null. */
    public StockLine line(String itemId) {
        String key = Battle.squash(itemId);
        return stock.stream().filter(l -> Battle.squash(l.itemId()).equals(key)).findFirst().orElse(null);
    }

    /** A copy with these shelves. */
    public Merchant withStock(List<StockLine> value) {
        return new Merchant(id, name, sells, buys, priceMultiplier, restockRounds, lastRestock, value);
    }

    /** A copy where the shelf of this item holds this many. */
    public Merchant withQuantity(String itemId, int quantity) {
        List<StockLine> lines = new ArrayList<>();
        for (StockLine l : stock) {
            lines.add(Battle.squash(l.itemId()).equals(Battle.squash(itemId)) ? l.withQuantity(quantity) : l);
        }
        return withStock(lines);
    }

    /** A copy with another multiplier. */
    public Merchant withMultiplier(double value) {
        return new Merchant(id, name, sells, buys, value, restockRounds, lastRestock, stock);
    }

    /** A copy with what it does with the players (sells to them, buys from them). */
    public Merchant withTrading(boolean canSell, boolean canBuy) {
        return new Merchant(id, name, canSell, canBuy, priceMultiplier, restockRounds, lastRestock, stock);
    }

    /** A copy whose shelves are full again, as of this round. */
    public Merchant restocked(int round) {
        List<StockLine> lines = new ArrayList<>();
        for (StockLine l : stock) {
            lines.add(l.endless() ? l : l.withQuantity(l.restockTo()));
        }
        return new Merchant(id, name, sells, buys, priceMultiplier, restockRounds, round, lines);
    }

    /** True when a restock is due in this round: restocking is on and {@code restockRounds} rounds have passed. */
    public boolean restockDue(int round) {
        return restockRounds > 0 && round - lastRestock >= restockRounds;
    }
}
