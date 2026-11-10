/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.domain.audit.AuditRepository;
import net.quimmilho.pokednd.api.domain.audit.AuditRepository.AuditEntry;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.domain.settings.RuleSetRepository;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.realtime.GameEvents;
import net.quimmilho.pokednd.api.games.GameService;
import net.quimmilho.pokednd.api.settings.SettingsLayers.Layer;
import net.quimmilho.pokednd.api.settings.SettingsLayers.Resolved;

/**
 * The settings of a game (chapter 12): the value of every key of Appendix A, where it came from, and the ways an admin changes it:
 * one setting at a time, by resetting, by loading a rule set or a preset, and by undoing the last of these.
 *
 * <p>Every change is checked against the registry first, is kept only if nobody changed the settings in the meantime, and is written to
 * the audit trail with the state before and after. The undo in this class reverts settings changes only; the undo of the game's events
 * (12.5) comes with the event log.
 */
@ApplicationScoped
public class SettingsService {

    /** The audit actions of a settings change. All start with {@link #AUDIT_PREFIX}. */
    public static final String AUDIT_PREFIX = "settings.";
    public static final String ACTION_UPDATE = "settings.update";
    public static final String ACTION_RESET = "settings.reset";
    public static final String ACTION_RULESET = "settings.ruleset";
    public static final String ACTION_UNDO = "settings.undo";

    /** The game's settings, as one view: its rule set and the value of each key. */
    public record View(Game game, String ruleSetId, String ruleSetName, Map<String, Resolved> values) {
    }

    /** One setting that would change, or did. */
    public record Change(String key, JsonNode from, JsonNode to, String fromSource, String toSource) {
    }

    /** What loading a rule set did or would do. */
    public record Applied(String ruleSetId, String ruleSetName, List<Change> changes, boolean applied) {
    }

    @Inject
    SettingsRegistry registry;

    @Inject
    BuiltinRuleSets builtins;

    @Inject
    GameRepository games;

    @Inject
    RuleSetRepository ruleSets;

    @Inject
    AuditRepository audit;

    /** Changes are announced to the realtime channel (phase 40), after the transaction is committed. */
    @Inject
    Event<Object> events;

    @Inject
    GameService gameService;

    @Inject
    RuleSetService ruleSetService;

    @Inject
    CurrentUser current;

    // ------------------------------------------------------------------ reading

    /** The layers of a game, lowest first: its rule set, then its own changes. The built-in defaults are the layer below. */
    List<Layer> layers(Game game) {
        List<Layer> layers = new ArrayList<>();
        layers.add(new Layer(SettingsLayers.RULE_SET, ruleSetSettings(game.ruleSet())));
        layers.add(new Layer(SettingsLayers.GAME, valid(game.settings())));
        return layers;
    }

    private Map<String, JsonNode> ruleSetSettings(String ref) {
        if (ref == null) {
            return Map.of();
        }
        if (BuiltinRuleSets.isBuiltin(ref)) {
            return builtins.find(ref).map(BuiltinRuleSets.Builtin::settings).orElse(Map.of());
        }
        try {
            return ruleSets.find(UUID.fromString(ref)).map(r -> valid(r.settings())).orElse(Map.of());
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
    }

    /** The entries of a stored settings object that the registry still accepts; the others are ignored, so an old rule set cannot break a game. */
    private Map<String, JsonNode> valid(JsonNode stored) {
        Map<String, JsonNode> raw = new LinkedHashMap<>();
        if (stored != null && stored.isObject()) {
            stored.fields().forEachRemaining(e -> raw.put(e.getKey(), e.getValue()));
        }
        Map<String, JsonNode> kept = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : raw.entrySet()) {
            SettingsRegistry.Checked c = registry.check(e.getKey(), e.getValue());
            if (c.ok()) {
                kept.put(e.getKey(), c.value());
            }
        }
        return kept;
    }

    /** The value of one setting in a game. */
    public JsonNode value(Game game, String key) {
        SettingDef def = registry.get(key);
        if (def == null) {
            throw new IllegalArgumentException("There is no setting " + key);
        }
        return SettingsLayers.resolveOne(def, layers(game)).value();
    }

    public boolean bool(Game game, String key) {
        return value(game, key).asBoolean();
    }

    public int integer(Game game, String key) {
        return value(game, key).asInt();
    }

    /** Every setting of the game with where it came from. Anyone who is in the game may read them. */
    public View view(UUID gameId) {
        return view(gameService.authorize(gameId, Action.VIEW_GAME));
    }

    private View view(Game game) {
        String[] rs = describeRuleSet(game.ruleSet());
        return new View(game, rs[0], rs[1], SettingsLayers.resolve(registry, layers(game)));
    }

