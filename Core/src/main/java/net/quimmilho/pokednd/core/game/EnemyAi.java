/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.quimmilho.pokednd.core.combat.Damage;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * The enemy AI (chapter 7.13): what a wild Pokémon, a boss or an NPC's Pokémon does when nobody has chosen for it. It only ever returns an
 * action that the rules of declaring accept (a move with PP, a legal target, a switch to a healthy Pokémon of the reserve), so
 * {@link BattleDesk#declareEnemies} never refuses it.
 *
 * <p><b>Scoring.</b> A damaging move is worth {@code expected damage × hit chance}, ×1.25 when it is super effective and ×1.5 when it
 * would finish the target; the expected damage is the real formula of chapter 7.6 with the average of the damage dice. A status or setup
 * move is worth a small fixed number, and only when it would help (a status on a target that has none, a boost that is not high yet, a
 * heal at half HP or less). A move that cannot hurt the target is worth nothing.
 *
 * <p><b>Levels</b> ({@code combat.ai.level}). {@code easy} picks any legal move. {@code normal} picks among the moves that score at least
 * 60 % of the best one, weighted by their score. {@code hard} picks the best one. An NPC trainer's Pokémon in a bad type matchup (a foe
 * hits it super effectively and it cannot hit back that way) is switched out: always on {@code hard}, half of the time on {@code normal},
 * never on {@code easy}. Wild Pokémon and bosses never switch. (Using items comes with the bag, phase 50.)
 *
 * <p><b>Trainers as targets</b> (7.12). With the chance {@code combat.ai.targetTrainerPct} an enemy aims its damaging single-target
 * move at a trainer of the other side instead of at a Pokémon. Players' Pokémon never do (they are not enemies), so a PvP battle has
 * none of it.
 */
final class EnemyAi {

    private static final JsonNode NOTHING = JsonNodeFactory.instance.objectNode();

    private EnemyAi() {
    }

    /** One thing the Pokémon could do, with how good it is. */
    private record Option(Action action, double score, MoveSlot slot, boolean damaging, double effectiveness) {
    }

    /** The action of one enemy Pokémon. All the randomness comes from {@code random}. */
    static Action choose(GameState state, Battle b, Combatant c, MoveBook book, Random random) {
        BattleSettings.Enemy rules = b.rules().enemy();
        BattleSettings.AiLevel level = rules.aiLevel();
        Participant owner = b.ownerOf(c);
        List<Option> options = options(b, c, book);
        if (options.isEmpty()) {
            if (b.rules().ppEnabled() && c.outOfPp()) {
                List<String> foes = b.legalTargets(c, TargetKind.FOE);
                if (!foes.isEmpty()) {
                    return Action.struggle(foes.get(random.nextInt(foes.size())));
                }
            }
            return Action.skip();
        }
        // an NPC trainer switches out of a bad matchup
        if (owner.kind() == Kind.NPC && level != BattleSettings.AiLevel.EASY) {
            String in = betterPokemon(b, c, owner, book, options);
            if (in != null && (level == BattleSettings.AiLevel.HARD || random.nextBoolean())) {
                return Action.switchTo(in);
            }
        }
        Option pick = pick(level, options, random);
        // sometimes the target is a trainer, not a Pokémon
        if (owner.kind() != Kind.TRAINER && pick.damaging() && pick.slot() != null && pick.slot().targetKind() == TargetKind.FOE
                && rules.targetTrainerPct() > 0 && random.nextInt(100) < rules.targetTrainerPct()) {
            List<String> trainers = foeTrainers(state, b, c);
            if (!trainers.isEmpty()) {
                return Action.move(pick.slot().moveId(), Battle.trainerTarget(trainers.get(random.nextInt(trainers.size()))));
            }
        }
        return pick.action();
    }

    // ================================================================================================================ the options

