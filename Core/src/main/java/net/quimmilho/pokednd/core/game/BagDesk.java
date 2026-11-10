/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Trigger;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.ItemRules.Handler;
import net.quimmilho.pokednd.core.rules.Nature;
import net.quimmilho.pokednd.core.rules.Stat;
import net.quimmilho.pokednd.core.rules.Stats;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * The bag and the items in it (chapter 8.8): handing items out, holding them, and using them outside a battle. Like the other desks it
 * only returns events; the game adds them to its log as one action, so an undo takes back the item, the effect and the log line together.
 * Nothing changes when a call is refused.
 *
 * <p><b>Effects are hard-coded per category</b> (decided), with the numbers of the imported data ({@code ItemRules}): potions heal, status
 * heals cure, a Revive brings back a fainted Pokémon, Ether and Elixir restore PP, vitamins add EVs, PP Up and PP Max raise the PP of a
 * move, Rare Candy and Exp. Candy give XP, IV candies move one IV by {@code items.ivCandy.step}, a nature mint sets the nature, an
 * evolution item is tried against the evolution rules, a berry is eaten, and a TM teaches its move. An item that is held is given with
 * {@link #hold}. An item whose effect is not defined is text only: the admin applies it ({@code use} by the admin only notes it).
 *
 * <p>Using an item always uses it up, except when it would do nothing: a potion on a Pokémon at full HP is refused and stays in the bag.
 * A TM is used up (Suggested, Appendix D). Inside a battle an item is an action ({@link Battle.Action#item}); only some items work there
 * ({@link #usableInBattle}).
 */
public final class BagDesk {

    /** What an item needs from the caller. */
    public enum Needs {
        /** A Pokémon to use it on. */
        POKEMON,
        /** A Pokémon and one of its moves (Ether, PP Up). */
        POKEMON_AND_MOVE,
        /** A Pokémon, and the move to forget when it knows four (a TM). */
        TM,
        /** Only a battle: a Poké Ball, an X item, Dire Hit, Guard Spec. */
        BATTLE_ONLY,
        /** A Pokémon to hold it: a held item, a type gem. */
        HOLD,
        /** No automatic effect: the admin applies it by hand. */
        ADMIN,
        /** It does nothing (a mint plant). */
        NOTHING
    }

    /**
     * The extra input some items need.
     *
     * @param moveId the move an Ether or PP Up is used on
     * @param forget the place (0 to 3) of the move to forget when a TM is used on a Pokémon that knows four, or null
     */
    public record Choice(String moveId, Integer forget) {
        public static Choice none() {
            return new Choice(null, null);
        }

        public static Choice move(String moveId) {
            return new Choice(moveId, null);
        }

        public static Choice forgetting(int place) {
            return new Choice(null, place);
        }
    }

    private static final Set<String> HELD_POCKETS = Set.of("held_items", "berries", "evolution", "type_gems");
    private static final Set<String> BATTLE_HANDLERS = Set.of("heal_hp", "heal_hp_full", "full_restore", "revive", "restore_pp", "restore_pp_full",
            "restore_pp_all", "restore_pp_all_full", "cure_status", "x_stat", "dire_hit", "guard_spec", "berry");

    /** The berries that lower an EV by 10 and raise friendship (they cannot be eaten in a battle). */
    private static final Map<String, Stat> FRIENDSHIP_BERRIES = Map.of("pomegberry", Stat.HP, "kelpsyberry", Stat.ATTACK, "qualotberry", Stat.DEFENSE,
            "hondewberry", Stat.SPECIAL_ATTACK, "grepaberry", Stat.SPECIAL_DEFENSE, "tamatoberry", Stat.SPEED);

    private BagDesk() {
    }

    // ================================================================================================================ what an item needs

    /** What the app must ask for before an item can be used. */
    public static Needs needs(MoveBook book, String itemId) {
        ItemEntry entry = book.item(itemId).orElse(null);
        Handler h = entry == null ? null : entry.handler().orElse(null);
        if (h == null) {
            return Needs.ADMIN;
        }
        return switch (h.name()) {
            case "restore_pp", "restore_pp_full", "pp_up", "pp_max" -> Needs.POKEMON_AND_MOVE;
            case "tm" -> Needs.TM;
            case "catch_ball", "x_stat", "dire_hit", "guard_spec" -> Needs.BATTLE_ONLY;
            case "held_item", "type_gem" -> Needs.HOLD;
            case "none" -> Needs.NOTHING;
            default -> Needs.POKEMON;
        };
    }

    /** True if the item can be used as a battle action (not a ball: that is the {@code BALL} action). */
    public static boolean usableInBattle(MoveBook book, String itemId) {
        return book.item(itemId).flatMap(ItemEntry::handler).map(h -> BATTLE_HANDLERS.contains(h.name()) && (!h.name().equals("berry") || ItemScripts.isBerry(itemId)))
                .orElse(false);
    }

    /** The bag as pockets: pocket name to item id to count, in alphabetical order. */
    public static Map<String, Map<String, Integer>> pockets(BagRules rules, Trainer trainer) {
        return Bag.pockets(rules.moves(), trainer.items());
    }

    // =============================================================================================================== handing out items

    /** The admin puts items in a bag (a reward, a fix). The limits of the bag apply. */
    public static List<GameEvent> give(GameState state, BagRules rules, Actor who, String trainerId, String itemId, int quantity) {
        requireAdmin(who, "give items");
        Trainer trainer = trainer(state, trainerId);
        if (rules.moves().item(itemId).isEmpty()) {
            throw new BattleRefusedException("There is no item " + itemId + " in the game");
        }
        Trainer next = Bag.add(rules, trainer, itemId, quantity, trainer.name());
        return checked(state, List.of(new GameEvent.TrainerSet(trainerId, next),
                new GameEvent.GameNoted(trainerId, trainer.name() + " got " + quantity + " " + Bag.name(rules, itemId) + ".")));
    }

    /** Items leave a bag: the admin takes them away, or a player throws them away. */
    public static List<GameEvent> take(GameState state, BagRules rules, String trainerId, String itemId, int quantity) {
        Trainer trainer = trainer(state, trainerId);
        Trainer next = Bag.remove(rules, trainer, itemId, quantity, trainer.name());
        return checked(state, List.of(new GameEvent.TrainerSet(trainerId, next),
                new GameEvent.GameNoted(trainerId, trainer.name() + " lost " + quantity + " " + Bag.name(rules, itemId) + ".")));
    }

    /** Gives a Pokémon an item to hold. What it held before goes back to the bag. */
    public static List<GameEvent> hold(GameState state, BagRules rules, String trainerId, String pokemonId, String itemId) {
        Trainer trainer = trainer(state, trainerId);
        PokemonSheet p = ownedSheet(state, trainer, trainerId, pokemonId);
        ItemEntry entry = rules.moves().item(itemId).orElseThrow(() -> new BattleRefusedException("There is no item " + itemId + " in the game"));
        if (!HELD_POCKETS.contains(entry.category())) {
            throw new BattleRefusedException(entry.name() + " cannot be held");
        }
        Trainer next = Bag.remove(rules, trainer, itemId, 1, trainer.name());
        String before = p.heldItem();
        if (before != null) {
            next = Bag.add(rules, next, before, 1, trainer.name());
        }
        return checked(state, List.of(new GameEvent.PokemonSet(p.withHeldItem(entry.id())), new GameEvent.TrainerSet(trainerId, next),
                new GameEvent.GameNoted(trainerId, p.name() + " now holds " + entry.name() + (before == null ? "." : " (" + Bag.name(rules, before) + " went back in the bag)."))));
    }

    /** Takes the held item from a Pokémon and puts it in the bag. */
    public static List<GameEvent> unhold(GameState state, BagRules rules, String trainerId, String pokemonId) {
        Trainer trainer = trainer(state, trainerId);
        PokemonSheet p = ownedSheet(state, trainer, trainerId, pokemonId);
        if (p.heldItem() == null) {
            throw new BattleRefusedException(p.name() + " holds nothing");
        }
        Trainer next = Bag.add(rules, trainer, p.heldItem(), 1, trainer.name());
        return checked(state, List.of(new GameEvent.PokemonSet(p.withHeldItem(null)), new GameEvent.TrainerSet(trainerId, next),
                new GameEvent.GameNoted(trainerId, trainer.name() + " took " + Bag.name(rules, p.heldItem()) + " from " + p.name() + ".")));
    }

    // =============================================================================================================== using items

    /** The text of what an item did, and the events that did it (before the item is used up). */
    record Done(List<GameEvent> events, String text) {
    }

    /**
     * Uses an item from the bag outside a battle. {@code pokemonId} is the Pokémon it is used on (null for an item that is only noted by
     * the admin); {@code choice} carries the move for Ether and PP Up and the move to forget for a TM.
     *
     * @throws BattleRefusedException if the trainer does not have the item, the item cannot be used now, or it would do nothing
     */
    public static List<GameEvent> use(GameState state, BagRules rules, Actor who, String trainerId, String itemId, String pokemonId, Choice choice) {
        Trainer trainer = trainer(state, trainerId);
        Choice pick = choice == null ? Choice.none() : choice;
        String name = Bag.name(rules, itemId);
        if (Bag.count(trainer.items(), itemId) < 1) {
            throw new BattleRefusedException(trainer.name() + " has no " + name);
        }
        ItemEntry entry = rules.moves().item(itemId).orElseThrow(() -> new BattleRefusedException("There is no item " + itemId + " in the game"));
        Handler h = entry.handler().orElse(null);
        Done done;
        if (h == null) {
            if (!who.admin()) {
                throw new BattleRefusedException(name + " has no automatic effect: the admin applies it by hand");
            }
            done = new Done(List.of(), "The admin applied " + name + " for " + trainer.name() + " by hand.");
        } else {
            switch (h.name()) {
                case "catch_ball" -> throw new BattleRefusedException("A Poké Ball is thrown in a battle");
                case "x_stat", "dire_hit", "guard_spec" -> throw new BattleRefusedException(name + " works only in a battle");
                case "held_item", "type_gem" -> throw new BattleRefusedException(name + " is held by a Pokémon: give it to one to hold");
                case "none" -> throw new BattleRefusedException(name + " does nothing by itself");
                default -> {
                }
            }
            if (pokemonId == null) {
                throw new BattleRefusedException("Choose the Pokémon to use " + name + " on");
            }
            PokemonSheet p = ownedSheet(state, trainer, trainerId, pokemonId);
            done = apply(state, rules, trainer, entry, h, p, pick);
        }
        List<GameEvent> events = new ArrayList<>(done.events());
        GameState after = apply(state, events);
        Trainer current = after.trainers().get(trainerId);
        events.add(new GameEvent.TrainerSet(trainerId, Bag.remove(rules, current, itemId, 1, trainer.name())));
        events.add(new GameEvent.GameNoted(trainerId, done.text()));
        return checked(state, events);
    }

    private static Done apply(GameState state, BagRules rules, Trainer trainer, ItemEntry entry, Handler h, PokemonSheet p, Choice pick) {
        SpeciesBook.Species sp = ProgressDesk.species(rules.species(), p);
        int max = BattleSetup.maxHp(p, sp, p.level());
        String name = entry.name();
        String who = trainer.name();
        int amount = h.amount().orElse(0);
        String detail = h.detail().orElse("");
        switch (h.name()) {
            case "heal_hp", "heal_hp_full" -> {
                requireStanding(p, "a Revive");
                if (p.hp() >= max) {
                    throw new BattleRefusedException(p.name() + " is at full HP");
                }
                int hp = h.name().equals("heal_hp_full") ? max : Math.min(max, p.hp() + amount);
                return new Done(List.of(new GameEvent.PokemonSet(p.withHp(hp))), who + " used " + name + " on " + p.name() + ": " + (hp - p.hp()) + " HP back (" + hp + "/" + max + ").");
            }
            case "full_restore" -> {
                requireStanding(p, "a Revive");
                if (p.hp() >= max && p.status() == Status.NONE) {
                    throw new BattleRefusedException(p.name() + " is at full HP and has no status");
                }
                return new Done(List.of(new GameEvent.PokemonSet(p.withHp(max).withStatus(Status.NONE))),
                        who + " used " + name + " on " + p.name() + ": full HP and no status.");
            }
            case "revive" -> {
                if (!p.fainted()) {
                    throw new BattleRefusedException(p.name() + " has not fainted");
                }
                int hp = Math.max(1, max * amount / 100);
                return new Done(List.of(new GameEvent.PokemonSet(p.withHp(hp))), who + " used " + name + " on " + p.name() + ": it is back with " + hp + "/" + max + " HP.");
            }
            case "cure_status" -> {
                requireStanding(p, "a Revive");
                if (!ItemEffects.cures(detail, p.status())) {
                    throw new BattleRefusedException(name + " does nothing for " + p.name() + (p.status() == Status.NONE ? ": it has no status" : ": it is " + p.status().name().toLowerCase(Locale.ROOT)));
                }
                return new Done(List.of(new GameEvent.PokemonSet(p.withStatus(Status.NONE))), who + " used " + name + " on " + p.name() + ": the status is gone.");
            }
            case "restore_pp", "restore_pp_full", "restore_pp_all", "restore_pp_all_full" -> {
                requireStanding(p, "a Revive");
                boolean all = h.name().startsWith("restore_pp_all");
                boolean full = h.name().endsWith("_full");
                List<MoveSlot> moves = new ArrayList<>();
                boolean changed = false;
                MoveSlot chosen = all ? null : slotOf(p, pick.moveId(), name);
                for (MoveSlot m : p.moves()) {
                    boolean target = all || m == chosen;
                    if (target && m.pp() < m.maxPp()) {
                        int pp = full ? m.maxPp() : Math.min(m.maxPp(), m.pp() + (amount == 0 ? m.maxPp() : amount));
                        moves.add(new MoveSlot(m.moveId(), m.name(), pp, m.maxPp(), m.priority(), m.target()));
                        changed = true;
                    } else {
                        moves.add(m);
                    }
                }
                if (!changed) {
                    throw new BattleRefusedException(all ? "No move of " + p.name() + " is missing PP" : chosen.name() + " has all its PP");
                }
                return new Done(List.of(new GameEvent.PokemonSet(p.withMoves(moves))), who + " used " + name + " on " + p.name() + (all ? ": PP restored." : ": " + chosen.name() + " has PP back."));
            }
            case "vitamin" -> {
                Stat stat = stat(detail);
                int room = Math.min(Stats.MAX_EV - p.evs().get(stat), Stats.MAX_EV_TOTAL - p.evs().total());
                if (room <= 0) {
                    throw new BattleRefusedException(p.name() + " cannot take any more " + stat.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " training");
                }
                int add = Math.min(amount, room);
                PokemonSheet next = rescale(p.withEvs(p.evs().with(stat, p.evs().get(stat) + add)), p, sp)
                        .withFriendship(Friendship.afterItem(p.friendship(), Friendship.VITAMIN, friendshipMultiplier(rules, trainer)));
                return new Done(List.of(new GameEvent.PokemonSet(next)), who + " used " + name + " on " + p.name() + ": +" + add + " " + stat.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " EVs.");
            }
            case "pp_up", "pp_max" -> {
                MoveSlot m = slotOf(p, pick.moveId(), name);
                String counter = "ppup:" + Battle.squash(m.moveId());
                int boosts = p.count(counter);
                if (boosts >= 3) {
                    throw new BattleRefusedException(m.name() + " has had all the PP boosts it can take");
                }
                int base = rules.moves().entry(m.moveId()).map(e -> e.pp()).orElse(m.maxPp());
                int times = h.name().equals("pp_max") ? 3 - boosts : Math.max(1, amount);
                times = Math.min(times, 3 - boosts);
                int gain = base / 5 * times;
                List<MoveSlot> moves = new ArrayList<>();
                for (MoveSlot s : p.moves()) {
                    moves.add(s == m ? new MoveSlot(s.moveId(), s.name(), s.pp() + gain, s.maxPp() + gain, s.priority(), s.target()) : s);
                }
                return new Done(List.of(new GameEvent.PokemonSet(p.withMoves(moves).withCount(counter, times))),
                        who + " used " + name + " on " + p.name() + ": " + m.name() + " has " + gain + " more PP.");
            }
            case "rare_candy", "exp_candy" -> {
                int cap = Math.min(Stats.MAX_LEVEL, rules.progress().pokemon().levelCap());
                if (p.level() >= cap) {
                    throw new BattleRefusedException(p.name() + " is at the level cap (" + cap + ")");
                }
                int xp = h.name().equals("exp_candy") ? amount : sp.growth().totalXp(Math.min(cap, p.level() + Math.max(1, amount))) - p.xp();
                List<GameEvent> events = ProgressDesk.gainXp(state, rules.species(), rules.moves(), rules.rulebook(), rules.progress(), p.id(), Math.max(1, xp), rules.context());
                return new Done(events, who + " used " + name + " on " + p.name() + ".");
            }
            case "iv_candy" -> {
                Stat stat = stat(detail);
                int step = (amount < 0 ? -1 : 1) * rules.economy().ivCandyStep();
                int current = p.ivs().get(stat);
                int next = Math.max(0, Math.min(Stats.MAX_IV, current + step));
                if (next == current) {
                    throw new BattleRefusedException(p.name() + "'s " + stat.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " IV is already " + (step > 0 ? "at the top (" : "at the bottom (") + current + ")");
                }
                PokemonSheet changed = p.withIvs(p.ivs().with(stat, next));
                return new Done(List.of(new GameEvent.PokemonSet(rescale(changed, p, sp))),
                        who + " used " + name + " on " + p.name() + ": " + stat.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " IV " + current + " → " + next + ".");
            }
            case "nature_mint" -> {
                if (!rules.progress().pokemon().naturesEnabled()) {
                    throw new BattleRefusedException("Natures are switched off in this game");
                }
                Nature nature = Nature.find(detail).orElseThrow(() -> new BattleRefusedException("Unknown nature " + detail));
                if (nature.id().equals(p.nature())) {
                    throw new BattleRefusedException(p.name() + " has the " + nature.name() + " nature already");
                }
                PokemonSheet changed = p.withNature(nature.id());
                return new Done(List.of(new GameEvent.PokemonSet(rescale(changed, p, sp))), who + " used " + name + " on " + p.name() + ": it has the " + nature.name() + " nature now.");
            }
            case "evolution_item" -> {
                List<GameEvent> events = ProgressDesk.check(state, rules.species(), rules.moves(), rules.rulebook(), rules.progress(), p.id(), Trigger.USE_ITEM,
                        rules.context().withUsedItem(entry.id()));
                if (events.isEmpty()) {
                    throw new BattleRefusedException(name + " has no effect on " + p.name() + " (to make a Pokémon hold it, give it to hold)");
                }
                return new Done(events, who + " used " + name + " on " + p.name() + ".");
            }
            case "berry" -> {
                requireStanding(p, "a Revive");
                String id = Battle.squash(entry.id());
                Stat trained = FRIENDSHIP_BERRIES.get(id);
                if (trained != null) {
                    int ev = p.evs().get(trained);
                    int friendship = Friendship.afterItem(p.friendship(), Friendship.FRIENDSHIP_BERRY, friendshipMultiplier(rules, trainer));
                    if (ev == 0 && friendship == p.friendship()) {
                        throw new BattleRefusedException(name + " would do nothing for " + p.name());
                    }
                    PokemonSheet next = rescale(p.withEvs(p.evs().with(trained, Math.max(0, ev - 10))), p, sp).withFriendship(friendship);
                    return new Done(List.of(new GameEvent.PokemonSet(next)), who + " fed " + name + " to " + p.name() + ": " + trained.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                            + " EVs " + ev + " → " + next.evs().get(trained) + ", friendship " + p.friendship() + " → " + next.friendship() + ".");
                }
                if (!ItemScripts.isBerry(id)) {
                    throw new BattleRefusedException(name + " has no effect when eaten");
                }
                int heal = ItemScripts.berryHeal(id, max);
                if (heal > 0 && p.hp() < max) {
                    int hp = Math.min(max, p.hp() + heal);
                    return new Done(List.of(new GameEvent.PokemonSet(p.withHp(hp))), who + " fed " + name + " to " + p.name() + ": " + (hp - p.hp()) + " HP back (" + hp + "/" + max + ").");
                }
                if (ItemScripts.berryCures(id, p.status())) {
                    return new Done(List.of(new GameEvent.PokemonSet(p.withStatus(Status.NONE))), who + " fed " + name + " to " + p.name() + ": the status is gone.");
                }
                throw new BattleRefusedException(name + " would do nothing for " + p.name());
            }
            case "tm" -> {
                String moveId = h.detail().orElseThrow();
                var learnset = rules.species().learnset(p.speciesId())
                        .orElseThrow(() -> new BattleRefusedException("The data has no TM moves for " + sp.name()));
                if (!learnset.tm().contains(moveId)) {
                    throw new BattleRefusedException(sp.name() + " cannot learn " + BattleReducer.pretty(moveId) + " from a TM");
                }
                return teach(rules, p, moveId, pick, who, name);
            }
            default -> throw new BattleRefusedException(name + " cannot be used this way");
        }
    }

    /** A TM, or a tutor: the Pokémon learns the move, or replaces the move at place {@code forget} when it knows four (chapter 5.5). */
    static Done teach(BagRules rules, PokemonSheet p, String moveId, Choice pick, String who, String name) {
        if (rules.progress().movesFrozen()) {
            throw new BattleRefusedException("Moves are frozen to the printed card in physical card mode: " + p.name() + " cannot learn " + BattleReducer.pretty(moveId));
        }
        if (p.moves().stream().anyMatch(m -> m.moveId().equals(moveId))) {
            throw new BattleRefusedException(p.name() + " knows " + BattleReducer.pretty(moveId) + " already");
        }
        List<MoveSlot> list = new ArrayList<>(p.moves());
        MoveSlot learned = Leveling.slot(rules.moves(), moveId);
        String text;
        if (list.size() < rules.progress().movesMax()) {
            list.add(learned);
            text = who + " used " + name + ": " + p.name() + " learned " + learned.name() + ".";
        } else {
            Integer place = pick.forget();
            if (place == null) {
                throw new BattleRefusedException(p.name() + " knows " + list.size() + " moves: choose the move to forget (0 to " + (list.size() - 1) + ")");
            }
            if (place < 0 || place >= list.size()) {
                throw new BattleRefusedException("There is no move at place " + place);
            }
            text = who + " used " + name + ": " + p.name() + " forgot " + list.get(place).name() + " and learned " + learned.name() + ".";
            list.set(place, learned);
        }
        return new Done(List.of(new GameEvent.PokemonSet(p.withMoves(list))), text);
    }

    // ================================================================================================================ helpers

    /** The Pokémon with its HP moved by what its maximum HP changed (EVs, IVs): it never loses HP for a change, and stays fainted when fainted. */
    private static PokemonSheet rescale(PokemonSheet next, PokemonSheet old, SpeciesBook.Species sp) {
        int oldMax = BattleSetup.maxHp(old, sp, old.level());
        int newMax = BattleSetup.maxHp(next, sp, next.level());
        if (old.fainted() || oldMax == newMax) {
            return next;
        }
        return next.withHp(Math.max(1, Math.min(newMax, old.hp() + (newMax - oldMax))));
    }

    private static double friendshipMultiplier(BagRules rules, Trainer trainer) {
        return rules.rulebook().sheet(trainer).effects().value("friendship.gainMultiplier");
    }

    private static Stat stat(String id) {
        try {
            return Stat.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BattleRefusedException("Unknown stat " + id);
        }
    }

    private static MoveSlot slotOf(PokemonSheet p, String moveId, String itemName) {
        if (moveId == null || moveId.isBlank()) {
            throw new BattleRefusedException("Choose the move to use " + itemName + " on");
        }
        String key = Battle.squash(moveId);
        return p.moves().stream().filter(m -> Battle.squash(m.moveId()).equals(key)).findFirst()
                .orElseThrow(() -> new BattleRefusedException(p.name() + " does not know " + BattleReducer.pretty(moveId)));
    }

    private static void requireStanding(PokemonSheet p, String cure) {
        if (p.fainted()) {
            throw new BattleRefusedException(p.name() + " has fainted: it needs " + cure);
        }
    }

    private static void requireAdmin(Actor who, String what) {
        if (!who.admin()) {
            throw new BattleRefusedException("Only the admin can " + what);
        }
    }

    static Trainer trainer(GameState state, String trainerId) {
        Trainer t = trainerId == null ? null : state.trainers().get(trainerId);
        if (t == null) {
            throw new BattleRefusedException("There is no trainer " + trainerId);
        }
        return t;
    }

    /** The Pokémon of this trainer; it must not be in a battle that has not been settled yet (the battle works on its own copy). */
    static PokemonSheet ownedSheet(GameState state, Trainer trainer, String trainerId, String pokemonId) {
        PokemonSheet p = state.pokemon().get(pokemonId);
        if (p == null) {
            throw new BattleRefusedException("There is no Pokémon " + pokemonId);
        }
        if (!trainerId.equals(p.ownerId())) {
            throw new BattleRefusedException(p.name() + " is not " + trainer.name() + "'s Pokémon");
        }
        if (inBattle(state, p.id())) {
            throw new BattleRefusedException(p.name() + " is in a battle: use the item as a battle action");
        }
        return p;
    }

    /** True if the Pokémon is in a battle whose rewards have not been paid yet. */
    static boolean inBattle(GameState state, String pokemonId) {
        Battle b = state.battle();
        return b != null && !b.settled() && b.combatants().containsKey(pokemonId);
    }

    private static GameState apply(GameState state, List<GameEvent> events) {
        try {
            return GameReducer.applyAll(state, events);
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
    }

    private static List<GameEvent> checked(GameState state, List<GameEvent> events) {
        return ProgressDesk.check(state, events);
    }

}
