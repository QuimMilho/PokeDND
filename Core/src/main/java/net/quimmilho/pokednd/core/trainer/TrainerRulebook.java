/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The trainer rules of one game (chapter 4): its class list, its skill list and its settings. It creates trainers, works out
 * their sheet, and levels them up. Everything is checked, and a refusal comes with plain sentences ({@link TrainerProblem}).
 *
 * @param classes  the game's classes ({@code trainer.classes.set})
 * @param skills   the game's skills ({@code trainer.skills.set})
 * @param settings the trainer settings
 */
public record TrainerRulebook(TrainerClasses classes, List<SkillDef> skills, TrainerSettings settings) {

    /** The trainer levels that give an Ability Score Improvement (chapter 4.1). */
    public static final List<Integer> IMPROVEMENT_LEVELS = List.of(4, 8, 12, 16, 19);
    /** The points an Ability Score Improvement gives. */
    public static final int IMPROVEMENT_POINTS = 2;

    public TrainerRulebook {
        skills = List.copyOf(skills);
        Set<String> ids = new HashSet<>();
        for (SkillDef s : skills) {
            if (!ids.add(s.id())) {
                throw new IllegalArgumentException("Two skills have the id " + s.id());
            }
        }
        List<String> problems = classes.check(skills);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
    }

    /** The rules with the default classes, the seventeen skills and the default settings. */
    public static TrainerRulebook defaults() {
        return new TrainerRulebook(TrainerClasses.defaults(), SkillDef.defaults(), TrainerSettings.defaults());
    }

    /** A copy with other settings. */
    public TrainerRulebook withSettings(TrainerSettings value) {
        return new TrainerRulebook(classes, skills, value);
    }

    /** A copy with another class list. */
    public TrainerRulebook withClasses(TrainerClasses value) {
        return new TrainerRulebook(value, skills, settings);
    }

    /** A copy with another skill list. */
    public TrainerRulebook withSkills(List<SkillDef> value) {
        return new TrainerRulebook(classes, value, settings);
    }

    // ---------------------------------------------------------------- small formulas of 4.1