    private static List<Option> options(Battle b, Combatant c, MoveBook book) {
        List<Option> options = new ArrayList<>();
        String locked = b.choiceLockedMove(c); // a Choice item keeps the holder to one move
        for (MoveSlot slot : c.moves()) {
            if (b.rules().ppEnabled() && slot.pp() <= 0 || locked != null && !slot.moveId().equals(locked)) {
                continue;
            }
            TargetKind kind = slot.targetKind();
            MoveProfile profile = book.profile(slot.moveId()).orElse(null);
            boolean damaging = profile != null && profile.damaging();
            if (kind.choosesOne()) {
                for (String id : b.legalTargets(c, kind)) {
                    Combatant target = b.combatant(id);
                    double score = score(b, c, slot, target, book);
                    options.add(new Option(Action.move(slot.moveId(), id), score, slot, damaging, effectiveness(book, profile, target)));
                }
            } else if (kind == TargetKind.SELF || kind == TargetKind.SIDE) {
                options.add(new Option(Action.move(slot.moveId()), score(b, c, slot, c, book), slot, damaging, 1.0));
            } else {
                List<String> group = b.groupTargets(c, kind);
                if (group.isEmpty()) {
                    continue; // nobody to hit: the move would fail
                }
                double total = 0;
                double best = 0;
                for (String id : group) {
                    Combatant target = b.combatant(id);
                    double s = score(b, c, slot, target, book);
                    total += b.sideOf(target) == b.sideOf(c) ? -s : s; // hurting a friend is bad
                    best = Math.max(best, effectiveness(book, profile, target));
                }
                options.add(new Option(Action.move(slot.moveId()), Math.max(0, total), slot, damaging, best));
            }
        }
        return options;
    }

    private static double score(Battle b, Combatant a, MoveSlot slot, Combatant t, MoveBook book) {
        MoveProfile p = book.profile(slot.moveId()).orElse(null);
        MoveEntry entry = book.entry(slot.moveId()).orElse(null);
        if (p == null || entry == null) {
            return 1.0; // the game does not know it: the admin gets a task, so it is a weak choice
        }
        if (!p.damaging()) {
            return statusScore(a, t, entry);
        }
        double eff = effectiveness(book, p, t);
        if (eff == 0.0) {
            return 0.0;
        }
        double damage;
        double hit = p.accuracy().map(x -> x / 100.0).orElse(1.0);
        if (p.ohko()) {
            damage = t.hp();
            hit = Math.max(0.0, Math.min(1.0, (30 + a.level() - t.level()) / 100.0));
        } else if (p.fixedDamage().isPresent()) {
            damage = Damage.fixed(p.fixedDamage().get(), a.level(), eff);
        } else if (p.usesDamageDice()) {
            Dice dice = p.damageDice(book.combat()).orElseThrow();
            int average = (int) Math.round(dice.count() * (dice.sides() + 1) / 2.0);
            boolean physical = p.category() == MoveProfile.Category.PHYSICAL;
            boolean stab = !MoveSlot.STRUGGLE.equals(slot.moveId()) && a.types().contains(p.type());
            boolean burned = physical && a.status() == Status.BURN;
            int attack = physical ? a.build().attack() : a.build().spAttack();
            int defense = physical ? t.build().defense() : t.build().spDefense();
            damage = Damage.compute(new Damage.Input(a.level(), average, Math.max(1, attack), Math.max(1, defense),
                    a.condition().stage(physical ? "atk" : "spa"), t.condition().stage(physical ? "def" : "spd"), false, stab, eff, burned, 1.0),
                    book.combat()).damage();
        } else {
            damage = 0;
        }
        double score = damage * hit;
        if (eff > 1.0) {
            score *= 1.25;
        }
        if (damage >= t.hp()) {
            score *= 1.5;
        }
        return Math.max(0.0, score);
    }

