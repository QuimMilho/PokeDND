/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The nine default classes (chapter 4.4), compared with a table typed in separately from the data file. */
class TrainerClassesTest {

    /** id, hit die, primary, saves, the five skills to pick two from; typed from the table in chapter 4.4. */
    private record Expected(String id, String name, int die, Ability primary, List<Ability> saves, List<String> skills) {
    }

    private static final List<Expected> TABLE = List.of(
            new Expected("ace_trainer", "Ace Trainer", 8, Ability.CHA, List.of(Ability.DEX, Ability.CHA),
                    List.of("pokemon_handling", "persuasion", "insight", "athletics", "intimidation")),
            new Expected("breeder", "Breeder", 6, Ability.WIS, List.of(Ability.WIS, Ability.CON),
                    List.of("pokemon_handling", "medicine", "nature", "insight", "survival")),
            new Expected("researcher", "Researcher", 6, Ability.INT, List.of(Ability.INT, Ability.WIS),
                    List.of("pokemon_lore", "investigation", "nature", "medicine", "insight")),
            new Expected("ranger", "Ranger", 10, Ability.DEX, List.of(Ability.STR, Ability.DEX),
                    List.of("survival", "perception", "stealth", "athletics", "pokemon_handling")),
            new Expected("hiker", "Hiker", 10, Ability.STR, List.of(Ability.STR, Ability.CON),
                    List.of("athletics", "survival", "perception", "intimidation", "nature")),
            new Expected("coordinator", "Coordinator", 6, Ability.CHA, List.of(Ability.DEX, Ability.CHA),
                    List.of("performance", "persuasion", "deception", "acrobatics", "pokemon_handling")),
            new Expected("psychic", "Psychic", 6, Ability.WIS, List.of(Ability.INT, Ability.WIS),
                    List.of("insight", "perception", "pokemon_lore", "deception", "medicine")),
            new Expected("collector", "Collector", 8, Ability.CHA, List.of(Ability.INT, Ability.CHA),
                    List.of("appraisal", "persuasion", "investigation", "sleight_of_hand", "insight")),
            new Expected("medic", "Medic", 8, Ability.WIS, List.of(Ability.WIS, Ability.CON),
                    List.of("medicine", "pokemon_handling", "insight", "perception", "nature")));

    @Test
    void thereAreNineDefaultClassesMatchingTheTable() {
        TrainerClasses classes = TrainerClasses.defaults();
        assertEquals(TABLE.stream().map(Expected::id).toList(), classes.ids());
        for (Expected e : TABLE) {
            TrainerClass c = classes.find(e.id()).orElseThrow();
            assertEquals(e.name(), c.name(), e.id());
            assertEquals(e.die(), c.hitDie(), e.id() + " hit die");
            assertEquals(e.primary(), c.primaryAbility(), e.id() + " primary ability");
            assertEquals(e.saves(), c.saves(), e.id() + " saves");
            assertEquals(2, c.skillChoices().pick(), e.id() + " picks two skills");
            assertEquals(e.skills(), c.skillChoices().from(), e.id() + " skills");
            assertFalse(c.description().isBlank(), e.id() + " has a description");
        }
    }

    @Test
    void everyClassHasSixFeaturesAtLevels1_3_6_10_14And20() {
        for (TrainerClass c : TrainerClasses.defaults().classes()) {
            assertEquals(List.of(1, 3, 6, 10, 14, 20), c.features().stream().map(ClassFeature::level).toList(), c.id());
            for (ClassFeature f : c.features()) {
                assertFalse(f.text().isBlank(), c.id() + "/" + f.id() + " has text");
                assertFalse(f.effects().isEmpty(), c.id() + "/" + f.id() + " has at least one effect (or narrative)");
            }
        }
    }

    @Test
    void featureNamesMatchChapter4_4() {
        List<List<String>> names = List.of(
                List.of("Commanding Presence", "Quick Switch", "Second Wind", "Critical Eye", "Unbreakable Bond", "Champion's Presence"),
                List.of("Gentle Hands", "Egg Whisperer", "Field Medic", "Natural Mender", "Perfect Care", "Soul Tender"),
                List.of("Field Notes", "Weakness Analysis", "Data Gathering", "Exploit Weakness", "Encyclopedic Mind", "Perfect Analysis"),
                List.of("Pathfinder", "Light Step", "Wild Whisperer", "Swift Retreat", "Trailblazer", "Master of the Wild"),
                List.of("Tough as Rock", "Stand Your Ground", "Heavy Pack", "Bulwark", "Mountain Stride", "Immovable"),
                List.of("Showmanship", "Appeal", "Crowd Favourite", "Combo Master", "Encore", "Grand Festival"),
                List.of("Foresight", "Mind Link", "Calm Mind", "Precognition", "Psychic Shield", "Clairvoyance"),
                List.of("Keen Eye", "Haggler", "Item Savvy", "Hidden Stash", "Treasure Hunter", "Midas Touch"),
                List.of("First Aid", "Triage", "Status Clear", "Field Hospital", "Resuscitate", "Miracle Worker"));
        List<TrainerClass> classes = TrainerClasses.defaults().classes();
        for (int i = 0; i < 9; i++) {
            assertEquals(names.get(i), classes.get(i).features().stream().map(ClassFeature::name).toList(), classes.get(i).id());
        }
    }

