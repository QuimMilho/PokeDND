/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Candidate;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Trigger;
import net.quimmilho.pokednd.core.importer.Learnsets.LevelMove;
import net.quimmilho.pokednd.core.rules.Experience;

/**
 * Growth of one Pokémon (chapters 5.3, 5.4, 5.5 and 5.8): XP becomes levels, a level brings HP, friendship and new moves, and a level
 * can make it ready to evolve. Pure: it returns the Pokémon after, and the prompts that wait for an answer.
 */
final class Leveling {

    private Leveling() {
    }

    /**
     * What happened to a Pokémon.
     *
     * @param sheet        the Pokémon after
     * @param prompts      what waits for an answer
     * @param levelsGained how many levels it gained
     * @param evolved      true if it evolved on its own ({@code pokemon.evolution.mode} automatic)
     * @param learned      the moves it learned by itself
     */
    record Result(PokemonSheet sheet, List<Prompt> prompts, int levelsGained, boolean evolved, List<String> learned) {
    }

    /**
     * The Pokémon after gaining XP.
     *
     * @param friendshipMultiplier the class feature {@code friendship.gainMultiplier} of the trainer (1.0 if none)
     * @param existing             the ids of the prompts that already wait (a prompt is not opened twice)
     */
    static Result gain(PokemonSheet sheet, SpeciesBook.Species species, int xp, double friendshipMultiplier, EvolutionCheck.Context ctx, SpeciesBook book,
                       MoveBook moves, ProgressSettings settings, String trainerId, Set<String> existing) {
        Experience.Gain g = Experience.add(species.growth(), sheet.xp(), xp, settings.pokemon());
        if (g.levelsGained() == 0) {
            return new Result(sheet.withGrowth(sheet.level(), g.totalXp(), sheet.hp()), List.of(), 0, false, List.of());
        }
        int oldMax = BattleSetup.maxHp(sheet, species, sheet.level());
        int newMax = BattleSetup.maxHp(sheet, species, g.level());
        int hp = sheet.fainted() ? 0 : Math.min(newMax, sheet.hp() + (newMax - oldMax));
        PokemonSheet s = sheet.withGrowth(g.level(), g.totalXp(), hp);
        s = s.withFriendship(Friendship.afterLevelUps(settings, s.friendship(), g.levelsGained(), friendshipMultiplier));
        List<Prompt> prompts = new ArrayList<>();
        List<String> learned = new ArrayList<>();
        if (!settings.movesFrozen()) {
            for (LevelMove lm : species.levelMoves()) {
                if (lm.level() <= sheet.level() || lm.level() > g.level()) {
                    continue;
                }
                PokemonSheet now = s;
                if (now.moves().stream().anyMatch(m -> m.moveId().equals(lm.move()))) {
                    continue;
                }
                if (now.moves().size() < settings.movesMax()) {
                    List<MoveSlot> list = new ArrayList<>(now.moves());
                    list.add(slot(moves, lm.move()));
                    s = now.withMoves(list);
                    learned.add(lm.move());
                } else {
                    String id = "learn-" + s.id() + "-L" + lm.level() + "-" + lm.move();
                    if (!existing.contains(id)) {
                        prompts.add(new Prompt(id, Prompt.Kind.LEARN_MOVE, trainerId, s.id(), lm.move(),
                                s.name() + " wants to learn " + BattleReducer.pretty(lm.move()) + ", but it knows " + settings.movesMax() + " moves. Forget one?"));
                    }
                }
            }
        }
        Offer offer = offer(s, species, Trigger.LEVEL_UP, ctx, book, settings, trainerId, existing);
        prompts.addAll(offer.prompts());
        return new Result(offer.sheet(), prompts, g.levelsGained(), offer.evolved(), learned);
    }

    /**
     * What the evolution rules make of the Pokémon at this trigger.
     *
     * @param sheet   the Pokémon after (evolved if {@code pokemon.evolution.mode} is automatic)
     * @param prompts the question for the owner or the admin
     * @param evolved true if it evolved now
     */
    record Offer(PokemonSheet sheet, List<Prompt> prompts, boolean evolved) {
    }

    /** Looks at the evolution rules (chapter 5.4): the first rule that holds is evolved at once, or asked about, as the settings say. */
    static Offer offer(PokemonSheet s, SpeciesBook.Species species, Trigger trigger, EvolutionCheck.Context ctx, SpeciesBook book, ProgressSettings settings,
                       String trainerId, Set<String> existing) {
        List<Candidate> candidates = EvolutionCheck.candidates(s, species, BattleSetup.stats(s, species), trigger, ctx, settings);
        for (Candidate c : candidates) {
            SpeciesBook.Species to = book.species(c.rule().to()).orElse(null);
            if (to == null) {
                continue;
            }
            Prompt.Kind kind;
            if (c.needsAdmin()) {
                if (settings.unknownFallback() == ProgressSettings.UnknownFallback.SKIP) {
                    continue;
                }
                kind = settings.unknownFallback() == ProgressSettings.UnknownFallback.ASK_PLAYER ? Prompt.Kind.EVOLVE : Prompt.Kind.ADMIN_EVOLVE;
            } else {
                switch (settings.evolutionMode()) {
                    case AUTOMATIC -> {
                        return new Offer(EvolutionCheck.evolve(s, species, to, c.rule()), List.of(), true);
                    }
                    case ASK_ADMIN -> kind = Prompt.Kind.ADMIN_EVOLVE;
                    default -> kind = Prompt.Kind.EVOLVE;
                }
            }
            String id = "evolve-" + s.id() + "-" + to.id();
            if (existing.contains(id)) {
                return new Offer(s, List.of(), false);
            }
            String text = kind == Prompt.Kind.ADMIN_EVOLVE
                    ? s.name() + " could evolve into " + to.name() + " (" + c.rule().text() + "). The admin decides."
                    : "Your " + s.name() + " is evolving into " + to.name() + "! Evolve or not now?";
            return new Offer(s, List.of(new Prompt(id, kind, trainerId, s.id(), to.id(), text)), false);
        }
        return new Offer(s, List.of(), false);
    }

    /** A move as a Pokémon learns it: the PP, priority and target of the data, or plain defaults when the book does not know it. */
    static MoveSlot slot(MoveBook moves, String moveId) {
        return moves.slot(moveId).orElseGet(() -> new MoveSlot(moveId, BattleReducer.pretty(moveId), 10, 10, 0, "normal"));
    }
}
