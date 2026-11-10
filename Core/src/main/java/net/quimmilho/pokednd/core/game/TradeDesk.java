/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Trigger;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.game.Trade.Side;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * Trading between two players (chapter 8.8): one proposes what they give and what they want back, the other looks at it and confirms, and
 * then everything changes hands at once. With {@code economy.trade.requireApproval} the admin has to approve a confirmed trade first. By
 * default a trade happens only at a trade-centre NPC ({@code economy.trade.location}).
 *
 * <p>A completed trade is <b>one action</b>: Pokémon, items and money of both trainers change in the same list of events, so either all of
 * it happens or none of it, and an undo takes all of it back. Everything is checked again when the trade completes, because the
 * trainers may have changed since the proposal. Pokémon that arrive go to the party if there is room ({@code pokemon.party.size}) and to
 * a PC box otherwise. After the trade the evolution rules are looked at for each Pokémon that changed hands (trade evolutions; chapter 5.4).
 * A Pokémon that is in a battle that has not been settled cannot be traded.
 */
public final class TradeDesk {

    private TradeDesk() {
    }

    // ================================================================================================================ the steps

    /**
     * {@code fromId} proposes a trade to {@code toId}.
     *
     * @param centreId the trade-centre NPC where the two stand, or null when {@code economy.trade.location} is {@code anywhere}
     */
    public static List<GameEvent> propose(GameState state, BagRules rules, String tradeId, String fromId, String toId, String centreId, Side give, Side want) {
        if (tradeId == null || tradeId.isBlank()) {
            throw new BattleRefusedException("A trade needs an id");
        }
        if (state.trades().containsKey(tradeId)) {
            throw new BattleRefusedException("The trade " + tradeId + " exists already");
        }
        Trainer from = BagDesk.trainer(state, fromId);
        Trainer to = BagDesk.trainer(state, toId);
        if (fromId.equals(toId)) {
            throw new BattleRefusedException("A trainer cannot trade with themselves");
        }
        if (rules.economy().tradeLocation() == EconomySettings.TradeLocation.TRADE_CENTRE && (centreId == null || centreId.isBlank())) {
            throw new BattleRefusedException("Trades happen at a trade centre");
        }
        Side g = give == null ? Side.nothing() : give;
        Side w = want == null ? Side.nothing() : want;
        if (g.empty() && w.empty()) {
            throw new BattleRefusedException("A trade needs something to trade");
        }
        validate(state, rules, from, fromId, g);
        validate(state, rules, to, toId, w);
        Trade trade = new Trade(tradeId, centreId, fromId, toId, g, w, Trade.Status.PROPOSED);
        return ProgressDesk.check(state, List.of(new GameEvent.TradeSet(trade),
                new GameEvent.GameNoted(fromId, from.name() + " offers " + describe(rules, state, g) + " to " + to.name() + " for " + describe(rules, state, w) + ".")));
    }

    /**
     * The trainer who was asked confirms. The trade happens now, unless the game needs the admin's approval: then it waits for
     * {@link #approve}.
     */
    public static List<GameEvent> confirm(GameState state, BagRules rules, String tradeId, String byTrainerId) {
        Trade t = open(state, tradeId);
        if (!t.toId().equals(byTrainerId)) {
            throw new BattleRefusedException("Only " + BagDesk.trainer(state, t.toId()).name() + " can confirm this trade");
        }
        if (t.status() != Trade.Status.PROPOSED) {
            throw new BattleRefusedException("This trade is confirmed already and waits for the admin");
        }
        if (rules.economy().tradeApproval()) {
            return ProgressDesk.check(state, List.of(new GameEvent.TradeSet(t.withStatus(Trade.Status.CONFIRMED)),
                    new GameEvent.GameNoted(byTrainerId, BagDesk.trainer(state, t.toId()).name() + " confirmed the trade; it waits for the admin.")));
        }
        return complete(state, rules, t);
    }

    /** The admin approves a confirmed trade, and it happens. */
    public static List<GameEvent> approve(GameState state, BagRules rules, Actor who, String tradeId) {
        if (!who.admin()) {
            throw new BattleRefusedException("Only the admin can approve a trade");
        }
        Trade t = open(state, tradeId);
        if (t.status() != Trade.Status.CONFIRMED) {
            throw new BattleRefusedException("The other trainer has not confirmed this trade yet");
        }
        return complete(state, rules, t);
    }

    /** Either trainer says no or takes the offer back; the admin can close any trade. Nothing changes hands. */
    public static List<GameEvent> decline(GameState state, Actor who, String tradeId, String byTrainerId) {
        Trade t = open(state, tradeId);
        if (!who.admin() && !t.fromId().equals(byTrainerId) && !t.toId().equals(byTrainerId)) {
            throw new BattleRefusedException("Only the two trainers of a trade can call it off");
        }
        boolean offerer = t.fromId().equals(byTrainerId);
        String outcome = who.admin() && !t.fromId().equals(byTrainerId) && !t.toId().equals(byTrainerId) ? "refused" : offerer ? "cancelled" : "declined";
        return ProgressDesk.check(state, List.of(new GameEvent.TradeClosed(tradeId, outcome),
                new GameEvent.GameNoted(byTrainerId, "The trade between " + BagDesk.trainer(state, t.fromId()).name() + " and " + BagDesk.trainer(state, t.toId()).name() + " was " + outcome + ".")));
    }

    // ================================================================================================================ happening