    /** A status, setup or healing move is worth a little, and only when it would help. */
    private static double statusScore(Combatant a, Combatant t, MoveEntry entry) {
        JsonNode effect = entry.effect() == null ? NOTHING : entry.effect();
        double score = 0;
        if (Status.fromId(effect.path("status").asText("")) != null && t != a && t.status() == Status.NONE) {
            score += 12;
        }
        if (effect.path("boosts").isObject() && t == a) {
            for (var boost : effect.path("boosts").properties()) {
                if (boost.getValue().asInt() > 0 && a.condition().stage(boost.getKey()) < 3) {
                    score += 10;
                    break;
                }
            }
        }
        if (effect.has("heal") && t == a && a.hp() * 2 <= a.maxHp()) {
            score += 25;
        }
        return score;
    }

    private static double effectiveness(MoveBook book, MoveProfile profile, Combatant target) {
        if (profile == null || target.types().isEmpty()) {
            return 1.0;
        }
        return book.chart().effectiveness(profile.type(), target.types());
    }

    // ================================================================================================================ the choice

    private static Option pick(BattleSettings.AiLevel level, List<Option> options, Random random) {
        double best = options.stream().mapToDouble(Option::score).max().orElse(0.0);
        if (level == BattleSettings.AiLevel.EASY || best <= 0.0) {
            return options.get(random.nextInt(options.size()));
        }
        if (level == BattleSettings.AiLevel.HARD) {
            return options.stream().filter(o -> o.score() == best).findFirst().orElseThrow();
        }
        List<Option> good = options.stream().filter(o -> o.score() >= 0.6 * best).toList();
        double total = good.stream().mapToDouble(Option::score).sum();
        double roll = random.nextDouble() * total;
        for (Option o : good) {
            roll -= o.score();
            if (roll < 0) {
                return o;
            }
        }
        return good.get(good.size() - 1);
    }

    // ================================================================================================================ switching

    /**
     * The Pokémon an NPC should send in because the one on the field is in a bad matchup, or null. A matchup is bad when a foe has a type
     * that hits this Pokémon super effectively and none of this Pokémon's moves hits a foe super effectively.
     */
    private static String betterPokemon(Battle b, Combatant c, Participant owner, MoveBook book, List<Option> options) {
        List<Combatant> foes = b.standing().stream().filter(f -> b.sideOf(f) != b.sideOf(c)).toList();
        if (foes.isEmpty() || c.types().isEmpty()) {
            return null;
        }
        boolean threatened = foes.stream().anyMatch(f -> threat(book, f, c.types()) >= 2.0);
        boolean canHitBack = options.stream().anyMatch(o -> o.damaging() && o.effectiveness() >= 2.0);
        if (!threatened || canHitBack) {
            return null;
        }
        return owner.reserve().stream().map(b::combatant).filter(r -> !r.fainted())
                .min(Comparator.comparingDouble((Combatant r) -> foes.stream().mapToDouble(f -> threat(book, f, r.types())).max().orElse(1.0))
                        .thenComparing(Comparator.comparingInt(Combatant::hp).reversed()))
                .filter(r -> foes.stream().mapToDouble(f -> threat(book, f, r.types())).max().orElse(1.0) < foes.stream().mapToDouble(f -> threat(book, f, c.types())).max().orElse(1.0))
                .map(Combatant::id).orElse(null);
    }

    /** How hard the best of a foe's own types hits a Pokémon with these types. */
    private static double threat(MoveBook book, Combatant foe, List<PokemonType> defenderTypes) {
        if (defenderTypes.isEmpty()) {
            return 1.0;
        }
        double worst = 1.0;
        for (PokemonType type : foe.types()) {
            worst = Math.max(worst, book.chart().effectiveness(type, defenderTypes));
        }
        return worst;
    }

    // ================================================================================================================ trainers

    /** The trainers on the other side that an enemy may aim at: in the battle, standing, not at 0 HP. */
    private static List<String> foeTrainers(GameState state, Battle b, Combatant c) {
        List<String> list = new ArrayList<>();
        for (Participant p : b.participants().values()) {
            Trainer trainer = p.kind() == Kind.TRAINER && p.trainerId() != null ? state.trainers().get(p.trainerId()) : null;
            if (trainer != null && trainer.hp() > 0 && p.side() != b.sideOf(c) && !b.out(p)) {
                list.add(p.id());
            }
        }
        return list;
    }
}
