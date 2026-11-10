/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.game.MoveResolver.Spec;
import net.quimmilho.pokednd.core.game.MoveResolver.Strike;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * The scripted moves that Core runs by hand (chapter 19.6, phase 46): the 25 scripted moves that the most learnsets contain. The list of
 * ids is {@code rules/implemented-effects.json}; a test checks that this class has a branch for every id on it. Most of them only change
 * how the standard path of {@link MoveResolver} treats the move ({@link #shape}, {@link #failure}, {@link #extras}); a few act on their
 * own ({@link #special}).
 *
 * <p>Where the games need something the game here does not have, the simple version is used and written down in Appendix D of the
 * specification: Tera Blast has no Terastal type, so it is Normal; Hidden Power reads the type worked out from the IVs; Secret Power
 * paralyzes (there is no terrain); Round and Helping Hand need allies on the field.
 */
final class MoveScripts {

    private MoveScripts() {
    }

    // ----------------------------------------------------------------------------------------------------------------- shape

    /** Changes the move before it is used: its type, category and power. */
    static void shape(MoveResolver r, Spec s) {
        Combatant a = r.actor();
        MoveProfile p = s.p;
        switch (s.id) {
            case "terablast" -> {
                boolean physical = r.staged(a, "atk") > r.staged(a, "spa");
                s.p = p.withType(PokemonType.NORMAL).withCategory(physical ? MoveProfile.Category.PHYSICAL : MoveProfile.Category.SPECIAL);
            }
            case "hiddenpower" -> s.p = p.withType(a.build().hiddenPower() == null ? PokemonType.NORMAL : a.build().hiddenPower());
            case "return" -> s.p = p.withPower(Math.max(1, a.build().friendship() * 2 / 5));
            case "frustration" -> s.p = p.withPower(Math.max(1, (255 - a.build().friendship()) * 2 / 5));
            case "facade" -> {
                s.ignoreBurnPenalty = true;
                Status st = a.status();
                if (st == Status.BURN || st == Status.POISON || st == Status.TOXIC || st == Status.PARALYSIS) {
                    s.p = p.withPower(p.power() * 2);
                }
            }
            case "stompingtantrum" -> {
                if (a.condition().has(Volatiles.LAST_FAILED)) {
                    s.p = p.withPower(p.power() * 2);
                }
            }
            case "round" -> {
                if (allyUsed(r, "round")) {
                    s.p = p.withPower(p.power() * 2);
                }
            }
            case "rollout" -> s.p = p.withPower(p.power() << Math.min(4, a.condition().volatileValue(Volatiles.ROLLOUT_HITS)));
            case "naturalgift" -> {
                String item = a.condition().heldItem();
                var gift = item == null ? java.util.Optional.<MoveBook.Gift>empty() : r.book().gift(item);
                if (gift.isEmpty()) {
                    s.failure = "But it failed!";
                } else {
                    s.p = p.withPower(gift.get().power()).withType(gift.get().type());
                }
            }
            default -> {
            }
        }
    }

    /** True if a friend of the user, who moved earlier this round, chose this move. */
    private static boolean allyUsed(MoveResolver r, String moveId) {
        Battle b = r.start();
        for (String id : b.resolved()) {
            Combatant c = b.combatant(id);
            Battle.Action action = b.declared().get(id);
            if (c != null && !id.equals(r.actor().id()) && b.sideOf(c) == b.sideOf(r.actor()) && action != null
                    && action.kind() == Battle.ActionKind.MOVE && moveId.equals(action.moveId())) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------------------------- failure

    /** The reason a move cannot work at all (so "But it failed!"), or null. */
    static String failure(MoveResolver r, Spec s, String prefix) {
        Combatant a = r.actor();
        boolean asleep = a.status() == Status.SLEEP && !r.awake();
        switch (s.id) {
            case "rest" -> {
                if (a.hp() >= a.maxHp() || asleep) {
                    return "But it failed!";
                }
            }
            case "snore" -> {
                if (!asleep) {
                    return "But it failed!";
                }
            }
            case "sleeptalk" -> {
                if (!asleep || callable(r).isEmpty()) {
                    return "But it failed!";
                }
            }
            case "substitute" -> {
                if (a.condition().has(Volatiles.SUBSTITUTE)) {
                    return r.start().label(a) + " already has a substitute!";
                }
                if (a.hp() <= Math.max(1, a.maxHp() / 4)) {
                    return "It was too weak to make a substitute!";
                }
            }
            case "protect", "endure" -> {
                if (r.start().queue().size() <= 1) {
                    return "But it failed!";
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** The moves Sleep Talk may call: known, with PP, and not marked as impossible to call. */
    private static List<MoveSlot> callable(MoveResolver r) {
        List<MoveSlot> list = new ArrayList<>();
        for (MoveSlot m : r.actor().moves()) {
            if (m.moveId().equals("sleeptalk") || r.start().rules().ppEnabled() && m.pp() <= 0) {
                continue;
            }
            var entry = r.entryOf(m.moveId());
            if (entry.isEmpty() || entry.get().effect() != null && entry.get().effect().path("flags").path("nosleeptalk").asBoolean(false)) {
                continue;
            }
            list.add(m);
        }
        return list;
    }

    // ---------------------------------------------------------------------------------------------------------------- special

    /** The moves that do not use the standard hit-and-damage path. Null if the move uses it. */
    static Runnable special(MoveResolver r, Spec s, List<Combatant> targets, String prefix) {
        Combatant a = r.actor();
        String who = r.start().label(a);
        switch (s.id) {
            case "rest" -> {
                return () -> {
                    Combatant now = r.cur(a.id());
                    if (now.status() != Status.NONE) {
                        r.status(now, Status.NONE, 0, null);
                    }
                    r.hp(now, now.maxHp() - now.hp(), "from resting");
                    r.status(now, Status.SLEEP, 2, who + " went to sleep and became healthy!");
                    r.finish(s, false);
                };
            }
            case "sleeptalk" -> {
                List<MoveSlot> choices = callable(r);
                MoveSlot picked = choices.get(r.draw(prefix + "talk", choices.size()));
                Runnable inner = r.planCalled(picked.moveId(), "call-");
                return () -> {
                    r.note(who + " uses " + picked.name() + " in its sleep!");
                    inner.run();
                    r.finish(s, false);
                };
            }
            case "protect", "endure" -> {
                int n = a.condition().volatileValue(Volatiles.STALL);
                boolean ok = n == 0 || r.chance(prefix + "stall", s.name + ": it may fail when used again and again", Math.pow(1.0 / 3.0, n));
                String key = s.id.equals("protect") ? Volatiles.PROTECT : Volatiles.ENDURE;
                return () -> {
                    if (!ok) {
                        r.note("But it failed!");
                        r.vol(r.cur(a.id()), Volatiles.STALL, 0, null);
                        r.finish(s, true);
                        return;
                    }
                    r.vol(r.cur(a.id()), key, 1, who + (key.equals(Volatiles.PROTECT) ? " protected itself!" : " braced itself!"));
                    r.vol(r.cur(a.id()), Volatiles.STALL, n + 1, null);
                    r.finish(s, false);
                };
            }
            case "substitute" -> {
                return () -> {
                    Combatant now = r.cur(a.id());
                    int cost = Math.max(1, now.maxHp() / 4);
                    r.hp(now, -cost, "to make a substitute");
                    r.vol(now, Volatiles.SUBSTITUTE, cost, who + " put in a substitute!");
                    r.finish(s, false);
                };
            }
            case "helpinghand" -> {
                Combatant ally = targets.get(0);
                boolean ok = !r.start().resolved().contains(ally.id());
                return () -> {
                    if (ok) {
                        r.vol(r.cur(ally.id()), Volatiles.HELPING_HAND, 1, who + " is ready to help " + r.start().label(ally) + "!");
                    } else {
                        r.note("But it failed!");
                    }
                    r.finish(s, !ok);
                };
            }
            case "curse" -> {
                boolean ghost = a.types().contains(PokemonType.GHOST);
                Combatant foe = targets.isEmpty() ? null : targets.get(0);
                return () -> {
                    Combatant now = r.cur(a.id());
                    if (ghost) {
                        if (foe == null || r.cur(foe.id()).condition().has(Volatiles.CURSE)) {
                            r.note("But it failed!");
                            r.finish(s, true);
                            return;
                        }
                        r.hp(now, -Math.max(1, now.maxHp() / 2), "as it cuts its own HP");
                        r.vol(r.cur(foe.id()), Volatiles.CURSE, 1, who + " laid a curse on " + r.start().label(foe) + "!");
                    } else {
                        r.stage(now, "spe", -1);
                        r.stage(now, "atk", 1);
                        r.stage(now, "def", 1);
                    }
                    r.finish(s, false);
                };
            }
            default -> {
                return null;
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------- extras

    /** What a move does after the standard path: items, locks and the like. */
    static void extras(MoveResolver r, Spec s, List<Strike> strikes, boolean anyLanded) {
        Combatant a = r.actor();
        String who = r.start().label(a);
        switch (s.id) {
            case "knockoff" -> {
                for (Strike x : strikes) {
                    Combatant t = r.cur(x.target.id());
                    String held = t.condition().heldItem();
                    if (x.landed() && !t.fainted() && held != null && x.absorbedBySubstitute == 0) {
                        r.item(t, null, who + " knocked off " + r.start().label(t) + "'s " + BattleReducer.pretty(held) + "!");
                    }
                }
            }
            case "covet" -> {
                Combatant user = r.cur(a.id());
                for (Strike x : strikes) {
                    Combatant t = r.cur(x.target.id());
                    String held = t.condition().heldItem();
                    if (x.landed() && !user.fainted() && user.condition().heldItem() == null && !t.fainted() && held != null && x.absorbedBySubstitute == 0) {
                        r.item(t, null, null);
                        r.item(user, held, who + " stole " + r.start().label(t) + "'s " + BattleReducer.pretty(held) + "!");
                        break;
                    }
                }
            }
            case "uproar" -> {
                Combatant user = r.cur(a.id());
                if (!user.fainted() && !user.condition().has(Volatiles.UPROAR)) {
                    r.vol(user, Volatiles.UPROAR, 3, who + " caused an uproar!");
                }
            }
            case "rollout" -> {
                Combatant user = r.cur(a.id());
                int hits = a.condition().volatileValue(Volatiles.ROLLOUT_HITS);
                if (user.fainted()) {
                    return;
                }
                if (anyLanded && hits + 1 < 5) {
                    r.vol(user, Volatiles.ROLLOUT_HITS, hits + 1, null);
                    r.vol(user, Volatiles.ROLLOUT, 5 - (hits + 1), null);
                } else {
                    r.vol(user, Volatiles.ROLLOUT_HITS, 0, null);
                    r.vol(user, Volatiles.ROLLOUT, 0, null);
                }
            }
            case "naturalgift" -> {
                String held = a.condition().heldItem();
                if (held != null && !r.cur(a.id()).fainted()) {
                    r.item(r.cur(a.id()), null, who + "'s " + BattleReducer.pretty(held) + " was used up!");
                }
            }
            case "attract" -> {
                for (Strike x : strikes) {
                    Combatant t = r.cur(x.target.id());
                    if (!x.landed() || t.fainted()) {
                        continue;
                    }
                    String g1 = a.build().gender();
                    String g2 = t.build().gender();
                    boolean opposite = (g1.equals("M") && g2.equals("F")) || (g1.equals("F") && g2.equals("M"));
                    if (opposite && !t.condition().has(Volatiles.ATTRACT)) {
                        r.vol(t, Volatiles.ATTRACT, 1, r.start().label(t) + " fell in love with " + who + "!");
                    } else {
                        r.note("But it failed!");
                    }
                }
            }
            case "endeavor" -> {
                if (strikes.stream().allMatch(x -> x.damage <= 0 || !x.landed())) {
                    boolean missed = strikes.stream().anyMatch(x -> x.hit == net.quimmilho.pokednd.core.combat.HitRoll.Result.MISS || x.blocked != null);
                    if (!missed) {
                        r.note("But it failed!");
                    }
                }
            }
            default -> {
            }
        }
    }

    /** True if the move has its own branch here (used by the tests to check the list of implemented moves). */
    static boolean knows(String moveId) {
        return switch (moveId) {
            case "rest", "sleeptalk", "helpinghand", "gigaimpact", "hyperbeam", "protect", "endure", "substitute", "terablast", "facade", "snore",
                 "round", "attract", "hiddenpower", "return", "frustration", "endeavor", "secretpower", "curse", "knockoff", "uproar", "covet",
                 "rollout", "stompingtantrum", "naturalgift", "struggle" -> true;
            default -> false;
        };
    }

    @SuppressWarnings("unused")
    private static JsonNode flags(Spec s) {
        return s.effect.path("flags");
    }
}