    /** Everything changes hands: one list of events, built and checked on a working copy of the state. */
    private static List<GameEvent> complete(GameState state, BagRules rules, Trade t) {
        Trainer from = BagDesk.trainer(state, t.fromId());
        Trainer to = BagDesk.trainer(state, t.toId());
        validate(state, rules, from, t.fromId(), t.give());
        validate(state, rules, to, t.toId(), t.want());
        // money
        Trainer a = from.withMoney(from.money() - t.give().money() + t.want().money());
        Trainer b = to.withMoney(to.money() - t.want().money() + t.give().money());
        // items: both leave first, then both arrive, so the limits of the bags are fair to both
        for (Map.Entry<String, Integer> e : t.give().items().entrySet()) {
            a = Bag.remove(rules, a, e.getKey(), e.getValue(), from.name());
        }
        for (Map.Entry<String, Integer> e : t.want().items().entrySet()) {
            b = Bag.remove(rules, b, e.getKey(), e.getValue(), to.name());
        }
        for (Map.Entry<String, Integer> e : t.want().items().entrySet()) {
            a = Bag.add(rules, a, e.getKey(), e.getValue(), from.name());
        }
        for (Map.Entry<String, Integer> e : t.give().items().entrySet()) {
            b = Bag.add(rules, b, e.getKey(), e.getValue(), to.name());
        }
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.TrainerSet(t.fromId(), a));
        events.add(new GameEvent.TrainerSet(t.toId(), b));
        // Pokémon: they arrive in the party while there is room, then in a PC box
        List<PokemonSheet> moved = new ArrayList<>();
        int roomFrom = rules.progress().partySize() - partyAfter(state, t.fromId(), t.give().pokemon());
        int roomTo = rules.progress().partySize() - partyAfter(state, t.toId(), t.want().pokemon());
        for (String id : t.give().pokemon()) {
            PokemonSheet p = state.pokemon().get(id);
            boolean party = roomTo-- > 0;
            moved.add(p.withOwner(t.toId(), party ? PokemonSheet.Location.PARTY : PokemonSheet.Location.PC, p.ball()));
        }
        for (String id : t.want().pokemon()) {
            PokemonSheet p = state.pokemon().get(id);
            boolean party = roomFrom-- > 0;
            moved.add(p.withOwner(t.fromId(), party ? PokemonSheet.Location.PARTY : PokemonSheet.Location.PC, p.ball()));
        }
        moved.forEach(p -> events.add(new GameEvent.PokemonSet(p)));
        events.add(new GameEvent.TradeClosed(t.id(), "completed"));
        events.add(new GameEvent.GameNoted(t.fromId(), from.name() + " and " + to.name() + " traded: " + describe(rules, state, t.give()) + " for " + describe(rules, state, t.want()) + "."));
        // trade evolutions
        GameState working = applyChecked(state, events);
        for (PokemonSheet p : moved) {
            List<GameEvent> evolution = ProgressDesk.check(working, rules.species(), rules.moves(), rules.rulebook(), rules.progress(), p.id(), Trigger.TRADE, rules.context());
            events.addAll(evolution);
            working = applyChecked(working, evolution);
        }
        return List.copyOf(events);
    }

    private static int partyAfter(GameState state, String trainerId, List<String> leaving) {
        return (int) BattleSetup.party(state, trainerId).stream().filter(p -> !leaving.contains(p.id())).count();
    }

    // ================================================================================================================ helpers

    /** What one trainer gives can be given: the Pokémon are theirs and free, the items and the money are in their bag and purse. */
    private static void validate(GameState state, BagRules rules, Trainer trainer, String trainerId, Side side) {
        for (String id : side.pokemon()) {
            PokemonSheet p = state.pokemon().get(id);
            if (p == null || !trainerId.equals(p.ownerId())) {
                throw new BattleRefusedException(trainer.name() + " does not have the Pokémon " + id);
            }
            if (BagDesk.inBattle(state, id)) {
                throw new BattleRefusedException(p.name() + " is in a battle and cannot be traded");
            }
        }
        if (side.pokemon().stream().distinct().count() != side.pokemon().size()) {
            throw new BattleRefusedException("A Pokémon can be traded only once");
        }
        for (Map.Entry<String, Integer> e : side.items().entrySet()) {
            if (Bag.count(trainer.items(), e.getKey()) < e.getValue()) {
                throw new BattleRefusedException(trainer.name() + " does not have " + e.getValue() + " " + Bag.name(rules, e.getKey()));
            }
        }
        if (trainer.money() < side.money()) {
            throw new BattleRefusedException(trainer.name() + " does not have " + side.money() + " " + rules.economy().currency());
        }
    }

    private static Trade open(GameState state, String tradeId) {
        Trade t = tradeId == null ? null : state.trades().get(tradeId);
        if (t == null) {
            throw new BattleRefusedException("There is no open trade " + tradeId);
        }
        return t;
    }

    private static String describe(BagRules rules, GameState state, Side side) {
        if (side.empty()) {
            return "nothing";
        }
        List<String> parts = new ArrayList<>();
        for (String id : side.pokemon()) {
            PokemonSheet p = state.pokemon().get(id);
            parts.add(p == null ? id : p.name());
        }
        side.items().forEach((id, n) -> parts.add(n + " " + Bag.name(rules, id)));
        if (side.money() > 0) {
            parts.add(side.money() + " " + rules.economy().currency());
        }
        return String.join(", ", parts);
    }

    private static GameState applyChecked(GameState state, List<GameEvent> events) {
        try {
            return GameReducer.applyAll(state, events);
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
    }
}
