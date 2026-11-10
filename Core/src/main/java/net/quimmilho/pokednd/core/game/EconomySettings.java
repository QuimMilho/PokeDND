/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The settings of the bag, money, shops and trading (chapter 8.8 and Appendix A, "Money, shops and trading" and "Bag") that
 * {@link BagDesk}, {@link ShopDesk} and {@link TradeDesk} read. Like the other settings records they are not stored in the game state:
 * the caller passes the settings of the game with each call.
 *
 * @param slotsPerPocket    {@code bag.limit.slotsPerPocket}: different items per pocket (0 = no limit); class features add {@code bag.extraSlots}
 * @param stackSize         {@code bag.limit.stackSize}: how many of one item (0 = no limit)
 * @param ivCandyStep       {@code items.ivCandy.step}: how much one IV candy raises or lowers an IV (default 1)
 * @param sellRatio         {@code economy.sellRatio}: the sell price as a fraction of the buy price (default 0.5)
 * @param tmPrice           {@code economy.prices.tm}: the default buy price of every TM (default 3000)
 * @param healerPrice       {@code economy.healer.price}: the default price of a healing (default 0)
 * @param tutorPrice        {@code economy.tutor.price}: the default price of a move taught by a tutor (default 1000)
 * @param restockRounds     {@code economy.merchant.restockRounds}: rounds between restocks of a merchant (default 10, 0 = never)
 * @param tradeApproval     {@code economy.trade.requireApproval}: the admin must approve every trade (default false)
 * @param tradeLocation     {@code economy.trade.location}: where trading is allowed (default at a trade centre)
 * @param priceOverrides    prices the admin changed ({@code economy.prices.table} edits): item id to buy price in ₽
 * @param currency          {@code economy.currency.symbol}
 */
public record EconomySettings(int slotsPerPocket, int stackSize, int ivCandyStep, double sellRatio, int tmPrice, int healerPrice, int tutorPrice,
                              int restockRounds, boolean tradeApproval, TradeLocation tradeLocation, Map<String, Integer> priceOverrides, String currency) {

    /** {@code economy.trade.location}. */
    public enum TradeLocation {
        /** Only at a trade-centre NPC. */
        TRADE_CENTRE("tradeCentre"),
        /** Anywhere. */
        ANYWHERE("anywhere");

        private final String id;

        TradeLocation(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static TradeLocation parse(String text) {
            for (TradeLocation l : values()) {
                if (l.id.equalsIgnoreCase(text.strip())) {
                    return l;
                }
            }
            throw new IllegalArgumentException("economy.trade.location must be tradeCentre or anywhere, not " + text);
        }
    }

    public EconomySettings {
        priceOverrides = priceOverrides == null ? Map.of() : Map.copyOf(priceOverrides);
        currency = currency == null || currency.isBlank() ? "₽" : currency;
        tradeLocation = tradeLocation == null ? TradeLocation.TRADE_CENTRE : tradeLocation;
        if (slotsPerPocket < 0 || stackSize < 0 || healerPrice < 0 || tutorPrice < 0 || restockRounds < 0 || tmPrice < 0) {
            throw new IllegalArgumentException("An economy setting cannot be negative");
        }
        if (sellRatio < 0 || sellRatio > 1) {
            throw new IllegalArgumentException("economy.sellRatio is 0 to 1, not " + sellRatio);
        }
        if (ivCandyStep < 1 || ivCandyStep > 31) {
            throw new IllegalArgumentException("items.ivCandy.step is 1 to 31, not " + ivCandyStep);
        }
        for (Map.Entry<String, Integer> e : priceOverrides.entrySet()) {
            if (e.getValue() < 0) {
                throw new IllegalArgumentException("The price of " + e.getKey() + " cannot be negative");
            }
        }
    }

    /** The defaults of Appendix A: no bag limits, IV candies of 1, sell at half, TMs for 3000, tutors for 1000, healing free, restock every 10 rounds. */
    public static EconomySettings defaults() {
        return new EconomySettings(0, 0, 1, 0.5, 3000, 0, 1000, 10, false, TradeLocation.TRADE_CENTRE, Map.of(), "₽");
    }

    /** Reads the settings from their keys; a key that is missing keeps its default. */
    public static EconomySettings fromValues(Map<String, String> values) {
        EconomySettings d = defaults();
        return new EconomySettings(number(values, "bag.limit.slotsPerPocket", d.slotsPerPocket()), number(values, "bag.limit.stackSize", d.stackSize()),
                number(values, "items.ivCandy.step", d.ivCandyStep()), decimal(values, "economy.sellRatio", d.sellRatio()),
                number(values, "economy.prices.tm", d.tmPrice()), number(values, "economy.healer.price", d.healerPrice()),
                number(values, "economy.tutor.price", d.tutorPrice()), number(values, "economy.merchant.restockRounds", d.restockRounds()),
                values.containsKey("economy.trade.requireApproval") && Boolean.parseBoolean(values.get("economy.trade.requireApproval").strip().toLowerCase(Locale.ROOT)),
                values.containsKey("economy.trade.location") ? TradeLocation.parse(values.get("economy.trade.location")) : d.tradeLocation(), d.priceOverrides(),
                values.getOrDefault("economy.currency.symbol", d.currency()));
    }

    private static int number(Map<String, String> values, String key, int fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, not " + v);
        }
    }

    private static double decimal(Map<String, String> values, String key, double fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number, not " + v);
        }
    }

    /** A copy with these bag limits. */
    public EconomySettings withBagLimits(int slots, int stack) {
        return new EconomySettings(slots, stack, ivCandyStep, sellRatio, tmPrice, healerPrice, tutorPrice, restockRounds, tradeApproval, tradeLocation,
                priceOverrides, currency);
    }

    public EconomySettings withIvCandyStep(int value) {
        return new EconomySettings(slotsPerPocket, stackSize, value, sellRatio, tmPrice, healerPrice, tutorPrice, restockRounds, tradeApproval, tradeLocation,
                priceOverrides, currency);
    }

    public EconomySettings withTrade(boolean approval, TradeLocation location) {
        return new EconomySettings(slotsPerPocket, stackSize, ivCandyStep, sellRatio, tmPrice, healerPrice, tutorPrice, restockRounds, approval, location,
                priceOverrides, currency);
    }

    public EconomySettings withPrices(double ratio, int tm, int healer, int tutor) {
        return new EconomySettings(slotsPerPocket, stackSize, ivCandyStep, ratio, tm, healer, tutor, restockRounds, tradeApproval, tradeLocation,
                priceOverrides, currency);
    }

    public EconomySettings withRestockRounds(int value) {
        return new EconomySettings(slotsPerPocket, stackSize, ivCandyStep, sellRatio, tmPrice, healerPrice, tutorPrice, value, tradeApproval, tradeLocation,
                priceOverrides, currency);
    }

    /** A copy where the admin has set the buy price of one item. */
    public EconomySettings withPrice(String itemId, int price) {
        Map<String, Integer> copy = new LinkedHashMap<>(priceOverrides);
        copy.put(itemId, price);
        return new EconomySettings(slotsPerPocket, stackSize, ivCandyStep, sellRatio, tmPrice, healerPrice, tutorPrice, restockRounds, tradeApproval, tradeLocation,
                copy, currency);
    }
}
