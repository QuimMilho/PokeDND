/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.EvolutionCheck.Trigger;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.rules.TrainerXp;
import net.quimmilho.pokednd.core.trainer.Trainer;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;

/**
 * The calls that grow, heal and revive the Pokémon of a game, and answer what a level-up or an evolution asks (chapters 5.3 to 5.8). Like
 * the other desks it only returns events; the game adds them to its log. A fainted Pokémon stays fainted until it is healed at a healer
 * ({@link #healParty}) or revived ({@link #revive}).
 *
 * <p>The hooks for the later phases are here too: {@link #check} looks at the evolution rules when an item is used or a Pokémon is traded
 * (phase 50), {@link #travelled} counts movement (phase 51), and {@link #moveUsed} counts a move used in battle.
 */
public final class ProgressDesk {

    private ProgressDesk() {
    }

    // ================================================================================================================== healing

    /** The Pokémon at full HP and PP, with no status. */
    static PokemonSheet healed(PokemonSheet sheet, SpeciesBook species) {
        SpeciesBook.Species sp = species.species(sheet.speciesId()).orElseThrow(() -> new IllegalArgumentException("Unknown species " + sheet.speciesId()));
        List<MoveSlot> moves = sheet.moves().stream().map(m -> new MoveSlot(m.moveId(), m.name(), m.maxPp(), m.maxPp(), m.priority(), m.target())).toList();
        return sheet.withHp(BattleSetup.maxHp(sheet, sp, sheet.level())).withStatus(Battle.Status.NONE).withMoves(moves);
    }

    /** A healer restores the HP, the PP and the status of every Pokémon in the trainer's party, the fainted ones too (chapter 5.7). */
    public static List<GameEvent> healParty(GameState state, SpeciesBook species, String trainerId) {
        List<GameEvent> events = new ArrayList<>();
        for (PokemonSheet s : BattleSetup.party(state, trainerId)) {
            PokemonSheet next = healed(s, species);
            if (!next.equals(s)) {
                events.add(new GameEvent.PokemonSet(next));
            }
        }
        return check(state, events);
    }

    /** A potion or a spell: {@code amount} HP back, never above the maximum. A fainted Pokémon needs a revive first. */
    public static List<GameEvent> heal(GameState state, SpeciesBook species, String pokemonId, int amount) {
        PokemonSheet s = sheet(state, pokemonId);
        if (s.fainted()) {
            throw new BattleRefusedException(s.name() + " has fainted: it needs a Revive");
        }
        if (amount < 1) {
            throw new BattleRefusedException("Healing is at least 1 HP");
        }
        int max = BattleSetup.maxHp(s, species(species, s), s.level());
        return check(state, List.of(new GameEvent.PokemonSet(s.withHp(Math.min(max, s.hp() + amount)))));
    }

    /** A Revive (50 %) or Max Revive (100 %): a fainted Pokémon is back with {@code percent} % of its HP, at least 1. */
    public static List<GameEvent> revive(GameState state, SpeciesBook species, String pokemonId, int percent) {
        PokemonSheet s = sheet(state, pokemonId);
        if (!s.fainted()) {
            throw new BattleRefusedException(s.name() + " has not fainted");
        }
        if (percent < 1 || percent > 100) {
            throw new BattleRefusedException("A revive restores 1 to 100 % of the HP");
        }
        int max = BattleSetup.maxHp(s, species(species, s), s.level());
        return check(state, List.of(new GameEvent.PokemonSet(s.withHp(Math.max(1, max * percent / 100)))));
    }

    // =============================================================================================================== the prompts

