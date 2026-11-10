/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.rules.Evs;
import net.quimmilho.pokednd.core.rules.Experience;
import net.quimmilho.pokednd.core.rules.StatBlock;
import net.quimmilho.pokednd.core.rules.TrainerXp;
import net.quimmilho.pokednd.core.trainer.KnockOut;
import net.quimmilho.pokednd.core.trainer.Sheet;
import net.quimmilho.pokednd.core.trainer.Trainer;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;

/**
 * The end of a battle (chapter 7.15): the rewards are paid and the Pokémon grow. {@link #settle} reads the battle that has just ended and
 * returns the events that
 * <ol>
 *   <li>write the battle's results back to the owned Pokémon: HP, status, PP, held item, and friendship lost for fainting (a fainted Pokémon
 *       stays fainted until it is healed or revived, 5.7);</li>
 *   <li>make a caught Pokémon its catcher's, in the party or in a PC box, and let the other wild Pokémon go (they despawn);</li>
 *   <li>pay <b>Pokémon XP</b> and <b>EVs</b> for every defeated Pokémon (5.3): the full share to the Pokémon that were on the field together
 *       with it, the rest of the party the half share of {@code pokemon.xp.share}; then level-ups, new moves, friendship and evolution (5.4,
 *       5.5, 5.8), asking where a choice is needed;</li>
 *   <li>pay <b>trainer XP</b> (4.5), the <b>prize money</b> of an NPC trainer (7.14) and the wager of a PvP battle;</li>
 *   <li>make a trainer whose whole party fainted <b>white out</b>: they lose some ₽ and the party is healed.</li>
 * </ol>
 * It needs no dice. It happens once for each battle ({@link GameEvent.BattleSettled}). A trainer who left the battle (ran away, was knocked
 * out, surrendered) gets nothing for it.
 */
public final class AfterBattle {

    /** The XP value used for a defeated Pokémon whose species the book does not know. */
    public static final int DEFAULT_BASE_EXPERIENCE = 64;

    private AfterBattle() {
    }

