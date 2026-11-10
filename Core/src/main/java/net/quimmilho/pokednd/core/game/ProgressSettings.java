/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.quimmilho.pokednd.core.rules.PokemonSettings;
import net.quimmilho.pokednd.core.rules.TrainerXp;

/**
 * The settings of experience, levelling, evolution, friendship and moves (chapters 5.3 to 5.12) that {@link AfterBattle} and
 * {@link ProgressDesk} read. They are not copied into a battle: the caller passes the settings of the game when it settles a battle.
 *
 * @param pokemon           the level cap, the XP share rule and the XP multiplier (chapter 5.12)
 * @param evolutionMode     {@code pokemon.evolution.mode}: who confirms an evolution (default askPlayer)
 * @param unknownFallback   {@code pokemon.evolution.unknownFallback}: what to do with an evolution only the admin can judge (default askAdmin)
 * @param friendshipEvolveAt {@code pokemon.friendship.evolveAt}: friendship that friendship evolutions need (default 220)
 * @param levelUpGain       {@code pokemon.friendship.levelUpGain}: friendship gained per level-up below 100, from 100 to 199 and from 200 (default 5, 4, 3)
 * @param faintLoss         {@code pokemon.friendship.faintLoss}: friendship lost when fainting (default 1)
 * @param pointsPerGain     {@code pokemon.friendship.pointsPerGain}: movement points travelled per +1 friendship (default 10)
 * @param movesMax          {@code pokemon.moves.max}: moves a Pokémon can know (default 4)
 * @param partySize         {@code pokemon.party.size}: Pokémon in the party (default 6)
 * @param movesFrozen       true in physical card mode ({@code cards.mode} = physical): no move is learned by level-up (chapter 5.5)
 * @param blocksPerPoint    {@code world.movement.blocksPerPoint}: blocks that one movement point is worth for {@code Travel N blocks} (default 8)
 * @param trainerXp         the trainer XP table and curve (chapter 4.5)
 */
