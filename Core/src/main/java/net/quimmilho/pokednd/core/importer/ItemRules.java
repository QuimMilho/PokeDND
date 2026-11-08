/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What every item does by default and what it costs (chapter 8.8). Effects are hard-coded per category (decided); the
 * reference for the categories Cobblemon already has is its {@code data/cobblemon/bag_items/} scripts (potion, potion by
 * portion, full restore, revive, ether, elixir, cure status, clear boost, X stat, Dire Hit, Guard Spec).
 *
 * <p>The price table is the one of chapter 8.8, confirmed by the owner on 2026-11-08. Prices not listed there are left
 * empty on purpose: the admin sets them (they show up in the items report).
 */
public final class ItemRules {

    /** An IV candy changes one IV by this much (decided; setting {@code items.ivCandy.step}). */
    public static final int IV_CANDY_STEP = 1;

    /** Sell price is half of the buy price. */
    public static final int SELL_PERCENT = 50;

    /** Price of every TM. */
    public static final int TM_PRICE = 3000;

    private ItemRules() {
    }

    /**
     * What an item does.
     *
     * @param name   handler name, for example {@code heal_hp} or {@code catch_ball}
     * @param amount a number the handler needs (HP, percent, stages, XP, EV…); empty if none
     * @param detail a second value: a stat, a status, a type, a nature or a move; empty if none
     */
    public record Handler(String name, Optional<Integer> amount, Optional<String> detail) {
    }

    private static Handler h(String name) {
        return new Handler(name, Optional.empty(), Optional.empty());
    }

    private static Handler h(String name, int amount) {
        return new Handler(name, Optional.of(amount), Optional.empty());
    }

    private static Handler h(String name, String detail) {
        return new Handler(name, Optional.empty(), Optional.of(detail));
    }

    private static Handler h(String name, int amount, String detail) {
        return new Handler(name, Optional.of(amount), Optional.of(detail));
    }

    private static final Map<String, Handler> MEDICINE = Map.ofEntries(
            Map.entry("potion", h("heal_hp", 20)),
            Map.entry("super_potion", h("heal_hp", 60)),
            Map.entry("hyper_potion", h("heal_hp", 120)),
            Map.entry("max_potion", h("heal_hp_full")),
            Map.entry("full_restore", h("full_restore")),
            Map.entry("revive", h("revive", 50)),
            Map.entry("max_revive", h("revive", 100)),
            Map.entry("ether", h("restore_pp", 10)),
            Map.entry("max_ether", h("restore_pp_full")),
            Map.entry("elixir", h("restore_pp_all", 10)),
            Map.entry("max_elixir", h("restore_pp_all_full")),
            Map.entry("antidote", h("cure_status", "poison")),
            Map.entry("burn_heal", h("cure_status", "burn")),
            Map.entry("ice_heal", h("cure_status", "freeze")),
            Map.entry("paralyze_heal", h("cure_status", "paralysis")),
            Map.entry("awakening", h("cure_status", "sleep")),
            Map.entry("full_heal", h("cure_status", "all")),
            Map.entry("hp_up", h("vitamin", 10, "hp")),
            Map.entry("protein", h("vitamin", 10, "attack")),
            Map.entry("iron", h("vitamin", 10, "defense")),
            Map.entry("calcium", h("vitamin", 10, "special_attack")),
            Map.entry("zinc", h("vitamin", 10, "special_defense")),
            Map.entry("carbos", h("vitamin", 10, "speed")),
            Map.entry("pp_up", h("pp_up", 1)),
            Map.entry("pp_max", h("pp_max")));

    private static final Map<String, Handler> BATTLE = Map.of(
            "x_accuracy", h("x_stat", 2, "accuracy"),
            "x_attack", h("x_stat", 2, "attack"),
            "x_defense", h("x_stat", 2, "defense"),
            "x_sp_atk", h("x_stat", 2, "special_attack"),
            "x_sp_def", h("x_stat", 2, "special_defense"),
            "x_speed", h("x_stat", 2, "speed"),
            "dire_hit", h("dire_hit", 2),
            "guard_spec", h("guard_spec"));