    /**
     * Answers a prompt. {@code learnMove}: the place (0 to 3) of the move to forget, or {@code skip}. {@code evolve}: {@code yes} or
     * {@code no} (declining waits for the next level-up). An evolution only the admin can judge is answered by the admin. A whiteout is
     * acknowledged with {@code ok}. An evolution gives its trainer the XP of chapter 4.5.
     */
    public static List<GameEvent> answer(GameState state, Actor who, String promptId, String answer, SpeciesBook species, MoveBook moves, TrainerRulebook rulebook,
                                         ProgressSettings settings) {
        Prompt prompt = state.prompts().get(promptId);
        if (prompt == null) {
            throw new BattleRefusedException("There is no prompt " + promptId + " waiting");
        }
        List<GameEvent> events = new ArrayList<>();
        switch (prompt.kind()) {
            case WHITEOUT -> {
            }
            case LEARN_MOVE -> {
                PokemonSheet s = sheet(state, prompt.pokemonId());
                if (!"skip".equals(answer)) {
                    int place;
                    try {
                        place = Integer.parseInt(answer.strip());
                    } catch (NumberFormatException e) {
                        throw new BattleRefusedException("Answer skip, or the place (0 to " + (settings.movesMax() - 1) + ") of the move to forget");
                    }
                    List<MoveSlot> list = new ArrayList<>(s.moves());
                    if (s.moves().stream().anyMatch(m -> m.moveId().equals(prompt.data()))) {
                        throw new BattleRefusedException(s.name() + " knows " + prompt.data() + " already");
                    }
                    if (list.size() < settings.movesMax()) {
                        list.add(Leveling.slot(moves, prompt.data()));
                    } else if (place >= 0 && place < list.size()) {
                        list.set(place, Leveling.slot(moves, prompt.data()));
                    } else {
                        throw new BattleRefusedException("There is no move at place " + place);
                    }
                    events.add(new GameEvent.PokemonSet(s.withMoves(list)));
                }
            }
            case EVOLVE, ADMIN_EVOLVE -> {
                if (prompt.kind() == Prompt.Kind.ADMIN_EVOLVE && !who.admin()) {
                    throw new BattleRefusedException("Only the admin can confirm this evolution");
                }
                PokemonSheet s = sheet(state, prompt.pokemonId());
                if ("yes".equals(answer)) {
                    SpeciesBook.Species from = species(species, s);
                    SpeciesBook.Species to = species.species(prompt.data()).orElseThrow(() -> new BattleRefusedException("Unknown species " + prompt.data()));
                    var rule = from.evolutions().stream().filter(r -> r.to().equals(to.id())).findFirst()
                            .orElseThrow(() -> new BattleRefusedException(from.name() + " does not evolve into " + to.name()));
                    PokemonSheet evolved = EvolutionCheck.evolve(s, from, to, rule);
                    events.add(new GameEvent.PokemonSet(evolved));
                    trainerXp(state, rulebook, settings, prompt.trainerId(), events);
                } else if ("no".equals(answer)) {
                    events.add(new GameEvent.PokemonSet(s.withDeclinedLevel(s.level())));
                } else {
                    throw new BattleRefusedException("Answer yes or no");
                }
            }
        }
        events.add(new GameEvent.PromptAnswered(promptId, answer));
        return check(state, events);
    }

    // =================================================================================================================== hooks

    /**
     * Looks at the evolution rules of a Pokémon for this trigger and acts as {@code pokemon.evolution.mode} says: asks the owner or the admin,
     * or evolves it at once. The item of an {@link Trigger#USE_ITEM} (a stone) is used up by the bag, not here.
     */
    public static List<GameEvent> check(GameState state, SpeciesBook species, MoveBook moves, TrainerRulebook rulebook, ProgressSettings settings, String pokemonId,
                                        Trigger trigger, EvolutionCheck.Context context) {
        PokemonSheet s = sheet(state, pokemonId);
        SpeciesBook.Species sp = species(species, s);
        EvolutionCheck.Context ctx = (context == null ? EvolutionCheck.Context.none() : context)
                .withMoveTypes(id -> moves.profile(id).map(MoveProfile::type).orElse(null))
                .withParty(s.ownerId() == null ? List.of() : BattleSetup.party(state, s.ownerId()).stream().map(PokemonSheet::speciesId).toList());
        Leveling.Offer offer = Leveling.offer(s, sp, trigger, ctx, species, settings, s.ownerId(), state.prompts().keySet());
        List<GameEvent> events = new ArrayList<>();
        if (offer.evolved()) {
            events.add(new GameEvent.PokemonSet(offer.sheet()));
            trainerXp(state, rulebook, settings, s.ownerId(), events);
        }
        offer.prompts().forEach(p -> events.add(new GameEvent.PromptOpened(p)));
        return check(state, events);
    }

