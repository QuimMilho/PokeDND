/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.quimmilho.pokednd.core.game.Merchant.StockLine;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.Learnsets;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * Money and the people who take it (chapter 8.8): merchants (stock, restock, price multiplier, buying and selling), move tutors and healers.
 * Like the other desks it only returns events. A purchase is one action: the money, the bag and the shelf change together, and an undo
 * puts all three back.
 *
 * <p><b>Prices.</b> The buy price of an item is the one the admin set ({@link EconomySettings#priceOverrides}), or the default table of
 * chapter 8.8 ({@code ItemRules}; every TM costs {@code economy.prices.tm}). An item with no price is not sold until the admin sets one. A
 * merchant multiplies the table price by its multiplier (a price written on its own shelf is final). The class features of the buyer
 * ({@code shop.discountPct}) lower the price. The sell price is {@code economy.sellRatio} of the buy price (50 %), raised by
 * {@code shop.sellBonusPct}; what a player sells does not go on the merchant's shelf.
 *
 * <p><b>Restocking.</b> A merchant with {@code restockRounds} above 0 fills its finite shelves again when that many rounds have gone by
 * since the last time. {@link #restockDue} gives the events for every merchant that is due; a purchase from a merchant that is due
 * restocks it first, so the shelf a player sees is always the one for the round the game is in.
 */
public final class ShopDesk {

    private ShopDesk() {
    }

    // ================================================================================================================ prices

    /** The buy price of an item in the price table, with the admin's changes; empty when it is not for sale. */
    public static OptionalInt tablePrice(BagRules rules, String itemId) {
        String key = Battle.squash(itemId);
        for (var e : rules.economy().priceOverrides().entrySet()) {
            if (Battle.squash(e.getKey()).equals(key)) {
                return OptionalInt.of(e.getValue());
            }
        }
        ItemEntry entry = rules.moves().item(itemId).orElse(null);
        if (entry == null) {
            return OptionalInt.empty();
        }
        if (entry.category().equals("tm")) {
            return OptionalInt.of(rules.economy().tmPrice());
        }
        return entry.buyPrice().isPresent() ? OptionalInt.of(entry.buyPrice().get()) : OptionalInt.empty();
    }

    /** What one item costs this buyer at this merchant, or empty if it is not for sale there. */
    public static OptionalInt buyPrice(BagRules rules, Merchant merchant, String itemId, Trainer buyer) {
        StockLine line = merchant.line(itemId);
        int base;
        if (line != null && line.price() != null) {
            base = line.price();
        } else {
            OptionalInt table = tablePrice(rules, itemId);
            if (table.isEmpty()) {
                return OptionalInt.empty();
            }
            base = (int) Math.round(table.getAsInt() * merchant.priceMultiplier());
        }
        double discount = Math.max(0, Math.min(90, rules.rulebook().sheet(buyer).effects().value("shop.discountPct")));
        int price = (int) Math.round(base * (100 - discount) / 100.0);
        return OptionalInt.of(base > 0 ? Math.max(1, price) : 0);
    }

    /** What the merchant pays for one item of this seller, or empty if the item has no price. */
    public static OptionalInt sellPrice(BagRules rules, Merchant merchant, String itemId, Trainer seller) {
        StockLine line = merchant.line(itemId);
        OptionalInt base = line != null && line.price() != null ? OptionalInt.of(line.price()) : tablePrice(rules, itemId);
        if (base.isEmpty()) {
            return OptionalInt.empty();
        }
        double bonus = Math.max(0, rules.rulebook().sheet(seller).effects().value("shop.sellBonusPct"));
        return OptionalInt.of((int) Math.floor(base.getAsInt() * rules.economy().sellRatio() * (100 + bonus) / 100.0));
    }

    // ================================================================================================================ merchants

    /** A new merchant with the restock interval of the game's settings, as of the round the game is in. */
    public static Merchant merchant(GameState state, BagRules rules, String id, String name, List<StockLine> stock) {
        return Merchant.of(id, name, rules.economy().restockRounds(), state.round(), stock);
    }

    /** The admin sets a merchant up, or replaces it (its shelves, prices and manners). */
    public static List<GameEvent> setUp(GameState state, Actor who, Merchant merchant) {
        requireAdmin(who);
        return ProgressDesk.check(state, List.of(new GameEvent.MerchantSet(merchant)));
    }

    /** The admin sends a merchant away. */
    public static List<GameEvent> remove(GameState state, Actor who, String merchantId) {
        requireAdmin(who);
        merchant(state, merchantId);
        return ProgressDesk.check(state, List.of(new GameEvent.MerchantRemoved(merchantId)));
    }

    /** The merchant as it is in the round the game is in: restocked if it is due. */
    public static Merchant current(GameState state, String merchantId) {
        Merchant m = merchant(state, merchantId);
        return m.restockDue(state.round()) ? m.restocked(state.round()) : m;
    }

    /** The admin fills a merchant's shelves now. */
    public static List<GameEvent> restock(GameState state, Actor who, String merchantId) {
        requireAdmin(who);
        Merchant m = merchant(state, merchantId);
        return ProgressDesk.check(state, List.of(new GameEvent.MerchantSet(m.restocked(state.round())),
                new GameEvent.GameNoted(null, m.name() + " has restocked.")));
    }

    /** The events that restock every merchant that is due in the round the game is in (the caller adds them after a round advances). */
    public static List<GameEvent> restockDue(GameState state) {
        List<GameEvent> events = new ArrayList<>();
        for (Merchant m : state.merchants().values()) {
            if (m.restockDue(state.round())) {
                events.add(new GameEvent.MerchantSet(m.restocked(state.round())));
                events.add(new GameEvent.GameNoted(null, m.name() + " has restocked."));
            }
        }
        return events;
    }

    // ================================================================================================================ buying and selling

    /** A trainer buys items from a merchant. */
    public static List<GameEvent> buy(GameState state, BagRules rules, String trainerId, String merchantId, String itemId, int quantity) {
        Trainer trainer = BagDesk.trainer(state, trainerId);
        Merchant m = current(state, merchantId);
        String name = Bag.name(rules, itemId);
        if (quantity < 1) {
            throw new BattleRefusedException("Buy at least 1");
        }
        if (!m.sells()) {
            throw new BattleRefusedException(m.name() + " does not sell anything");
        }
        StockLine line = m.line(itemId);
        if (line == null) {
            throw new BattleRefusedException(m.name() + " does not sell " + name);
        }
        if (!line.endless() && line.quantity() < quantity) {
            throw new BattleRefusedException(m.name() + " has " + (line.quantity() == 0 ? "no" : "only " + line.quantity()) + " " + name + " left");
        }
        OptionalInt unit = buyPrice(rules, m, itemId, trainer);
        if (unit.isEmpty()) {
            throw new BattleRefusedException(name + " has no price yet: the admin has to set one");
        }
        long total = (long) unit.getAsInt() * quantity;
        if (trainer.money() < total) {
            throw new BattleRefusedException(trainer.name() + " needs " + total + " " + rules.economy().currency() + " but has " + trainer.money());
        }
        Trainer next = Bag.add(rules, trainer.withMoney(trainer.money() - total), itemId, quantity, trainer.name());
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.TrainerSet(trainerId, next));
        Merchant after = m;
        if (!line.endless()) {
            after = m.withQuantity(itemId, line.quantity() - quantity);
        }
        if (!after.equals(state.merchants().get(merchantId))) {
            events.add(new GameEvent.MerchantSet(after));
        }
        events.add(new GameEvent.GameNoted(trainerId, trainer.name() + " bought " + quantity + " " + name + " from " + m.name() + " for " + total + " " + rules.economy().currency() + "."));
        return ProgressDesk.check(state, events);
    }

    /** A trainer sells items to a merchant. */
    public static List<GameEvent> sell(GameState state, BagRules rules, String trainerId, String merchantId, String itemId, int quantity) {
        Trainer trainer = BagDesk.trainer(state, trainerId);
        Merchant m = current(state, merchantId);
        String name = Bag.name(rules, itemId);
        if (quantity < 1) {
            throw new BattleRefusedException("Sell at least 1");
        }
        if (!m.buys()) {
            throw new BattleRefusedException(m.name() + " does not buy anything");
        }
        OptionalInt unit = sellPrice(rules, m, itemId, trainer);
        if (unit.isEmpty()) {
            throw new BattleRefusedException(m.name() + " does not buy " + name);
        }
        Trainer bag = Bag.remove(rules, trainer, itemId, quantity, trainer.name());
        long total = (long) unit.getAsInt() * quantity;
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.TrainerSet(trainerId, bag.withMoney(bag.money() + total)));
        if (!m.equals(state.merchants().get(merchantId))) {
            events.add(new GameEvent.MerchantSet(m)); // it restocked first
        }
        events.add(new GameEvent.GameNoted(trainerId, trainer.name() + " sold " + quantity + " " + name + " to " + m.name() + " for " + total + " " + rules.economy().currency() + "."));
        return ProgressDesk.check(state, events);
    }

    // ================================================================================================================ money

    /** The admin gives or takes money (a reward, a fine): {@code delta} may be negative, the money never goes below 0. */
    public static List<GameEvent> money(GameState state, BagRules rules, Actor who, String trainerId, long delta, String reason) {
        requireAdmin(who);
        Trainer trainer = BagDesk.trainer(state, trainerId);
        if (delta == 0) {
            throw new BattleRefusedException("A change of money is not 0");
        }
        if (trainer.money() + delta < 0) {
            throw new BattleRefusedException(trainer.name() + " has only " + trainer.money() + " " + rules.economy().currency());
        }
        String why = reason == null || reason.isBlank() ? "" : " (" + reason.strip() + ")";
        return ProgressDesk.check(state, List.of(new GameEvent.TrainerSet(trainerId, trainer.withMoney(trainer.money() + delta)),
                new GameEvent.GameNoted(trainerId, trainer.name() + (delta > 0 ? " got " : " lost ") + Math.abs(delta) + " " + rules.economy().currency() + why + ".")));
    }

    // ================================================================================================================ tutors and healers

    /**
     * A move tutor teaches a move for money (chapter 5.5): the move must be on the Pokémon's list for one of the {@code methods} the tutor
     * offers ({@code tutor} by default; a tutor of legacy moves offers {@code legacy}). It replaces the move at place {@code forget} when the
     * Pokémon knows four.
     *
     * @param price   the NPC's own price, or null for {@code economy.tutor.price}
     * @param methods the learnset lists the tutor teaches from, or null for {@code tutor}
     */
    public static List<GameEvent> tutor(GameState state, BagRules rules, String trainerId, String pokemonId, String moveId, Integer price, Set<String> methods,
                                        Integer forget) {
        Trainer trainer = BagDesk.trainer(state, trainerId);
        PokemonSheet p = BagDesk.ownedSheet(state, trainer, trainerId, pokemonId);
        SpeciesBook.Species sp = ProgressDesk.species(rules.species(), p);
        Learnsets.Learnset ls = rules.species().learnset(p.speciesId())
                .orElseThrow(() -> new BattleRefusedException("The data has no list of moves for " + sp.name()));
        Set<String> offered = methods == null || methods.isEmpty() ? Set.of("tutor") : methods;
        Set<String> teachable = new LinkedHashSet<>();
        for (String method : offered) {
            switch (method) {
                case "tutor" -> teachable.addAll(ls.tutor());
                case "legacy" -> teachable.addAll(ls.legacy());
                case "egg" -> teachable.addAll(ls.egg());
                case "special" -> teachable.addAll(ls.special());
                default -> throw new BattleRefusedException("A tutor cannot teach by \"" + method + "\"");
            }
        }
        if (!teachable.contains(moveId)) {
            throw new BattleRefusedException(sp.name() + " cannot learn " + BattleReducer.pretty(moveId) + " from this tutor");
        }
        long cost = price == null ? rules.economy().tutorPrice() : price;
        if (cost < 0) {
            throw new BattleRefusedException("A price cannot be negative");
        }
        if (trainer.money() < cost) {
            throw new BattleRefusedException(trainer.name() + " needs " + cost + " " + rules.economy().currency() + " but has " + trainer.money());
        }
        BagDesk.Done done = BagDesk.teach(rules, p, moveId, new BagDesk.Choice(null, forget), trainer.name(), "the move tutor");
        List<GameEvent> events = new ArrayList<>(done.events());
        events.add(new GameEvent.TrainerSet(trainerId, trainer.withMoney(trainer.money() - cost)));
        events.add(new GameEvent.GameNoted(trainerId, done.text() + (cost > 0 ? " It cost " + cost + " " + rules.economy().currency() + "." : "")));
        return ProgressDesk.check(state, events);
    }

    /**
     * A healer restores the HP, PP and status of the whole party and the HP of the trainer (chapter 8.11). It is free unless the NPC or
     * {@code economy.healer.price} says otherwise.
     *
     * @param price the NPC's own price, or null for {@code economy.healer.price}
     */
    public static List<GameEvent> heal(GameState state, BagRules rules, String trainerId, Integer price) {
        Trainer trainer = BagDesk.trainer(state, trainerId);
        long cost = price == null ? rules.economy().healerPrice() : price;
        if (cost < 0) {
            throw new BattleRefusedException("A price cannot be negative");
        }
        if (trainer.money() < cost) {
            throw new BattleRefusedException(trainer.name() + " needs " + cost + " " + rules.economy().currency() + " but has " + trainer.money());
        }
        for (PokemonSheet p : BattleSetup.party(state, trainerId)) {
            if (BagDesk.inBattle(state, p.id())) {
                throw new BattleRefusedException(p.name() + " is in a battle: heal after it");
            }
        }
        List<GameEvent> events = new ArrayList<>(ProgressDesk.healParty(state, rules.species(), trainerId));
        events.add(new GameEvent.TrainerSet(trainerId, trainer.healFully().withMoney(trainer.money() - cost)));
        events.add(new GameEvent.GameNoted(trainerId, trainer.name() + "'s team was healed" + (cost > 0 ? " for " + cost + " " + rules.economy().currency() : "") + "."));
        return ProgressDesk.check(state, events);
    }

    // ================================================================================================================ helpers

    private static Merchant merchant(GameState state, String merchantId) {
        Merchant m = merchantId == null ? null : state.merchants().get(merchantId);
        if (m == null) {
            throw new BattleRefusedException("There is no merchant " + merchantId);
        }
        return m;
    }

    private static void requireAdmin(Actor who) {
        if (!who.admin()) {
            throw new BattleRefusedException("Only the admin can set up the shops");
        }
    }
}
