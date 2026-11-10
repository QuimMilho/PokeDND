/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.ActionKind;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.InitiativeEntry;
import net.quimmilho.pokednd.core.game.Battle.InitiativeSlot;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Participant;

/**
 * Who acts first (chapter 7.3). Two orders are worked out here, both as pure functions of the state:
 *
 * <ul>
 *   <li><b>Initiative</b>: the total of the 1d20 roll and the Speed modifier, highest first. A tie goes to the higher Speed stat, then
 *       to the random number that was drawn for the coin flip.</li>
 *   <li><b>The queue</b> of a round: first all switches, then items and Poké Balls, then flee attempts (each group by initiative); then
 *       the moves by priority bracket from +5 down to −7, and inside a bracket by initiative. A Pokémon of the same participant in a
 *       later slot acts after the one before it. With {@code combat.order.trainerActionsFirst} off, a switch, an item, a ball and a flee
 *       attempt count as a move of priority 0.</li>
 * </ul>
 */
final class BattleOrder {

    private static final int GROUP_SWITCH = 0;
    private static final int GROUP_ITEM = 1;
    private static final int GROUP_FLEE = 2;
    private static final int GROUP_MOVE = 3;

    private BattleOrder() {
    }

    /**
     * The initiative of the participants that rolled, first to act first.
     *
     * @throws InvalidEventException if a roll has not been answered
     */
    static List<InitiativeEntry> initiative(GameState state, Battle battle, List<InitiativeSlot> slots, Map<String, Integer> tieBreaks) {
        List<InitiativeEntry> entries = new ArrayList<>();
        for (InitiativeSlot slot : slots) {
            GameState.RollResult roll = null;
            for (GameState.RollResult r : state.rolls()) {
                if (r.requestId().equals(slot.requestId())) {
                    roll = r; // the last answer wins: an admin may have changed it
                }
            }
            Participant p = battle.participants().get(slot.participantId());
            if (roll == null) {
                throw new InvalidEventException(BattleReducer.name(p) + " has not rolled initiative yet");
            }
            int die = roll.total();
            entries.add(new InitiativeEntry(slot.participantId(), die, slot.modifier(), die + slot.modifier(), slot.speed(),
                    tieBreaks.getOrDefault(slot.participantId(), 0)));
        }
        entries.sort(Comparator.comparingInt(InitiativeEntry::total).reversed()
                .thenComparing(Comparator.comparingInt(InitiativeEntry::speed).reversed())
                .thenComparing(Comparator.comparingInt(InitiativeEntry::tieBreak).reversed())
                .thenComparing(InitiativeEntry::participantId));
        return entries;
    }

    /** The queue of the round: the Pokémon that have declared, in the order their actions resolve. */
    static List<String> queue(Battle battle) {
        record Item(String actorId, int group, int priority, int initiative, int slot) {
        }
        List<Item> items = new ArrayList<>();
        for (Map.Entry<String, Action> e : battle.declared().entrySet()) {
            Combatant c = battle.combatant(e.getKey());
            if (c == null || !battle.isActive(c)) {
                continue; // it was switched out since: its choice no longer counts
            }
            Participant owner = battle.ownerOf(c);
            int group = group(battle.rules(), e.getValue());
            items.add(new Item(c.id(), group, group == GROUP_MOVE ? priority(c, e.getValue()) : 0, battle.initiativeIndex(owner.id()),
                    owner.active().indexOf(c.id())));
        }
        items.sort(Comparator.comparingInt(Item::group)
                .thenComparing(Comparator.comparingInt(Item::priority).reversed())
                .thenComparingInt(Item::initiative)
                .thenComparingInt(Item::slot));
        return items.stream().map(Item::actorId).toList();
    }

    private static int group(BattleSettings rules, Action action) {
        if (!rules.trainerActionsFirst()) {
            return GROUP_MOVE;
        }
        return switch (action.kind()) {
            case SWITCH -> GROUP_SWITCH;
            case ITEM, BALL -> GROUP_ITEM;
            case FLEE -> GROUP_FLEE;
            case MOVE, SKIP -> GROUP_MOVE;
        };
    }

    private static int priority(Combatant actor, Action action) {
        if (action.kind() != ActionKind.MOVE) {
            return 0;
        }
        return actor.moves().stream().filter(m -> m.moveId().equals(action.moveId())).map(MoveSlot::priority).findFirst().orElse(0);
    }
}
