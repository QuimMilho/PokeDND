/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.ActionKind;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.InitiativeSlot;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Result;
import net.quimmilho.pokednd.core.game.Battle.Step;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.game.GameEvent.ActionDeclared;
import net.quimmilho.pokednd.core.game.GameEvent.ActionResolved;
import net.quimmilho.pokednd.core.game.GameEvent.BattleEnded;
import net.quimmilho.pokednd.core.game.GameEvent.BattleStarted;
import net.quimmilho.pokednd.core.game.GameEvent.CombatantSet;
import net.quimmilho.pokednd.core.game.GameEvent.InitiativeSet;
import net.quimmilho.pokednd.core.game.GameEvent.QueueReordered;
import net.quimmilho.pokednd.core.game.GameEvent.ReplacementSent;
import net.quimmilho.pokednd.core.game.GameEvent.ResolveStarted;
import net.quimmilho.pokednd.core.game.GameEvent.RoundEnded;
import net.quimmilho.pokednd.core.game.GameEvent.RoundOpened;
import net.quimmilho.pokednd.core.game.GameState.RollClass;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;

/**
 * The commands of a battle (chapter 7). Each method is one thing a person or the clock does; like {@link RollDesk} it looks at the state
 * and the settings, and returns the events to add with {@link GameLog#append} (as one action), or refuses with a
 * {@link BattleRefusedException} whose message can be shown. The desk changes nothing, has no clock (the time is given) and makes no
 * random numbers of its own (seeds are given). Before it returns, it applies the events to a copy of the state, so the reducer never
 * refuses what the desk handed out.
 *
 * <p>The way of a round, as the API drives it:
 * <ol>
 *   <li>{@link #openRound}: asks every participant for an initiative roll (the roll events of {@link RollDesk}).</li>
 *   <li>{@link #setOrder}: once everybody has rolled, fixes the order and starts the declare step.</li>
 *   <li>{@link #declare} for every active Pokémon (the players on their phones, the AI for the enemies, the admin for anyone), or
 *       {@link #expireDeclarations} when the turn timer runs out.</li>
 *   <li>{@link #startResolving}, then {@link #resolveNext} until the queue is empty.</li>
 *   <li>{@link #endRound}, or {@link #endIfOver} when only one side is left.</li>
 * </ol>
 */
public final class BattleDesk {

    private BattleDesk() {
    }

    // ---------------------------------------------------------------------------------------------------------------- starting

    /** The admin (or a trigger the admin allowed) starts a battle. */
    public static List<GameEvent> start(GameState state, Actor actor, String battleId, Battle.Type type, BattleSettings rules,
                                        List<Participant> participants, List<Combatant> combatants) {
        return start(state, actor, battleId, type, rules, participants, combatants, null);
    }

