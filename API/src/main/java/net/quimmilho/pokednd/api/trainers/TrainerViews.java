/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.StoredTrainer;
import net.quimmilho.pokednd.api.settings.SettingsService;
import net.quimmilho.pokednd.core.trainer.Ability;
import net.quimmilho.pokednd.core.trainer.AbilityScoreRules;
import net.quimmilho.pokednd.core.trainer.ClassFeature;
import net.quimmilho.pokednd.core.trainer.ScoreMethod;
import net.quimmilho.pokednd.core.trainer.Sheet;
import net.quimmilho.pokednd.core.trainer.SkillDef;
import net.quimmilho.pokednd.core.trainer.TrainerNames;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** What the trainer endpoints answer: a trainer with its sheet, and the options the wizard needs. */
@ApplicationScoped
public class TrainerViews {

    public record StarterDto(String species, String name, int level) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkinDto(String model, String source, String username, String sha256, Instant updatedAt) {
    }

    public record SheetDto(int proficiencyBonus, int armorClass, Map<String, Integer> modifiers, Map<String, Integer> savingThrows,
                           Map<String, Integer> skillBonuses, List<String> proficient, int handlingModifier, List<ClassFeature> features,
                           Map<String, Double> effects, List<String> narrative) {
    }

    /** A trainer. {@code sheet} is missing when the game's class list no longer has the trainer's class. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrainerDto(String id, String gameId, String userId, String ownerName, String name, String classId, String className, int level, long xp,
                             Long xpForNextLevel, Map<String, Integer> abilityScores, List<String> skills, int hp, int hpMax, long money,
                             Map<String, Integer> items, String scoreMethod, StarterDto starter, SkinDto skin, SheetDto sheet) {
    }

    public record SpeciesDto(String id, String name, int dex, List<String> types) {
    }

    public record StartersDto(String method, int level, List<SpeciesDto> species) {
    }

    public record SkinRulesDto(boolean allowUpload, boolean allowCopyFromUsername, int maxBytes, int size) {
    }

    /** What the wizard needs to draw its steps. */
    public record OptionsDto(String nameMode, int nameMinLength, int nameMaxLength, List<String> scoreMethods, int pointBuyBudget,
                             Map<Integer, Integer> pointBuyCosts, List<Integer> standardArray, int startLevel, List<Integer> improvementLevels,
                             List<SkillDef> skills, StartersDto starters, SkinRulesDto skin, boolean hasTrainer) {
    }

    @Inject
    TrainerService service;

    @Inject
    TrainerRules rules;

    @Inject
    SettingsService settings;

    @Inject
    ReferenceRepository reference;

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "pokednd.skins.max-bytes", defaultValue = "65536")
    int skinMaxBytes;

    public OptionsDto options(Game game, boolean hasTrainer) {
        TrainerRulebook rulebook = rules.rulebook(game);
        TrainerRules.Starters starters = rules.starters(game);
        Map<Integer, Integer> costs = new TreeMap<>();
        for (int s = AbilityScoreRules.POINT_BUY_MIN; s <= AbilityScoreRules.POINT_BUY_MAX; s++) {
            costs.put(s, AbilityScoreRules.pointBuyCost(s));
        }
        List<String> methods = new ArrayList<>();
        for (ScoreMethod m : ScoreMethod.values()) {
            if (rulebook.settings().allowedMethods().contains(m)) {
                methods.add(m.id());
            }
        }
        List<SpeciesDto> species = starters.species().stream().map(s -> new SpeciesDto(s.id(), s.name(), s.dex(), s.types())).toList();
        return new OptionsDto(settings.value(game, "game.trainerNames.mode").asText(), TrainerNames.MIN_LENGTH, TrainerNames.MAX_LENGTH, methods,
                rulebook.settings().pointBuyBudget(), costs, AbilityScoreRules.STANDARD_ARRAY, rulebook.settings().xp().levelStart(),
                TrainerRulebook.IMPROVEMENT_LEVELS, rulebook.skills(), new StartersDto(starters.method(), starters.level(), species),
                new SkinRulesDto(settings.bool(game, "game.skins.allowUpload"), settings.bool(game, "game.skins.allowCopyFromUsername"), skinMaxBytes,
                        SkinImages.SIZE), hasTrainer);
    }

    public TrainerDto dto(Game game, StoredTrainer t) {
        return dto(t, rules.rulebook(game));
    }

    public TrainerDto dto(StoredTrainer t, TrainerRulebook rulebook) {
        net.quimmilho.pokednd.core.trainer.Trainer core = TrainerService.coreTrainer(t);
        SheetDto sheet = null;
        String className = t.classId();
        if (rulebook.classes().find(t.classId()).isPresent()) {
            Sheet s = rulebook.sheet(core);
            className = s.trainerClass().name();
            Map<String, Integer> mods = new LinkedHashMap<>();
            Map<String, Integer> saves = new LinkedHashMap<>();
            for (Ability a : Ability.values()) {
                mods.put(a.id(), s.modifiers().get(a));
                saves.put(a.id(), s.savingThrows().get(a));
            }
            sheet = new SheetDto(s.proficiencyBonus(), s.armorClass(), mods, saves, s.skillBonuses(), s.proficient().stream().sorted().toList(),
                    s.handlingModifier(), s.features(), new TreeMap<>(s.effects().numbers()), s.effects().narrative());
        }
        Long toNext = t.level() < rulebook.settings().xp().levelCap() ? rulebook.settings().xp().totalXp(t.level() + 1) : null;
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Ability a : Ability.values()) {
            scores.put(a.id(), t.abilityScores().path(a.id()).asInt());
        }
        List<String> skills = new ArrayList<>();
        t.skills().forEach(n -> skills.add(n.asText()));
        Map<String, Integer> items = new TreeMap<>();
        t.items().fields().forEachRemaining(e -> items.put(e.getKey(), e.getValue().asInt()));
        StarterDto starter = null;
        if (t.starterSpecies() != null) {
            String name = reference.species(t.starterSpecies()).map(SpeciesRecord::name).orElse(t.starterSpecies());
            starter = new StarterDto(t.starterSpecies(), name, t.starterLevel() == null ? 5 : t.starterLevel());
        }
        SkinDto skin = t.hasSkin() ? new SkinDto(t.skinModel(), t.skinSource(), t.skinUsername(), t.skinSha256(), t.skinAt()) : null;
        return new TrainerDto(t.id().toString(), t.gameId().toString(), t.userId().toString(), service.ownerName(t.userId()), t.name(), t.classId(),
                className, t.level(), t.xp(), toNext, scores, skills, t.hp(), t.hpMax(), t.money(), items, t.scoreMethod(), starter, skin, sheet);
    }
}
