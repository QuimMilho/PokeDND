/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.ItemRules.Handler;

/**
 * What an item does when it is used as a battle action (chapter 7.4, 8.8). Only some items work in a battle ({@link BagDesk#usableInBattle}):
 * potions, status heals, Revive, Ether and Elixir, X items, Dire Hit, Guard Spec and berries. {@link #plan} gives the events; no events
 * means the item would do nothing now, and then it is neither used nor used up. Dire Hit (+2 critical stages) and Guard Spec (no stat can be
 * lowered by a foe) last until the Pokémon leaves the field.
 */
final class BattleItems {

    private BattleItems() {
    }

    /** The events of using the item on this Pokémon now; empty when it would have no effect. {@code moveId} is the move an Ether is used on. */
    static List<GameEvent> plan(Battle b, MoveBook book, Combatant t, String itemId, String moveId) {
        ItemEntry entry = book.item(itemId).orElse(null);
        Handler h = entry == null ? null : entry.handler().orElse(null);
        List<GameEvent> events = new ArrayList<>();
        if (h == null || !BagDesk.usableInBattle(book, itemId)) {
            return events;
        }
        String id = b.battleId();
        int amount = h.amount().orElse(0);
        String detail = h.detail().orElse("");
        String cause = "from the " + entry.name();
        switch (h.name()) {
            case "heal_hp", "heal_hp_full" -> {
                if (!t.fainted() && t.hp() < t.maxHp()) {
                    int delta = h.name().equals("heal_hp_full") ? t.maxHp() - t.hp() : Math.min(amount, t.maxHp() - t.hp());
                    events.add(new GameEvent.HpChanged(id, t.id(), delta, cause));
                }
            }
            case "full_restore" -> {
                if (!t.fainted()) {
                    if (t.hp() < t.maxHp()) {
                        events.add(new GameEvent.HpChanged(id, t.id(), t.maxHp() - t.hp(), cause));
                    }
                    if (t.status() != Status.NONE) {
                        events.add(new GameEvent.StatusSet(id, t.id(), Status.NONE, 0, null));
                    }
                }
            }
            case "revive" -> {
                if (t.fainted() && !b.isActive(t)) {
                    events.add(new GameEvent.CombatantSet(id, t.withHp(Math.max(1, t.maxHp() * amount / 100))));
                }
            }
            case "cure_status" -> {
                if (!t.fainted() && ItemEffects.cures(detail, t.status())) {
                    events.add(new GameEvent.StatusSet(id, t.id(), Status.NONE, 0, null));
                }
            }
            case "restore_pp", "restore_pp_full", "restore_pp_all", "restore_pp_all_full" -> {
                if (!t.fainted()) {
                    boolean all = h.name().startsWith("restore_pp_all");
                    boolean full = h.name().endsWith("_full");
                    List<MoveSlot> moves = new ArrayList<>();
                    boolean changed = false;
                    for (MoveSlot m : t.moves()) {
                        boolean target = all || m.moveId().equals(moveId);
                        if (target && m.pp() < m.maxPp()) {
                            int pp = full ? m.maxPp() : Math.min(m.maxPp(), m.pp() + (amount == 0 ? m.maxPp() : amount));
                            moves.add(new MoveSlot(m.moveId(), m.name(), pp, m.maxPp(), m.priority(), m.target()));
                            changed = true;
                        } else {
                            moves.add(m);
                        }
                    }
                    if (changed) {
                        events.add(new GameEvent.CombatantSet(id, t.withMoves(moves)));
                        events.add(new GameEvent.BattleNoted(id, "The PP of " + b.label(t) + " was restored."));
                    }
                }
            }
            case "x_stat" -> {
                String stage = ItemEffects.stageId(detail);
                if (!t.fainted() && t.condition().stage(stage) < 6) {
                    events.add(new GameEvent.StageChanged(id, t.id(), stage, amount, cause));
                }
            }
            case "dire_hit" -> {
                if (!t.fainted() && !t.condition().has(Volatiles.FOCUS_ENERGY)) {
                    events.add(new GameEvent.VolatileSet(id, t.id(), Volatiles.FOCUS_ENERGY, 1, b.label(t) + " is getting pumped!"));
                }
            }
            case "guard_spec" -> {
                if (!t.fainted() && !t.condition().has(Volatiles.GUARD_SPEC)) {
                    events.add(new GameEvent.VolatileSet(id, t.id(), Volatiles.GUARD_SPEC, 1, b.label(t) + " is shrouded in mist!"));
                }
            }
            case "berry" -> {
                String berry = Battle.squash(entry.id());
                int heal = ItemScripts.berryHeal(berry, t.maxHp());
                if (!t.fainted() && heal > 0 && t.hp() < t.maxHp()) {
                    events.add(new GameEvent.HpChanged(id, t.id(), Math.min(heal, t.maxHp() - t.hp()), "from the berry"));
                } else if (!t.fainted() && ItemScripts.berryCures(berry, t.status())) {
                    events.add(new GameEvent.StatusSet(id, t.id(), Status.NONE, 0, null));
                }
            }
            default -> {
            }
        }
        return events;
    }

    /** Why the item cannot be chosen now, or null when it can. Used when the action is declared. */
    static String refusal(Battle b, MoveBook book, Combatant t, String itemId, String moveId) {
        ItemEntry entry = book.item(itemId).orElse(null);
        if (entry == null) {
            return null; // the book does not know the item: the admin handles it
        }
        if (!BagDesk.usableInBattle(book, itemId)) {
            return entry.name() + " cannot be used in a battle";
        }
        String detail = entry.handler().map(h -> h.name()).orElse("");
        if (detail.startsWith("restore_pp") && !detail.contains("_all") && (moveId == null || t.moves().stream().noneMatch(m -> m.moveId().equals(moveId)))) {
            return "Choose the move to use " + entry.name() + " on";
        }
        if (plan(b, book, t, itemId, moveId).isEmpty()) {
            return entry.name() + " would have no effect on " + t.name();
        }
        return null;
    }
}