    /** The id and name of a game's rule set; the plain defaults when it has none. */
    private String[] describeRuleSet(String ref) {
        if (ref == null) {
            return new String[] {BuiltinRuleSets.DEFAULTS, builtins.find(BuiltinRuleSets.DEFAULTS).orElseThrow().name()};
        }
        if (BuiltinRuleSets.isBuiltin(ref)) {
            return new String[] {ref, builtins.find(ref).map(BuiltinRuleSets.Builtin::name).orElse(ref)};
        }
        return new String[] {ref, ruleSets.find(UUID.fromString(ref)).map(RuleSetRepository.RuleSet::name).orElse(ref)};
    }

    /** The settings of the game that differ from the defaults (what a saved rule set holds). The caller must be allowed to run the game. */
    public Map<String, JsonNode> differencesFromDefaults(UUID gameId) {
        Game game = gameService.authorize(gameId, Action.RUN_GAME);
        Map<String, JsonNode> now = new LinkedHashMap<>();
        SettingsLayers.resolve(registry, layers(game)).forEach((k, r) -> now.put(k, r.value()));
        return registry.withoutDefaults(now);
    }

    // ------------------------------------------------------------------ changing

    /**
     * Sets the game's own value of some settings; a value of JSON null removes the game's own value, so the rule set or the default shows
     * again. Nothing is changed unless every value is allowed.
     *
     * @throws ApiException 400 INVALID_SETTING (the details list each refused setting); 409 GAME_ARCHIVED or GAME_CHANGED
     */
    @Transactional
    public List<Resolved> change(UUID gameId, Map<String, JsonNode> changes) {
        Game game = editable(gameId);
        if (changes == null || changes.isEmpty()) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a \"settings\" object with at least one setting.");
        }
        List<Map<String, String>> errors = new ArrayList<>();
        Map<String, JsonNode> set = new LinkedHashMap<>();
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : changes.entrySet()) {
            if (e.getValue() == null || e.getValue().isNull()) {
                if (registry.has(e.getKey())) {
                    remove.add(e.getKey());
                } else {
                    errors.add(Map.of("key", e.getKey(), "message", e.getKey() + ": there is no such setting."));
                }
            } else {
                set.put(e.getKey(), e.getValue());
            }
        }
        Map<String, JsonNode> checked = registry.checkAll(set, errors);
        refuseIfAny(errors);
        ObjectNode overrides = copy(game.settings());
        remove.forEach(overrides::remove);
        checked.forEach(overrides::set);
        List<String> touched = new ArrayList<>(checked.keySet());
        touched.addAll(remove);
        return write(game, ACTION_UPDATE, null, game.ruleSet(), overrides, touched);
    }

    /**
     * Removes the game's own value of these settings (or of all of them), so each is what the rule set says, or else its default.
     *
     * @throws ApiException 400 INVALID_SETTING for a key that does not exist
     */
    @Transactional
    public List<Resolved> reset(UUID gameId, List<String> keys, boolean all) {
        Game game = editable(gameId);
        if (!all && (keys == null || keys.isEmpty())) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs \"keys\" (the settings to reset) or \"all\": true.");
        }
        ObjectNode overrides = copy(game.settings());
        List<String> touched = new ArrayList<>();
        if (all) {
            overrides.fieldNames().forEachRemaining(touched::add);
            overrides.removeAll();
        } else {
            List<Map<String, String>> errors = new ArrayList<>();
            for (String k : keys) {
                if (!registry.has(k)) {
                    errors.add(Map.of("key", String.valueOf(k), "message", k + ": there is no such setting."));
                }
            }
            refuseIfAny(errors);
            for (String k : keys) {
                overrides.remove(k);
                touched.add(k);
            }
        }
        return write(game, ACTION_RESET, null, game.ruleSet(), overrides, touched);
    }

    /**
     * Loads a rule set (or a preset) into the game: from now on it is the game's rule set. The game's own changes stay on top of it unless
     * {@code resetOverrides} is true. With {@code dryRun} nothing is changed, and the answer lists what would change.
     *
     * @throws ApiException 404 if the rule set does not exist or is not the caller's to use
     */
    @Transactional
    public Applied applyRuleSet(UUID gameId, String ruleSetRef, boolean dryRun, boolean resetOverrides) {
        Game game = editable(gameId);
        RuleSetService.Found rs = ruleSetService.requireUsable(ruleSetRef);
        String newRef = rs.builtin() && BuiltinRuleSets.DEFAULTS.equals(rs.id()) ? null : rs.id();
        ObjectNode overrides = resetOverrides ? JsonNodeFactory.instance.objectNode() : copy(game.settings());

        Map<String, Resolved> before = SettingsLayers.resolve(registry, layers(game));
        Game hypothetical = new Game(game.id(), game.serverId(), game.ownerId(), game.name(), game.state(), game.joinCode(), newRef, overrides,
                game.settingsVersion(), game.createdAt());
        Map<String, Resolved> after = SettingsLayers.resolve(registry, layers(hypothetical));
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, Resolved> e : before.entrySet()) {
            Resolved was = e.getValue();
            Resolved will = after.get(e.getKey());
            if (!SettingsRegistry.sameValue(was.value(), will.value())) {
                changes.add(new Change(e.getKey(), was.value(), will.value(), was.source(), will.source()));
            }
        }
        if (dryRun) {
            return new Applied(rs.id(), rs.name(), changes, false);
        }
        write(game, ACTION_RULESET, rs.id(), newRef, overrides, List.of());
        return new Applied(rs.id(), rs.name(), changes, true);
    }

    /**
     * Takes back the last change to the game's settings (a change, a reset or a loaded rule set) that has not been taken back yet, going
     * further back with each call. It restores the rule set and the game's own values exactly as they were before.
     *
     * @throws ApiException 409 NOTHING_TO_UNDO
     */
    @Transactional
    public List<Resolved> undo(UUID gameId) {
        Game game = editable(gameId);
        List<AuditEntry> entries = audit.forGame(gameId, AUDIT_PREFIX);
        Set<String> reverted = new HashSet<>();
        for (AuditEntry e : entries) {
            if (ACTION_UNDO.equals(e.action()) && e.target() != null) {
                reverted.add(e.target());
            }
        }
        AuditEntry last = null;
        for (AuditEntry e : entries) {
            if (ACTION_UNDO.equals(e.action()) || reverted.contains(e.id().toString()) || e.before() == null || e.after() == null) {
                continue;
            }
            if (last == null || e.after().path("version").asLong() > last.after().path("version").asLong()) {
                last = e;
            }
        }
        if (last == null) {
            throw new ApiException(409, "NOTHING_TO_UNDO", "There is no change to the settings to take back.");
        }
        JsonNode was = last.before();
        String ref = was.hasNonNull("ruleSet") ? was.get("ruleSet").asText() : null;
        ObjectNode overrides = copy(was.get("overrides"));
        Map<String, Resolved> beforeUndo = SettingsLayers.resolve(registry, layers(game));
        List<String> touched = new ArrayList<>();
        Game after = new Game(game.id(), game.serverId(), game.ownerId(), game.name(), game.state(), game.joinCode(), ref, overrides, game.settingsVersion(),
                game.createdAt());
        Map<String, Resolved> afterUndo = SettingsLayers.resolve(registry, layers(after));
        for (Map.Entry<String, Resolved> e : beforeUndo.entrySet()) {
            if (!SettingsRegistry.sameValue(e.getValue().value(), afterUndo.get(e.getKey()).value()) || !e.getValue().source().equals(afterUndo.get(e.getKey()).source())) {
                touched.add(e.getKey());
            }
        }
        return write(game, ACTION_UNDO, last.id().toString(), ref, overrides, touched);
    }

    // ------------------------------------------------------------------ helpers

    private Game editable(UUID gameId) {
        Game game = gameService.authorize(gameId, Action.RUN_GAME);
        if (game.state() == GameState.ARCHIVED) {
            throw new ApiException(409, "GAME_ARCHIVED", "The settings of an archived game cannot be changed.");
        }
        return game;
    }

    /** Keeps the new rule set and overrides if nobody changed the settings meanwhile, and writes the audit entry. Returns the touched settings as they now are. */
    private List<Resolved> write(Game game, String action, String target, String ruleSet, ObjectNode overrides, List<String> touched) {
        Instant now = Instant.now();
        if (!games.replaceSettings(game.id(), game.settingsVersion(), ruleSet, overrides, now)) {
            throw new ApiException(409, "GAME_CHANGED", "Someone changed this game's settings a moment ago. Look again and retry.");
        }
        audit.append(new AuditEntry(Ids.newId(), game.id(), current.id(), action, target, snapshot(game.ruleSet(), game.settings(), game.settingsVersion()),
                snapshot(ruleSet, overrides, game.settingsVersion() + 1), now));
        events.fire(new GameEvents.SettingsChanged(game.id()));
        Game changed = games.find(game.id()).orElseThrow();
        Map<String, Resolved> now2 = SettingsLayers.resolve(registry, layers(changed));
        List<Resolved> out = new ArrayList<>();
        for (String k : touched) {
            if (now2.containsKey(k)) {
                out.add(now2.get(k));
            }
        }
        return out;
    }

    private static ObjectNode snapshot(String ruleSet, JsonNode overrides, long version) {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        if (ruleSet == null) {
            o.putNull("ruleSet");
        } else {
            o.put("ruleSet", ruleSet);
        }
        o.set("overrides", overrides == null ? JsonNodeFactory.instance.objectNode() : overrides.deepCopy());
        o.put("version", version);
        return o;
    }

    private static ObjectNode copy(JsonNode node) {
        return node != null && node.isObject() ? ((ObjectNode) node).deepCopy() : JsonNodeFactory.instance.objectNode();
    }

    private static void refuseIfAny(List<Map<String, String>> errors) {
        if (errors.isEmpty()) {
            return;
        }
        String first = errors.get(0).get("message");
        String message = errors.size() == 1 ? first : first + " (and " + (errors.size() - 1) + " more)";
        throw new ApiException(400, "INVALID_SETTING", message, Map.of("errors", errors));
    }
}