    /**
     * A Pokémon gains experience outside a battle (a Rare Candy, an Exp. Candy): the levels, the moves it can learn and the evolution rules are
     * handled as after a battle. {@code pokemon.xp.multiplier} does not apply to an item, and {@code pokemon.level.cap} does.
     */
    public static List<GameEvent> gainXp(GameState state, SpeciesBook species, MoveBook moves, TrainerRulebook rulebook, ProgressSettings settings, String pokemonId,
                                         int xp, EvolutionCheck.Context context) {
        if (xp < 1) {
            throw new BattleRefusedException("Experience is at least 1");
        }
        PokemonSheet s = sheet(state, pokemonId);
        SpeciesBook.Species sp = species(species, s);
        Trainer trainer = s.ownerId() == null ? null : state.trainers().get(s.ownerId());
        double friendship = trainer == null ? 1.0 : rulebook.sheet(trainer).effects().value("friendship.gainMultiplier");
        EvolutionCheck.Context ctx = (context == null ? EvolutionCheck.Context.none() : context)
                .withMoveTypes(id -> moves.profile(id).map(MoveProfile::type).orElse(null))
                .withParty(s.ownerId() == null ? List.of() : BattleSetup.party(state, s.ownerId()).stream().map(PokemonSheet::speciesId).toList());
        ProgressSettings flat = settings.withPokemon(settings.pokemon().withXpMultiplier(1.0));
        Leveling.Result res = Leveling.gain(s, sp, xp, friendship, ctx, species, moves, flat, s.ownerId(), state.prompts().keySet());
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.PokemonSet(res.sheet()));
        if (res.evolved()) {
            trainerXp(state, rulebook, settings, s.ownerId(), events);
        }
        res.prompts().forEach(p -> events.add(new GameEvent.PromptOpened(p)));
        return check(state, events);
    }

    /** A move was used in battle: Rage Fist counts it (chapter 5.4). The evolution rules are looked at again. */
    public static List<GameEvent> moveUsed(GameState state, SpeciesBook species, MoveBook moves, TrainerRulebook rulebook, ProgressSettings settings, String pokemonId,
                                           String moveId) {
        PokemonSheet s = sheet(state, pokemonId);
        GameState counted = GameReducer.apply(state, new GameEvent.PokemonSet(s.withCount("use:" + Battle.squash(moveId), 1)));
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.PokemonSet(counted.pokemon().get(pokemonId)));
        events.addAll(check(counted, species, moves, rulebook, settings, pokemonId, Trigger.TICK, null));
        return events;
    }

    /**
     * The lead Pokémon travelled {@code points} movement points (chapters 5.4 and 5.8): its distance grows by {@code world.movement.blocksPerPoint}
     * blocks for each, its friendship by 1 for every {@code pokemon.friendship.pointsPerGain} points, and a {@code Travel N blocks} evolution is looked at.
     */
    public static List<GameEvent> travelled(GameState state, SpeciesBook species, MoveBook moves, TrainerRulebook rulebook, ProgressSettings settings, String pokemonId,
                                            int points) {
        if (points < 1) {
            throw new BattleRefusedException("A journey is at least 1 movement point");
        }
        PokemonSheet s = sheet(state, pokemonId);
        int carry = s.count("travel:carry");
        int[] friendship = Friendship.afterTravel(settings, s.friendship(), carry, points);
        PokemonSheet next = s.withTravelled(s.travelledBlocks() + points * settings.blocksPerPoint()).withFriendship(friendship[0]).withCount("travel:carry", friendship[1] - carry);
        GameState counted = GameReducer.apply(state, new GameEvent.PokemonSet(next));
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.PokemonSet(next));
        events.addAll(check(counted, species, moves, rulebook, settings, pokemonId, Trigger.TICK, null));
        return events;
    }

    // ================================================================================================================ helpers

    static void trainerXp(GameState state, TrainerRulebook rulebook, ProgressSettings settings, String trainerId, List<GameEvent> events) {
        Trainer trainer = trainerId == null ? null : state.trainers().get(trainerId);
        if (trainer != null) {
            // the evolution of a Pokémon gives its trainer XP (chapter 4.5)
            events.add(new GameEvent.TrainerSet(trainerId, rulebook.addXp(trainer, settings.trainerXp().award(TrainerXp.Event.EVOLVE_OR_HATCH, 1))));
        }
    }

    static PokemonSheet sheet(GameState state, String pokemonId) {
        PokemonSheet s = pokemonId == null ? null : state.pokemon().get(pokemonId);
        if (s == null) {
            throw new BattleRefusedException("There is no Pokémon " + pokemonId);
        }
        return s;
    }

    static SpeciesBook.Species species(SpeciesBook book, PokemonSheet s) {
        return book.species(s.speciesId()).orElseThrow(() -> new BattleRefusedException("The species " + s.speciesId() + " is not known"));
    }

    /** Checks that the events can happen, and returns them. */
    static List<GameEvent> check(GameState state, List<GameEvent> events) {
        try {
            GameReducer.applyAll(state, events);
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
        return List.copyOf(events);
    }
}
