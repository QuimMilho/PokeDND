/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.cards;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.quimmilho.pokednd.core.combat.MoveProfile.Category;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.importer.Derived.Stage;
import net.quimmilho.pokednd.core.importer.Learnsets.LevelMove;
import net.quimmilho.pokednd.core.rules.PokemonType;

/**
 * The four signature moves of a species card (chapter 9.3). A rule picks them, and a hand-written overrides file can fix
 * them for any species or form ({@link SignatureOverrides}).
 *
 * <ol>
 *   <li><b>Candidates:</b> the level-up moves of the form, without useless moves and repeats, learnt at or below the level cap
 *       of its stage. If that leaves fewer than four, the cap is lifted.</li>
 *   <li><b>Score</b> each candidate (see {@link #score}).</li>
 *   <li><b>Pick</b> the best damaging move of the Pokémon's own type; then the best damaging move of another type; then the best
 *       of the rest, with at most two moves of the same type.</li>
 * </ol>
 *
 * <p>Ties are broken by the later level, then by the move id, so the result is always the same.
 */
public final class SignatureMoves {

    /** Moves that do nothing in the game and never go on a card. */
    public static final Set<String> USELESS = Set.of("splash", "celebrate", "struggle", "holdhands", "happyhour");

    /** The score of a damaging move that has no power number (fixed damage, variable power, one-hit KO). */
    public static final int UNKNOWN_POWER = 50;

    private SignatureMoves() {
    }

    /**
     * The level cap for each stage ({@code cards.signature.stageLevelCaps}, default 25, 40, 100).
     *
     * @param basic  Basic Pokémon that evolve
     * @param stage1 Stage 1 Pokémon
     * @param stage2 Stage 2 Pokémon, and Pokémon that do not evolve at all
     */
    public record StageLevelCaps(int basic, int stage1, int stage2) {

        /** The defaults. */
        public static StageLevelCaps defaults() {
            return new StageLevelCaps(25, 40, 100);
        }

        /**
         * Reads {@code 25,40,100}.
         *
         * @throws IllegalArgumentException if it is not three whole numbers
         */
        public static StageLevelCaps parse(String text) {
            String[] parts = text.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("cards.signature.stageLevelCaps needs three numbers like 25,40,100, not " + text);
            }
            try {
                return new StageLevelCaps(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()),
                        Integer.parseInt(parts[2].strip()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cards.signature.stageLevelCaps needs three numbers like 25,40,100, not " + text);
            }
        }

        /**
         * The cap for a Pokémon.
         *
         * @param stage       its stage badge
         * @param hasEvolutionLine true if it evolves from or into something (a Basic Pokémon alone counts as single-stage)
         */
        public int capFor(Stage stage, boolean hasEvolutionLine) {
            return switch (stage) {
                case BASIC -> hasEvolutionLine ? basic : stage2;
                case STAGE_1 -> stage1;
                case STAGE_2 -> stage2;
            };
        }
    }

    /**
     * What the rule needs to know about a move.
     *
     * @param id                  the move id
     * @param type                its type
     * @param category            physical, special or status
     * @param power               base power (0 or 1 when the move has none)
     * @param accuracy            percent; empty when the move never misses
     * @param changesStatusOrStats a status move that inflicts a status or changes stats
     * @param drawback            recharge, two turns, recoil or self-destruct
     * @param usable              false for moves that are not part of the game (Z-moves, Max moves)
     */
    public record MoveInfo(String id, PokemonType type, Category category, int power, Optional<Integer> accuracy,
                           boolean changesStatusOrStats, boolean drawback, boolean usable) {

        /** True for physical and special moves. */
        public boolean damaging() {
            return category != Category.STATUS;
        }

        /** Reads a move from the bundle. */
        public static MoveInfo from(MoveEntry move) {
            JsonNode e = move.effect();
            boolean statusOrStats = e != null && (e.has("status") || e.has("volatileStatus") || e.has("boosts")
                    || e.path("self").has("boosts"));
            boolean drawback = e != null && (e.path("flags").has("recharge") || e.path("flags").has("charge") || e.has("recoil")
                    || e.has("selfdestruct") || e.has("mindBlownRecoil"));
            PokemonType type = PokemonType.parse(move.type())
                    .orElseThrow(() -> new IllegalArgumentException(move.id() + ": unknown type " + move.type()));
            return new MoveInfo(move.id(), type, Category.parse(move.category()), move.power(), move.accuracy(), statusOrStats,
                    drawback, !move.reason().contains("z_max"));
        }
    }

    /** Why a move was picked. */
    public enum Role {
        /** The best damaging move of the Pokémon's own type. */
        OWN_TYPE,
        /** The best damaging move of another type. */
        OTHER_TYPE,
        /** One of the best remaining moves. */
        BEST_REMAINING,
        /** Taken only because the two-moves-per-type limit left a place empty. */
        FILLER
    }

    /**
     * One chosen move.
     *
     * @param move  the move id
     * @param level the level it is learnt at
     * @param score its score
     * @param role  why it was picked
     */
    public record Pick(String move, int level, double score, Role role) {
    }

