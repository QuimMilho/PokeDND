/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.quimmilho.pokednd.core.combat.BallTable;
import net.quimmilho.pokednd.core.combat.CatchCalculator;
import net.quimmilho.pokednd.core.combat.CombatSettings;
import net.quimmilho.pokednd.core.combat.FleeCalculator;
import net.quimmilho.pokednd.core.combat.Damage;
import net.quimmilho.pokednd.core.combat.HitRoll;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.dice.DiceChoice;
import net.quimmilho.pokednd.core.dice.SmartDice;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.ActionKind;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Result;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.game.GameState.RandomDraw;
import net.quimmilho.pokednd.core.game.GameState.RollClass;
import net.quimmilho.pokednd.core.game.GameState.RollResult;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.rules.MajorStatus;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.Stats;
import net.quimmilho.pokednd.core.trainer.KnockOut;
import net.quimmilho.pokednd.core.trainer.KoMode;
import net.quimmilho.pokednd.core.trainer.Trainer;

/**
 * Resolves one declared action of the queue, with hit rolls, damage, types, statuses and the effects of moves (chapters 7.4 to 7.8 and
 * 19.6). It is the engine behind {@link BattleDesk#resolveNext(GameState, MoveBook, RollSettings, long, LongSupplier)}.
 *
 * <p><b>How it waits for rolls.</b> A move may need several rolls one after the other: the hit roll, the number of hits, the damage dice,
 * the chance of a side effect, the duration of a sleep. Nothing about the battle changes until the last of them is answered. So on every
 * call the resolver starts again from the top and follows the same path through the state that has not changed: a roll that was answered
 * gives its number, the first roll that has not been asked yet is asked, and a roll that is asked and not yet answered makes the call
 * return (the {@link Waiting} signal) with only the roll requests. When everything is answered, the whole outcome is built in one go:
 * the effects, then {@code ActionResolved}'s successors. Random choices (Sleep Talk, a forced switch) are stored as {@code RandomDrawn}
 * events the first time, so that the next call reads the same value. Roll requests have fixed ids, so the same roll is never asked twice.
 *
 * <p><b>Abilities and held items</b> (phase 47) act through {@link AbilityScripts} and {@link ItemScripts}: the damage factor, immunities,
 * the hit odds, surviving a hit, recoil, berries after the move, and the Choice lock. When a Pokémon comes onto the field ({@link #entry}),
 * its ability acts and an ability or item that Core does not run makes a task.
 *
 * <p><b>Balls, running away and attacks on trainers</b> (phase 48) are resolved here too: {@link #ball} asks the catch roll,
 * {@link #flee} the flee roll, and {@link #trainerAttack} the opposed roll of chapter 7.12 when an enemy aims a move at a trainer.
 *
 * <p><b>What it does not do.</b> The effect of using items (phase 50), weather and terrain (not in the game). Experience, levels and evolution
 * are paid after the battle by {@link AfterBattle}. A move whose effect the game cannot run is left to the admin: see
 * {@link MoveBook.Handling}.
 */
final class MoveResolver {

    /** Thrown to leave the straight-line code when a roll has been asked and not answered. */
    private static final class Waiting extends RuntimeException {
        Waiting() {
            super("waiting for a roll", null, false, false);
        }
    }

    private static final JsonNode NOTHING = JsonNodeFactory.instance.objectNode();

    private final MoveBook book;
    private final RollSettings rollSettings;
    private final long now;
    private final LongSupplier seeds;
    private final BattleBonuses bonuses;

    /** The state with everything emitted so far applied: the roll requests and answers, the draws, and in the end the effects. */
    private GameState work;
    private final List<GameEvent> out = new ArrayList<>();
    private boolean effectsStarted;

    private final Battle b;
    private final Combatant actor;
    private final Action action;
    private final Participant owner;
    private final CombatSettings combat;
    private boolean awake;

    private MoveResolver(GameState state, MoveBook book, RollSettings rollSettings, long now, LongSupplier seeds, BattleBonuses bonuses) {
        this.work = state;
        this.book = book;
        this.rollSettings = rollSettings;
        this.now = now;
        this.seeds = seeds;
        this.bonuses = bonuses;
        this.b = state.battle();
        this.actor = b.combatant(b.queue().get(0));
        this.action = b.declared().get(actor.id());
        this.owner = b.ownerOf(actor);
        this.combat = book.combat();
    }

    /**
     * Resolves the next action of the queue as far as the rolls allow. The result is the events to add: roll requests (and the answers of
     * rolls the system makes) while a roll is missing; or the whole outcome, ending with the action leaving the queue.
     */
    static List<GameEvent> resolveNext(GameState state, MoveBook book, RollSettings rollSettings, long now, LongSupplier seeds,
                                       BattleBonuses bonuses) {
        MoveResolver r = new MoveResolver(state, book, rollSettings, now, seeds, bonuses);
        try {
            r.run();
        } catch (Waiting w) {
            // the roll requests are in r.out
        }
        return List.copyOf(r.out);
    }

    // =============================================================================================================== the action

    private void run() {
        if (actor.fainted()) {
            effect(resolved(Result.CANCELLED_FAINTED));
            return;
        }
        if (!b.isActive(actor)) {
            effect(resolved(Result.CANCELLED_SWITCHED));
            return;
        }
        if (BattleReducer.noTarget(b, actor, action)) {
            effect(resolved(Result.FAILED_NO_TARGET));
            return;
        }
        if (action.kind() == ActionKind.BALL) {
            ball();
            return;
        }
        if (action.kind() == ActionKind.FLEE) {
            flee();
            return;
        }
        if (action.kind() == ActionKind.ITEM) {
            item();
            return;
        }
        if (action.kind() != ActionKind.MOVE) {
            boolean switching = action.kind() == ActionKind.SWITCH;
            if (switching) {
                GameEvent heal = AbilityScripts.leaveHeal(b, actor);
                if (heal != null) {
                    effect(heal);
                }
            }
            effect(resolved(Result.DONE));
            if (switching) {
                entry(action.switchTo());
            }
            return;
        }
        List<GameEvent> pre = new ArrayList<>();
        if (!canAct(pre)) {
            effectsStarted = true;
            pre.forEach(this::effect);
            effect(resolved(Result.PREVENTED));
            afterPrevented();
            return;
        }
        Runnable apply = plan(action.moveId(), "", action.targets(), false);
        effectsStarted = true;
        pre.forEach(this::effect);
        effect(resolved(Result.DONE));
        int lock = ItemScripts.lockFor(b, actor, action.moveId());
        if (lock > 0) {
            vol(cur(actor.id()), Volatiles.CHOICE_LOCK, lock, b.label(actor) + " is locked into " + slotName(action.moveId()) + " by its "
                    + BattleReducer.pretty(actor.condition().heldItem()) + ".");
        }
        apply.run();
    }

    // ================================================================================================ enemies attack a trainer

