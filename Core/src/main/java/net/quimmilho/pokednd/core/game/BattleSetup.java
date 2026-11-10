/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import net.quimmilho.pokednd.core.game.Battle.Build;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Condition;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.rules.HiddenPower;
import net.quimmilho.pokednd.core.rules.Nature;
import net.quimmilho.pokednd.core.rules.StatBlock;
import net.quimmilho.pokednd.core.rules.Stats;

/**
 * Makes what a battle fights with from an owned Pokémon (chapter 5.2 and 7.1): the Pokémon's six stats come from its species, IVs, EVs,
 * nature and level; its moves keep their PP, and its status, held item, friendship and ability go along. After the battle
 * {@link AfterBattle} writes the results back to the same {@link PokemonSheet}, found by the same id.
 */
public final class BattleSetup {

    private BattleSetup() {
    }

    /** The six stats of a Pokémon now. */
    public static StatBlock stats(PokemonSheet sheet, SpeciesBook.Species species) {
        Nature nature = sheet.nature() == null ? neutral() : Nature.find(sheet.nature()).orElse(neutral());
        return Stats.compute(species.base(), sheet.ivs(), sheet.evs(), sheet.level(), nature);
    }

    /** The maximum HP of a Pokémon at a level (its IVs and EVs as they are now). */
    public static int maxHp(PokemonSheet sheet, SpeciesBook.Species species, int level) {
        return Stats.hp(species.base().hp(), sheet.ivs().hp(), sheet.evs().hp(), level);
    }

    /**
     * The Pokémon as a battle sees it. Its id in the battle is the id of the sheet; {@code participantId} is the participant it belongs to.
     *
     * @throws IllegalArgumentException if the book does not know the species
     */
    public static Combatant combatant(PokemonSheet sheet, String participantId, SpeciesBook species, MoveBook moves) {
        SpeciesBook.Species sp = species.species(sheet.speciesId())
                .orElseThrow(() -> new IllegalArgumentException("The species " + sheet.speciesId() + " of " + sheet.id() + " is not in the book"));
        StatBlock stats = stats(sheet, sp);
        int maxHp = stats.hp();
        Build build = new Build(sp.types(), stats.attack(), stats.defense(), stats.specialAttack(), stats.specialDefense(), stats.speed(), sheet.gender(),
                sheet.friendship(), HiddenPower.type(sheet.ivs()), sheet.ability(), sheet.speciesId(), sp.catchRate());
        Condition condition = Condition.NONE.withStatus(sheet.status(), sheet.status() == Status.SLEEP ? 1 : 0).withHeldItem(sheet.heldItem());
        // the Speed of the battle is worked out from the build and the condition
        Combatant c = new Combatant(sheet.id(), participantId, sheet.name(), sheet.level(), Math.min(sheet.hp(), maxHp), maxHp, stats.speed(), false,
                new ArrayList<>(sheet.moves()), build, condition);
        return c.withCondition(condition);
    }

    /** The Pokémon of this trainer that are in the party, in the order of the game (the first one that can fight leads). */
    public static List<PokemonSheet> party(GameState state, String trainerId) {
        return state.pokemon().values().stream()
                .filter(p -> trainerId.equals(p.ownerId()) && p.location() == PokemonSheet.Location.PARTY).toList();
    }

    private static Nature neutral() {
        return Nature.find("hardy").orElseThrow();
    }
}
