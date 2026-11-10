/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * The rules of a bag (chapter 8.8): items are sorted into pockets by their category, and the admin can limit the number of different items
 * in a pocket ({@code bag.limit.slotsPerPocket}, plus the class feature {@code bag.extraSlots}) and how many of one item fit in a stack
 * ({@code bag.limit.stackSize}). The bag itself is the {@code items} map of the {@link Trainer}: item id to count. These are pure
 * functions on that map; they return a new map or refuse with a {@link BattleRefusedException} that says why.
 */
final class Bag {

    /** The pocket of an item that the book does not know. */
    static final String OTHER = "other";

    private Bag() {
    }

    /** The pocket of an item: its category in {@code item.csv} ({@code medicine}, {@code poke_balls}, {@code tm}…). */
    static String pocket(MoveBook book, String itemId) {
        return book.item(itemId).map(ItemEntry::category).orElse(OTHER);
    }

    /** The key under which the bag keeps this item: the key already there if the bag has the item in another spelling, else the id as given. */
    static String key(Map<String, Integer> items, String itemId) {
        String squashed = Battle.squash(itemId);
        for (String k : items.keySet()) {
            if (Battle.squash(k).equals(squashed)) {
                return k;
            }
        }
        return itemId;
    }

    /** How many of an item the bag holds. */
    static int count(Map<String, Integer> items, String itemId) {
        return items.getOrDefault(key(items, itemId), 0);
    }

    /** How many different items of the pocket the bag holds. */
    static int slotsUsed(MoveBook book, Map<String, Integer> items, String pocket) {
        int n = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (e.getValue() > 0 && pocket(book, e.getKey()).equals(pocket)) {
                n++;
            }
        }
        return n;
    }

    /** The slots a pocket of this trainer has, or 0 for no limit. */
    static int slotLimit(BagRules rules, Trainer trainer) {
        int base = rules.economy().slotsPerPocket();
        if (base == 0) {
            return 0;
        }
        return base + rules.rulebook().sheet(trainer).effects().intValue("bag.extraSlots");
    }

    /** How many more of this item fit in the bag, or {@link Integer#MAX_VALUE} for no limit. */
    static int room(BagRules rules, Trainer trainer, String itemId) {
        Map<String, Integer> items = trainer.items();
        int have = count(items, itemId);
        int room = Integer.MAX_VALUE;
        int stack = rules.economy().stackSize();
        if (stack > 0) {
            room = Math.max(0, stack - have);
        }
        int slots = slotLimit(rules, trainer);
        if (slots > 0 && have == 0 && slotsUsed(rules.moves(), items, pocket(rules.moves(), itemId)) >= slots) {
            return 0;
        }
        return room;
    }

    /**
     * The bag with {@code quantity} more of an item.
     *
     * @throws BattleRefusedException if it does not fit: the pocket is full or the stack would be too big
     */
    static Trainer add(BagRules rules, Trainer trainer, String itemId, int quantity, String owner) {
        if (quantity < 1) {
            throw new BattleRefusedException("An amount of items is at least 1");
        }
        int have = count(trainer.items(), itemId);
        int stack = rules.economy().stackSize();
        if (stack > 0 && have + quantity > stack) {
            throw new BattleRefusedException(owner + " cannot carry " + quantity + " more " + name(rules, itemId) + ": a stack holds at most " + stack + " and "
                    + have + " are in the bag");
        }
        int slots = slotLimit(rules, trainer);
        if (slots > 0 && have == 0 && slotsUsed(rules.moves(), trainer.items(), pocket(rules.moves(), itemId)) >= slots) {
            throw new BattleRefusedException(owner + "'s " + pocket(rules.moves(), itemId).replace('_', ' ') + " pocket is full (" + slots + " slots)");
        }
        Map<String, Integer> bag = new LinkedHashMap<>(trainer.items());
        bag.merge(key(trainer.items(), itemId), quantity, Integer::sum);
        return trainer.withItems(bag);
    }

    /**
     * The bag with {@code quantity} fewer of an item.
     *
     * @throws BattleRefusedException if the bag does not hold that many
     */
    static Trainer remove(BagRules rules, Trainer trainer, String itemId, int quantity, String owner) {
        if (quantity < 1) {
            throw new BattleRefusedException("An amount of items is at least 1");
        }
        String key = key(trainer.items(), itemId);
        int have = trainer.items().getOrDefault(key, 0);
        if (have < quantity) {
            throw new BattleRefusedException(owner + " has " + have + " " + name(rules, itemId) + ", not " + quantity);
        }
        Map<String, Integer> bag = new LinkedHashMap<>(trainer.items());
        if (have == quantity) {
            bag.remove(key);
        } else {
            bag.put(key, have - quantity);
        }
        return trainer.withItems(bag);
    }

    /** The bag as pockets: pocket name to item id to count, pockets and items in alphabetical order. */
    static Map<String, Map<String, Integer>> pockets(MoveBook book, Map<String, Integer> items) {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        new TreeMap<>(items).forEach((id, n) -> {
            if (n > 0) {
                out.computeIfAbsent(pocket(book, id), p -> new TreeMap<>()).put(id, n);
            }
        });
        return out;
    }

    /** The name of an item for a message. */
    static String name(BagRules rules, String itemId) {
        return rules.moves().item(itemId).map(ItemEntry::name).orElse(BattleReducer.pretty(itemId));
    }
}
