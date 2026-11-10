/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.quimmilho.pokednd.core.combat.MoveProfile;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.ActionKind;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.TargetKind;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.importer.Catalog.AbilityEntry;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * The abilities that Core runs by itself (chapter 7.9, 19.6, phase 47). Every method reads the ability through
 * {@link Battle#abilityOf}, which is {@code null} when {@code combat.abilities.enabled} is off, so with abilities switched off none of
 * this does anything.
 *
 * <p>The list of abilities is {@code rules/implemented-effects.json} ({@code abilities}); {@link #knows} is the same list in code and a
 * test checks that they agree. {@code abilitiesNoEffect} lists the abilities whose only effect is weather or terrain, which are not in the
 * game: they are known and do nothing, so the admin never gets a task for them.
 */
final class AbilityScripts {

    private static final Set<String> IMPLEMENTED = Set.of("sturdy", "keeneye", "intimidate", "innerfocus", "blaze", "overgrow", "torrent", "swarm",
            "sheerforce", "owntempo", "frisk", "thickfat", "gluttony", "infiltrator", "oblivious", "shedskin", "waterabsorb", "flashfire", "guts",
            "unnerve", "hustle", "regenerator", "pressure", "weakarmor", "damp");

    private static final Set<String> NO_EFFECT = Set.of("swiftswim", "chlorophyll", "sandveil", "leafguard", "drizzle", "drought", "sandstream",
            "snowwarning", "sandrush", "slushrush", "sandforce", "raindish", "icebody", "hydration", "solarpower", "snowcloak", "flowergift",
            "forecast", "electricsurge", "grassysurge", "mistysurge", "psychicsurge", "surgesurfer");

    private AbilityScripts() {
    }

    /** True if Core runs this ability. */
    static boolean knows(String id) {
        return IMPLEMENTED.contains(id);
    }

    /** True if the ability only matters in weather or terrain, which the game does not have. */
    static boolean noEffect(String id) {
        return NO_EFFECT.contains(id);
    }

    static Set<String> implemented() {
        return IMPLEMENTED;
    }

    static Set<String> noEffectIds() {
        return NO_EFFECT;
    }

    // =============================================================================================================== damage

    /** A Pokémon at a third of its HP or less (Blaze, Overgrow, Torrent, Swarm). */
    private static boolean pinch(Combatant c) {
        return c.hp() * 3 <= c.maxHp();
    }

    /**
     * What the attacker's ability does to the damage of a move: Blaze, Overgrow, Torrent and Swarm ×1.5 for their type at a third of the
     * HP or less, Guts ×1.5 for physical moves with a status, Hustle ×1.5 for physical moves, Flash Fire ×1.5 for Fire moves after it
     * absorbed one, Sheer Force ×1.3 when the move has secondary effects.
     */
    static double attackFactor(Battle b, Combatant a, PokemonType type, boolean physical, boolean sheerForce) {
        String ability = b.abilityOf(a);
        if (ability == null) {
            return 1.0;
        }
        return switch (ability) {
            case "blaze" -> type == PokemonType.FIRE && pinch(a) ? 1.5 : 1.0;
            case "overgrow" -> type == PokemonType.GRASS && pinch(a) ? 1.5 : 1.0;
            case "torrent" -> type == PokemonType.WATER && pinch(a) ? 1.5 : 1.0;
            case "swarm" -> type == PokemonType.BUG && pinch(a) ? 1.5 : 1.0;
            case "guts" -> physical && a.status() != Status.NONE ? 1.5 : 1.0;
            case "hustle" -> physical ? 1.5 : 1.0;
            case "flashfire" -> type == PokemonType.FIRE && a.condition().has(Volatiles.FLASH_FIRE) ? 1.5 : 1.0;
            case "sheerforce" -> sheerForce ? 1.3 : 1.0;
            default -> 1.0;
        };
    }

    /** What the target's ability does to the damage it takes: Thick Fat halves Fire and Ice. */
    static double defenseFactor(Battle b, Combatant target, PokemonType type) {
        return b.hasAbility(target, "thickfat") && (type == PokemonType.FIRE || type == PokemonType.ICE) ? 0.5 : 1.0;
    }

    /** Guts: a burn does not halve the Attack. */
    static boolean ignoresBurn(Battle b, Combatant a) {
        return b.hasAbility(a, "guts");
    }

    /** True if Sheer Force changes this move: it has secondary effects, which it drops for the extra power. */
    static boolean sheerForceActive(Battle b, Combatant a, JsonNode effect) {
        if (!b.hasAbility(a, "sheerforce")) {
            return false;
        }
        return effect.path("secondary").isObject() || effect.path("secondaries").isArray() && !effect.path("secondaries").isEmpty();
    }

    // ============================================================================================================ the hit roll

    /** Keen Eye: the target's evasion stages do not count. */
    static boolean ignoresEvasion(Battle b, Combatant a) {
        return b.hasAbility(a, "keeneye");
    }

    /** Hustle: physical moves are 20 % less accurate. */
    static Optional<Integer> accuracy(Battle b, Combatant a, MoveProfile p) {
        if (b.hasAbility(a, "hustle") && p.category() == MoveProfile.Category.PHYSICAL && p.accuracy().isPresent()) {
            return Optional.of((int) Math.round(p.accuracy().get() * 0.8));
        }
        return p.accuracy();
    }

    // ============================================================================================================== immunities

    /**
     * Why the ability of the target stops a damaging move before the hit roll: {@code sturdy} (one-hit-KO moves), {@code waterabsorb} and
     * {@code flashfire}; null if nothing stops it.
     */
    static String blocks(Battle b, Combatant target, MoveProfile p) {
        if (!p.damaging()) {
            return null;
        }
        String ability = b.abilityOf(target);
        if (ability == null) {
            return null;
        }
        return switch (ability) {
            case "sturdy" -> p.ohko() ? "sturdy" : null;
            case "waterabsorb" -> p.type() == PokemonType.WATER ? "waterabsorb" : null;
            case "flashfire" -> p.type() == PokemonType.FIRE ? "flashfire" : null;
            default -> null;
        };
    }

    /** Own Tempo: cannot be confused. */
    static boolean preventsConfusion(Battle b, Combatant t) {
        return b.hasAbility(t, "owntempo");
    }

    /** Inner Focus: never flinches. */
    static boolean preventsFlinch(Battle b, Combatant t) {
        return b.hasAbility(t, "innerfocus");
    }

    /** Oblivious: cannot be attracted. */
    static boolean preventsLove(Battle b, Combatant t) {
        return b.hasAbility(t, "oblivious");
    }

    /** Infiltrator: the moves pass a Substitute. */
    static boolean bypassesSubstitute(Battle b, Combatant a) {
        return b.hasAbility(a, "infiltrator");
    }

    /** Damp: a Pokémon on the field stops Self-Destruct, Explosion and the like. Returns the text of the failure, or null. */
    static String moveFailure(Battle b, Combatant user, String moveName, JsonNode effect) {
        if (effect.path("selfdestruct").asText("").isEmpty()) {
            return null;
        }
        for (Combatant c : b.standing()) {
            if (b.hasAbility(c, "damp")) {
                return b.label(c) + "'s Damp prevents " + b.label(user) + " from using " + moveName + "!";
            }
        }
        return null;
    }

    /** Keen Eye: the accuracy of the holder cannot be lowered. */
    static boolean preventsAccuracyDrop(Battle b, Combatant c, String stat, int delta) {
        return delta < 0 && "accuracy".equals(stat) && b.hasAbility(c, "keeneye");
    }

    // ======================================================================================================== after a hit, PP

    /** Weak Armor: hit by a physical move, the holder's Defense falls and its Speed rises sharply. */
    static boolean weakArmorTriggers(Battle b, Combatant target, MoveProfile p, int damage) {
        return damage > 0 && p.category() == MoveProfile.Category.PHYSICAL && b.hasAbility(target, "weakarmor");
    }

    /** Pressure: a move aimed at a Pokémon that has it spends one more PP. Returns the extra PP (0 or 1). */
    static int pressure(Battle b, Combatant actor, Action action, TargetKind kind) {
        if (action.kind() != ActionKind.MOVE || !b.rules().abilitiesEnabled()) {
            return 0;
        }
        List<Combatant> aimed = new ArrayList<>();
        if (kind.choosesOne()) {
            if (!action.targets().isEmpty()) {
                Combatant t = b.combatant(b.resolveTarget(action.targets().get(0)));
                if (t != null) {
                    aimed.add(t);
                }
            }
        } else if (kind != TargetKind.SELF && kind != TargetKind.SIDE) {
            b.groupTargets(actor, kind).forEach(id -> aimed.add(b.combatant(id)));
        }
        int side = b.sideOf(actor);
        return aimed.stream().anyMatch(t -> b.sideOf(t) != side && b.hasAbility(t, "pressure")) ? 1 : 0;
    }

    // =============================================================================================== coming onto the field

    /**
     * What happens when a Pokémon comes onto the field: Intimidate lowers the Attack of the foes (Inner Focus, Own Tempo and Oblivious
     * block it), Frisk tells what the foes hold, and a scripted ability or held item that Core does not run opens one task for the admin.
     * The events are computed from the state {@code b}, in the order they happen.
     */
    static List<GameEvent> onEntry(Battle b, Combatant entering, MoveBook book) {
        List<GameEvent> events = new ArrayList<>();
        String who = b.label(entering);
        String ability = b.abilityOf(entering);
        if (ability != null && !entering.fainted()) {
            switch (ability) {
                case "intimidate" -> {
                    for (Combatant foe : b.standing()) {
                        if (b.sideOf(foe) == b.sideOf(entering)) {
                            continue;
                        }
                        if (preventsFlinch(b, foe) || preventsConfusion(b, foe) || preventsLove(b, foe)) {
                            events.add(new GameEvent.BattleNoted(b.battleId(), who + "'s Intimidate does not bother " + b.label(foe) + "."));
                        } else {
                            events.add(new GameEvent.BattleNoted(b.battleId(), who + "'s Intimidate cuts the Attack of " + b.label(foe) + "!"));
                            events.add(new GameEvent.StageChanged(b.battleId(), foe.id(), "atk", -1, null));
                        }
                    }
                }
                case "frisk" -> {
                    for (Combatant foe : b.standing()) {
                        String held = foe.condition().heldItem();
                        if (b.sideOf(foe) != b.sideOf(entering) && held != null) {
                            events.add(new GameEvent.BattleNoted(b.battleId(), who + " frisks " + b.label(foe) + " and finds its " + BattleReducer.pretty(held) + "!"));
                        }
                    }
                }
                case "unnerve" -> events.add(new GameEvent.BattleNoted(b.battleId(), who + "'s Unnerve makes the foes too nervous to eat Berries!"));
                default -> {
                }
            }
        }
        events.addAll(tasks(b, entering, book));
        return events;
    }

    /** One task for a scripted ability, and one for a scripted held item, that the game does not run (asked once per Pokémon). */
    private static List<GameEvent> tasks(Battle b, Combatant c, MoveBook book) {
        List<GameEvent> events = new ArrayList<>();
        String who = b.label(c);
        String ability = b.abilityOf(c);
        if (ability != null && book != null && book.abilityHandling(ability) == MoveBook.Handling.MANUAL) {
            String id = b.battleId() + "-ability-" + c.id();
            if (b.tasks().stream().noneMatch(t -> t.id().equals(id))) {
                String text = book.ability(ability).map(AbilityEntry::name).orElse(BattleReducer.pretty(ability));
                String about = book.ability(ability).flatMap(AbilityEntry::description).map(d -> ": " + d).orElse("");
                events.add(new GameEvent.AdminTaskOpened(b.battleId(), id, c.id(), "ability:" + ability, "the ability " + text + " of " + who + about));
            }
        }
        String item = b.itemOf(c);
        if (item != null && book != null && book.itemHandling(item) == MoveBook.Handling.MANUAL) {
            String id = b.battleId() + "-item-" + c.id() + "-" + item;
            if (b.tasks().stream().noneMatch(t -> t.id().equals(id))) {
                String name = BattleReducer.pretty(c.condition().heldItem());
                events.add(new GameEvent.AdminTaskOpened(b.battleId(), id, c.id(), "item:" + item, "the held item " + name + " of " + who));
            }
        }
        return events;
    }

    /** Regenerator: a Pokémon that goes back to its ball heals a third of its maximum HP. Null if it does not. */
    static GameEvent leaveHeal(Battle b, Combatant leaving) {
        if (leaving.fainted() || leaving.hp() >= leaving.maxHp() || !b.hasAbility(leaving, "regenerator")) {
            return null;
        }
        return new GameEvent.HpChanged(b.battleId(), leaving.id(), Math.max(1, leaving.maxHp() / 3), "from Regenerator");
    }

    // ===================================================================================================== end of the round

    /** Shed Skin: the chance (30 %) of curing a status at the end of the round, as a draw out of 10. */
    static boolean shedsSkin(Battle b, Combatant c) {
        return !c.fainted() && c.status() != Status.NONE && b.hasAbility(c, "shedskin");
    }
}