    /** The proficiency bonus of a trainer level: +2 for 1 to 4, +3 for 5 to 8, +4 for 9 to 12, +5 for 13 to 16, +6 for 17 to 20. */
    public static int proficiencyBonus(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("A level is at least 1, not " + level);
        }
        return 2 + (level - 1) / 4;
    }

    /** What the hit die gives on a level after the first: the average rounded up (d6 gives 4, d8 gives 5, d10 gives 6). */
    public static int hitDieAverage(int hitDie) {
        return hitDie / 2 + 1;
    }

    /** True if this trainer level gives an Ability Score Improvement. */
    public static boolean givesImprovement(int level) {
        return IMPROVEMENT_LEVELS.contains(level);
    }

    // ---------------------------------------------------------------- creating a trainer (4.3)

    /**
     * What a player chooses when making a trainer (chapter 4.3). The starter Pokémon and the skin are chosen in the same
     * wizard but are not part of the sheet.
     *
     * @param name         the trainer's name
     * @param classId      the class
     * @param method       how the scores were made
     * @param scores       the six scores
     * @param skills       the skills picked from the class list
     * @param improvements points for the Ability Score Improvements a trainer starting above level 3 has already earned (empty
     *                     at the default starting level of 1)
     */
    public record CreateRequest(String name, String classId, ScoreMethod method, AbilityScores scores, List<String> skills,
                                Map<Ability, Integer> improvements) {

        public CreateRequest {
            skills = skills == null ? List.of() : List.copyOf(skills);
            improvements = improvements == null ? Map.of() : Map.copyOf(improvements);
        }

        /** A request for a trainer that starts at level 1 or 2 or 3: no improvements to hand out. */
        public CreateRequest(String name, String classId, ScoreMethod method, AbilityScores scores, List<String> skills) {
            this(name, classId, method, scores, skills, Map.of());
        }
    }

    /** Every reason this request breaks the rules, empty if it is fine. Names are only checked for their shape. */
    public List<TrainerProblem> check(CreateRequest request) {
        return check(request, List.of());
    }

    /** Every reason this request breaks the rules, with the names already used in the game checked for duplicates. */
    public List<TrainerProblem> check(CreateRequest r, Collection<String> namesInGame) {
        List<TrainerProblem> problems = new ArrayList<>(TrainerNames.check(r.name(), namesInGame));

        TrainerClass cls = r.classId() == null ? null : classes.find(r.classId()).orElse(null);
        if (cls == null) {
            problems.add(new TrainerProblem("unknown_class", "class", r.classId() == null || r.classId().isBlank()
                    ? "Choose a class."
                    : "There is no class \"" + r.classId() + "\" in this game. The classes are: " + String.join(", ", classes.ids()) + "."));
        }

        if (r.method() == null || r.scores() == null) {
            problems.add(new TrainerProblem("scores_missing", "scores", "Choose a method and set all six ability scores."));
        } else {
            problems.addAll(AbilityScoreRules.check(r.method(), r.scores(), settings));
        }

        if (cls != null) {
            problems.addAll(checkSkills(cls, r.skills()));
        }
        if (r.scores() != null) {
            problems.addAll(checkStartImprovements(r));
        }
        return problems;
    }

    private List<TrainerProblem> checkSkills(TrainerClass cls, List<String> picked) {
        List<TrainerProblem> problems = new ArrayList<>();
        int pick = cls.skillChoices().pick();
        if (picked.size() != pick) {
            problems.add(new TrainerProblem("skill_count", "skills", "The " + cls.name() + " picks " + pick + " skills, but "
                    + picked.size() + " were chosen."));
        }
        Set<String> seen = new HashSet<>();
        for (String s : picked) {
            if (!seen.add(s)) {
                problems.add(new TrainerProblem("skill_duplicate", "skills", skillName(s) + " was chosen twice."));
            } else if (!cls.skillChoices().from().contains(s)) {
                problems.add(new TrainerProblem("skill_not_allowed", "skills", skillName(s) + " is not one of the skills the "
                        + cls.name() + " can pick: " + cls.skillChoices().from().stream().map(this::skillName)
                        .collect(java.util.stream.Collectors.joining(", ")) + "."));
            }
        }
        return problems;
    }

    private List<TrainerProblem> checkStartImprovements(CreateRequest r) {
        int start = settings.xp().levelStart();
        int owed = IMPROVEMENT_POINTS * (int) IMPROVEMENT_LEVELS.stream().filter(l -> l <= start).count();
        List<TrainerProblem> problems = new ArrayList<>();
        int given = r.improvements().values().stream().mapToInt(Integer::intValue).sum();
        if (r.improvements().values().stream().anyMatch(v -> v < 0)) {
            problems.add(new TrainerProblem("improvement_negative", "improvement", "Ability improvements cannot take points away."));
        } else if (given != owed) {
            problems.add(new TrainerProblem("improvement_points", "improvement", owed == 0
                    ? "A trainer who starts at level " + start + " has no ability improvements to spend."
                    : "A trainer who starts at level " + start + " spends exactly " + owed + " improvement points, not " + given + "."));
        } else {
            AbilityScores after = r.scores().plus(r.improvements());
            for (Ability a : Ability.values()) {
                if (after.get(a) > AbilityScores.IMPROVEMENT_CAP) {
                    problems.add(new TrainerProblem("score_cap", "improvement", a.label() + " would be " + after.get(a)
                            + ", but no score goes above " + AbilityScores.IMPROVEMENT_CAP + "."));
                }
            }
        }
        return problems;
    }

    /**
     * Makes a trainer, or throws {@link InvalidTrainerException} listing every problem.
     *
     * @param request what the player chose
     * @return a trainer at {@code trainer.level.start}, at full HP, with the starting kit
     */
    public Trainer create(CreateRequest request) {
        return create(request, List.of());
    }

    /** Like {@link #create(CreateRequest)}, also refusing a name that another trainer of the game already has. */
    public Trainer create(CreateRequest request, Collection<String> namesInGame) {
        List<TrainerProblem> problems = check(request, namesInGame);
        if (!problems.isEmpty()) {
            throw new InvalidTrainerException(problems);
        }
        TrainerClass cls = classes.find(request.classId()).orElseThrow();
        AbilityScores scores = request.scores().plus(request.improvements());
        int level = settings.xp().levelStart();

        int maxHp = hpAtLevel(cls, 1, scores.modifier(Ability.CON));
        for (int l = 2; l <= level; l++) {
            maxHp += hpGain(cls, l, scores.modifier(Ability.CON));
        }

        Map<String, Integer> items = new TreeMap<>(settings.startItems());
        cls.startingItems().forEach((item, n) -> items.merge(item, n, Integer::sum));
        return new Trainer(request.name(), cls.id(), level, settings.xp().totalXp(level), scores, request.skills(), maxHp, maxHp,
                settings.startMoney() + cls.startingMoney(), items);
    }

    /** HP at level 1: the hit die at its maximum plus the CON modifier, at least 1, plus the class's HP-per-level bonus. */
    private int hpAtLevel(TrainerClass cls, int level, int conModifier) {
        return Math.max(1, cls.hitDie() + conModifier) + perLevelBonus(cls, level);
    }

    /** HP gained on a level after the first: the die's average rounded up plus the CON modifier, at least 1, plus the bonus. */
    private int hpGain(TrainerClass cls, int level, int conModifier) {
        return Math.max(1, hitDieAverage(cls.hitDie()) + conModifier) + perLevelBonus(cls, level);
    }

    private int perLevelBonus(TrainerClass cls, int level) {
        return EffectTotals.of(cls.featuresUpTo(level), proficiencyBonus(level)).intValue("hp.perLevelBonus");
    }

    // ---------------------------------------------------------------- the sheet (4.1)

    /** The trainer's whole sheet. Throws if the trainer's class is not in this game's class list. */
    public Sheet sheet(Trainer trainer) {
        TrainerClass cls = classes.find(trainer.classId()).orElseThrow(() -> new IllegalArgumentException(
                "The class " + trainer.classId() + " is not in this game's class list"));
        int prof = proficiencyBonus(trainer.level());
        List<ClassFeature> features = cls.featuresUpTo(trainer.level());
        EffectTotals effects = EffectTotals.of(features, prof);

        Map<Ability, Integer> mods = new EnumMap<>(Ability.class);
        Map<Ability, Integer> saves = new EnumMap<>(Ability.class);
        for (Ability a : Ability.values()) {
            int m = trainer.scores().modifier(a);
            mods.put(a, m);
            saves.put(a, m + (cls.saves().contains(a) ? prof : 0));
        }

        // Expertise in a skill also makes the trainer proficient in it (Suggested, Appendix D)
        Set<String> proficient = new LinkedHashSet<>(trainer.skills());
        proficient.addAll(effects.expertise());
        Map<String, Integer> bonuses = new TreeMap<>();
        for (SkillDef s : skills) {
            int b = mods.get(s.ability());
            if (proficient.contains(s.id())) {
                b += prof;
            }
            if (effects.expertise().contains(s.id())) {
                b += prof;
            }
            bonuses.put(s.id(), b);
        }
        Integer handling = bonuses.get(SkillDef.POKEMON_HANDLING);
        int handlingModifier = handling != null ? handling : mods.get(Ability.WIS);
        return new Sheet(trainer, cls, prof, mods, 10 + mods.get(Ability.DEX) + cls.acBonus(), saves, Set.copyOf(proficient), bonuses,
                handlingModifier, features, effects);
    }

    // ---------------------------------------------------------------- XP and levelling (4.5)

    /** A copy of the trainer with more total XP. The level does not change until the player confirms {@link #levelUp}. */
    public Trainer addXp(Trainer trainer, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("XP cannot be negative");
        }
        return new Trainer(trainer.name(), trainer.classId(), trainer.level(), trainer.xp() + amount, trainer.scores(), trainer.skills(),
                trainer.maxHp(), trainer.hp(), trainer.money(), trainer.items());
    }

    /** How many level-ups the trainer's XP has earned but the player has not yet confirmed. */
    public int pendingLevels(Trainer trainer) {
        return Math.max(0, settings.xp().levelFor(trainer.xp()) - trainer.level());
    }

    /**
     * What the next level-up will give, for the walk-through of chapter 4.5.
     *
     * @param level                the new level
     * @param proficiencyBonus     the proficiency bonus at the new level
     * @param improvement          true if this level gives an Ability Score Improvement (levels 4, 8, 12, 16, 19)
     * @param improvementPoints    the points to spread (2), or 0
     * @param hpGain               the HP gained, with the current CON modifier (an improvement to CON is not counted)
     * @param newFeatures          the class features that arrive at this level
     */
    public record LevelUpPreview(int level, int proficiencyBonus, boolean improvement, int improvementPoints, int hpGain,
                                 List<ClassFeature> newFeatures) {
    }

    /** What the trainer's next level-up gives. Throws if no level-up is waiting. */
    public LevelUpPreview previewLevelUp(Trainer trainer) {
        requirePending(trainer);
        TrainerClass cls = classes.find(trainer.classId()).orElseThrow();
        int next = trainer.level() + 1;
        boolean asi = givesImprovement(next);
        return new LevelUpPreview(next, proficiencyBonus(next), asi, asi ? IMPROVEMENT_POINTS : 0,
                hpGain(cls, next, trainer.scores().modifier(Ability.CON)), cls.featuresAt(next));
    }

    /** Every reason this level-up is refused, empty if it is fine. {@code improvement} is empty on levels without one. */
    public List<TrainerProblem> checkLevelUp(Trainer trainer, Map<Ability, Integer> improvement) {
        List<TrainerProblem> problems = new ArrayList<>();
        if (pendingLevels(trainer) == 0) {
            int cap = settings.xp().levelCap();
            problems.add(new TrainerProblem("no_level_pending", "level", trainer.level() >= cap
                    ? "The trainer is already at the highest level, " + cap + "."
                    : "The trainer needs " + (settings.xp().totalXp(trainer.level() + 1) - trainer.xp()) + " more XP for level "
                            + (trainer.level() + 1) + "."));
            return problems;
        }
        int next = trainer.level() + 1;
        int points = improvement.values().stream().mapToInt(Integer::intValue).sum();
        if (improvement.values().stream().anyMatch(v -> v < 0)) {
            problems.add(new TrainerProblem("improvement_negative", "improvement", "Ability improvements cannot take points away."));
        } else if (givesImprovement(next)) {
            if (points != IMPROVEMENT_POINTS) {
                problems.add(new TrainerProblem("improvement_points", "improvement", "Level " + next + " gives an Ability Score "
                        + "Improvement: spread exactly " + IMPROVEMENT_POINTS + " points, not " + points + "."));
            } else {
                AbilityScores after = trainer.scores().plus(improvement);
                for (Ability a : Ability.values()) {
                    if (after.get(a) > AbilityScores.IMPROVEMENT_CAP) {
                        problems.add(new TrainerProblem("score_cap", "improvement", a.label() + " would be " + after.get(a)
                                + ", but no score goes above " + AbilityScores.IMPROVEMENT_CAP + "."));
                    }
                }
            }
        } else if (points != 0) {
            problems.add(new TrainerProblem("improvement_not_allowed", "improvement", "Level " + next
                    + " has no Ability Score Improvement. They come at levels " + IMPROVEMENT_LEVELS + "."));
        }
        return problems;
    }

    /** Raises the trainer one level, or throws {@link InvalidTrainerException}. The new HP are added to the maximum and to the current HP. */
    public Trainer levelUp(Trainer trainer, Map<Ability, Integer> improvement) {
        List<TrainerProblem> problems = checkLevelUp(trainer, improvement);
        if (!problems.isEmpty()) {
            throw new InvalidTrainerException(problems);
        }
        TrainerClass cls = classes.find(trainer.classId()).orElseThrow();
        int next = trainer.level() + 1;
        AbilityScores scores = trainer.scores().plus(improvement);
        int gain = hpGain(cls, next, scores.modifier(Ability.CON));
        return new Trainer(trainer.name(), trainer.classId(), next, trainer.xp(), scores, trainer.skills(), trainer.maxHp() + gain,
                trainer.hp() + gain, trainer.money(), trainer.items());
    }

    private void requirePending(Trainer trainer) {
        if (pendingLevels(trainer) == 0) {
            throw new InvalidTrainerException(checkLevelUp(trainer, Map.of()));
        }
    }

    private String skillName(String id) {
        return skills.stream().filter(s -> s.id().equals(id)).map(SkillDef::name).findFirst().orElse(id);
    }
}