    /**
     * Same as above; the Pokémon on the field also "come onto the field" (chapter 7.9): Intimidate acts, Frisk tells, and a scripted ability
     * or held item that Core does not run opens a task, as the {@code book} says. Without a book there are no tasks.
     */
    public static List<GameEvent> start(GameState state, Actor actor, String battleId, Battle.Type type, BattleSettings rules,
                                        List<Participant> participants, List<Combatant> combatants, MoveBook book) {
        requireAdmin(actor, "start a battle");
        if (participants.stream().noneMatch(p -> p.trainerId() != null)) {
            throw new BattleRefusedException("A battle needs at least one trainer of the game");
        }
        if (type == Battle.Type.PVP && !rules.prizes().pvpEnabled()) {
            throw new BattleRefusedException("PvP battles are switched off in this game (combat.pvp.enabled)");
        }
        BattleStarted started = new BattleStarted(battleId, type, rules, participants, combatants);
        List<GameEvent> events = new ArrayList<>();
        events.add(started);
        GameState work;
        try {
            work = GameReducer.apply(state, started);
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
        for (Participant p : work.battle().participants().values()) {
            for (String id : p.active()) {
                Battle now = work.battle();
                for (GameEvent e : AbilityScripts.onEntry(now, now.combatant(id), book)) {
                    work = apply(work, e, events);
                }
            }
        }
        return checked(state, events);
    }

    /**
     * Begins the next round: asks every participant that can still fight for a 1d20 roll plus the Speed modifier
     * {@code floor(Speed / combat.initiative.speedDivisor)} of its fastest Pokémon on the field (chapter 7.3). A player rolls their own;
     * the roll of an enemy is an enemy roll. The roll of a participant that has no trainer in the game ({@code wild}, {@code NPC}) is
     * kept under the first trainer of the battle. If initiative is not rolled again, the round goes straight to declaring.
     *
     * @param bonuses what class features add to the initiative of a participant, by participant id (may be empty)
     */
    public static List<GameEvent> openRound(GameState state, RollSettings rollSettings, Map<String, Integer> bonuses, long now, LongSupplier seeds) {
        Battle b = battle(state);
        if (b.step() != Step.START && b.step() != Step.ROUND_END) {
            throw new BattleRefusedException("Round " + (b.round() + 1) + " cannot begin: round " + b.round() + " is not over");
        }
        int round = b.round() + 1;
        long deadline = b.rules().turnTimerEnabled() ? now + b.rules().turnTimerSeconds() * 1000L : 0;
        if (!b.rules().rerollEachRound() && !b.order().isEmpty()) {
            return checked(state, List.of(new RoundOpened(b.battleId(), round, List.of(), deadline)));
        }
        String anchor = b.participants().values().stream().map(Participant::trainerId).filter(t -> t != null).findFirst()
                .orElseThrow(() -> new BattleRefusedException("A battle needs at least one trainer of the game"));
        List<GameEvent> events = new ArrayList<>();
        List<InitiativeSlot> slots = new ArrayList<>();
        for (Participant p : b.participants().values()) {
            if (b.out(p)) {
                continue;
            }
            Combatant lead = b.activeOf(p).stream().filter(c -> !c.fainted()).max(java.util.Comparator.comparingInt(Combatant::speed))
                    .orElseThrow(() -> new BattleRefusedException(p.name() + " must send in a replacement first"));
            int speed = (int) (lead.speed() * ItemScripts.speedFactor(b, lead)); // Choice Scarf
            int modifier = Math.floorDiv(speed, b.rules().speedDivisor()) + bonuses.getOrDefault(p.id(), 0);
            String requestId = b.battleId() + "-r" + round + "-init-" + p.id();
            RollClass rollClass = p.kind() == Kind.TRAINER ? RollClass.NORMAL : RollClass.ENEMY;
            GameEvent.RollRequested ask = RollRequests.classic(requestId, p.trainerId() != null ? p.trainerId() : anchor,
                    "initiative of " + b.label(lead), modifier, null, List.of(), rollClass, p.userId());
            events.addAll(RollDesk.open(state, rollSettings, ask, now, seeds));
            slots.add(new InitiativeSlot(p.id(), requestId, modifier, speed));
        }
        events.add(new RoundOpened(b.battleId(), round, slots, 0));
        return checked(state, events);
    }

    /**
     * Everybody has rolled: fixes the initiative order and starts the declare step. A random number per participant (from
     * {@code seeds}) only decides a tie of both the total and the Speed. With {@code combat.turnTimer.enabled} the declare step gets a
     * time limit.
     */
    public static List<GameEvent> setOrder(GameState state, long now, LongSupplier seeds) {
        Battle b = battle(state);
        requireStep(b, Step.INITIATIVE, "set the order");
        Map<String, Integer> tieBreaks = new LinkedHashMap<>();
        for (InitiativeSlot s : b.slots()) {
            tieBreaks.put(s.participantId(), (int) Math.floorMod(seeds.getAsLong(), 1_000_000L));
        }
        long deadline = b.rules().turnTimerEnabled() ? now + b.rules().turnTimerSeconds() * 1000L : 0;
        return checked(state, List.of(new InitiativeSet(b.battleId(), b.round(), tieBreaks, deadline)));
    }

    /** True when every initiative roll of the round has been answered, so {@link #setOrder} can be called. */
    public static boolean initiativeReady(GameState state) {
        Battle b = state.battle();
        if (b == null || b.step() != Step.INITIATIVE) {
            return false;
        }
        return b.slots().stream().allMatch(s -> state.rolls().stream().anyMatch(r -> r.requestId().equals(s.requestId())));
    }

    // ---------------------------------------------------------------------------------------------------------------- declaring

    /**
     * A Pokémon's action is chosen. A player may choose for their own Pokémon; the admin for any (the AI's choice for an enemy is
     * declared by the system as the admin). Choosing again replaces the choice, until the declare step is over. Besides the rules of
     * chapter 7.4, the settings decide: balls need {@code combat.catch.enabled}, running away from another trainer needs
     * {@code combat.flee.allowTrainerBattles}, and running away from a boss needs {@code combat.flee.allowBoss}.
     */
    public static List<GameEvent> declare(GameState state, Actor who, String actorId, Action action, long now) {
        Battle b = battle(state);
        requireStep(b, Step.DECLARE, "choose an action");
        Combatant actor = b.combatant(actorId);
        if (actor == null) {
            throw new BattleRefusedException("There is no Pokémon " + actorId + " in this battle");
        }
        Participant owner = b.ownerOf(actor);
        if (!who.admin() && (owner.userId() == null || !owner.userId().equals(who.userId()))) {
            throw new BattleRefusedException("This is not your Pokémon");
        }
        if (!who.admin() && b.declareDeadlineMillis() > 0 && now > b.declareDeadlineMillis()) {
            throw new BattleRefusedException("The time to choose is over");
        }
        if (action == null) {
            throw new BattleRefusedException("Choose an action");
        }
        BattleSettings rules = b.rules();
        if (action.kind() == ActionKind.BALL) {
            if (!rules.catchEnabled()) {
                throw new BattleRefusedException("Catching is switched off in this game");
            }
            Combatant target = action.targets().isEmpty() ? null : b.combatant(action.targets().get(0));
            if (target != null && b.ownerOf(target).kind() != Kind.WILD && !rules.catchAllowTrainerOwned()) {
                throw new BattleRefusedException("A trainer's Pokémon cannot be caught in this game");
            }
        }
        if (action.kind() == ActionKind.FLEE) {
            boolean playerFoe = b.participants().values().stream().anyMatch(p -> p.kind() == Kind.TRAINER && p.side() != owner.side() && !b.out(p));
            if (playerFoe) {
                throw new BattleRefusedException("You cannot run away from another player: surrender instead");
            }
            boolean trainerBattle = b.participants().values().stream().anyMatch(p -> p.kind() != Kind.WILD && p.side() != owner.side());
            if (trainerBattle && !rules.fleeAllowTrainerBattles()) {
                throw new BattleRefusedException("You cannot run away from a trainer battle");
            }
            boolean boss = b.combatants().values().stream().anyMatch(c -> c.boss() && b.sideOf(c) != owner.side());
            if (boss && !rules.fleeAllowBoss()) {
                throw new BattleRefusedException("You cannot run away from this boss");
            }
        }
        return checked(state, List.of(new ActionDeclared(b.battleId(), b.round(), actorId, action)));
    }

    /**
     * Like {@link #declare(GameState, Actor, String, Action, long)}, and an item is also checked against the item data: it must be one
     * that works in a battle, and it must have an effect now (a Potion on a Pokémon at full HP is refused, so no turn is lost).
     */
    public static List<GameEvent> declare(GameState state, Actor who, String actorId, Action action, long now, MoveBook book) {
        List<GameEvent> events = declare(state, who, actorId, action, now);
        if (action.kind() == ActionKind.ITEM && action.itemId() != null && !action.targets().isEmpty()) {
            Battle b = battle(state);
            Combatant target = b.combatant(action.targets().get(0));
            String why = target == null ? null : BattleItems.refusal(b, book, target, action.itemId(), action.moveId());
            if (why != null) {
                throw new BattleRefusedException(why);
            }
        }
        return events;
    }

    /**
     * The enemies choose (chapter 7.13): every Pokémon of a wild participant or an NPC that has not been given an action yet gets one
     * from the AI of {@code combat.ai.level}. The admin can take over any enemy by declaring for it first, or at any time by declaring
     * again; a player's Pokémon are never touched. All the randomness of the choices comes from {@code seeds}, and the choices are stored
     * as ordinary declared actions, so a replay never chooses again.
     */
    public static List<GameEvent> declareEnemies(GameState state, MoveBook book, LongSupplier seeds) {
        Battle b = battle(state);
        requireStep(b, Step.DECLARE, "choose the actions of the enemies");
        java.util.Random random = new java.util.Random(seeds.getAsLong());
        List<GameEvent> events = new ArrayList<>();
        for (Combatant c : b.standing()) {
            if (b.ownerOf(c).kind() == Kind.TRAINER || b.declared().containsKey(c.id())) {
                continue;
            }
            events.add(new ActionDeclared(b.battleId(), b.round(), c.id(), EnemyAi.choose(state, b, c, book, random)));
        }
        return events.isEmpty() ? events : checked(state, events);
    }

    /** The turn timer ran out: everybody who has not chosen skips, or plays the first move that has PP, as {@code combat.turnTimer.onTimeout} says. */
    public static List<GameEvent> expireDeclarations(GameState state, long now) {
        Battle b = state.battle();
        if (b == null || b.step() != Step.DECLARE || b.declareDeadlineMillis() <= 0 || now < b.declareDeadlineMillis()) {
            return List.of();
        }
        List<GameEvent> events = new ArrayList<>();
        for (Combatant c : b.standing()) {
            if (!b.declared().containsKey(c.id())) {
                Action action = b.rules().onTimeout() == BattleSettings.OnTimeout.AUTO_MOVE ? autoMove(b, c) : Action.skip();
                events.add(new ActionDeclared(b.battleId(), b.round(), c.id(), action));
            }
        }
        return events.isEmpty() ? events : checked(state, events);
    }

    /** The first move with PP against the first foe, or Struggle, or a skip when there is nobody to aim at. */
    private static Action autoMove(Battle b, Combatant c) {
        String locked = b.choiceLockedMove(c); // a Choice item keeps the holder to one move
        MoveSlot slot = c.moves().stream().filter(m -> locked == null || m.moveId().equals(locked))
                .filter(m -> !b.rules().ppEnabled() || m.pp() > 0).findFirst().orElse(null);
        if (slot == null) {
            if (!b.rules().ppEnabled() || !c.outOfPp()) {
                return Action.skip();
            }
            slot = MoveSlot.struggle();
        }
        TargetKind kind = slot.targetKind();
        if (kind.choosesOne()) {
            List<String> legal = b.legalTargets(c, kind);
            return legal.isEmpty() ? Action.skip() : Action.move(slot.moveId(), legal.get(0));
        }
        return Action.move(slot.moveId());
    }

    /** True when every Pokémon that can act has chosen, so {@link #startResolving} can be called. */
    public static boolean allDeclared(GameState state) {
        Battle b = state.battle();
        return b != null && b.step() == Step.DECLARE && b.standing().stream().allMatch(c -> b.declared().containsKey(c.id()));
    }

    /**
     * What a viewer may see of the declared actions (chapter 7.3, {@code combat.declare.hidden}): the admin sees all; a player sees
     * their own, and the others' only when the setting is off or the actions have started to resolve.
     */
    public static Map<String, Action> visibleDeclarations(Battle battle, Actor viewer) {
        if (viewer.admin() || !battle.rules().declareHidden() || battle.step() == Step.RESOLVE || battle.step() == Step.ROUND_END) {
            return battle.declared();
        }
        Map<String, Action> own = new LinkedHashMap<>();
        battle.declared().forEach((id, action) -> {
            Participant owner = battle.ownerOf(battle.combatant(id));
            if (owner.userId() != null && owner.userId().equals(viewer.userId())) {
                own.put(id, action);
            }
        });
        return own;
    }

    // ---------------------------------------------------------------------------------------------------------------- resolving

    /** Declaring is over: puts the actions in order. Everybody who can act must have chosen (the admin can have the rest skip first). */
    public static List<GameEvent> startResolving(GameState state) {
        Battle b = battle(state);
        requireStep(b, Step.DECLARE, "start resolving");
        List<String> missing = b.standing().stream().filter(c -> !b.declared().containsKey(c.id())).map(b::label).toList();
        if (!missing.isEmpty()) {
            throw new BattleRefusedException("Waiting for " + String.join(", ", missing));
        }
        List<GameEvent> events = new ArrayList<>();
        for (Combatant c : b.standing()) {
            // a Pokémon that is locked into a move (Rollout, Uproar) uses it again, whatever was chosen
            String lock = BattleReducer.lockedMove(c);
            MoveSlot slot = lock == null ? null : c.moves().stream().filter(m -> m.moveId().equals(lock)).findFirst().orElse(null);
            if (slot == null) {
                continue;
            }
            Action forced = null;
            if (slot.targetKind().choosesOne()) {
                List<String> legal = b.legalTargets(c, slot.targetKind());
                if (!legal.isEmpty()) {
                    forced = Action.move(lock, legal.get(0));
                }
            } else {
                forced = Action.move(lock);
            }
            if (forced != null && !forced.equals(b.declared().get(c.id()))) {
                events.add(new ActionDeclared(b.battleId(), b.round(), c.id(), forced));
            }
        }
        events.add(new ResolveStarted(b.battleId(), b.round()));
        return checked(state, events);
    }

    /**
     * The next action in the queue happens: it is cancelled if its Pokémon fainted, fails if its only target fainted, and is done
     * otherwise (a move spends its PP, a switch swaps the Pokémon). What a move does to HP, status and so on is added by the later phases
     * as more events in the same action.
     */
    public static List<GameEvent> resolveNext(GameState state) {
        Battle b = battle(state);
        requireStep(b, Step.RESOLVE, "resolve an action");
        if (b.queue().isEmpty()) {
            throw new BattleRefusedException("Every action of this round has already happened");
        }
        Combatant actor = b.combatant(b.queue().get(0));
        Action action = b.declared().get(actor.id());
        Result result;
        if (actor.fainted()) {
            result = Result.CANCELLED_FAINTED;
        } else if (!b.isActive(actor)) {
            result = Result.CANCELLED_SWITCHED;
        } else {
            result = BattleReducer.noTarget(b, actor, action) ? Result.FAILED_NO_TARGET : Result.DONE;
        }
        return checked(state, List.of(new ActionResolved(b.battleId(), b.round(), actor.id(), result)));
    }

    /**
     * The next action in the queue happens, with hits, damage, types, statuses and the effects of the move (chapters 7.4 to 7.8). The
     * moves and their numbers come from the {@code book}. A move needs several rolls one after the other (hit, number of hits, damage,
     * the chance of a side effect, the length of a sleep); each call goes as far as the answered rolls allow and then returns the roll
     * requests that are missing (and nothing else). So the caller calls it again after the rolls are answered (and, for rolls the
     * system makes, at once), until the action has left the queue: {@link #awaiting} tells which rolls are still open.
     *
     * <p>A move that Core cannot run (it is not in the book, or its effect is scripted and not written) becomes an
     * {@link GameEvent.AdminTaskOpened admin task}; it never fails. Nothing about the battle changes until the last roll is answered, so
     * an admin who changes a roll or a Pokémon in between gets a consistent result.
     *
     * @param rollSettings who answers the rolls and how long they wait
     * @param now          the time now, for the time limits of roll requests
     * @param seeds        where the seeds of rolls the system makes, and of random choices, come from
     */
    public static List<GameEvent> resolveNext(GameState state, MoveBook book, RollSettings rollSettings, long now, LongSupplier seeds) {
        return resolveNext(state, book, rollSettings, now, seeds, Map.of());
    }

    /**
     * Same as above; {@code accuracyBonusPp} is what each participant's trainer adds to the hit chance of their Pokémon's moves, in
     * percentage points, by participant id (the Handling bonus of chapter 4.2).
     */
    public static List<GameEvent> resolveNext(GameState state, MoveBook book, RollSettings rollSettings, long now, LongSupplier seeds,
                                              Map<String, Double> accuracyBonusPp) {
        return resolveNext(state, book, rollSettings, now, seeds, BattleBonuses.accuracy(accuracyBonusPp));
    }

    /**
     * Same as above with everything the trainers' sheets add ({@link BattleBonuses}): the accuracy bonus, the catch and flee bonuses, the
     * defence of a trainer that an enemy attacks, and the place and time that some balls read. Besides moves, this resolves a thrown
     * Poké Ball (chapter 7.10: one catch roll of the trainer's player, the ball is used up, the Pokémon is caught or breaks free) and an
     * attempt to run away (7.11: one flee roll; the trainer leaves the battle with their Pokémon if it works).
     */
    public static List<GameEvent> resolveNext(GameState state, MoveBook book, RollSettings rollSettings, long now, LongSupplier seeds,
                                              BattleBonuses bonuses) {
        Battle b = battle(state);
        requireStep(b, Step.RESOLVE, "resolve an action");
        if (b.queue().isEmpty()) {
            throw new BattleRefusedException("Every action of this round has already happened");
        }
        if (!b.mustSwitch().isEmpty()) {
            throw new BattleRefusedException(b.label(b.combatant(b.mustSwitch().get(0))) + " has to be replaced before the next action");
        }
        return checked(state, MoveResolver.resolveNext(state, book, rollSettings, now, seeds, bonuses));
    }

    /** The roll requests of this battle that nobody has answered yet: the next action waits for them. */
    public static List<GameState.RollRequest> awaiting(GameState state) {
        Battle b = state.battle();
        if (b == null) {
            return List.of();
        }
        String prefix = b.battleId() + "-r";
        return state.pendingRolls().values().stream().filter(r -> r.requestId().startsWith(prefix)).toList();
    }

    /**
     * The round is over (every action has happened). End-of-round effects come first (chapter 7.3, step 5): burn takes 1/16 of the
     * maximum HP, poison 1/8, bad poison n/16 with n growing by one each round, a curse 1/4; an uproar runs down; flinching, Protect,
     * Endure and Helping Hand end.
     */
    public static List<GameEvent> endRound(GameState state) {
        return endRound(state, () -> {
            throw new IllegalStateException("This round needs a random number (Shed Skin): use endRound(state, seeds)");
        });
    }

    /**
     * Same as above; {@code seeds} is where the random choices of the end of the round come from. They are kept in the log, so a replay
     * never chooses again. Besides the effects of chapter 7.3, Leftovers heal 1/16, Shed Skin may cure a status (3 in 10), and berries are
     * eaten when the HP or the status calls for it (chapter 7.9).
     */
    public static List<GameEvent> endRound(GameState state, LongSupplier seeds) {
        Battle b = battle(state);
        requireStep(b, Step.RESOLVE, "end the round");
        if (!b.queue().isEmpty()) {
            throw new BattleRefusedException(b.queue().size() + " action(s) have not happened yet");
        }
        List<GameEvent> events = new ArrayList<>(endOfRound(state, b, seeds));
        events.add(new RoundEnded(b.battleId(), b.round()));
        return checked(state, events);
    }

    private static List<GameEvent> endOfRound(GameState state, Battle b, LongSupplier seeds) {
        List<GameEvent> events = new ArrayList<>();
        GameState work = state;
        String battleId = b.battleId();
        for (Combatant first : b.standing()) {
            Combatant c = work.battle().combatant(first.id());
            if (c.fainted()) {
                continue;
            }
            String id = c.id();
            List<GameEvent> mine = new ArrayList<>();
            switch (c.status()) {
                case BURN -> mine.add(new GameEvent.HpChanged(battleId, id, -Math.min(c.hp(), Math.max(1, c.maxHp() / 16)), "from the burn"));
                case POISON -> mine.add(new GameEvent.HpChanged(battleId, id, -Math.min(c.hp(), Math.max(1, c.maxHp() / 8)), "from the poison"));
                case TOXIC -> {
                    int n = Math.max(1, c.condition().volatileValue(Battle.Volatiles.TOXIC));
                    mine.add(new GameEvent.HpChanged(battleId, id, -Math.min(c.hp(), Math.max(1, c.maxHp() * n / 16)), "from the bad poison"));
                    mine.add(new GameEvent.VolatileSet(battleId, id, Battle.Volatiles.TOXIC, n + 1, null));
                }
                default -> {
                }
            }
            if (c.condition().has(Battle.Volatiles.CURSE)) {
                mine.add(new GameEvent.HpChanged(battleId, id, -Math.min(c.hp(), Math.max(1, c.maxHp() / 4)), "from the curse"));
            }
            for (GameEvent e : mine) {
                work = apply(work, e, events);
            }
            Combatant after = work.battle().combatant(id);
            if (after.fainted()) {
                continue;
            }
            // held items and abilities (chapter 7.9): Leftovers, Shed Skin, then the berries that the changes of this round call for
            GameEvent leftovers = ItemScripts.residual(work.battle(), after);
            if (leftovers != null) {
                work = apply(work, leftovers, events);
            }
            after = work.battle().combatant(id);
            if (AbilityScripts.shedsSkin(work.battle(), after)) {
                String purpose = battleId + "-r" + b.round() + "-" + id + "-shedskin";
                int value = -1;
                for (GameState.RandomDraw d : work.draws()) {
                    if (d.purpose().equals(purpose)) {
                        value = d.value();
                    }
                }
                if (value < 0) {
                    value = (int) Math.floorMod(seeds.getAsLong(), 10L);
                    work = apply(work, new GameEvent.RandomDrawn(purpose, 10, value), events);
                }
                if (value < 3) {
                    work = apply(work, new GameEvent.StatusSet(battleId, id, Battle.Status.NONE, 0, b.label(after) + "'s Shed Skin cured its status!"), events);
                }
            }
            for (GameEvent e : ItemScripts.berry(work.battle(), work.battle().combatant(id))) {
                work = apply(work, e, events);
            }
            after = work.battle().combatant(id);
            if (after.fainted()) {
                continue;
            }
            Battle.Condition cond = after.condition();
            if (cond.has(Battle.Volatiles.UPROAR)) {
                int left = cond.volatileValue(Battle.Volatiles.UPROAR) - 1;
                work = apply(work, new GameEvent.VolatileSet(battleId, id, Battle.Volatiles.UPROAR, left,
                        left == 0 ? b.label(after) + " calmed down." : null), events);
            }
            boolean stalled = cond.has(Battle.Volatiles.PROTECT) || cond.has(Battle.Volatiles.ENDURE);
            if (cond.has(Battle.Volatiles.STALL) && !stalled) {
                work = apply(work, new GameEvent.VolatileSet(battleId, id, Battle.Volatiles.STALL, 0, null), events);
            }
            for (String key : List.of(Battle.Volatiles.FLINCH, Battle.Volatiles.PROTECT, Battle.Volatiles.ENDURE, Battle.Volatiles.HELPING_HAND)) {
                if (cond.has(key)) {
                    work = apply(work, new GameEvent.VolatileSet(battleId, id, key, 0, null), events);
                }
            }
        }
        return events;
    }

    private static GameState apply(GameState work, GameEvent event, List<GameEvent> events) {
        events.add(event);
        return GameReducer.apply(work, event);
    }

    /** A participant sends in a Pokémon from the reserve for one that fainted. It does not use an action. */
    public static List<GameEvent> sendReplacement(GameState state, Actor who, String participantId, String replacementId) {
        return sendReplacement(state, who, participantId, replacementId, null);
    }

    /** Same as above; the {@code book} lets a scripted ability or held item that Core does not run open a task when the Pokémon comes in. */
    public static List<GameEvent> sendReplacement(GameState state, Actor who, String participantId, String replacementId, MoveBook book) {
        Battle b = battle(state);
        Participant p = b.participants().get(participantId);
        if (p == null) {
            throw new BattleRefusedException("There is no participant " + participantId);
        }
        if (!who.admin() && (p.userId() == null || !p.userId().equals(who.userId()))) {
            throw new BattleRefusedException("This is not your team");
        }
        String fainted = p.active().stream().filter(id -> b.combatant(id).fainted() || b.mustSwitch().contains(id)).findFirst()
                .orElseThrow(() -> new BattleRefusedException("None of the Pokémon of " + p.name() + " on the field has fainted or has to be replaced"));
        List<GameEvent> events = new ArrayList<>();
        GameEvent heal = AbilityScripts.leaveHeal(b, b.combatant(fainted)); // Regenerator, for a Pokémon that is going back to its ball alive
        if (heal != null) {
            events.add(heal);
        }
        ReplacementSent sent = new ReplacementSent(b.battleId(), participantId, fainted, replacementId);
        events.add(sent);
        GameState work;
        try {
            work = GameReducer.applyAll(state, events);
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
        Battle now = work.battle();
        Combatant coming = now.combatant(replacementId);
        for (GameEvent e : AbilityScripts.onEntry(now, coming, book)) {
            work = apply(work, e, events);
        }
        return checked(state, events);
    }

    /**
     * A player gives up (chapter 7.14): they leave the battle with their Pokémon and have lost it. Players cannot run away from another
     * player or an NPC, but they can surrender. Not while the initiative is being rolled. The admin can make anybody surrender.
     */
    public static List<GameEvent> surrender(GameState state, Actor who, String participantId) {
        Battle b = battle(state);
        Participant p = b.participants().get(participantId);
        if (p == null || p.kind() != Kind.TRAINER) {
            throw new BattleRefusedException("Only a player's trainer can surrender");
        }
        if (!who.admin() && (p.userId() == null || !p.userId().equals(who.userId()))) {
            throw new BattleRefusedException("This is not your team");
        }
        if (b.left().containsKey(p.id())) {
            throw new BattleRefusedException(p.name() + " has left the battle already");
        }
        if (b.step() == Step.INITIATIVE || b.step() == Step.START) {
            throw new BattleRefusedException("Wait for the round to begin before surrendering");
        }
        if (b.participants().values().stream().noneMatch(o -> o.side() != p.side() && o.kind() != Kind.WILD && !b.out(o))) {
            throw new BattleRefusedException("There is nobody to surrender to: run away from a wild Pokémon instead");
        }
        List<GameEvent> events = new ArrayList<>();
        for (String id : java.util.stream.Stream.concat(p.active().stream(), p.reserve().stream()).toList()) {
            String prefix = b.battleId() + "-r" + b.round() + "-" + id + "-";
            for (GameState.RollRequest r : state.pendingRolls().values()) {
                if (r.requestId().startsWith(prefix)) {
                    events.add(new GameEvent.RollCancelled(r.requestId()));
                }
            }
        }
        events.add(new GameEvent.ParticipantLeft(b.battleId(), p.id(), "surrendered"));
        return checked(state, events);
    }

    /** The battle is over when at most one side can still fight: returns the event that ends it, or nothing. */
    public static List<GameEvent> endIfOver(GameState state) {
        Battle b = state.battle();
        if (b == null || b.step() == Step.ENDED || b.step() == Step.START || !b.over()) {
            return List.of();
        }
        List<GameEvent> events = new ArrayList<>(cancelInitiativeRequests(state, b));
        events.add(new BattleEnded(b.battleId(), b.sidesStanding().isEmpty() ? "nobody is left to fight" : "only one side is left"));
        events.addAll(TrainerDesk.afterBattle(state, b.rules())); // a knocked-out trainer goes back to the healer
        return checked(state, events);
    }

    // ---------------------------------------------------------------------------------------------------------- the admin's side

    /** The admin ends the battle at any point (chapter 7.3). Initiative rolls nobody answered are withdrawn. */
    public static List<GameEvent> end(GameState state, Actor who, String reason) {
        requireAdmin(who, "end a battle");
        Battle b = battle(state);
        List<GameEvent> events = new ArrayList<>(cancelInitiativeRequests(state, b));
        events.add(new BattleEnded(b.battleId(), reason == null || reason.isBlank() ? "the admin ended it" : reason));
        events.addAll(TrainerDesk.afterBattle(state, b.rules()));
        return checked(state, events);
    }

    /** The admin puts the actions that have not happened yet in another order. */
    public static List<GameEvent> reorder(GameState state, Actor who, List<String> queue) {
        requireAdmin(who, "reorder the actions");
        Battle b = battle(state);
        return checked(state, List.of(new QueueReordered(b.battleId(), queue)));
    }

    /** The admin takes one action out of the round (it is cancelled when its turn comes). */
    public static List<GameEvent> cancelAction(GameState state, Actor who, String actorId) {
        requireAdmin(who, "cancel an action");
        Battle b = battle(state);
        requireStep(b, Step.RESOLVE, "cancel an action");
        if (!b.queue().contains(actorId)) {
            throw new BattleRefusedException("There is no action of " + actorId + " waiting");
        }
        // the cancelled action moves to the front and resolves as cancelled
        List<String> queue = new ArrayList<>(b.queue());
        queue.remove(actorId);
        queue.add(0, actorId);
        List<GameEvent> events = new ArrayList<>();
        if (!queue.equals(b.queue())) {
            events.add(new QueueReordered(b.battleId(), queue));
        }
        String prefix = b.battleId() + "-r" + b.round() + "-" + actorId + "-";
        for (GameState.RollRequest r : state.pendingRolls().values()) {
            if (r.requestId().startsWith(prefix)) {
                events.add(new GameEvent.RollCancelled(r.requestId()));
            }
        }
        events.add(new ActionResolved(b.battleId(), b.round(), actorId, Result.CANCELLED_BY_ADMIN));
        return checked(state, events);
    }

    /** The admin heals, damages or changes a Pokémon (level, moves, Speed…). */
    public static List<GameEvent> change(GameState state, Actor who, Combatant combatant) {
        requireAdmin(who, "change a Pokémon");
        Battle b = battle(state);
        return checked(state, List.of(new CombatantSet(b.battleId(), combatant)));
    }

    /** The admin has applied the effect of a move by hand: the task leaves the list. */
    public static List<GameEvent> completeTask(GameState state, Actor who, String taskId) {
        requireAdmin(who, "close a task");
        Battle b = battle(state);
        return checked(state, List.of(new GameEvent.AdminTaskClosed(b.battleId(), taskId)));
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    private static List<GameEvent> cancelInitiativeRequests(GameState state, Battle b) {
        List<GameEvent> events = new ArrayList<>();
        for (InitiativeSlot s : b.slots()) {
            if (state.pendingRolls().containsKey(s.requestId())) {
                events.add(new GameEvent.RollCancelled(s.requestId()));
            }
        }
        return events;
    }

    private static Battle battle(GameState state) {
        Battle b = state.battle();
        if (b == null || b.step() == Step.ENDED) {
            throw new BattleRefusedException("There is no battle going on");
        }
        return b;
    }

    private static void requireStep(Battle b, Step step, String what) {
        if (b.step() != step) {
            throw new BattleRefusedException("You cannot " + what + " now: the battle is at the " + b.step().name().toLowerCase().replace('_', ' ') + " step");
        }
    }

    private static void requireAdmin(Actor who, String what) {
        if (!who.admin()) {
            throw new BattleRefusedException("Only the admin can " + what);
        }
    }

    /** Applies the events to a copy of the state: if the reducer refuses one, the desk refuses with the same words. */
    private static List<GameEvent> checked(GameState state, List<? extends GameEvent> events) {
        try {
            GameReducer.applyAll(state, events);
        } catch (InvalidEventException e) {
            throw new BattleRefusedException(e.getMessage());
        }
        return List.copyOf(events);
    }
}
