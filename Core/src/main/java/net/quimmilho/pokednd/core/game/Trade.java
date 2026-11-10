/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A trade between two trainers that has been proposed and is not finished (chapter 8.8, "Trading"). One trainer proposes what they give and
 * what they want back; the other looks at it and confirms; then everything changes hands at once ({@link TradeDesk}). If the game needs the
 * admin's approval ({@code economy.trade.requireApproval}) the trade waits for it after the confirmation.
 *
 * @param id          the id of the trade, unique in a game
 * @param centreId    the trade-centre NPC where it happens, or null when trading is allowed anywhere
 * @param fromId      the trainer who proposed
 * @param toId        the trainer who was asked
 * @param give        what the proposer gives
 * @param want        what the proposer wants from the other trainer (it may be empty: a gift)
 * @param status      where the trade is
 */
public record Trade(String id, String centreId, String fromId, String toId, Side give, Side want, Status status) {

    /** One side of a trade: Pokémon, items and money. */
    public record Side(List<String> pokemon, Map<String, Integer> items, long money) {
        public Side {
            pokemon = pokemon == null ? List.of() : List.copyOf(pokemon);
            items = items == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(items));
            if (money < 0) {
                throw new IllegalArgumentException("Money in a trade cannot be negative");
            }
            for (Map.Entry<String, Integer> e : items.entrySet()) {
                if (e.getValue() < 1) {
                    throw new IllegalArgumentException("A trade moves at least 1 of an item, not " + e.getValue() + " " + e.getKey());
                }
            }
        }

        /** Nothing at all. */
        public static Side nothing() {
            return new Side(List.of(), Map.of(), 0);
        }

        public static Side pokemon(String... ids) {
            return new Side(List.of(ids), Map.of(), 0);
        }

        public boolean empty() {
            return pokemon.isEmpty() && items.isEmpty() && money == 0;
        }
    }

    /** Where a trade is. */
    public enum Status {
        /** The proposer has made the offer; the other trainer has to answer. */
        PROPOSED,
        /** The other trainer confirmed; the admin has to approve it before it happens. */
        CONFIRMED
    }

    public Trade {
        if (id == null || id.isBlank() || fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            throw new IllegalArgumentException("A trade needs an id and two trainers");
        }
        give = give == null ? Side.nothing() : give;
        want = want == null ? Side.nothing() : want;
        status = status == null ? Status.PROPOSED : status;
    }

    public Trade withStatus(Status value) {
        return new Trade(id, centreId, fromId, toId, give, want, value);
    }
}
