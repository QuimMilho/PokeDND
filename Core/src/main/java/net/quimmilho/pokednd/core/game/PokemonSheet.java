/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.Battle.MoveSlot;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.rules.StatBlock;

/**
 * One individual Pokémon of the game (chapter 5.1): the one a trainer owns, or a wild one that has been made and not caught yet. A battle
 * works on a copy of what it needs ({@link BattleSetup#combatant}); {@link AfterBattle} writes the results back. Its stats are not
 * kept: they follow from the species, the IVs, the EVs, the nature and the level, so they can never disagree with them.
 *
 * @param id            the id of the Pokémon in the game; it is also its id in a battle
 * @param ownerId       the trainer who owns it (null for a wild one)
 * @param speciesId     the species ({@code bulbasaur})
 * @param name          the nickname, or the name of the species
 * @param level         1 to 100
 * @param xp            total XP; at a level it is at least the XP that the species' growth group needs for that level
 * @param nature        the nature's id ({@code adamant}), or null for none
 * @param ivs           0 to 31 per stat
 * @param evs           0 to 252 per stat, 510 in all
 * @param gender        {@code M}, {@code F} or {@code N}
 * @param shiny         a cosmetic flag
 * @param ability       the ability's id ({@code sturdy})
 * @param heldItem      the held item's id, or null
 * @param moves         up to four, with their PP
 * @param hp            HP now; 0 means it has fainted and stays so until it is healed or revived (chapter 5.7)
 * @param status        the major status
 * @param friendship    0 to 255 (chapter 5.8)
 * @param location      party, a PC box, or wild
 * @param ball          the ball it was caught with, or null
 * @param declinedLevel the level at which its owner said "not now" to a level evolution (0 if never): it is offered again at the next level
 * @param counters      things that evolutions count: {@code use:<move>} (times used in battle) and {@code defeat:<species>}
 * @param travelledBlocks blocks travelled as the lead Pokémon (chapter 5.4 {@code Travel N blocks})
 * @param hidden        a value kept from creation that decides an evolution: {@code silcoon} or {@code cascoon} for a Wurmple
 */
public record PokemonSheet(String id, String ownerId, String speciesId, String name, int level, int xp, String nature, StatBlock ivs, StatBlock evs,
                           String gender, boolean shiny, String ability, String heldItem, List<MoveSlot> moves, int hp, Status status,
                           int friendship, Location location, String ball, int declinedLevel, Map<String, Integer> counters,
                           int travelledBlocks, String hidden) {

    /** Where a Pokémon is. */
    public enum Location {
        /** With its trainer, among the six. */
        PARTY,
        /** In a PC box. */
        PC,
        /** Wild: it belongs to nobody. */
        WILD
    }

    public PokemonSheet {
        if (id == null || id.isBlank() || speciesId == null || speciesId.isBlank()) {
            throw new IllegalArgumentException("A Pokémon needs an id and a species");
        }
        if (level < 1 || level > 100) {
            throw new IllegalArgumentException("A level is 1 to 100, not " + level);
        }
        if (xp < 0 || hp < 0 || friendship < 0 || friendship > 255 || travelledBlocks < 0) {
            throw new IllegalArgumentException("XP, HP, friendship and distance cannot be out of range");
        }
        name = name == null || name.isBlank() ? speciesId : name;
        ivs = ivs == null ? StatBlock.all(0) : ivs;
        evs = evs == null ? StatBlock.all(0) : evs;
        gender = gender == null ? "N" : gender;
        moves = List.copyOf(moves);
        status = status == null ? Status.NONE : status;
        location = location == null ? Location.PARTY : location;
        counters = counters == null ? Map.of() : Map.copyOf(counters);
    }

    /** A Pokémon with the usual defaults: no nickname, no item, no status, full friendship of 70, in the party, no counters. */
    public static PokemonSheet of(String id, String ownerId, String speciesId, int level, int xp, int hp, List<MoveSlot> moves) {
        return new PokemonSheet(id, ownerId, speciesId, speciesId, level, xp, null, StatBlock.all(0), StatBlock.all(0), "N", false, null, null, moves, hp,
                Status.NONE, 70, ownerId == null ? Location.WILD : Location.PARTY, null, 0, Map.of(), 0, null);
    }

    /** True at 0 HP. */
    public boolean fainted() {
        return hp <= 0;
    }

    public PokemonSheet withHp(int value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, value, value <= 0 ? Status.NONE : status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    public PokemonSheet withStatus(Status value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, hp, value,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    /** A copy at another level with another total XP and HP. */
    public PokemonSheet withGrowth(int newLevel, int newXp, int newHp) {
        return new PokemonSheet(id, ownerId, speciesId, name, newLevel, newXp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, newHp, status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    public PokemonSheet withGender(String value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, value, shiny, ability, heldItem, moves, hp, status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    public PokemonSheet withEvs(StatBlock value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, value, gender, shiny, ability, heldItem, moves, hp, status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    /** A copy with other IVs (an IV candy, the admin). */
    public PokemonSheet withIvs(StatBlock value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, value, evs, gender, shiny, ability, heldItem, moves, hp, status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    /** A copy with another nature (a mint, the admin). */
    public PokemonSheet withNature(String value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, value, ivs, evs, gender, shiny, ability, heldItem, moves, hp, status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    public PokemonSheet withMoves(List<MoveSlot> value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, value, hp, status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    public PokemonSheet withFriendship(int value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, hp, status,
                Math.max(0, Math.min(255, value)), location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    public PokemonSheet withHeldItem(String value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, value, moves, hp, status,
                friendship, location, ball, declinedLevel, counters, travelledBlocks, hidden);
    }

    public PokemonSheet withDeclinedLevel(int value) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, hp, status,
                friendship, location, ball, value, counters, travelledBlocks, hidden);
    }

    /** A copy owned by this trainer, in the party or a box. */
    public PokemonSheet withOwner(String trainerId, Location where, String ballId) {
        return new PokemonSheet(id, trainerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, hp, status,
                friendship, where, ballId, declinedLevel, counters, travelledBlocks, hidden);
    }

    /** A copy of another species (evolution): the name, the ability and the HP are the caller's. */
    public PokemonSheet evolvedTo(String newSpeciesId, String newName, String newAbility, int newHp) {
        return new PokemonSheet(id, ownerId, newSpeciesId, newName, level, xp, nature, ivs, evs, gender, shiny, newAbility, heldItem, moves, newHp, status,
                friendship, location, ball, 0, counters, travelledBlocks, hidden);
    }

    /** A copy that has used a move once more (evolutions such as Rage Fist count it). */
    public PokemonSheet withCount(String key, int amount) {
        Map<String, Integer> next = new LinkedHashMap<>(counters);
        next.merge(key, amount, Integer::sum);
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, hp, status,
                friendship, location, ball, declinedLevel, next, travelledBlocks, hidden);
    }

    public PokemonSheet withTravelled(int blocks) {
        return new PokemonSheet(id, ownerId, speciesId, name, level, xp, nature, ivs, evs, gender, shiny, ability, heldItem, moves, hp, status,
                friendship, location, ball, declinedLevel, counters, blocks, hidden);
    }

    /** The counter of something (0 if never counted). */
    public int count(String key) {
        return counters.getOrDefault(key, 0);
    }
}