    @Test
    void theStartingKitExtrasMatchChapter4_4() {
        TrainerClasses classes = TrainerClasses.defaults();
        assertEquals(Map.of("poke_ball", 3), classes.find("ranger").orElseThrow().startingItems());
        assertEquals(Map.of("antidote", 2, "revive", 1), classes.find("medic").orElseThrow().startingItems());
        assertEquals(1000, classes.find("collector").orElseThrow().startingMoney());
        for (String other : List.of("ace_trainer", "breeder", "researcher", "hiker", "coordinator", "psychic")) {
            assertTrue(classes.find(other).orElseThrow().startingItems().isEmpty(), other);
            assertEquals(0, classes.find(other).orElseThrow().startingMoney(), other);
        }
    }

    @Test
    void everySkillAClassMentionsIsInTheSeventeenSkills() {
        assertEquals(17, SkillDef.defaults().size());
        assertEquals(List.of(), TrainerClasses.defaults().check(SkillDef.defaults()));
    }

    @Test
    void theSkillListMatchesChapter4_1() {
        Map<Ability, List<String>> expected = Map.of(
                Ability.STR, List.of("athletics"),
                Ability.DEX, List.of("acrobatics", "sleight_of_hand", "stealth"),
                Ability.INT, List.of("investigation", "nature", "pokemon_lore", "appraisal"),
                Ability.WIS, List.of("insight", "medicine", "perception", "survival", "pokemon_handling"),
                Ability.CHA, List.of("deception", "intimidation", "performance", "persuasion"));
        for (Map.Entry<Ability, List<String>> e : expected.entrySet()) {
            List<String> actual = SkillDef.defaults().stream().filter(s -> s.ability() == e.getKey()).map(SkillDef::id).toList();
            assertEquals(e.getValue().stream().sorted().toList(), actual.stream().sorted().toList(), e.getKey().name());
        }
        assertTrue(SkillDef.defaults().stream().noneMatch(s -> s.ability() == Ability.CON), "no skill uses CON");
    }

    @Test
    void theClassListRoundTripsThroughJson() {
        TrainerClasses classes = TrainerClasses.defaults();
        assertEquals(classes, TrainerClasses.fromJson(classes.toJson()));
    }

    @Test
    void anAdminCanEditAndAddAClass() {
        String json = TrainerClasses.defaults().toJson().replace("\"name\" : \"Medic\"", "\"name\" : \"Nurse\"");
        TrainerClasses edited = TrainerClasses.fromJson(json);
        assertEquals("Nurse", edited.find("medic").orElseThrow().name());

        String custom = """
                {"classes":[{"id":"gardener","name":"Gardener","hitDie":8,"primaryAbility":"wis","saves":["wis","con"],
                 "skillChoices":{"pick":1,"from":["nature","survival"]},
                 "features":[{"level":1,"id":"green_thumb","name":"Green Thumb","text":"Berries heal more.",
                   "effects":[{"key":"heal.itemBonusPct","value":10}]}]}]}
                """;
        TrainerClasses one = TrainerClasses.fromJson(custom);
        assertEquals(List.of("gardener"), one.ids());
        assertEquals(0, one.find("gardener").orElseThrow().acBonus());
    }

    @Test
    void aFeatureWithAnUnknownEffectKeyIsRefusedWithAReadableMessage() {
        String bad = TrainerClasses.defaults().toJson().replace("accuracy.bonusPp", "accuracy.bonusPoints");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TrainerClasses.fromJson(bad));
        assertTrue(e.getMessage().contains("accuracy.bonusPoints"), e.getMessage());
    }

    @Test
    void badClassDataIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TrainerClasses.fromJson("{\"classes\":[{\"id\":\"x\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> TrainerClasses.fromJson("not json"));
        TrainerClasses defaults = TrainerClasses.defaults();
        assertThrows(IllegalArgumentException.class,
                () -> new TrainerClasses(List.of(defaults.classes().get(0), defaults.classes().get(0))), "duplicate id");
    }

    @Test
    void aClassThatPicksASkillTheGameDoesNotHaveIsReported() {
        List<SkillDef> noLore = SkillDef.defaults().stream().filter(s -> !s.id().equals("pokemon_lore")).toList();
        List<String> problems = TrainerClasses.defaults().check(noLore);
        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.contains("researcher") && p.contains("pokemon_lore")), problems.toString());
        assertThrows(IllegalArgumentException.class,
                () -> new TrainerRulebook(TrainerClasses.defaults(), noLore, TrainerSettings.defaults()));
    }

    @Test
    void theEffectVocabularyHasEveryKeyOfChapter4_4() {
        List<String> spec = List.of("accuracy.bonusPp", "crit.stageBonus", "catch.multiplierBonus", "flee.bonusPp", "initiative.bonus",
                "initiative.advantage", "movement.bonus", "encounter.chanceShiftPp", "heal.itemBonusPct", "xp.bonusPct",
                "friendship.gainMultiplier", "damage.trainerReductionFlat", "hp.perLevelBonus", "skill.expertise", "skill.advantage",
                "skill.reroll", "battle.rerollPerBattle", "battle.freeSwitchPerBattle", "battle.freeItemPerBattle",
                "battle.surviveKoPerBattle", "battle.forceRerollPerBattle", "battle.styleDie", "battle.superEffectiveBonusPct",
                "battle.statusResistPp", "battle.startStage", "battle.interposePerBattle", "session.revive", "session.fullHeal",
                "session.survive", "money.bonusPct", "shop.discountPct", "shop.sellBonusPct", "loot.extraRolls", "bag.extraSlots",
                "map.revealAll", "narrative");
        assertEquals(spec.stream().sorted().toList(), EffectKeys.keys().stream().sorted().toList());
    }
}