    /**
     * An enemy's move aimed at a trainer (chapter 7.12). The enemy rolls {@code 1d20 + level / 8}; the trainer's player rolls
     * {@code 1d20 + (AC − 10)} and dodges with at least the same total (a tie favours the trainer). A natural 20 always hits and is a
     * critical hit, a natural 1 always misses; neither asks the trainer to roll. A hit uses the damage formula of chapter 7.6 with the
     * trainer's Defense value and no type effect, then the damage is scaled down (rounded up, at least 1) and a class feature may take
     * some off. Abilities, held items and the side effects of the move do not reach a trainer.
     */
    private Runnable trainerAttack(String moveId, String target) {
        Participant tp = b.participants().get(Battle.trainerTargetId(target));
        Trainer trainer = tp == null || tp.trainerId() == null ? null : work.trainers().get(tp.trainerId());
        MoveEntry entry = book.entry(moveId).orElse(null);
        MoveProfile p = book.profile(moveId).orElse(null);
        String name = entry != null ? entry.name() : slotName(moveId);
        String attacker = b.label(actor);
        if (trainer == null || p == null || !p.damaging() || p.ohko()) {
            String who = tp == null ? "the trainer" : tp.name();
            return () -> note(attacker + "'s " + name + " has no effect on " + who + ".");
        }
        BattleSettings.Enemy rules = b.rules().enemy();
        int bonus = actor.level() / rules.attackLevelDivisor();
        int attackDie = roll("tatk", id -> RollRequests.classic(id, trainerId(), name + " against " + tp.name(), bonus, null, List.of(), RollClass.ENEMY, null));
        int attackTotal = attackDie + bonus;
        boolean crit = attackDie == 20;
        boolean hit;
        int defenseDie = 0;
        int defenseMod = bonuses.defenseRoll(tp.id());
        if (attackDie == 20) {
            hit = true;
        } else if (attackDie == 1) {
            hit = false;
        } else {
            String reason = "dodging " + attacker + "'s " + name + " (its attack total is " + attackTotal + ")";
            defenseDie = roll("tdef", id -> RollRequests.classic(id, tp.trainerId(), reason, defenseMod, attackTotal, List.of(), RollClass.NORMAL, tp.userId()));
            hit = defenseDie + defenseMod < attackTotal;
        }
        int damage = 0;
        if (hit) {
            int raw = 0;
            boolean physical = p.category() == MoveProfile.Category.PHYSICAL;
            String offKey = physical ? "atk" : "spa";
            if (p.fixedDamage().isPresent()) {
                raw = Damage.fixed(p.fixedDamage().get(), actor.level(), 1.0);
            } else if (p.usesDamageDice()) {
                Dice dice = p.damageDice(combat).orElseThrow();
                int total = roll("tdmg", id -> RollRequests.dice(id, trainerId(), name + ": damage", dice, RollClass.ENEMY, null));
                boolean stab = !MoveSlot.STRUGGLE.equals(moveId) && actor.types().contains(p.type());
                boolean burned = physical && actor.status() == Status.BURN;
                raw = Damage.compute(new Damage.Input(actor.level(), total, Math.max(1, stat(actor, offKey)), rules.trainerDefense(trainer.level()),
                        actor.condition().stage(offKey), 0, crit, stab, 1.0, burned, 1.0), combat).damage();
            }
            if (raw > 0) {
                damage = Math.max(1, (int) Math.ceil(raw * rules.damageScale() - 1e-9));
                damage = Math.max(0, damage - bonuses.damageReduction(tp.id()));
            }
        }
        boolean landed = hit;
        boolean critical = crit;
        int dealt = damage;
        return () -> {
            if (!landed) {
                note(attacker + "'s " + name + (attackDie == 1 ? " misses completely!" : " is dodged by " + tp.name() + "!"));
                setLastFailed(true);
                return;
            }
            if (critical) {
                note("A critical hit!");
            }
            Trainer now = work.trainers().get(tp.trainerId());
            int taken = Math.min(dealt, now.hp());
            if (taken <= 0) {
                note(tp.name() + " takes no damage.");
                return;
            }
            effect(new GameEvent.TrainerHpChanged(tp.trainerId(), -taken, "from " + name));
            note(tp.name() + " takes " + taken + " damage from " + attacker + "'s " + name + " (" + (now.hp() - taken) + "/" + now.maxHp() + " HP).");
            if (now.hp() - taken <= 0) {
                knockOut(tp);
            }
        };
    }

    /** A trainer is at 0 HP (chapter 4.6): the knock-out rule is stored, and the trainer leaves the battle with their Pokémon. */
    private void knockOut(Participant tp) {
        BattleSettings.KnockOutRules ko = b.rules().knockOut();
        KnockOut.DeathSaves saves = ko.mode() == KoMode.FAINT_RESPAWN
                ? new KnockOut.DeathSaves(0, 0, KnockOut.DeathSaves.State.RESPAWN) : KnockOut.DeathSaves.start();
        effect(new GameEvent.KnockoutSet(tp.trainerId(), new GameState.Knockout(ko.mode(), saves)));
        note(tp.name() + " is knocked out!");
        effect(new GameEvent.ParticipantLeft(b.battleId(), tp.id(), "knockedOut"));
        if (ko.mode() == KoMode.ADMIN) {
            openTask("knockout-" + tp.trainerId(), "the knock-out of " + tp.name() + ": the admin decides what happens");
        }
    }

    // ================================================================================================== catching and fleeing

    /** A trainer throws a Poké Ball (chapter 7.10): the catch roll of the trainer's player, then the ball is used up. */
    private void ball() {
        Combatant target = b.combatant(b.resolveTarget(action.targets().get(0)));
        Participant targetOwner = b.ownerOf(target);
        CatchCalculator.Target kind = target.boss() ? CatchCalculator.Target.BOSS
                : targetOwner.kind() == Kind.WILD ? CatchCalculator.Target.WILD : CatchCalculator.Target.TRAINER_OWNED;
        java.util.Optional<MajorStatus> status = switch (target.status()) {
            case BURN -> java.util.Optional.of(MajorStatus.BURN);
            case POISON, TOXIC -> java.util.Optional.of(MajorStatus.POISON);
            case PARALYSIS -> java.util.Optional.of(MajorStatus.PARALYSIS);
            case SLEEP -> java.util.Optional.of(MajorStatus.SLEEP);
            case FREEZE -> java.util.Optional.of(MajorStatus.FREEZE);
            case NONE -> java.util.Optional.empty();
        };
        String species = target.build().speciesId();
        CatchCalculator.Throw thrown = new CatchCalculator.Throw(kind, target.maxHp(), target.hp(), target.build().effectiveCatchRate(), action.itemId(),
                status, 0, bonuses.catchMultiplier(owner.id()),
                new BallTable.Context(b.round(), bonuses.nightOrCave(), species != null && bonuses.caughtBefore().contains(species), target.types()));
        CatchCalculator.Settings settings = new CatchCalculator.Settings(b.rules().catchEnabled(), b.rules().catchAllowTrainerOwned(), 0.05, false);
        CatchCalculator.Odds odds = CatchCalculator.odds(thrown, settings, BallTable.defaults(), book.dice());
        if (!odds.allowed()) {
            effect(resolved(Result.DONE));
            note(odds.reason());
            return;
        }
        int total = successRoll("ball", "catching " + b.label(target), odds.roll(), odds.chance());
        boolean caught = succeeded(odds.roll(), total);
        int shakes = caught ? 3 : shakesOf(odds.roll(), total);
        effect(resolved(Result.DONE));
        if (owner.trainerId() != null && work.trainers().containsKey(owner.trainerId())) {
            Trainer trainer = work.trainers().get(owner.trainerId());
            Map<String, Integer> bag = new LinkedHashMap<>(trainer.items());
            int left = bag.getOrDefault(action.itemId(), 0) - 1;
            if (left > 0) {
                bag.put(action.itemId(), left);
            } else {
                bag.remove(action.itemId());
            }
            effect(new GameEvent.TrainerSet(owner.trainerId(), trainer.withItems(bag)));
        }
        effect(new GameEvent.BallThrown(b.battleId(), target.id(), owner.id(), action.itemId(), shakes, caught));
    }

    /** A trainer uses an item from the bag on one of their Pokémon (chapter 7.4, 8.8): it does its work and is used up, or it does nothing and stays in the bag. */
    private void item() {
        Combatant target = cur(b.resolveTarget(action.targets().get(0)));
        List<GameEvent> events = BattleItems.plan(b, book, target, action.itemId(), action.moveId());
        effect(resolved(Result.DONE));
        if (events.isEmpty()) {
            note("It won't have any effect on " + b.label(target) + ".");
            return;
        }
        events.forEach(this::effect);
        useUp(action.itemId());
    }

    /** One item leaves the bag of the trainer who owns the acting Pokémon (an NPC has no bag in the game state). */
    private void useUp(String itemId) {
        if (owner.trainerId() == null || !work.trainers().containsKey(owner.trainerId())) {
            return;
        }
        Trainer trainer = work.trainers().get(owner.trainerId());
        Map<String, Integer> bag = new LinkedHashMap<>(trainer.items());
        int left = bag.getOrDefault(itemId, 0) - 1;
        if (left > 0) {
            bag.put(itemId, left);
        } else {
            bag.remove(itemId);
        }
        effect(new GameEvent.TrainerSet(owner.trainerId(), trainer.withItems(bag)));
    }