    private static final Map<String, Handler> CANDY = Map.ofEntries(
            Map.entry("rare_candy", h("rare_candy", 1)),
            Map.entry("exp_candy_xs", h("exp_candy", 100)),
            Map.entry("exp_candy_s", h("exp_candy", 800)),
            Map.entry("exp_candy_m", h("exp_candy", 3000)),
            Map.entry("exp_candy_l", h("exp_candy", 10_000)),
            Map.entry("exp_candy_xl", h("exp_candy", 30_000)),
            Map.entry("health_candy", h("iv_candy", IV_CANDY_STEP, "hp")),
            Map.entry("mighty_candy", h("iv_candy", IV_CANDY_STEP, "attack")),
            Map.entry("tough_candy", h("iv_candy", IV_CANDY_STEP, "defense")),
            Map.entry("smart_candy", h("iv_candy", IV_CANDY_STEP, "special_attack")),
            Map.entry("courage_candy", h("iv_candy", IV_CANDY_STEP, "special_defense")),
            Map.entry("quick_candy", h("iv_candy", IV_CANDY_STEP, "speed")),
            Map.entry("sickly_candy", h("iv_candy", -IV_CANDY_STEP, "hp")),
            Map.entry("weak_candy", h("iv_candy", -IV_CANDY_STEP, "attack")),
            Map.entry("brittle_candy", h("iv_candy", -IV_CANDY_STEP, "defense")),
            Map.entry("numb_candy", h("iv_candy", -IV_CANDY_STEP, "special_attack")),
            Map.entry("coward_candy", h("iv_candy", -IV_CANDY_STEP, "special_defense")),
            Map.entry("slow_candy", h("iv_candy", -IV_CANDY_STEP, "speed")));

    /**
     * The default handler of an item, or empty when its effect is not defined (the item is then text-only: the admin applies
     * it by hand).
     */
    public static Optional<Handler> handler(String id, String category) {
        return Optional.ofNullable(switch (category) {
            case "poke_balls" -> h("catch_ball");
            case "medicine" -> MEDICINE.get(id);
            case "battle_items" -> BATTLE.get(id);
            case "candy" -> CANDY.get(id);
            case "berries" -> h("berry");
            case "held_items" -> h("held_item");
            case "evolution" -> h("evolution_item");
            case "mints" -> id.endsWith("_mint") ? h("nature_mint", id.substring(0, id.length() - "_mint".length()))
                    : h("none"); // mint plants (leaf, seeds) have no battle effect
            case "type_gems" -> h("type_gem", id.substring(0, id.length() - "_gem".length()));
            default -> null;
        });
    }

    // ---------------------------------------------------------------- prices

    private static final Set<String> NOT_FOR_SALE = Set.of("master_ball", "exp_share", "rare_candy");

    /** Rare berries (price 300 instead of 100). Suggested list. */
    private static final Set<String> RARE_BERRIES = Set.of("apicot_berry", "custap_berry", "enigma_berry", "ganlon_berry",
            "jaboca_berry", "kee_berry", "lansat_berry", "leppa_berry", "liechi_berry", "lum_berry", "maranga_berry",
            "micle_berry", "petaya_berry", "rowap_berry", "salac_berry", "sitrus_berry", "starf_berry");

    /** Rare held items (price 5,000 instead of 2,000). Suggested list. */
    private static final Set<String> RARE_HELD = Set.of("assault_vest", "black_sludge", "choice_band", "choice_scarf",
            "choice_specs", "eviolite", "expert_belt", "focus_sash", "leftovers", "life_orb", "lucky_egg", "rocky_helmet",
            "shell_bell", "weakness_policy");

    private static final Map<String, Integer> MEDICINE_PRICE = Map.ofEntries(
            Map.entry("potion", 200), Map.entry("super_potion", 700), Map.entry("hyper_potion", 1500),
            Map.entry("max_potion", 2500), Map.entry("full_restore", 3000), Map.entry("revive", 1500),
            Map.entry("max_revive", 4000), Map.entry("antidote", 100), Map.entry("paralyze_heal", 200),
            Map.entry("awakening", 250), Map.entry("burn_heal", 250), Map.entry("ice_heal", 250),
            Map.entry("full_heal", 400), Map.entry("ether", 1200), Map.entry("elixir", 3000));

    /** Whether the item can be bought in a shop at all. */
    public static boolean forSale(String id, String category) {
        if (NOT_FOR_SALE.contains(id) || category.equals("candy")) {
            return false;
        }
        return !(category.equals("poke_balls") && id.startsWith("ancient_"));
    }

    /**
     * The default buy price in ₽ (chapter 8.8), or empty when the table gives none: the item is not for sale, or the table
     * does not mention it (the admin sets a price).
     */
    public static Optional<Integer> buyPrice(String id, String category) {
        if (!forSale(id, category)) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (category) {
            case "poke_balls" -> switch (id) {
                case "poke_ball" -> 200;
                case "great_ball" -> 600;
                case "ultra_ball" -> 800;
                default -> 1000;
            };
            case "medicine" -> MEDICINE_PRICE.get(id);
            case "battle_items" -> 500;
            case "berries" -> RARE_BERRIES.contains(id) ? 300 : 100;
            case "held_items" -> RARE_HELD.contains(id) ? 5000 : 2000;
            case "evolution" -> id.endsWith("_stone") ? 2100 : null;
            case "mints" -> id.endsWith("_mint") ? 5000 : null;
            case "type_gems" -> 2000;
            case "tm" -> TM_PRICE;
            default -> null;
        });
    }

    /** The sell price: half of the buy price, rounded down. */
    public static Optional<Integer> sellPrice(Optional<Integer> buy) {
        return buy.map(p -> p * SELL_PERCENT / 100);
    }
}
