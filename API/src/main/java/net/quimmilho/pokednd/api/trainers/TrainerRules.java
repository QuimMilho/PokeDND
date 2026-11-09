/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.settings.SettingsService;
import net.quimmilho.pokednd.core.rules.TrainerXp;
import net.quimmilho.pokednd.core.trainer.KoMode;
import net.quimmilho.pokednd.core.trainer.ScoreMethod;
import net.quimmilho.pokednd.core.trainer.SkillDef;
import net.quimmilho.pokednd.core.trainer.TrainerClasses;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;
import net.quimmilho.pokednd.core.trainer.TrainerSettings;

/**
 * Turns the settings of a game into the trainer rules of Core (chapter 4.7): the allowed ways to make ability scores, the starting kit,
 * the XP curve and table, the class list and the skill list; and into the starter options of chapter 5.11.
 */
@ApplicationScoped
public class TrainerRules {

    /**
     * The starter Pokémon of generations 1 to 9 (chapter 5.11, <em>Suggested</em>, Appendix D): the three starters of each generation. A
     * species is offered only if the roster has a model for it.
     */
    static final List<String> DEFAULT_STARTERS = List.of("bulbasaur", "charmander", "squirtle", "chikorita", "cyndaquil", "totodile", "treecko",
            "torchic", "mudkip", "turtwig", "chimchar", "piplup", "snivy", "tepig", "oshawott", "chespin", "fennekin", "froakie", "rowlet", "litten",
            "popplio", "grookey", "scorbunny", "sobble", "sprigatito", "fuecoco", "quaxly");

    /** The starter rules of a game. */
    public record Starters(String method, int level, List<SpeciesRecord> species) {
    }

    @Inject
    SettingsService settings;

    @Inject
    ReferenceRepository reference;

    @Inject
    ObjectMapper json;

    /** The trainer rules of this game. Throws 422 {@code TRAINER_RULES_INVALID} if the game's own class list, skill list or XP table is broken. */
    public TrainerRulebook rulebook(Game game) {
        try {
            TrainerXp xp = new TrainerXp(settings.integer(game, "trainer.xp.curveFactor"), settings.value(game, "trainer.xp.multiplier").asDouble(),
                    settings.integer(game, "trainer.level.start"), settings.integer(game, "trainer.level.cap"),
                    xpTable(settings.value(game, "trainer.xp.table"), TrainerXp.defaultTable()));
            TrainerSettings s = new TrainerSettings(ScoreMethod.parseAllowed(settings.value(game, "trainer.abilityScores.method").asText()),
                    settings.integer(game, "trainer.abilityScores.pointBuyBudget"), settings.value(game, "trainer.start.money").asLong(),
                    items(settings.value(game, "trainer.start.items")), settings.integer(game, "trainer.hp.recoverPerRoundPct"),
                    KoMode.parse(settings.value(game, "trainer.ko.mode").asText()), settings.bool(game, "trainer.ko.deathAllowed"), xp);
            TrainerClasses classes = classes(settings.value(game, "trainer.classes.set"));
            List<SkillDef> skills = skills(settings.value(game, "trainer.skills.set"));
            return new TrainerRulebook(classes, skills, s);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(422, "TRAINER_RULES_INVALID", "The trainer settings of this game are not valid: " + e.getMessage());
        }
    }

    /** The starter Pokémon the game offers, with the method and level. */
    public Starters starters(Game game) {
        JsonNode set = settings.value(game, "pokemon.starter.list");
        List<String> ids = new ArrayList<>();
        JsonNode list = set != null && set.isObject() && set.has("species") ? set.get("species") : set;
        if (list != null && list.isArray()) {
            list.forEach(n -> ids.add(n.asText()));
        } else {
            ids.addAll(DEFAULT_STARTERS);
        }
        List<SpeciesRecord> species = new ArrayList<>();
        for (String id : ids) {
            reference.species(id.strip().toLowerCase(Locale.ROOT)).filter(SpeciesRecord::playable).ifPresent(species::add);
        }
        return new Starters(settings.value(game, "pokemon.starter.method").asText(), settings.integer(game, "pokemon.starter.level"), species);
    }

    // ------------------------------------------------------------------ readers of the settings values

    /**
     * Reads {@code trainer.start.items}: {@code pokeBall×5,potion×3}. An item id written in camel case ({@code pokeBall}) becomes the id of the
     * data bundle ({@code poke_ball}).
     */
    static Map<String, Integer> items(JsonNode node) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<String> tokens = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> tokens.add(n.asText()));
        } else {
            for (String t : node.asText().split(",")) {
                tokens.add(t);
            }
        }
        for (String raw : tokens) {
            String t = raw.strip();
            if (t.isEmpty()) {
                continue;
            }
            int times = t.indexOf('×');
            String id = times < 0 ? t : t.substring(0, times).strip();
            int count = times < 0 ? 1 : Integer.parseInt(t.substring(times + 1).strip());
            out.merge(snake(id), count, Integer::sum);
        }
        return out;
    }

    /** {@code pokeBall} becomes {@code poke_ball}; an id already in snake case stays. */
    static String snake(String id) {
        return id.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** The XP table: an object from event to a number (XP) or {@code {"amount": 5, "perLevel": true}}; missing events keep their default. */
    private static Map<TrainerXp.Event, TrainerXp.Reward> xpTable(JsonNode node, Map<TrainerXp.Event, TrainerXp.Reward> defaults) {
        if (node == null || !node.isObject()) {
            return defaults;
        }
        Map<TrainerXp.Event, TrainerXp.Reward> out = new java.util.EnumMap<>(defaults);
        node.fields().forEachRemaining(e -> {
            TrainerXp.Event event = null;
            for (TrainerXp.Event candidate : TrainerXp.Event.values()) {
                if (candidate.name().equalsIgnoreCase(e.getKey()) || camel(candidate.name()).equalsIgnoreCase(e.getKey())) {
                    event = candidate;
                }
            }
            if (event == null) {
                throw new IllegalArgumentException("trainer.xp.table has no event called " + e.getKey());
            }
            JsonNode v = e.getValue();
            boolean perLevel = out.get(event).perLevel();
            int amount;
            if (v.isObject()) {
                amount = v.path("amount").asInt(out.get(event).amount());
                perLevel = v.path("perLevel").asBoolean(perLevel);
            } else {
                amount = v.asInt();
            }
            out.put(event, new TrainerXp.Reward(amount, perLevel));
        });
        return out;
    }

    private static String camel(String enumName) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : enumName.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }

    /** The class list: nothing means the nine defaults; otherwise {@code {"classes": [...]}} as Core reads it. */
    private static TrainerClasses classes(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || (node.isObject() && node.isEmpty())) {
            return TrainerClasses.defaults();
        }
        return TrainerClasses.fromJson(node.toString());
    }

    /** The skill list: nothing means the seventeen defaults; otherwise a list of {@code {id, name, ability}}, or an object with {@code skills}. */
    private List<SkillDef> skills(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || (node.isObject() && node.isEmpty())) {
            return SkillDef.defaults();
        }
        JsonNode list = node.isObject() ? node.path("skills") : node;
        if (!list.isArray()) {
            throw new IllegalArgumentException("trainer.skills.set must be a list of skills");
        }
        return List.of(json.convertValue(list, SkillDef[].class));
    }
}