    /** A trainer tries to run away (chapter 7.11): the flee roll, and the trainer leaves the battle with their Pokémon if it works. */
    private void flee() {
        int ownSpeed = b.activeOf(owner).stream().filter(c -> !c.fainted()).mapToInt(Combatant::speed).max().orElse(0);
        int opponentSpeed = b.standing().stream().filter(c -> b.sideOf(c) != owner.side()).mapToInt(Combatant::speed).max().orElse(0);
        boolean boss = b.standing().stream().anyMatch(c -> b.sideOf(c) != owner.side() && c.boss());
        boolean trainerFoe = b.participants().values().stream().anyMatch(p -> p.side() != owner.side() && p.kind() != Kind.WILD && !b.out(p));
        FleeCalculator.Battle kind = boss ? FleeCalculator.Battle.BOSS : trainerFoe ? FleeCalculator.Battle.TRAINER : FleeCalculator.Battle.WILD;
        int attempt = 1 + b.fleeAttempts().getOrDefault(owner.id(), 0);
        FleeCalculator.Attempt tryToFlee = new FleeCalculator.Attempt(kind, ownSpeed, opponentSpeed, attempt, 0, bonuses.fleePp(owner.id()));
        FleeCalculator.Odds odds = FleeCalculator.odds(tryToFlee,
                new FleeCalculator.Settings(b.rules().fleeAllowTrainerBattles(), b.rules().fleeAllowBoss(), 2.0), book.dice());
        if (!odds.allowed()) {
            effect(resolved(Result.DONE));
            note(odds.reason());
            return;
        }
        int total = successRoll("flee", owner.name() + " running away", odds.roll(), odds.chance());
        boolean escaped = succeeded(odds.roll(), total);
        effect(resolved(Result.DONE));
        if (escaped) {
            effect(new GameEvent.ParticipantLeft(b.battleId(), owner.id(), "fled"));
        } else {
            effect(new GameEvent.FleeFailed(b.battleId(), owner.id()));
        }
    }

    /** The number a yes/no roll showed, or -1 when nobody rolled (a chance of 100 % or 0 %). */
    private int successRoll(String key, String reason, SmartDice.Success success, double chance) {
        if (success.kind() != SmartDice.Kind.ROLL) {
            return -1;
        }
        return roll(key, id -> RollRequests.smart(id, trainerId(), reason, success.choice(), chance, List.of(), rollClass(), owner.userId(),
                rollSettings.showTrueChance()));
    }

    private static boolean succeeded(SmartDice.Success success, int total) {
        return switch (success.kind()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case ROLL -> total >= success.successFrom() && total <= success.successTo();
        };
    }

    /** How many times a ball shook before it failed, 0 to 2: the closer the roll was to the lowest face that catches, the more. */
    private static int shakesOf(SmartDice.Success success, int total) {
        if (success.kind() != SmartDice.Kind.ROLL || success.successFrom() <= 1) {
            return 0;
        }
        return Math.max(0, Math.min(2, (int) Math.floor(3.0 * (total - 1) / (success.successFrom() - 1))));
    }

    /** A Pokémon has come onto the field (a switch, a forced switch): its ability and item act (chapter 7.9). */
    void entry(String combatantId) {
        Battle now = work.battle();
        for (GameEvent e : AbilityScripts.onEntry(now, now.combatant(combatantId), book)) {
            effect(e);
        }
    }