    /**
     * The outcome of the rule.
     *
     * @param picks       up to four moves, in the order they are shown on the card
     * @param levelCap    the cap that applied
     * @param capLifted   true if too few moves were left under the cap, so it was lifted
     * @param candidates  how many moves were considered
     */
    public record Result(List<Pick> picks, int levelCap, boolean capLifted, int candidates) {
        public Result {
            picks = List.copyOf(picks);
        }

        /** The move ids in card order. */
        public List<String> moves() {
            return picks.stream().map(Pick::move).toList();
        }
    }

    /**
     * The score of a move for a Pokémon with these types.
     * <ul>
     *   <li>damaging move: {@code min(power, 120) / 2}, plus 25 for a move of the Pokémon's own type (STAB), plus 10 if its accuracy
     *       is 90 or more or it never misses;</li>
     *   <li>status move: 20 if it inflicts a status or changes stats, otherwise 5;</li>
     *   <li>minus 15 for a drawback (recharge, two turns, recoil, self-destruct).</li>
     * </ul>
     */
    public static double score(MoveInfo move, Set<PokemonType> types) {
        double score;
        if (move.damaging()) {
            int power = move.power() >= 2 ? move.power() : UNKNOWN_POWER;
            score = Math.min(power, 120) / 2.0;
            if (types.contains(move.type())) {
                score += 25;
            }
            if (move.accuracy().isEmpty() || move.accuracy().get() >= 90) {
                score += 10;
            }
        } else {
            score = move.changesStatusOrStats() ? 20 : 5;
        }
        if (move.drawback()) {
            score -= 15;
        }
        return score;
    }

    /**
     * Picks the four moves.
     *
     * @param levelMoves what the form learns by level, in the order of the data
     * @param types      the form's types
     * @param levelCap   the level cap of its stage
     * @param moves      the move data by id; a move that is not in it is skipped
     * @return the picks; fewer than four only if the form has fewer usable level-up moves
     */
    public static Result select(List<LevelMove> levelMoves, Set<PokemonType> types, int levelCap, Map<String, MoveInfo> moves) {
        // 1. Candidates: usable, not useless, first time a move is mentioned.
        Map<String, Integer> levelOf = new LinkedHashMap<>();
        for (LevelMove lm : levelMoves) {
            MoveInfo info = moves.get(lm.move());
            if (info == null || !info.usable() || USELESS.contains(lm.move())) {
                continue;
            }
            levelOf.putIfAbsent(lm.move(), lm.level());
        }
        List<String> underCap = levelOf.entrySet().stream().filter(e -> e.getValue() <= levelCap).map(Map.Entry::getKey).toList();
        boolean lifted = underCap.size() < 4 && levelOf.size() > underCap.size();
        List<String> candidates = lifted ? new ArrayList<>(levelOf.keySet()) : new ArrayList<>(underCap);

        // 2. Score, best first. Ties: the later level, then the id.
        record Scored(MoveInfo info, int level, double score) {
        }
        List<Scored> sorted = new ArrayList<>();
        for (String id : candidates) {
            MoveInfo info = moves.get(id);
            sorted.add(new Scored(info, levelOf.get(id), score(info, types)));
        }
        sorted.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(Comparator.comparingInt(Scored::level).reversed())
                .thenComparing(s -> s.info().id()));

        // 3. Pick.
        List<Pick> picks = new ArrayList<>();
        List<Scored> left = new ArrayList<>(sorted);
        Scored first = left.stream().filter(s -> s.info().damaging() && types.contains(s.info().type())).findFirst().orElse(null);
        if (first != null) {
            picks.add(new Pick(first.info().id(), first.level(), first.score(), Role.OWN_TYPE));
            left.remove(first);
        }
        PokemonType firstType = first == null ? null : first.info().type();
        Scored second = left.stream().filter(s -> s.info().damaging() && s.info().type() != firstType).findFirst().orElse(null);
        if (second != null) {
            picks.add(new Pick(second.info().id(), second.level(), second.score(), Role.OTHER_TYPE));
            left.remove(second);
        }
        for (Scored s : new ArrayList<>(left)) {
            if (picks.size() >= 4) {
                break;
            }
            if (countOfType(picks, moves, s.info().type()) < 2) {
                picks.add(new Pick(s.info().id(), s.level(), s.score(), Role.BEST_REMAINING));
                left.remove(s);
            }
        }
        for (Scored s : new ArrayList<>(left)) { // the type limit left a place empty: fill it anyway
            if (picks.size() >= 4) {
                break;
            }
            picks.add(new Pick(s.info().id(), s.level(), s.score(), Role.FILLER));
            left.remove(s);
        }
        return new Result(picks, levelCap, lifted, sorted.size());
    }

    private static int countOfType(List<Pick> picks, Map<String, MoveInfo> moves, PokemonType type) {
        int n = 0;
        for (Pick p : picks) {
            if (moves.get(p.move()).type() == type) {
                n++;
            }
        }
        return n;
    }
}
