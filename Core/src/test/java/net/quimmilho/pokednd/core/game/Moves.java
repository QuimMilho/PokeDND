/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;

/**
 * Move data for the battle tests, written the way the importer writes it: the numbers of {@code attack.csv} and the {@code effect} that
 * the Showdown data gives (copied from the real data of Cobblemon 1.8.4, so a test uses what the game uses). Only moves that a test needs.
 */
final class Moves {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Moves() {
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(text, e);
        }
    }

    /** An automatic move. */
    static MoveEntry auto(String id, String name, String type, String category, int power, Integer accuracy, int pp, int priority, String target,
                          String effect) {
        return new MoveEntry(id, name, type, category, power, Optional.ofNullable(accuracy), pp, priority, target, name + " (test move).",
                EffectClass.AUTOMATIC, List.of(), List.of(), json(effect));
    }

    /** A scripted move; {@code handlers} are the Showdown code handlers. */
    static MoveEntry scripted(String id, String name, String type, String category, int power, Integer accuracy, int pp, int priority, String target,
                              String effect, List<String> reason, List<String> handlers) {
        return new MoveEntry(id, name, type, category, power, Optional.ofNullable(accuracy), pp, priority, target, name + " (test move).",
                EffectClass.SCRIPTED, reason, handlers, json(effect));
    }

    static ItemEntry berry(String id, int power, String type) {
        return new ItemEntry(id, id, "berries", List.of(), "", Optional.empty(), EffectClass.AUTOMATIC, "no-script", List.of(), Optional.empty(),
                json("{\"naturalGift\":{\"basePower\":" + power + ",\"type\":\"" + type + "\"},\"isBerry\":true}"), Optional.empty(), Optional.empty(), false,
                Optional.empty(), Optional.empty());
    }

    /** Every move the battle tests use. */
    static List<MoveEntry> all() {
        List<MoveEntry> m = new ArrayList<>();
        // ---- hit, damage, secondary effects (phase 45)
        m.add(auto("ember", "Ember", "Fire", "Special", 40, 100, 25, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":{\"chance\":10,\"status\":\"brn\"}}"));
        m.add(auto("flamethrower", "Flamethrower", "Fire", "Special", 90, 100, 15, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":{\"chance\":10,\"status\":\"brn\"}}"));
        m.add(auto("tackle", "Tackle", "Normal", "Physical", 40, 100, 35, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}"));
        m.add(auto("scratch", "Scratch", "Normal", "Physical", 40, 100, 35, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}"));
        m.add(auto("quickattack", "Quick Attack", "Normal", "Physical", 40, 100, 30, 1, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}"));
        m.add(auto("swift", "Swift", "Normal", "Special", 60, null, 20, 0, "allAdjacentFoes", "{\"flags\":{\"protect\":1},\"secondary\":null}"));
        m.add(auto("watergun", "Water Gun", "Water", "Special", 40, 100, 25, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null}"));
        m.add(auto("slash", "Slash", "Normal", "Physical", 70, 100, 20, 0, "normal", "{\"critRatio\":2,\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}"));
        m.add(auto("frostbreath", "Frost Breath", "Ice", "Special", 60, 90, 10, 0, "normal", "{\"willCrit\":true,\"flags\":{\"protect\":1},\"secondary\":null}"));
        m.add(auto("growl", "Growl", "Normal", "Status", 0, 100, 40, 0, "allAdjacentFoes", "{\"boosts\":{\"atk\":-1},\"flags\":{\"protect\":1,\"sound\":1},\"secondary\":null}"));
        m.add(auto("swordsdance", "Swords Dance", "Normal", "Status", 0, null, 20, 0, "self", "{\"boosts\":{\"atk\":2},\"flags\":{\"snatch\":1},\"secondary\":null}"));
        m.add(auto("sandattack", "Sand Attack", "Ground", "Status", 0, 100, 15, 0, "normal", "{\"boosts\":{\"accuracy\":-1},\"flags\":{\"protect\":1},\"secondary\":null}"));
        m.add(auto("willowisp", "Will-O-Wisp", "Fire", "Status", 0, 85, 15, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null,\"status\":\"brn\"}"));
        m.add(auto("poisonpowder", "Poison Powder", "Poison", "Status", 0, 75, 35, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null,\"status\":\"psn\"}"));
        m.add(auto("toxic", "Toxic", "Poison", "Status", 0, 90, 10, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null,\"status\":\"tox\"}"));
        m.add(auto("thunderwave", "Thunder Wave", "Electric", "Status", 0, 90, 20, 0, "normal", "{\"flags\":{\"protect\":1},\"ignoreImmunity\":false,\"secondary\":null,\"status\":\"par\"}"));
        m.add(auto("hypnosis", "Hypnosis", "Psychic", "Status", 0, 60, 20, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null,\"status\":\"slp\"}"));
        m.add(auto("icebeam", "Ice Beam", "Ice", "Special", 90, 100, 10, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":{\"chance\":10,\"status\":\"frz\"}}"));
        m.add(auto("confuseray", "Confuse Ray", "Ghost", "Status", 0, 100, 10, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null,\"volatileStatus\":\"confusion\"}"));
        m.add(auto("bite", "Bite", "Dark", "Physical", 60, 100, 25, 0, "normal", "{\"flags\":{\"bite\":1,\"contact\":1,\"protect\":1},\"secondary\":{\"chance\":30,\"volatileStatus\":\"flinch\"}}"));
        // ---- plain data effects (phase 46)
        m.add(auto("doubleedge", "Double-Edge", "Normal", "Physical", 120, 100, 15, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"recoil\":[33,100],\"secondary\":null}"));
        m.add(auto("absorb", "Absorb", "Grass", "Special", 20, 100, 25, 0, "normal", "{\"drain\":[1,2],\"flags\":{\"heal\":1,\"protect\":1},\"secondary\":null}"));
        m.add(auto("recover", "Recover", "Normal", "Status", 0, null, 5, 0, "self", "{\"flags\":{\"heal\":1,\"snatch\":1},\"heal\":[1,2],\"secondary\":null}"));
        m.add(auto("doubleslap", "Double Slap", "Normal", "Physical", 15, 85, 10, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"multihit\":[2,5],\"secondary\":null}"));
        m.add(auto("doublekick", "Double Kick", "Fighting", "Physical", 30, 100, 30, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"multihit\":2,\"secondary\":null}"));
        m.add(auto("fissure", "Fissure", "Ground", "Physical", 0, 30, 5, 0, "normal", "{\"flags\":{\"protect\":1},\"ohko\":true,\"secondary\":null}"));
        m.add(auto("seismictoss", "Seismic Toss", "Fighting", "Physical", 0, 100, 20, 0, "normal", "{\"damage\":\"level\",\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}"));
        m.add(auto("dragonrage", "Dragon Rage", "Dragon", "Special", 0, 100, 10, 0, "normal", "{\"damage\":40,\"flags\":{\"protect\":1},\"secondary\":null}"));
        m.add(auto("closecombat", "Close Combat", "Fighting", "Physical", 120, 100, 5, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null,\"self\":{\"boosts\":{\"def\":-1,\"spd\":-1}}}"));
        m.add(auto("flamecharge", "Flame Charge", "Fire", "Physical", 50, 100, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":{\"chance\":100,\"self\":{\"boosts\":{\"spe\":1}}}}"));
        m.add(auto("acid", "Acid", "Poison", "Special", 40, 100, 30, 0, "allAdjacentFoes", "{\"flags\":{\"protect\":1},\"secondary\":{\"chance\":10,\"boosts\":{\"spd\":-1}}}"));
        m.add(auto("firefang", "Fire Fang", "Fire", "Physical", 65, 95, 15, 0, "normal", "{\"flags\":{\"bite\":1,\"contact\":1,\"protect\":1},\"secondaries\":[{\"chance\":10,\"status\":\"brn\"},{\"chance\":10,\"volatileStatus\":\"flinch\"}]}"));
        m.add(auto("uturn", "U-turn", "Bug", "Physical", 70, 100, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null,\"selfSwitch\":true}"));
        m.add(auto("roar", "Roar", "Normal", "Status", 0, null, 20, -6, "normal", "{\"flags\":{\"sound\":1},\"forceSwitch\":true,\"secondary\":null}"));
        m.add(auto("explosion", "Explosion", "Normal", "Physical", 250, 100, 5, 0, "allAdjacent", "{\"flags\":{\"protect\":1},\"secondary\":null,\"selfdestruct\":\"always\"}"));
        m.add(auto("bodypress", "Body Press", "Fighting", "Physical", 80, 100, 10, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"overrideOffensiveStat\":\"def\",\"secondary\":null}"));
        m.add(auto("foulplay", "Foul Play", "Dark", "Physical", 95, 100, 15, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"overrideOffensivePokemon\":\"target\",\"secondary\":null}"));
        m.add(auto("spectralthief", "Spectral Thief", "Ghost", "Physical", 90, 100, 10, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null,\"stealsBoosts\":true}"));
        // ---- abilities and held items (phase 47): a Bug and a Grass move for the pinch abilities
        m.add(auto("bugbuzz", "Bug Buzz", "Bug", "Special", 40, 100, 10, 0, "normal", "{\"flags\":{\"protect\":1,\"sound\":1},\"secondary\":null}"));
        // ---- enemies attack trainers (phase 48)
        m.add(auto("wingattack", "Wing Attack", "Flying", "Physical", 60, 100, 35, 0, "any", "{\"flags\":{\"contact\":1,\"protect\":1,\"distance\":1},\"secondary\":null}"));
        // ---- the 25 scripted moves (the effect is the Showdown data of the real move)
        m.add(scripted("rest", "Rest", "Psychic", "Status", 0, null, 5, 0, "self", "{\"flags\":{\"heal\":1,\"snatch\":1},\"secondary\":null}", List.of("onHit", "onTry"), List.of("onHit", "onTry")));
        m.add(scripted("sleeptalk", "Sleep Talk", "Normal", "Status", 0, null, 10, 0, "self", "{\"flags\":{\"nosleeptalk\":1},\"secondary\":null,\"sleepUsable\":true}", List.of("onHit", "onTry"), List.of("onHit", "onTry")));
        m.add(scripted("helpinghand", "Helping Hand", "Normal", "Status", 0, null, 20, 5, "adjacentAlly", "{\"flags\":{\"bypasssub\":1},\"secondary\":null,\"volatileStatus\":\"helpinghand\"}", List.of("onStart", "volatileStatus:helpinghand"), List.of("onStart")));
        m.add(scripted("gigaimpact", "Giga Impact", "Normal", "Physical", 150, 90, 5, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1,\"recharge\":1},\"secondary\":null,\"self\":{\"volatileStatus\":\"mustrecharge\"}}", List.of("volatileStatus:mustrecharge"), List.of()));
        m.add(scripted("hyperbeam", "Hyper Beam", "Normal", "Special", 150, 90, 5, 0, "normal", "{\"flags\":{\"protect\":1,\"recharge\":1},\"secondary\":null,\"self\":{\"volatileStatus\":\"mustrecharge\"}}", List.of("volatileStatus:mustrecharge"), List.of()));
        m.add(scripted("blastburn", "Blast Burn", "Fire", "Special", 150, 90, 5, 0, "normal", "{\"flags\":{\"protect\":1,\"recharge\":1},\"secondary\":null,\"self\":{\"volatileStatus\":\"mustrecharge\"}}", List.of("volatileStatus:mustrecharge"), List.of()));
        m.add(scripted("protect", "Protect", "Normal", "Status", 0, null, 10, 4, "self", "{\"flags\":{},\"secondary\":null,\"stallingMove\":true,\"volatileStatus\":\"protect\"}", List.of("onHit", "volatileStatus:protect"), List.of("onHit")));
        m.add(scripted("endure", "Endure", "Normal", "Status", 0, null, 10, 4, "self", "{\"flags\":{},\"secondary\":null,\"stallingMove\":true,\"volatileStatus\":\"endure\"}", List.of("onHit", "volatileStatus:endure"), List.of("onHit")));
        m.add(scripted("substitute", "Substitute", "Normal", "Status", 0, null, 10, 0, "self", "{\"flags\":{\"nonsky\":1,\"snatch\":1},\"secondary\":null,\"volatileStatus\":\"substitute\"}", List.of("onHit", "volatileStatus:substitute"), List.of("onHit")));
        m.add(scripted("terablast", "Tera Blast", "Normal", "Special", 80, 100, 10, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null}", List.of("onModifyType"), List.of("onModifyType")));
        m.add(scripted("facade", "Facade", "Normal", "Physical", 70, 100, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("onBasePower"), List.of("onBasePower")));
        m.add(scripted("snore", "Snore", "Normal", "Special", 50, 100, 15, 0, "normal", "{\"flags\":{\"bypasssub\":1,\"protect\":1,\"sound\":1},\"secondary\":{\"chance\":30,\"volatileStatus\":\"flinch\"},\"sleepUsable\":true}", List.of("onTry"), List.of("onTry")));
        m.add(scripted("round", "Round", "Normal", "Special", 60, 100, 15, 0, "normal", "{\"flags\":{\"bypasssub\":1,\"protect\":1,\"sound\":1},\"secondary\":null}", List.of("basePowerCallback"), List.of("basePowerCallback")));
        m.add(scripted("attract", "Attract", "Normal", "Status", 0, 100, 15, 0, "normal", "{\"flags\":{\"bypasssub\":1,\"protect\":1,\"reflectable\":1},\"secondary\":null,\"volatileStatus\":\"attract\"}", List.of("onStart", "volatileStatus:attract"), List.of("onStart")));
        m.add(scripted("hiddenpower", "Hidden Power", "Normal", "Special", 60, 100, 15, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null}", List.of("onModifyType"), List.of("onModifyType")));
        m.add(scripted("return", "Return", "Normal", "Physical", 0, 100, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("basePowerCallback"), List.of("basePowerCallback")));
        m.add(scripted("frustration", "Frustration", "Normal", "Physical", 0, 100, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("basePowerCallback"), List.of("basePowerCallback")));
        m.add(scripted("endeavor", "Endeavor", "Normal", "Physical", 0, 100, 5, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("damageCallback"), List.of("damageCallback")));
        m.add(scripted("secretpower", "Secret Power", "Normal", "Physical", 70, 100, 20, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":{\"chance\":30,\"status\":\"par\"}}", List.of("onModifyMove"), List.of("onModifyMove")));
        m.add(scripted("curse", "Curse", "Ghost", "Status", 0, null, 10, 0, "normal", "{\"flags\":{\"bypasssub\":1},\"secondary\":null,\"volatileStatus\":\"curse\"}", List.of("onModifyMove", "volatileStatus:curse"), List.of("onModifyMove")));
        m.add(scripted("knockoff", "Knock Off", "Dark", "Physical", 65, 100, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("onBasePower", "onAfterHit"), List.of("onBasePower", "onAfterHit")));
        m.add(scripted("uproar", "Uproar", "Normal", "Special", 90, 100, 10, 0, "randomNormal", "{\"flags\":{\"bypasssub\":1,\"protect\":1,\"sound\":1},\"secondary\":null,\"self\":{\"volatileStatus\":\"uproar\"}}", List.of("onStart", "volatileStatus:uproar"), List.of("onStart")));
        m.add(scripted("covet", "Covet", "Normal", "Physical", 60, 100, 25, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("onAfterHit"), List.of("onAfterHit")));
        m.add(scripted("rollout", "Rollout", "Rock", "Physical", 30, 90, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("basePowerCallback", "onModifyMove"), List.of("basePowerCallback", "onModifyMove")));
        m.add(scripted("stompingtantrum", "Stomping Tantrum", "Ground", "Physical", 75, 100, 10, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("basePowerCallback"), List.of("basePowerCallback")));
        m.add(scripted("naturalgift", "Natural Gift", "Normal", "Physical", 0, 100, 15, 0, "normal", "{\"flags\":{\"protect\":1},\"secondary\":null}", List.of("onModifyType", "onPrepareHit"), List.of("onModifyType", "onPrepareHit")));
        // ---- scripted and not written yet
        m.add(scripted("brickbreak", "Brick Break", "Fighting", "Physical", 75, 100, 15, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("onTryHit"), List.of("onTryHit")));
        m.add(scripted("pursuit", "Pursuit Test", "Dark", "Physical", 0, 100, 20, 0, "normal", "{\"flags\":{\"contact\":1,\"protect\":1},\"secondary\":null}", List.of("basePowerCallback"), List.of("basePowerCallback")));
        return m;
    }
}