    /** Berries are eaten when the HP or the status calls for it: after a move, for everyone it touched and for the user. */
    private void berries(List<Strike> strikes) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        strikes.forEach(x -> ids.add(x.target.id()));
        ids.add(actor.id());
        for (String id : ids) {
            Battle now = work.battle();
            for (GameEvent e : ItemScripts.berry(now, now.combatant(id))) {
                effect(e);
            }
        }
    }

    private GameEvent resolved(Result result) {
        return new GameEvent.ActionResolved(b.battleId(), b.round(), actor.id(), result);
    }

    // ================================================================================================================ can it act?

    /**
     * The checks before a Pokémon moves, in the order of the games: recharge, sleep, freeze, flinch, confusion, love, paralysis. Rolls are
     * asked here; the events they lead to are put in {@code pre} and added only when everything is known.
     */
    private boolean canAct(List<GameEvent> pre) {
        Combatant a = actor;
        String who = b.label(a);
        MoveEntry entry = book.entry(action.moveId()).orElse(null);
        JsonNode flags = entry == null || entry.effect() == null ? NOTHING : entry.effect().path("flags");
        boolean sleepUsable = entry != null && entry.effect() != null && entry.effect().path("sleepUsable").asBoolean(false);
        awake = a.status() != Status.SLEEP;
        if (a.condition().has(Volatiles.RECHARGE)) {
            pre.add(new GameEvent.VolatileSet(b.battleId(), a.id(), Volatiles.RECHARGE, 0, null));
            pre.add(new GameEvent.BattleNoted(b.battleId(), who + " must recharge!"));
            return false;
        }
        if (a.status() == Status.SLEEP) {
            if (uproarOnField(b)) {
                pre.add(new GameEvent.StatusSet(b.battleId(), a.id(), Status.NONE, 0, who + " woke up in the uproar!"));
                awake = true;
            } else if (a.condition().statusTurns() > 0) {
                pre.add(new GameEvent.StatusSet(b.battleId(), a.id(), Status.SLEEP, a.condition().statusTurns() - 1, who + " is fast asleep."));
                if (!sleepUsable) {
                    return false;
                }
            } else {
                pre.add(new GameEvent.StatusSet(b.battleId(), a.id(), Status.NONE, 0, null));
                awake = true;
            }
        }
        if (a.status() == Status.FREEZE) {
            if (flags.path("defrost").asBoolean(false) || chance("thaw", "thawing out of the ice", 0.20)) {
                pre.add(new GameEvent.StatusSet(b.battleId(), a.id(), Status.NONE, 0, null));
            } else {
                pre.add(new GameEvent.BattleNoted(b.battleId(), who + " is frozen solid!"));
                return false;
            }
        }
        if (a.condition().has(Volatiles.FLINCH)) {
            pre.add(new GameEvent.VolatileSet(b.battleId(), a.id(), Volatiles.FLINCH, 0, null));
            pre.add(new GameEvent.BattleNoted(b.battleId(), who + " flinched and couldn't move!"));
            return false;
        }
        if (a.condition().has(Volatiles.CONFUSION)) {
            int left = a.condition().volatileValue(Volatiles.CONFUSION) - 1;
            if (left <= 0) {
                pre.add(new GameEvent.VolatileSet(b.battleId(), a.id(), Volatiles.CONFUSION, 0, who + " snapped out of its confusion!"));
            } else {
                pre.add(new GameEvent.VolatileSet(b.battleId(), a.id(), Volatiles.CONFUSION, left, who + " is confused!"));
                if (chance("confused", "hurting itself in its confusion", 1.0 / 3.0)) {
                    pre.add(new GameEvent.BattleNoted(b.battleId(), "It hurt itself in its confusion!"));
                    int damage = Damage.compute(new Damage.Input(a.level(), 8, Math.max(1, stat(a, "atk")), Math.max(1, stat(a, "def")),
                            a.condition().stage("atk"), a.condition().stage("def"), false, false, 1.0, false, 1.0), combat).damage();
                    pre.add(new GameEvent.HpChanged(b.battleId(), a.id(), -Math.min(damage, a.hp()), "from the confusion"));
                    return false;
                }
            }
        }
        if (a.condition().has(Volatiles.ATTRACT) && chance("love", "being in love", 0.5)) {
            pre.add(new GameEvent.BattleNoted(b.battleId(), who + " is immobilized by love!"));
            return false;
        }
        if (a.status() == Status.PARALYSIS && chance("paralysis", "paralysis", 0.25)) {
            pre.add(new GameEvent.BattleNoted(b.battleId(), who + " is fully paralyzed!"));
            return false;
        }
        return true;
    }

    /** What has to be tidied when a Pokémon could not move: a Rollout lock ends, and its last move counts as failed. */
    private void afterPrevented() {
        Combatant a = cur(actor.id());
        if (a.fainted()) {
            return;
        }
        if (a.condition().has(Volatiles.ROLLOUT)) {
            vol(a, Volatiles.ROLLOUT, 0, null);
            vol(a, Volatiles.ROLLOUT_HITS, 0, null);
        }
        if (!b.combatant(actor.id()).condition().has(Volatiles.RECHARGE)) {
            vol(a, Volatiles.LAST_FAILED, 1, null);
        }
    }

    static boolean uproarOnField(Battle battle) {
        return battle.standing().stream().anyMatch(c -> c.condition().has(Volatiles.UPROAR));
    }

    // ================================================================================================================= the move

    /** What a move turned out to be for this use: scripts may change its type, category, power and side effects. */
    static final class Spec {
        String id;
        String name;
        MoveProfile p;
        JsonNode effect;
        String failure;
        TargetKind kind;
        boolean ignoreBurnPenalty;
    }

    /** One Pokémon a move is aimed at, and what happens to it. */
    static final class Strike {
        final Combatant target;
        HitRoll.Result hit = HitRoll.Result.HIT;
        String blocked;
        double eff = 1.0;
        int damage;
        int absorbedBySubstitute;
        int hits = 1;
        boolean endured;

        Strike(Combatant target) {
            this.target = target;
        }

        boolean landed() {
            return blocked == null && (hit == HitRoll.Result.HIT || hit == HitRoll.Result.CRIT);
        }
    }

    /** One side effect of a move: a chance of a status, a volatile, or stat changes on the target or on the user. */
    private record Side(double chance, Status status, String volatileStatus, Map<String, Integer> boosts, Map<String, Integer> selfBoosts,
                        String selfVolatile, int index) {
    }

    /**
     * Plans a move: asks every roll it needs (this part may {@link Waiting}), and returns the code that adds its outcome.
     *
     * @param prefix   the start of the keys of its rolls ({@code call-} for a move that Sleep Talk calls)
     * @param chosen   the Pokémon the player aimed at (empty for a group, the user, or a called move)
     * @param called   true when another move called this one (it spends no PP and uses a random target)
     */
    private Runnable plan(String moveId, String prefix, List<String> chosen, boolean called) {
        if (!called && chosen.size() == 1 && Battle.isTrainerTarget(chosen.get(0))) {
            return trainerAttack(moveId, chosen.get(0));
        }
        MoveEntry entry = book.entry(moveId).orElse(null);
        MoveProfile base = book.profile(moveId).orElse(null);
        String name = entry != null ? entry.name() : slotName(moveId);
        MoveBook.Handling handling = entry == null ? MoveBook.Handling.MANUAL : book.handling(entry);
        if (entry == null || base == null || handling == MoveBook.Handling.MANUAL) {
            return () -> manual(moveId, name, entry);
        }
        Spec s = new Spec();
        s.id = moveId;
        s.name = name;
        s.p = base;
        s.effect = entry.effect() == null ? NOTHING : entry.effect();
        s.kind = called ? TargetKind.parse(entry.target()) : slotKind(moveId, entry);
        shape(s);
        if (s.failure == null) {
            s.failure = failure(s, prefix);
        }
        if (s.failure == null) {
            s.failure = AbilityScripts.moveFailure(b, actor, s.name, s.effect);
        }
        if (s.failure != null) {
            return () -> failed(s);
        }
        List<Combatant> targets = targetsOf(s, chosen, called, prefix);
        if (targets.isEmpty() && s.kind != TargetKind.SIDE) {
            return () -> {
                note("But " + b.label(actor) + "'s " + s.name + " has no target left.");
                setLastFailed(true);
            };
        }
        // the moves that act through a script of their own
        Runnable special = special(s, targets, prefix);
        if (special != null) {
            return special;
        }
        return strike(s, entry, handling, targets, prefix);
    }

    private Runnable strike(Spec s, MoveEntry entry, MoveBook.Handling handling, List<Combatant> targets, String prefix) {
        MoveProfile p = s.p;
        JsonNode effect = s.effect;
        PokemonType type = p.type();
        List<Strike> strikes = new ArrayList<>();
        for (Combatant t : targets) {
            Strike x = new Strike(t);
            if (t.id().equals(actor.id())) {
                strikes.add(x);
                continue;
            }
            if (t.condition().has(Volatiles.PROTECT) && effect.path("flags").path("protect").asBoolean(false)
                    && !effect.path("breaksProtect").asBoolean(false)) {
                x.blocked = "protected";
            } else if (p.damaging() || effect.path("ignoreImmunity").isBoolean() && !effect.path("ignoreImmunity").asBoolean()) {
                x.eff = effectiveness(type, t, MoveSlot.STRUGGLE.equals(s.id));
                if (x.eff == 0.0 && !effect.path("ignoreImmunity").asBoolean(false)) {
                    x.blocked = "immune";
                }
            }
            if (x.blocked == null) {
                x.blocked = AbilityScripts.blocks(b, t, p);
            }
            strikes.add(x);
        }
        // ---- the hit rolls: one roll for the targets that have the same odds
        List<HitRoll.Odds> groups = new ArrayList<>();
        for (Strike x : strikes) {
            if (x.blocked != null || x.target.id().equals(actor.id())) {
                continue;
            }
            HitRoll.Odds odds = oddsFor(s, x.target);
            int group = -1;
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).choice().equals(odds.choice())) {
                    group = i;
                }
            }
            if (group < 0) {
                groups.add(odds);
                group = groups.size() - 1;
            }
            HitRoll.Odds shared = groups.get(group);
            if (!shared.rolled()) {
                x.hit = shared.certain();
            } else {
                int number = group;
                int total = roll(prefix + "hit" + number, id -> RollRequests.smart(id, trainerId(), s.name + ": hit", shared.choice(), shared.hitChance(),
                        hitModifiers(s, x.target), rollClass(), owner.userId(), rollSettings.showTrueChance()));
                x.hit = shared.resolve(total);
            }
            if (!p.damaging() && x.hit == HitRoll.Result.CRIT) {
                x.hit = HitRoll.Result.HIT;
            }
        }
        boolean anyLanded = strikes.stream().anyMatch(Strike::landed);
        // ---- how many times a multi-hit move hits
        int hits = 1;
        if (anyLanded && p.damaging() && effect.has("multihit")) {
            hits = hitCount(s, effect.get("multihit"), prefix);
        }
        // ---- the damage dice
        int rolls = combat.damageRolls(hits);
        int[] totals = new int[rolls];
        boolean needsDice = p.usesDamageDice() && anyLanded && strikes.stream().anyMatch(x -> x.landed() && !x.target.id().equals(actor.id()));
        if (needsDice) {
            Dice dice = p.damageDice(combat).orElseThrow();
            for (int i = 0; i < rolls; i++) {
                int number = i;
                totals[i] = roll(prefix + "dmg" + i, id -> RollRequests.dice(id, trainerId(), s.name + (rolls > 1 ? ": damage of hit " + (number + 1) : ": damage"),
                        dice, rollClass(), owner.userId()));
            }
        }
        // ---- the damage of every target
        for (Strike x : strikes) {
            if (!x.landed() || x.target.id().equals(actor.id())) {
                continue;
            }
            x.hits = hits;
            computeDamage(s, x, totals);
        }
        // ---- side effects: their chance rolls and the durations that follow
        List<Side> sides = sides(s, handling);
        boolean[] happens = new boolean[sides.size()];
        for (Side side : sides) {
            boolean eligible = side.chance() <= 0 ? false : sideEligible(s, side, strikes);
            if (!eligible) {
                continue;
            }
            happens[side.index()] = side.chance() >= 1.0 || chance(prefix + "side" + side.index(), s.name + ": " + sideName(side), side.chance());
        }
        Map<String, Integer> durations = new LinkedHashMap<>();
        for (Side side : sides) {
            if (!happens[side.index()]) {
                continue;
            }
            for (Strike x : strikes) {
                if (!targetsSide(s, side) || !sideReaches(s, side, x)) {
                    continue;
                }
                if (side.status() == Status.SLEEP && canGetStatus(x.target, Status.SLEEP)) {
                    durations.put(side.index() + "-" + x.target.id(), sleepTurns(prefix + "slp" + side.index() + "-" + x.target.id(), s.name + ": how long it sleeps"));
                }
                if (Volatiles.CONFUSION.equals(side.volatileStatus()) && !x.target.condition().has(Volatiles.CONFUSION)) {
                    durations.put(side.index() + "-" + x.target.id(), confusionTurns(prefix + "conf" + side.index() + "-" + x.target.id(), s.name + ": how long it is confused"));
                }
            }
        }
        int hitsFinal = hits;
        return () -> applyStrike(s, entry, handling, strikes, sides, happens, durations, hitsFinal);
    }

    // ====================================================================================================== the outcome of a move

    private void applyStrike(Spec s, MoveEntry entry, MoveBook.Handling handling, List<Strike> strikes, List<Side> sides, boolean[] happens,
                             Map<String, Integer> durations, int hits) {
        MoveProfile p = s.p;
        JsonNode effect = s.effect;
        boolean selfTarget = s.kind == TargetKind.SELF;
        boolean anyLanded = strikes.stream().anyMatch(x -> x.landed() || (selfTarget && x.target.id().equals(actor.id())));
        int dealt = 0;
        // consume Helping Hand
        if (actor.condition().has(Volatiles.HELPING_HAND)) {
            vol(cur(actor.id()), Volatiles.HELPING_HAND, 0, null);
        }
        for (Strike x : strikes) {
            Combatant t = cur(x.target.id());
            String tl = b.label(t);
            if (x.blocked != null) {
                switch (x.blocked) {
                    case "immune" -> note("It doesn't affect " + tl + "...");
                    case "sturdy" -> note("It doesn't affect " + tl + ", thanks to its Sturdy!");
                    case "waterabsorb" -> {
                        note(tl + "'s Water Absorb made the attack useless!");
                        hp(t, Math.max(1, t.maxHp() / 4), "from Water Absorb");
                    }
                    case "flashfire" -> {
                        note(tl + "'s Flash Fire made the attack useless!");
                        vol(t, Volatiles.FLASH_FIRE, 1, tl + "'s Fire moves are stronger!");
                    }
                    default -> note(tl + " protected itself!");
                }
                continue;
            }
            if (t.id().equals(actor.id())) {
                continue;
            }
            if (x.hit == HitRoll.Result.MISS) {
                note(p.ohko() ? b.label(actor) + "'s attack missed " + tl + "! (a one-hit KO needs luck and a higher level)" : b.label(actor) + "'s attack missed " + tl + "!");
                continue;
            }
            if (!p.damaging()) {
                continue;
            }
            if (x.hit == HitRoll.Result.CRIT) {
                note("A critical hit!");
            }
            if (x.eff > 1.0) {
                note("It's super effective!");
            } else if (x.eff < 1.0 && x.eff > 0.0) {
                note("It's not very effective...");
            }
            int real = dealDamage(s, x, t);
            dealt += real;
            if (hits > 1 && x.damage > 0) {
                note("Hit " + hits + " times!");
            }
            Combatant hit = cur(t.id());
            if (!hit.fainted() && real > 0 && x.absorbedBySubstitute == 0 && AbilityScripts.weakArmorTriggers(b, hit, p, real)) {
                stage(hit, "def", -1);
                stage(hit, "spe", 2);
            }
        }
        // thaw a frozen target that was hit by fire
        for (Strike x : strikes) {
            Combatant t = cur(x.target.id());
            if (x.landed() && p.damaging() && !t.fainted() && t.status() == Status.FREEZE && x.absorbedBySubstitute == 0
                    && (p.type() == PokemonType.FIRE || effect.path("thawsTarget").asBoolean(false))) {
                status(t, Status.NONE, 0, null);
            }
        }
        boolean failedAll = !anyLanded && !selfTarget;
        int affected = 0;
        // side effects on the targets and on the user
        for (Side side : sides) {
            if (!happens[side.index()]) {
                continue;
            }
            for (Strike x : strikes) {
                if (!targetsSide(s, side) || !sideReaches(s, side, x)) {
                    continue;
                }
                Combatant t = cur(x.target.id());
                if (t.fainted()) {
                    continue;
                }
                if (side.status() != null && canGetStatus(t, side.status())) {
                    Integer turns = durations.get(side.index() + "-" + t.id());
                    status(t, side.status(), side.status() == Status.SLEEP && turns != null ? turns : 0, null);
                    affected++;
                }
                if (Volatiles.FLINCH.equals(side.volatileStatus()) && b.queue().contains(t.id()) && !t.condition().has(Volatiles.FLINCH)) {
                    vol(t, Volatiles.FLINCH, 1, null);
                }
                if (Volatiles.CONFUSION.equals(side.volatileStatus()) && !t.condition().has(Volatiles.CONFUSION)) {
                    Integer turns = durations.get(side.index() + "-" + t.id());
                    vol(t, Volatiles.CONFUSION, turns == null ? 3 : turns, b.label(t) + " became confused!");
                    affected++;
                }
                for (Map.Entry<String, Integer> bo : side.boosts().entrySet()) {
                    stage(t, bo.getKey(), bo.getValue());
                    affected++;
                }
            }
            if (anyLanded || selfTarget) {
                for (Map.Entry<String, Integer> bo : side.selfBoosts().entrySet()) {
                    stage(cur(actor.id()), bo.getKey(), bo.getValue());
                    affected++;
                }
                if (side.selfVolatile() != null && book.runsCondition("volatileStatus:" + side.selfVolatile())) {
                    vol(cur(actor.id()), side.selfVolatile(), 1, null);
                }
            }
        }
        // stealing stat boosts
        if (effect.path("stealsBoosts").asBoolean(false)) {
            for (Strike x : strikes) {
                Combatant t = cur(x.target.id());
                if (x.landed() && !t.fainted()) {
                    for (String stat : Battle.STAGE_IDS) {
                        int st = t.condition().stage(stat);
                        if (st > 0) {
                            stage(t, stat, -st);
                            stage(cur(actor.id()), stat, st);
                        }
                    }
                }
            }
        }
        // healing: a move that heals its target (the user for a self move)
        if (effect.has("heal")) {
            boolean any = false;
            for (Strike x : strikes) {
                Combatant t = cur(x.target.id());
                if (!t.fainted() && (x.landed() || t.id().equals(actor.id())) && t.hp() < t.maxHp()) {
                    int amount = Math.max(1, t.maxHp() * effect.get("heal").get(0).asInt() / effect.get("heal").get(1).asInt());
                    hp(t, amount, null);
                    any = true;
                }
            }
            if (!any) {
                note("But it failed!");
                failedAll = true;
            }
        }
        // drain and recoil from the damage that was really done
        Combatant user = cur(actor.id());
        if (dealt > 0 && effect.has("drain") && !user.fainted() && user.hp() < user.maxHp()) {
            int amount = Math.max(1, dealt * effect.get("drain").get(0).asInt() / effect.get("drain").get(1).asInt());
            hp(user, amount, "from the energy it drained");
        }
        if (dealt > 0 && effect.has("recoil") && !user.fainted()) {
            int amount = Math.max(1, dealt * effect.get("recoil").get(0).asInt() / effect.get("recoil").get(1).asInt());
            hp(user, -amount, "from the recoil");
        }
        if (MoveSlot.STRUGGLE.equals(s.id) && !user.fainted()) {
            hp(user, -Math.max(1, user.maxHp() / 4), "from the recoil");
        }
        // held items that a damaging move costs or uses up: Life Orb's recoil (not after a Sheer Force move) and a type gem
        user = cur(actor.id());
        if (p.damaging() && dealt > 0 && !user.fainted() && !AbilityScripts.sheerForceActive(b, actor, effect)) {
            int orb = ItemScripts.lifeOrbRecoil(b, user);
            if (orb > 0) {
                hp(user, -Math.min(orb, user.hp()), "from its Life Orb");
            }
        }
        user = cur(actor.id());
        if (p.damaging() && anyLanded && !user.fainted() && ItemScripts.holdsGemOf(b, user, p.type())) {
            item(user, null, b.label(actor) + "'s " + BattleReducer.pretty(user.condition().heldItem()) + " boosted the power of the move!");
        }
        // jumping out and forcing out
        user = cur(actor.id());
        if (!user.fainted() && effect.path("selfSwitch").asBoolean(false) && (anyLanded || p.damaging() == false) && hasAbleReserve(owner)) {
            toSwitch(user);
        }
        if (effect.path("forceSwitch").asBoolean(false)) {
            for (Strike x : strikes) {
                Combatant t = cur(x.target.id());
                if (x.landed() && !t.fainted() && hasAbleReserve(b.ownerOf(t)) && !b.mustSwitch().contains(t.id())) {
                    forceOut(t, s.id);
                } else if (x.landed() && !t.fainted()) {
                    note("But it failed!");
                }
            }
        }
        // self-destruction
        String boom = effect.path("selfdestruct").asText("");
        if (!boom.isEmpty() && (boom.equals("always") || anyLanded)) {
            Combatant u = cur(actor.id());
            if (!u.fainted()) {
                hp(u, -u.hp(), "as it blew itself up");
            }
        }
        // a status move that changed nothing (already asleep, a Fire type that cannot be burned, a stat at its limit is still a change)
        boolean mainEffect = !p.damaging() && !effect.has("heal") && sides.stream().anyMatch(sd -> sd.chance() >= 1.0 && targetsSide(s, sd));
        if (mainEffect && anyLanded && affected == 0 && !selfTarget) {
            note("But it failed!");
            failedAll = true;
        }
        extras(s, strikes, anyLanded);
        if (handling == MoveBook.Handling.PARTIAL) {
            openTask(s.id, entry.name() + ": " + entry.description());
        }
        berries(strikes);
        finish(s, failedAll);
    }

    /** Applies the damage of one target: substitute, endure, the HP. Returns the damage really done (for drain and recoil). */
    private int dealDamage(Spec s, Strike x, Combatant t) {
        int damage = x.damage;
        if (damage <= 0) {
            return 0;
        }
        if (t.condition().has(Volatiles.SUBSTITUTE) && !s.effect.path("flags").path("bypasssub").asBoolean(false)
                && !AbilityScripts.bypassesSubstitute(b, actor)) {
            int decoy = t.condition().volatileValue(Volatiles.SUBSTITUTE);
            int taken = Math.min(damage, decoy);
            x.absorbedBySubstitute = taken;
            if (taken >= decoy) {
                vol(t, Volatiles.SUBSTITUTE, 0, b.label(t) + "'s substitute faded!");
            } else {
                vol(t, Volatiles.SUBSTITUTE, decoy - taken, b.label(t) + "'s substitute took the hit!");
            }
            return taken;
        }
        if (t.condition().has(Volatiles.ENDURE) && damage >= t.hp()) {
            damage = Math.max(0, t.hp() - 1);
            x.endured = true;
            note(b.label(t) + " endured the hit!");
        } else if (damage >= t.hp() && t.hp() == t.maxHp() && b.hasAbility(t, "sturdy")) {
            damage = Math.max(0, t.hp() - 1);
            x.endured = true;
            note(b.label(t) + " endured the hit with Sturdy!");
        } else if (ItemScripts.sashSaves(b, t, damage)) {
            damage = Math.max(0, t.hp() - 1);
            x.endured = true;
            item(t, null, b.label(t) + " hung on using its Focus Sash!");
        }
        int real = Math.min(damage, t.hp());
        if (real > 0) {
            hp(t, -real, null);
        }
        return real;
    }

    /** Works out how much damage the move does to one target (the number only; {@link #dealDamage} applies it). */
    private void computeDamage(Spec s, Strike x, int[] totals) {
        MoveProfile p = s.p;
        JsonNode effect = s.effect;
        Combatant a = actor;
        Combatant t = x.target;
        if (!p.damaging() || x.eff == 0.0) {
            return;
        }
        if ("endeavor".equals(s.id)) {
            x.damage = Math.max(0, t.hp() - a.hp());
            return;
        }
        if (p.ohko()) {
            x.damage = t.hp();
            return;
        }
        if (p.fixedDamage().isPresent()) {
            x.damage = Damage.fixed(p.fixedDamage().get(), a.level(), x.eff);
            return;
        }
        if (!p.usesDamageDice()) {
            return;
        }
        boolean physical = p.category() == MoveProfile.Category.PHYSICAL;
        String offKey = effect.has("overrideOffensiveStat") ? effect.get("overrideOffensiveStat").asText() : physical ? "atk" : "spa";
        Combatant offSource = "target".equals(effect.path("overrideOffensivePokemon").asText("")) ? t : a;
        String defKey = effect.has("overrideDefensiveStat") ? effect.get("overrideDefensiveStat").asText() : physical ? "def" : "spd";
        boolean crit = x.hit == HitRoll.Result.CRIT;
        int defenseStage = effect.path("ignoreDefensive").asBoolean(false) ? 0 : t.condition().stage(defKey);
        boolean stab = !MoveSlot.STRUGGLE.equals(s.id) && a.types().contains(p.type());
        boolean burned = physical && a.status() == Status.BURN && !s.ignoreBurnPenalty && !AbilityScripts.ignoresBurn(b, a);
        double other = otherFactor(s, t);
        int total = 0;
        for (int k = 0; k < x.hits; k++) {
            int dice = totals[Math.min(k, totals.length - 1)];
            Damage.Input in = new Damage.Input(a.level(), dice, Math.max(1, stat(offSource, offKey)), Math.max(1, stat(t, defKey)),
                    offSource.condition().stage(offKey), defenseStage, crit, stab, x.eff, burned, other);
            total += Damage.compute(in, combat).damage();
        }
        x.damage = total;
    }

    private double otherFactor(Spec s, Combatant t) {
        double f = 1.0;
        if (actor.condition().has(Volatiles.HELPING_HAND)) {
            f *= 1.5;
        }
        if ("knockoff".equals(s.id) && t.condition().heldItem() != null) {
            f *= 1.5;
        }
        boolean physical = s.p.category() == MoveProfile.Category.PHYSICAL;
        PokemonType type = s.p.type();
        f *= AbilityScripts.attackFactor(b, actor, type, physical, AbilityScripts.sheerForceActive(b, actor, s.effect));
        f *= AbilityScripts.defenseFactor(b, t, type);
        f *= ItemScripts.damageFactor(b, actor, type, physical);
        return f;
    }

    // ================================================================================================================ hit odds

    private HitRoll.Odds oddsFor(Spec s, Combatant t) {
        MoveProfile p = s.p;
        if (p.ohko()) {
            return HitRoll.oneHitKo(actor.level(), t.level(), book.dice());
        }
        int evasion = s.effect.path("ignoreEvasion").asBoolean(false) || AbilityScripts.ignoresEvasion(b, actor) ? 0 : t.condition().stage("evasion");
        HitRoll.Input in = new HitRoll.Input(AbilityScripts.accuracy(b, actor, p), actor.condition().stage("accuracy"), evasion,
                bonuses.accuracyPp(owner.id()), 0, p.critStage() + (actor.condition().has(Volatiles.FOCUS_ENERGY) ? 2 : 0), p.alwaysCrit());
        if (!p.damaging()) {
            return HitRoll.compute(in.withCritStage(0).withAlwaysCrit(false), combat.withCritChancesPct(List.of(0, 0, 0, 0)), book.dice());
        }
        return HitRoll.compute(in, combat, book.dice());
    }

    private List<RollModifier> hitModifiers(Spec s, Combatant t) {
        List<RollModifier> list = new ArrayList<>();
        int evasion = s.effect.path("ignoreEvasion").asBoolean(false) || AbilityScripts.ignoresEvasion(b, actor) ? 0 : t.condition().stage("evasion");
        int net = actor.condition().stage("accuracy") - evasion;
        if (net != 0) {
            list.add(new RollModifier(RollModifier.Kind.STAGE, "accuracy stage", net));
        }
        double bonus = bonuses.accuracyPp(owner.id());
        if (bonus != 0) {
            list.add(new RollModifier(RollModifier.Kind.POINTS, "command bonus", bonus));
        }
        return list;
    }

    private int hitCount(Spec s, JsonNode multihit, String prefix) {
        if (multihit.isInt()) {
            return multihit.asInt();
        }
        int min = multihit.get(0).asInt();
        int max = multihit.get(1).asInt();
        List<SmartDice.Outcome> outcomes = new ArrayList<>();
        if (min == 2 && max == 5) {
            outcomes.add(new SmartDice.Outcome("2 hits", 0.35));
            outcomes.add(new SmartDice.Outcome("3 hits", 0.35));
            outcomes.add(new SmartDice.Outcome("4 hits", 0.15));
            outcomes.add(new SmartDice.Outcome("5 hits", 0.15));
        } else {
            for (int n = min; n <= max; n++) {
                outcomes.add(new SmartDice.Outcome(n + " hits", 1.0 / (max - min + 1)));
            }
        }
        DiceChoice choice = SmartDice.choose(book.dice(), outcomes);
        int total = roll(prefix + "hits", id -> RollRequests.smart(id, trainerId(), s.name + ": number of hits", choice, null, List.of(), rollClass(),
                owner.userId(), rollSettings.showTrueChance()));
        for (int i = 0; i < choice.bands().size(); i++) {
            DiceChoice.Band band = choice.bands().get(i);
            if (band.faces() > 0 && total >= band.from() && total <= band.to()) {
                return min + i;
            }
        }
        throw new IllegalStateException("No band covers " + total + " for the number of hits");
    }

    // ============================================================================================================= side effects

    private List<Side> sides(Spec s, MoveBook.Handling handling) {
        JsonNode effect = s.effect;
        List<Side> list = new ArrayList<>();
        int i = 0;
        // the main effect of a status move: a status, confusion or stat changes, with no chance
        Status primary = Status.fromId(effect.path("status").asText(""));
        String vs = effect.path("volatileStatus").asText("");
        boolean knownVolatile = Volatiles.CONFUSION.equals(vs) || Volatiles.FLINCH.equals(vs);
        Map<String, Integer> boosts = boostMap(effect.path("boosts"));
        if (primary != null || knownVolatile || !boosts.isEmpty()) {
            Map<String, Integer> onTarget = s.kind == TargetKind.SELF ? Map.of() : boosts;
            Map<String, Integer> onSelf = s.kind == TargetKind.SELF ? boosts : Map.of();
            list.add(new Side(1.0, primary, knownVolatile ? vs : null, onTarget, onSelf, null, i++));
        }
        // the self effects: Close Combat's stat drops, a recharge turn
        JsonNode self = effect.path("self");
        String selfVolatile = self.path("volatileStatus").asText("");
        Map<String, Integer> selfBoosts = new LinkedHashMap<>(boostMap(self.path("boosts")));
        selfBoosts.putAll(boostMap(effect.path("selfBoost").path("boosts")));
        if (!selfBoosts.isEmpty() || !selfVolatile.isEmpty()) {
            double chance = self.has("chance") ? self.get("chance").asInt() / 100.0 : 1.0;
            list.add(new Side(chance, null, null, Map.of(), selfBoosts, selfVolatile.isEmpty() ? null : selfVolatile, i++));
        }
        // the secondary effects
        List<JsonNode> secondaries = new ArrayList<>();
        boolean sheerForce = AbilityScripts.sheerForceActive(b, actor, effect); // Sheer Force trades the secondary effects for power
        if (effect.path("secondary").isObject() && !sheerForce) {
            secondaries.add(effect.get("secondary"));
        }
        if (effect.path("secondaries").isArray() && !sheerForce) {
            effect.get("secondaries").forEach(secondaries::add);
        }
        for (JsonNode sec : secondaries) {
            double chance = sec.has("chance") ? sec.get("chance").asInt() / 100.0 : 1.0;
            Status st = Status.fromId(sec.path("status").asText(""));
            String secVs = sec.path("volatileStatus").asText("");
            String known = Volatiles.CONFUSION.equals(secVs) || Volatiles.FLINCH.equals(secVs) ? secVs : null;
            list.add(new Side(chance, st, known, boostMap(sec.path("boosts")), boostMap(sec.path("self").path("boosts")), null, i++));
        }
        return list;
    }

    private static Map<String, Integer> boostMap(JsonNode node) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (Battle.STAGE_IDS.contains(e.getKey()) && e.getValue().asInt() != 0) {
                    map.put(e.getKey(), e.getValue().asInt());
                }
            }
        }
        return map;
    }

    private boolean targetsSide(Spec s, Side side) {
        return side.status() != null || side.volatileStatus() != null || !side.boosts().isEmpty();
    }

    /** True if a side effect can reach this target at all (it was hit, is alive after the damage, no decoy took the hit). */
    private boolean sideReaches(Spec s, Side side, Strike x) {
        if (s.kind == TargetKind.SELF) {
            return x.target.id().equals(actor.id());
        }
        if (!x.landed() || x.target.id().equals(actor.id())) {
            return false;
        }
        if (x.target.hp() - x.damage <= 0 && s.p.damaging() && x.absorbedBySubstitute == 0 && !x.endured) {
            return false;
        }
        boolean shielded = x.target.condition().has(Volatiles.SUBSTITUTE) && !s.effect.path("flags").path("bypasssub").asBoolean(false)
                && !AbilityScripts.bypassesSubstitute(b, actor);
        if (shielded) {
            return false;
        }
        if (Volatiles.FLINCH.equals(side.volatileStatus()) && AbilityScripts.preventsFlinch(b, x.target)
                || Volatiles.CONFUSION.equals(side.volatileStatus()) && AbilityScripts.preventsConfusion(b, x.target)) {
            return false;
        }
        if (side.status() != null && !canGetStatus(x.target, side.status())) {
            return false;
        }
        return true;
    }

    private boolean sideEligible(Spec s, Side side, List<Strike> strikes) {
        if (!side.selfBoosts().isEmpty() || side.selfVolatile() != null) {
            if (s.kind == TargetKind.SELF) {
                return true;
            }
            if (strikes.stream().anyMatch(Strike::landed)) {
                return true;
            }
        }
        if (!targetsSide(s, side)) {
            return false;
        }
        return strikes.stream().anyMatch(x -> sideReaches(s, side, x));
    }

    private String sideName(Side side) {
        if (side.status() != null) {
            return "chance of " + switch (side.status()) {
                case BURN -> "a burn";
                case POISON -> "poison";
                case TOXIC -> "bad poison";
                case PARALYSIS -> "paralysis";
                case SLEEP -> "sleep";
                case FREEZE -> "freezing";
                case NONE -> "no status";
            };
        }
        if (side.volatileStatus() != null) {
            return "chance of " + side.volatileStatus();
        }
        return "chance of a stat change";
    }

    private boolean canGetStatus(Combatant t, Status status) {
        if (t.fainted() || t.status() != Status.NONE) {
            return false;
        }
        if (status == Status.SLEEP && uproarOnField(b)) {
            return false;
        }
        List<PokemonType> types = t.types();
        return switch (status) {
            case BURN -> !types.contains(PokemonType.FIRE);
            case POISON, TOXIC -> !types.contains(PokemonType.POISON) && !types.contains(PokemonType.STEEL);
            case PARALYSIS -> !types.contains(PokemonType.ELECTRIC);
            case FREEZE -> !types.contains(PokemonType.ICE);
            default -> true;
        };
    }

    /** d6: 1–2 = 1 turn, 3–4 = 2 turns, 5–6 = 3 turns (chapter 5.6). */
    private int sleepTurns(String key, String reason) {
        DiceChoice choice = SmartDice.choose(book.dice(), List.of(new SmartDice.Outcome("1 turn", 1.0 / 3), new SmartDice.Outcome("2 turns", 1.0 / 3),
                new SmartDice.Outcome("3 turns", 1.0 / 3)));
        int total = roll(key, id -> RollRequests.smart(id, trainerId(), reason, choice, null, List.of(), rollClass(), owner.userId(), rollSettings.showTrueChance()));
        return bandIndex(choice, total) + 1;
    }

    /** d4 + 1: confusion lasts 2 to 5 turns (chapter 5.6). */
    private int confusionTurns(String key, String reason) {
        DiceChoice choice = SmartDice.choose(book.dice(), List.of(new SmartDice.Outcome("2 turns", 0.25), new SmartDice.Outcome("3 turns", 0.25),
                new SmartDice.Outcome("4 turns", 0.25), new SmartDice.Outcome("5 turns", 0.25)));
        int total = roll(key, id -> RollRequests.smart(id, trainerId(), reason, choice, null, List.of(), rollClass(), owner.userId(), rollSettings.showTrueChance()));
        return bandIndex(choice, total) + 2;
    }

    private static int bandIndex(DiceChoice choice, int total) {
        for (int i = 0; i < choice.bands().size(); i++) {
            DiceChoice.Band band = choice.bands().get(i);
            if (band.faces() > 0 && total >= band.from() && total <= band.to()) {
                return i;
            }
        }
        throw new IllegalStateException("No band covers " + total);
    }

    // ============================================================================================================== special moves

    /** Moves that need more than the standard path. Null for an ordinary move. */
    private Runnable special(Spec s, List<Combatant> targets, String prefix) {
        return MoveScripts.special(this, s, targets, prefix);
    }

    private void shape(Spec s) {
        MoveScripts.shape(this, s);
    }

    private String failure(Spec s, String prefix) {
        return MoveScripts.failure(this, s, prefix);
    }

    private void extras(Spec s, List<Strike> strikes, boolean anyLanded) {
        MoveScripts.extras(this, s, strikes, anyLanded);
    }

    // ================================================================================================================== finishing

    private void failed(Spec s) {
        note(s.failure);
        setLastFailed(true);
        if (!isStalling(s.id) && cur(actor.id()).condition().has(Volatiles.STALL)) {
            vol(cur(actor.id()), Volatiles.STALL, 0, null);
        }
    }

    private void manual(String moveId, String name, MoveEntry entry) {
        String text = entry != null && entry.description() != null && !entry.description().isBlank() ? entry.description()
                : "the effect of " + name + " is not known to the game";
        openTask(moveId, name + ": " + text);
        setLastFailed(false);
    }

    private void openTask(String moveId, String text) {
        String id = b.battleId() + "-r" + b.round() + "-" + actor.id() + "-task-" + moveId;
        GameEvent.AdminTaskOpened e = new GameEvent.AdminTaskOpened(b.battleId(), id, actor.id(), moveId, "Apply " + text);
        if (work.battle().tasks().stream().noneMatch(t -> t.id().equals(id))) {
            effect(e);
        }
    }

    /** What every used move leaves behind: whether it failed (Stomping Tantrum), and the end of a Protect streak. */
    void finish(Spec s, boolean failed) {
        setLastFailed(failed);
        Combatant a = cur(actor.id());
        if (!a.fainted() && !isStalling(s.id) && a.condition().has(Volatiles.STALL)) {
            vol(a, Volatiles.STALL, 0, null);
        }
    }

    private static boolean isStalling(String id) {
        return id.equals("protect") || id.equals("endure");
    }

    private void setLastFailed(boolean failed) {
        Combatant a = cur(actor.id());
        if (!a.fainted() && a.condition().has(Volatiles.LAST_FAILED) != failed) {
            vol(a, Volatiles.LAST_FAILED, failed ? 1 : 0, null);
        }
    }

    // =================================================================================================================== helpers

    Combatant cur(String id) {
        return work.battle().combatant(id);
    }

    Battle start() {
        return b;
    }

    Combatant actor() {
        return actor;
    }

    boolean awake() {
        return awake;
    }

    MoveBook book() {
        return book;
    }

    CombatSettings combat() {
        return combat;
    }

    Participant owner() {
        return owner;
    }

    private String slotName(String moveId) {
        return actor.moves().stream().filter(m -> m.moveId().equals(moveId)).map(MoveSlot::name).findFirst().orElse(BattleReducer.pretty(moveId));
    }

    private TargetKind slotKind(String moveId, MoveEntry entry) {
        return actor.moves().stream().filter(m -> m.moveId().equals(moveId)).map(MoveSlot::targetKind).findFirst()
                .orElse(TargetKind.parse(entry.target()));
    }

    private double effectiveness(PokemonType type, Combatant target, boolean typeless) {
        if (typeless || target.types().isEmpty()) {
            return 1.0;
        }
        return book.chart().effectiveness(type, target.types());
    }

    /** The Pokémon a move reaches, standing on the field now. */
    private List<Combatant> targetsOf(Spec s, List<String> chosen, boolean called, String prefix) {
        TargetKind kind = s.kind;
        List<Combatant> list = new ArrayList<>();
        switch (kind) {
            case SELF -> list.add(actor);
            case SIDE -> {
            }
            case FOE, ALLY, ALLY_OR_SELF -> {
                String id = chosen.isEmpty() ? null : b.resolveTarget(chosen.get(0));
                if (called || id == null) {
                    List<String> legal = b.legalTargets(actor, kind == TargetKind.FOE ? TargetKind.FOE : kind);
                    if (!legal.isEmpty()) {
                        id = legal.get(draw(prefix + "target", legal.size()));
                    }
                }
                Combatant t = id == null ? null : b.combatant(id);
                if (t != null && !t.fainted() && b.isActive(t)) {
                    list.add(t);
                }
            }
            default -> b.groupTargets(actor, kind).forEach(id -> list.add(b.combatant(id)));
        }
        return list;
    }

    private String trainerId() {
        if (owner.trainerId() != null) {
            return owner.trainerId();
        }
        return b.participants().values().stream().map(Participant::trainerId).filter(t -> t != null).findFirst().orElseThrow();
    }

    private RollClass rollClass() {
        return owner.kind() == Kind.TRAINER ? RollClass.NORMAL : RollClass.ENEMY;
    }

    private String requestId(String key) {
        return b.battleId() + "-r" + b.round() + "-" + actor.id() + "-" + key;
    }

    private Integer answer(String id) {
        RollResult last = null;
        for (RollResult r : work.rolls()) {
            if (r.requestId().equals(id)) {
                last = r;
            }
        }
        return last == null ? null : last.total();
    }

    /** The number of a roll: from its answer, or by asking for it and leaving with {@link Waiting}. */
    int roll(String key, Function<String, GameEvent.RollRequested> ask) {
        if (effectsStarted) {
            throw new IllegalStateException("A roll was asked after the effects started: " + key);
        }
        String id = requestId(key);
        Integer got = answer(id);
        if (got != null) {
            return got;
        }
        if (work.pendingRolls().containsKey(id)) {
            throw new Waiting();
        }
        emit(RollDesk.open(work, rollSettings, ask.apply(id), now, seeds));
        got = answer(id);
        if (got != null) {
            return got;
        }
        throw new Waiting();
    }

    /** A yes/no chance as a smart-dice roll made by the owner of the acting Pokémon. */
    boolean chance(String key, String reason, double chance) {
        if (chance >= 1.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        SmartDice.Success success = SmartDice.success(book.dice(), chance);
        if (success.kind() == SmartDice.Kind.ALWAYS) {
            return true;
        }
        if (success.kind() == SmartDice.Kind.NEVER) {
            return false;
        }
        int total = roll(key, id -> RollRequests.smart(id, trainerId(), reason, success.choice(), chance, List.of(), rollClass(), owner.userId(),
                rollSettings.showTrueChance()));
        return total >= success.successFrom() && total <= success.successTo();
    }

    /** A random choice, 0 up to (not including) {@code bound}, made once and kept in the log. */
    int draw(String key, int bound) {
        String purpose = requestId(key);
        for (RandomDraw d : work.draws()) {
            if (d.purpose().equals(purpose)) {
                return d.value();
            }
        }
        int value = (int) Math.floorMod(seeds.getAsLong(), (long) bound);
        emit(List.of(new GameEvent.RandomDrawn(purpose, bound, value)));
        return value;
    }

    private void emit(List<GameEvent> events) {
        work = GameReducer.applyAll(work, events);
        out.addAll(events);
    }

    /** Adds one event of the outcome. After the first one, no roll may be asked. */
    void effect(GameEvent e) {
        effectsStarted = true;
        work = GameReducer.apply(work, e);
        out.add(e);
    }

    void note(String text) {
        effect(new GameEvent.BattleNoted(b.battleId(), text));
    }

    void hp(Combatant c, int delta, String cause) {
        Combatant now = cur(c.id());
        if (delta == 0 || now.fainted()) {
            return;
        }
        if (delta > 0 && now.hp() >= now.maxHp()) {
            return;
        }
        effect(new GameEvent.HpChanged(b.battleId(), c.id(), delta, cause));
    }

    void status(Combatant c, Status status, int turns, String cause) {
        Combatant now = cur(c.id());
        if (now.fainted() || status == Status.NONE && now.status() == Status.NONE || status != Status.NONE && now.status() != Status.NONE) {
            return;
        }
        effect(new GameEvent.StatusSet(b.battleId(), c.id(), status, turns, cause));
    }

    void stage(Combatant c, String stat, int delta) {
        if (cur(c.id()).fainted() || delta == 0) {
            return;
        }
        if (delta < 0 && !c.id().equals(actor.id()) && cur(c.id()).condition().has(Volatiles.GUARD_SPEC)) {
            note(b.label(c) + "'s stats are protected by the mist!");
            return;
        }
        if (AbilityScripts.preventsAccuracyDrop(b, c, stat, delta)) {
            note(b.label(c) + "'s Keen Eye keeps its accuracy from being lowered!");
            return;
        }
        effect(new GameEvent.StageChanged(b.battleId(), c.id(), stat, delta, null));
    }

    void vol(Combatant c, String key, int value, String text) {
        Combatant now = cur(c.id());
        if (now.fainted() && value > 0) {
            return;
        }
        if (now.condition().volatileValue(key) == value && text == null) {
            return;
        }
        effect(new GameEvent.VolatileSet(b.battleId(), c.id(), key, value, text));
    }

    void item(Combatant c, String itemId, String text) {
        effect(new GameEvent.HeldItemSet(b.battleId(), c.id(), itemId, text));
    }

    boolean hasAbleReserve(Participant p) {
        Participant now = work.battle().participants().get(p.id());
        return now.reserve().stream().anyMatch(id -> !cur(id).fainted());
    }

    void toSwitch(Combatant c) {
        effect(new GameEvent.SwitchRequired(b.battleId(), c.id()));
    }

    /** The target is sent back and a Pokémon of its owner's reserve comes in, chosen at random (Roar, Whirlwind, Dragon Tail). */
    private void forceOut(Combatant t, String moveId) {
        Participant p = work.battle().participants().get(t.ownerId());
        List<String> able = p.reserve().stream().filter(id -> !cur(id).fainted()).toList();
        if (able.isEmpty()) {
            return;
        }
        int pick = draw("force-" + t.id(), able.size());
        effect(new GameEvent.SwitchRequired(b.battleId(), t.id()));
        GameEvent heal = AbilityScripts.leaveHeal(work.battle(), cur(t.id()));
        if (heal != null) {
            effect(heal);
        }
        effect(new GameEvent.ReplacementSent(b.battleId(), p.id(), t.id(), able.get(pick)));
        entry(able.get(pick));
    }

    int stat(Combatant c, String key) {
        return switch (key) {
            case "atk" -> c.build().attack();
            case "def" -> c.build().defense();
            case "spa" -> c.build().spAttack();
            case "spd" -> c.build().spDefense();
            case "spe" -> c.build().speed() > 0 ? c.build().speed() : c.speed();
            default -> 0;
        };
    }

    /** The stat with its stage, the way damage reads it. */
    int staged(Combatant c, String key) {
        return Stats.applyStage(stat(c, key), c.condition().stage(key));
    }

    Optional<MoveEntry> entryOf(String id) {
        return book.entry(id);
    }

    // The recursive call for a move that another move calls (Sleep Talk).
    Runnable planCalled(String moveId, String prefix) {
        return plan(moveId, prefix, List.of(), true);
    }
}