public record ProgressSettings(PokemonSettings pokemon, EvolutionMode evolutionMode, UnknownFallback unknownFallback, int friendshipEvolveAt,
                               List<Integer> levelUpGain, int faintLoss, int pointsPerGain, int movesMax, int partySize, boolean movesFrozen,
                               int blocksPerPoint, TrainerXp trainerXp) {

    /** {@code pokemon.evolution.mode}. */
    public enum EvolutionMode {
        /** The owner is asked. */
        ASK_PLAYER("askPlayer"),
        /** The admin is asked. */
        ASK_ADMIN("askAdmin"),
        /** The Pokémon evolves at once. */
        AUTOMATIC("automatic");

        private final String id;

        EvolutionMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static EvolutionMode parse(String text) {
            for (EvolutionMode m : values()) {
                if (m.id.equalsIgnoreCase(text.strip())) {
                    return m;
                }
            }
            throw new IllegalArgumentException("pokemon.evolution.mode must be askPlayer, askAdmin or automatic, not " + text);
        }
    }

    /** {@code pokemon.evolution.unknownFallback}. */
    public enum UnknownFallback {
        ASK_ADMIN("askAdmin"),
        ASK_PLAYER("askPlayer"),
        /** The evolution does not happen. */
        SKIP("skip");

        private final String id;

        UnknownFallback(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static UnknownFallback parse(String text) {
            for (UnknownFallback m : values()) {
                if (m.id.equalsIgnoreCase(text.strip())) {
                    return m;
                }
            }
            throw new IllegalArgumentException("pokemon.evolution.unknownFallback must be askAdmin, askPlayer or skip, not " + text);
        }
    }

    public ProgressSettings {
        levelUpGain = List.copyOf(levelUpGain);
        if (levelUpGain.size() != 3) {
            throw new IllegalArgumentException("pokemon.friendship.levelUpGain has three numbers (below 100, 100 to 199, 200 and more)");
        }
        if (friendshipEvolveAt < 0 || friendshipEvolveAt > 255 || faintLoss < 0 || pointsPerGain < 1 || movesMax < 1 || partySize < 1 || blocksPerPoint < 1) {
            throw new IllegalArgumentException("A progress setting is out of range");
        }
    }

    /** The defaults of chapter 5.12. */
    public static ProgressSettings defaults() {
        return new ProgressSettings(PokemonSettings.defaults(), EvolutionMode.ASK_PLAYER, UnknownFallback.ASK_ADMIN, 220, List.of(5, 4, 3), 1, 10, 4, 6,
                false, 8, TrainerXp.defaults());
    }

    /** Reads the settings from their keys; a key that is missing keeps its default. */
    public static ProgressSettings fromValues(Map<String, String> values) {
        ProgressSettings d = defaults();
        PokemonSettings p = d.pokemon();
        if (values.containsKey("pokemon.level.cap")) {
            p = p.withLevelCap(Integer.parseInt(values.get("pokemon.level.cap").strip()));
        }
        if (values.containsKey("pokemon.xp.share")) {
            p = p.withXpShare(PokemonSettings.XpShare.parse(values.get("pokemon.xp.share")));
        }
        if (values.containsKey("pokemon.xp.multiplier")) {
            p = p.withXpMultiplier(Double.parseDouble(values.get("pokemon.xp.multiplier").strip()));
        }
        List<Integer> gain = d.levelUpGain();
        if (values.containsKey("pokemon.friendship.levelUpGain")) {
            gain = java.util.Arrays.stream(values.get("pokemon.friendship.levelUpGain").split(",")).map(t -> Integer.parseInt(t.strip())).toList();
        }
        return new ProgressSettings(p,
                values.containsKey("pokemon.evolution.mode") ? EvolutionMode.parse(values.get("pokemon.evolution.mode")) : d.evolutionMode(),
                values.containsKey("pokemon.evolution.unknownFallback") ? UnknownFallback.parse(values.get("pokemon.evolution.unknownFallback")) : d.unknownFallback(),
                number(values, "pokemon.friendship.evolveAt", d.friendshipEvolveAt()), gain, number(values, "pokemon.friendship.faintLoss", d.faintLoss()),
                number(values, "pokemon.friendship.pointsPerGain", d.pointsPerGain()), number(values, "pokemon.moves.max", d.movesMax()),
                number(values, "pokemon.party.size", d.partySize()), "physical".equals(values.getOrDefault("cards.mode", "digital").strip().toLowerCase(Locale.ROOT)),
                number(values, "world.movement.blocksPerPoint", d.blocksPerPoint()), d.trainerXp());
    }

    private static int number(Map<String, String> values, String key, int fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, not " + v);
        }
    }

    public ProgressSettings withPokemon(PokemonSettings value) {
        return new ProgressSettings(value, evolutionMode, unknownFallback, friendshipEvolveAt, levelUpGain, faintLoss, pointsPerGain, movesMax, partySize,
                movesFrozen, blocksPerPoint, trainerXp);
    }

    public ProgressSettings withEvolution(EvolutionMode mode, UnknownFallback fallback) {
        return new ProgressSettings(pokemon, mode, fallback, friendshipEvolveAt, levelUpGain, faintLoss, pointsPerGain, movesMax, partySize,
                movesFrozen, blocksPerPoint, trainerXp);
    }

    public ProgressSettings withMovesFrozen(boolean value) {
        return new ProgressSettings(pokemon, evolutionMode, unknownFallback, friendshipEvolveAt, levelUpGain, faintLoss, pointsPerGain, movesMax, partySize,
                value, blocksPerPoint, trainerXp);
    }

    public ProgressSettings withPartySize(int value) {
        return new ProgressSettings(pokemon, evolutionMode, unknownFallback, friendshipEvolveAt, levelUpGain, faintLoss, pointsPerGain, movesMax, value,
                movesFrozen, blocksPerPoint, trainerXp);
    }

    public ProgressSettings withMovesMax(int value) {
        return new ProgressSettings(pokemon, evolutionMode, unknownFallback, friendshipEvolveAt, levelUpGain, faintLoss, pointsPerGain, value, partySize,
                movesFrozen, blocksPerPoint, trainerXp);
    }

    public ProgressSettings withFriendship(int evolveAt, List<Integer> gain, int faintLossValue, int perGain) {
        return new ProgressSettings(pokemon, evolutionMode, unknownFallback, evolveAt, gain, faintLossValue, perGain, movesMax, partySize,
                movesFrozen, blocksPerPoint, trainerXp);
    }
}