    /**
     * Settles the battle that has ended.
     *
     * @param context what evolutions read about the place and the time (use {@link EvolutionCheck.Context#none()} if the game does not say)
     * @param wager   the ₽ that each losing player pays in a PvP battle (0 for none; it only counts when {@code combat.pvp.wagerAllowed} is on)
     * @throws BattleRefusedException if there is no battle that has ended and not been settled
     */
    public static List<GameEvent> settle(GameState state, SpeciesBook species, MoveBook moves, TrainerRulebook rulebook, ProgressSettings settings,
                                         EvolutionCheck.Context context, long wager) {
        Battle b = state.battle();
        if (b == null || b.step() != Battle.Step.ENDED) {
            throw new BattleRefusedException("The rewards are paid after the battle has ended");
        }
        if (b.settled()) {
            throw new BattleRefusedException("The rewards of this battle have been paid already");
        }
        try {
            return new Run(state, species, moves, rulebook, settings, context, wager).go();
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
    }

    /** One settling: the state that the events build up, and the events. */
    private static final class Run {
        private GameState work;
        private final GameState start;
        private final Battle b;
        private final SpeciesBook species;
        private final MoveBook moves;
        private final TrainerRulebook rulebook;
        private final ProgressSettings settings;
        private final EvolutionCheck.Context context;
        private final long wager;
        private final List<GameEvent> events = new ArrayList<>();
        private final List<Prompt> prompts = new ArrayList<>();
        private final Map<String, Trainer> trainers = new LinkedHashMap<>();
        private final Map<String, Long> xp = new LinkedHashMap<>();
        private final Set<String> promptIds = new HashSet<>();

        Run(GameState state, SpeciesBook species, MoveBook moves, TrainerRulebook rulebook, ProgressSettings settings, EvolutionCheck.Context context,
            long wager) {
            this.work = state;
            this.start = state;
            this.b = state.battle();
            this.species = species;
            this.moves = moves;
            this.rulebook = rulebook;
            this.settings = settings;
            this.context = context == null ? EvolutionCheck.Context.none() : context;
            this.wager = wager;
            this.promptIds.addAll(state.prompts().keySet());
            this.trainers.putAll(state.trainers());
        }

        private void emit(GameEvent e) {
            work = GameReducer.apply(work, e);
            events.add(e);
        }

        private void set(PokemonSheet sheet) {
            PokemonSheet before = work.pokemon().get(sheet.id());
            if (!sheet.equals(before)) {
                emit(new GameEvent.PokemonSet(sheet));
            }
        }

        private void addXp(String trainerId, long amount) {
            if (amount > 0) {
                xp.merge(trainerId, amount, Long::sum);
            }
        }

        List<GameEvent> go() {
            List<Participant> stayed = b.participants().values().stream()
                    .filter(p -> p.kind() == Kind.TRAINER && p.trainerId() != null && !b.left().containsKey(p.id()) && trainers.containsKey(p.trainerId())).toList();
            writeBack();
            caught();
            for (Participant p : stayed) {
                grow(p);
            }
            trainerXp(stayed);
            money(stayed);
            whiteout(stayed);
            // the trainers that changed, then what waits for an answer
            for (var entry : trainers.entrySet()) {
                if (!entry.getValue().equals(start.trainers().get(entry.getKey()))) {
                    emit(new GameEvent.TrainerSet(entry.getKey(), entry.getValue()));
                }
            }
            for (Prompt p : prompts) {
                emit(new GameEvent.PromptOpened(p));
            }
            emit(new GameEvent.BattleSettled(b.battleId()));
            return List.copyOf(events);
        }

        // ------------------------------------------------------------------------------------------------ 1. the results of the fight

        private void writeBack() {
            for (Combatant c : b.combatants().values()) {
                PokemonSheet sheet = work.pokemon().get(c.id());
                if (sheet == null || b.ownerOf(c).kind() != Kind.TRAINER || b.caught().containsKey(c.id())) {
                    continue;
                }
                PokemonSheet next = sheet.withHp(c.hp()).withStatus(c.status()).withMoves(c.moves()).withHeldItem(c.condition().heldItem());
                if (c.fainted() && sheet.hp() > 0) {
                    next = next.withFriendship(Friendship.afterFainting(settings, sheet.friendship()));
                }
                set(next);
            }
        }

        // ------------------------------------------------------------------------------------------ 2. caught, and the ones that go away

        private void caught() {
            for (var entry : b.caught().entrySet()) {
                Combatant c = b.combatant(entry.getKey());
                Participant catcher = b.participants().get(entry.getValue().participantId());
                if (c == null || catcher == null || catcher.trainerId() == null || !trainers.containsKey(catcher.trainerId())) {
                    continue;
                }
                PokemonSheet sheet = work.pokemon().get(c.id());
                if (sheet == null) {
                    sheet = fromBattle(c);
                }
                long inParty = work.pokemon().values().stream()
                        .filter(p -> catcher.trainerId().equals(p.ownerId()) && p.location() == PokemonSheet.Location.PARTY).count();
                PokemonSheet.Location where = inParty < settings.partySize() ? PokemonSheet.Location.PARTY : PokemonSheet.Location.PC;
                PokemonSheet mine = sheet.withOwner(catcher.trainerId(), where, entry.getValue().ball()).withHp(entry.getValue().hp()).withStatus(Status.NONE);
                if (work.pokemon().containsKey(mine.id())) {
                    set(mine);
                } else {
                    emit(new GameEvent.PokemonSet(mine));
                }
            }
            for (Combatant c : b.combatants().values()) {
                PokemonSheet sheet = work.pokemon().get(c.id());
                if (sheet != null && sheet.location() == PokemonSheet.Location.WILD && !b.caught().containsKey(c.id())) {
                    emit(new GameEvent.PokemonRemoved(c.id())); // wild Pokémon despawn
                }
            }
        }

        /** A Pokémon the battle knows and the game does not (a battle set up by hand): a plain sheet with no IVs, EVs or nature. */
        private PokemonSheet fromBattle(Combatant c) {
            String speciesId = c.build().speciesId() == null ? c.name().toLowerCase(java.util.Locale.ROOT) : c.build().speciesId();
            int total = species.species(speciesId).map(s -> s.growth().totalXp(c.level())).orElse(0);
            return new PokemonSheet(c.id(), null, speciesId, c.name(), c.level(), total, null, StatBlock.all(0), StatBlock.all(0), c.build().gender(), false,
                    c.build().ability(), c.condition().heldItem(), c.moves(), c.hp(), Status.NONE, c.build().friendship(), PokemonSheet.Location.WILD, null, 0, Map.of(), 0, null);
        }

        // ------------------------------------------------------------------------------------------------------- 3. XP and growth

        private void grow(Participant p) {
            Trainer trainer = trainers.get(p.trainerId());
            Sheet sheet = rulebook.sheet(trainer);
            double xpBonus = 1.0 + sheet.effects().value("xp.bonusPct") / 100.0;
            double friendship = sheet.effects().value("friendship.gainMultiplier");
            List<Combatant> defeated = b.combatants().values().stream()
                    .filter(c -> c.fainted() && !b.caught().containsKey(c.id()) && b.sideOf(c) != p.side()).toList();
            EvolutionCheck.Context ctx = context.withMoveTypes(id -> moves.profile(id).map(MoveProfile::type).orElse(null))
                    .withParty(BattleSetup.party(work, p.trainerId()).stream().map(PokemonSheet::speciesId).toList());
            for (PokemonSheet r : BattleSetup.party(work, p.trainerId())) {
                SpeciesBook.Species sp = species.species(r.speciesId()).orElse(null);
                if (sp == null || r.fainted()) {
                    continue;
                }
                long total = 0;
                PokemonSheet now = r;
                StatBlock evs = r.evs();
                for (Combatant f : defeated) {
                    SpeciesBook.Species fs = species.species(f.build().speciesId() == null ? "" : f.build().speciesId()).orElse(null);
                    boolean took = b.met().getOrDefault(f.id(), List.of()).contains(r.id());
                    boolean lucky = "luckyegg".equals(Battle.squash(r.heldItem()));
                    int reward = Experience.reward(fs == null ? DEFAULT_BASE_EXPERIENCE : fs.baseExperience(), f.level(), r.level(), took,
                            b.ownerOf(f).kind() == Kind.NPC, lucky, settings.pokemon());
                    total += (long) Math.floor(reward * xpBonus + 1e-9);
                    if (took) {
                        if (fs != null) {
                            evs = Evs.gain(evs, fs.evYield());
                        }
                        String foeSpecies = f.build().speciesId();
                        if (foeSpecies != null) {
                            now = now.withCount("defeat:" + foeSpecies, 1);
                            if (f.condition().heldItem() != null) {
                                now = now.withCount("defeat:" + foeSpecies + ":" + EvolutionCheck.itemKey(f.condition().heldItem()), 1);
                            }
                        }
                    }
                }
                now = now.withEvs(evs);
                Leveling.Result res = Leveling.gain(now, sp, (int) Math.min(Integer.MAX_VALUE, total), friendship, ctx, species, moves, settings, p.trainerId(),
                        promptIds);
                PokemonSheet after = res.sheet();
                for (Prompt prompt : res.prompts()) {
                    if (promptIds.add(prompt.id())) {
                        prompts.add(prompt);
                    }
                }
                if (res.evolved()) {
                    addXp(p.trainerId(), settings.trainerXp().award(TrainerXp.Event.EVOLVE_OR_HATCH, after.level()));
                } else if (!defeated.isEmpty()) {
                    // a count may have reached what an evolution needs (Kingambit: three Bisharp defeated)
                    Leveling.Offer offer = Leveling.offer(after, species.species(after.speciesId()).orElse(sp), EvolutionCheck.Trigger.TICK, ctx, species, settings,
                            p.trainerId(), promptIds);
                    after = offer.sheet();
                    offer.prompts().forEach(prompt -> {
                        if (promptIds.add(prompt.id())) {
                            prompts.add(prompt);
                        }
                    });
                    if (offer.evolved()) {
                        addXp(p.trainerId(), settings.trainerXp().award(TrainerXp.Event.EVOLVE_OR_HATCH, after.level()));
                    }
                }
                set(after);
            }
        }

        // ---------------------------------------------------------------------------------------------------------- 4. trainer XP

        private void trainerXp(List<Participant> stayed) {
            TrainerXp table = settings.trainerXp();
            for (Participant p : stayed) {
                for (Combatant c : b.combatants().values()) {
                    if (!c.fainted() || b.caught().containsKey(c.id()) || b.sideOf(c) == p.side()) {
                        continue;
                    }
                    Participant owner = b.ownerOf(c);
                    if (c.boss()) {
                        addXp(p.trainerId(), table.award(TrainerXp.Event.DEFEAT_BOSS, c.level()));
                    } else if (owner.kind() == Kind.WILD) {
                        addXp(p.trainerId(), table.award(TrainerXp.Event.DEFEAT_WILD, c.level()));
                    }
                }
                for (Participant foe : b.participants().values()) {
                    if (foe.side() != p.side() && foe.kind() != Kind.WILD && !b.left().containsKey(foe.id()) && b.out(foe)) {
                        addXp(p.trainerId(), table.award(TrainerXp.Event.DEFEAT_TRAINER, highestLevel(foe)));
                    }
                }
                for (int i = 0; i < b.caught().size(); i++) {
                    addXp(p.trainerId(), table.award(TrainerXp.Event.CATCH, 1));
                }
            }
            for (var entry : xp.entrySet()) {
                trainers.put(entry.getKey(), rulebook.addXp(trainers.get(entry.getKey()), entry.getValue()));
            }
        }

        private int highestLevel(Participant p) {
            return java.util.stream.Stream.concat(p.active().stream(), p.reserve().stream()).map(b::combatant).mapToInt(Combatant::level).max().orElse(1);
        }

        // -------------------------------------------------------------------------------------------------------------- 5. money

        private void money(List<Participant> stayed) {
            Set<Integer> standing = b.sidesStanding();
            List<Participant> winners = stayed.stream().filter(p -> standing.contains(p.side())).toList();
            if (winners.isEmpty()) {
                return;
            }
            long prize = 0;
            for (Participant foe : b.participants().values()) {
                if (foe.kind() == Kind.NPC && !standing.contains(foe.side()) && !b.left().containsKey(foe.id()) && b.out(foe)) {
                    prize += (long) highestLevel(foe) * b.rules().prizes().perLevel();
                }
            }
            if (b.type() == Battle.Type.PVP && b.rules().prizes().wagerAllowed() && wager > 0) {
                for (Participant loser : b.participants().values()) {
                    Trainer t = loser.kind() == Kind.TRAINER && loser.trainerId() != null ? trainers.get(loser.trainerId()) : null;
                    if (t != null && !standing.contains(loser.side())) {
                        long pays = Math.min(wager, t.money());
                        trainers.put(loser.trainerId(), t.withMoney(t.money() - pays));
                        prize += pays;
                    }
                }
            }
            if (prize > 0) {
                long share = prize / winners.size();
                for (Participant w : winners) {
                    Trainer t = trainers.get(w.trainerId());
                    trainers.put(w.trainerId(), t.withMoney(t.money() + share));
                }
            }
        }

        // ----------------------------------------------------------------------------------------------------------- 6. whiteout

        private void whiteout(List<Participant> stayed) {
            for (Participant p : stayed) {
                List<PokemonSheet> party = BattleSetup.party(work, p.trainerId());
                if (party.isEmpty() || party.stream().anyMatch(s -> !s.fainted())) {
                    continue;
                }
                Trainer t = trainers.get(p.trainerId());
                trainers.put(p.trainerId(), t.withMoney(t.money() - KnockOut.moneyLost(t.money(), b.rules().knockOut().moneyLossPct())));
                for (PokemonSheet s : party) {
                    set(ProgressDesk.healed(s, species));
                }
                String id = "whiteout-" + b.battleId() + "-" + p.trainerId();
                if (promptIds.add(id)) {
                    prompts.add(new Prompt(id, Prompt.Kind.WHITEOUT, p.trainerId(), null, null,
                            t.name() + " has no Pokémon left that can fight, loses some money and returns to the last town."));
                }
            }
        }
    }
}
