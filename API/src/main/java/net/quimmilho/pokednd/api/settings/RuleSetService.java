/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.auth.Role;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository;
import net.quimmilho.pokednd.api.domain.settings.RuleSetRepository;
import net.quimmilho.pokednd.api.domain.settings.RuleSetRepository.RuleSet;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.servers.ServerService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Rule sets (chapter 12.3): named bundles of settings and data that an admin saves, shares as a JSON file and loads into games. The
 * built-in ones (the defaults and the presets of 12.1) can be read and exported but not changed.
 *
 * <p>Who may see a rule set: the built-in ones and the {@code site} ones, everybody who is logged in; a {@code server} one, the admins
 * of that server; a {@code user} one, its owner. The site admin sees and changes all of them. A person who may not see a rule set is
 * told it does not exist.
 */
@ApplicationScoped
public class RuleSetService {

    /** The version of the file format of 12.3. */
    public static final int FORMAT_VERSION = 1;
    public static final int NAME_MAX = 80;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A rule set, built-in or saved, as the REST layer shows it. {@code editable} is for the signed-in person. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record View(String id, String scope, UUID ownerId, UUID serverId, String name, String description, int formatVersion, String basedOn,
                       JsonNode settings, JsonNode data, Instant createdAt, Instant updatedAt, boolean editable) {
    }

    /** The id and name of a rule set that someone may use; {@code builtin} says whether it comes with the code. */
    public record Found(String id, String name, boolean builtin) {
    }

    /** What a person sends to make or change a rule set. When changing one, a field that is null stays as it was. */
    public record Input(String name, String scope, UUID serverId, String basedOn, JsonNode settings, JsonNode data, UUID fromGameId) {
    }

    @Inject
    RuleSetRepository repo;

    @Inject
    BuiltinRuleSets builtins;

    @Inject
    SettingsRegistry registry;

    @Inject
    SettingsService settingsService;

    @Inject
    GameRepository games;

    @Inject
    ServerService servers;

    @Inject
    CurrentUser current;

    @ConfigProperty(name = "pokednd.rulesets.max-per-user", defaultValue = "100")
    int maxPerUser;

    // ------------------------------------------------------------------ who may do what

    private boolean isSiteAdmin() {
        return current.siteRoles().contains(Role.SITE_ADMIN);
    }

    private boolean canRead(RuleSet r) {
        if (isSiteAdmin()) {
            return true;
        }
        return switch (r.scope()) {
            case RuleSetRepository.SCOPE_SITE -> true;
            case RuleSetRepository.SCOPE_SERVER -> r.serverId() != null && servers.isAdmin(r.serverId(), current.id());
            default -> r.ownerId().equals(current.id());
        };
    }

    private boolean canWrite(RuleSet r) {
        if (isSiteAdmin()) {
            return true;
        }
        return switch (r.scope()) {
            case RuleSetRepository.SCOPE_SITE -> false;
            case RuleSetRepository.SCOPE_SERVER -> r.serverId() != null && servers.isAdmin(r.serverId(), current.id());
            default -> r.ownerId().equals(current.id());
        };
    }

    // ------------------------------------------------------------------ reading

    /** Everything the signed-in person may use: the built-in rule sets, then the site's, their servers' and their own. */
    public List<View> list() {
        UUID me = current.id();
        List<View> out = new ArrayList<>();
        builtins.all().forEach(b -> out.add(view(b)));
        repo.ofScope(RuleSetRepository.SCOPE_SITE).forEach(r -> out.add(view(r)));
        List<UUID> adminOf = isSiteAdmin() ? servers.all().stream().map(ServerRepository.Server::id).toList() : servers.administeredBy(me).stream().map(ServerRepository.Server::id).toList();
        repo.ofServers(adminOf).forEach(r -> out.add(view(r)));
        repo.ownedBy(me).forEach(r -> out.add(view(r)));
        return out;
    }

    /** @throws ApiException 404 if it does not exist or the person may not see it */
    public View get(String id) {
        if (BuiltinRuleSets.isBuiltin(id)) {
            return view(builtins.find(id).orElseThrow(() -> ApiException.notFound("This rule set")));
        }
        return view(readable(id));
    }

    /** Checks that the person may load this rule set into a game: a built-in one, or a saved one they may see. */
    public Found requireUsable(String id) {
        if (id == null || id.isBlank()) {
            throw ApiException.badRequest("BAD_REQUEST", "The request needs a ruleSetId, such as \"builtin:casual\".");
        }
        if (BuiltinRuleSets.isBuiltin(id)) {
            BuiltinRuleSets.Builtin b = builtins.find(id).orElseThrow(() -> ApiException.notFound("This rule set"));
            return new Found(b.id(), b.name(), true);
        }
        RuleSet r = readable(id);
        return new Found(r.id().toString(), r.name(), false);
    }

    private RuleSet readable(String id) {
        UUID uuid = parse(id);
        RuleSet r = repo.find(uuid).orElseThrow(() -> ApiException.notFound("This rule set"));
        if (!canRead(r)) {
            throw ApiException.notFound("This rule set");
        }
        return r;
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("This rule set");
        }
    }

    // ------------------------------------------------------------------ making, changing, deleting

    /**
     * Saves a new rule set. With {@code fromGameId} it holds the game's rules (only what differs from the defaults); the caller must run
     * that game.
     *
     * @throws ApiException 400 for a bad name, setting or data; 403 for a scope the person may not use; 409 TOO_MANY_RULESETS
     */
    @Transactional
    public View create(Input in) {
        UUID me = current.id();
        String scope = in.scope() == null || in.scope().isBlank() ? RuleSetRepository.SCOPE_USER : in.scope();
        UUID serverId = null;
        switch (scope) {
            case RuleSetRepository.SCOPE_USER -> {
                if (repo.countOwnedBy(me) >= maxPerUser) {
                    throw new ApiException(409, "TOO_MANY_RULESETS", "You have " + maxPerUser + " rule sets already. Delete one first.");
                }
            }
            case RuleSetRepository.SCOPE_SITE -> {
                if (!isSiteAdmin()) {
                    throw ApiException.forbidden();
                }
            }
            case RuleSetRepository.SCOPE_SERVER -> {
                if (in.serverId() == null) {
                    throw ApiException.badRequest("BAD_REQUEST", "A rule set of a server needs a serverId.");
                }
                servers.get(in.serverId());
                if (!isSiteAdmin() && !servers.isAdmin(in.serverId(), me)) {
                    throw ApiException.forbidden();
                }
                serverId = in.serverId();
            }
            default -> throw ApiException.badRequest("BAD_REQUEST", "The scope is user, server or site.");
        }
        JsonNode settings;
        if (in.fromGameId() != null) {
            if (in.settings() != null && !in.settings().isNull()) {
                throw ApiException.badRequest("BAD_REQUEST", "Give either settings or fromGameId, not both.");
            }
            settings = toObject(settingsService.differencesFromDefaults(in.fromGameId()));
        } else {
            settings = cleanSettings(in.settings());
        }
        Instant now = Instant.now();
        RuleSet made = new RuleSet(Ids.newId(), scope, me, serverId, cleanName(in.name()), FORMAT_VERSION, cleanBasedOn(in.basedOn(), null), settings,
                cleanData(in.data()), now, now);
        repo.create(made);
        return view(made);
    }

    /**
     * Replaces the name, the settings and the data of a saved rule set. A saved rule set used by games can be changed: the games then
     * see the new values.
     *
     * @throws ApiException 404 if the person may not see it; 403 if they may see but not change it
     */
    @Transactional
    public View update(String id, Input in) {
        if (BuiltinRuleSets.isBuiltin(id)) {
            throw new ApiException(403, "BUILTIN_READ_ONLY", "The built-in rule sets cannot be changed. Save a copy instead.");
        }
        RuleSet r = readable(id);
        if (!canWrite(r)) {
            throw ApiException.forbidden();
        }
        String name = in.name() == null ? r.name() : cleanName(in.name());
        JsonNode settings = in.settings() == null ? r.settings() : cleanSettings(in.settings());
        JsonNode data = in.data() == null ? r.data() : cleanData(in.data());
        String basedOn = in.basedOn() == null ? r.basedOn() : cleanBasedOn(in.basedOn(), r.id());
        repo.update(r.id(), name, basedOn, settings, data, Instant.now());
        return view(repo.find(r.id()).orElseThrow());
    }

    /**
     * Deletes a saved rule set.
     *
     * @throws ApiException 409 RULESET_IN_USE while a game uses it
     */
    @Transactional
    public void delete(String id) {
        if (BuiltinRuleSets.isBuiltin(id)) {
            throw new ApiException(403, "BUILTIN_READ_ONLY", "The built-in rule sets cannot be deleted.");
        }
        RuleSet r = readable(id);
        if (!canWrite(r)) {
            throw ApiException.forbidden();
        }
        if (games.countUsingRuleSet(r.id()) > 0) {
            throw new ApiException(409, "RULESET_IN_USE", "A game uses this rule set. Load another one into that game first.");
        }
        repo.delete(r.id());
    }

    // ------------------------------------------------------------------ files

    /**
     * Reads a rule set file (the format of 12.3) and saves it as a rule set of the signed-in person.
     *
     * @throws ApiException 400 if the file is not a rule set or has settings this version does not know; 422 UNSUPPORTED_FORMAT if it is from a newer version
     */
    @Transactional
    public View importFile(JsonNode file) {
        if (file == null || !file.isObject()) {
            throw ApiException.badRequest("BAD_RULESET_FILE", "This is not a rule set file: it must be a JSON object with a name, a formatVersion and settings.");
        }
        JsonNode version = file.get("formatVersion");
        if (version == null || !version.isIntegralNumber()) {
            throw ApiException.badRequest("BAD_RULESET_FILE", "This is not a rule set file: it has no formatVersion.");
        }
        if (version.asInt() != FORMAT_VERSION) {
            throw new ApiException(422, "UNSUPPORTED_FORMAT", "This rule set file has format version " + version.asInt() + "; this server reads version " + FORMAT_VERSION + ".");
        }
        String basedOn = file.hasNonNull("basedOn") ? file.get("basedOn").asText() : null;
        // a note from another server may name a rule set that is not here; keep the note only when it is a built-in one
        if (basedOn != null && !BuiltinRuleSets.isBuiltin(basedOn)) {
            basedOn = null;
        }
        return create(new Input(file.hasNonNull("name") ? file.get("name").asText() : null, RuleSetRepository.SCOPE_USER, null, basedOn, file.get("settings"),
                file.get("data"), null));
    }

    /** The rule set as a file (the format of 12.3), to be saved and shared. */
    public ObjectNode export(String id) {
        View v = get(id);
        ObjectNode file = JsonNodeFactory.instance.objectNode();
        file.put("name", v.name());
        file.put("formatVersion", FORMAT_VERSION);
        file.put("basedOn", v.basedOn() == null ? BuiltinRuleSets.DEFAULTS : v.basedOn());
        file.set("settings", v.settings());
        file.set("data", v.data());
        return file;
    }

    // ------------------------------------------------------------------ checks and views

    private static String cleanName(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > NAME_MAX || n.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("INVALID_NAME", "A rule set name has 1 to " + NAME_MAX + " characters.");
        }
        return n;
    }

    private String cleanBasedOn(String basedOn, UUID itself) {
        if (basedOn == null || basedOn.isBlank()) {
            return BuiltinRuleSets.DEFAULTS;
        }
        if (BuiltinRuleSets.isBuiltin(basedOn)) {
            if (builtins.find(basedOn).isEmpty()) {
                throw ApiException.badRequest("INVALID_BASED_ON", "There is no built-in rule set " + basedOn + ".");
            }
            return basedOn;
        }
        try {
            UUID other = UUID.fromString(basedOn);
            if (other.equals(itself) || repo.find(other).filter(this::canRead).isEmpty()) {
                throw ApiException.badRequest("INVALID_BASED_ON", "There is no rule set " + basedOn + " that you can use as a starting point.");
            }
            return other.toString();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_BASED_ON", "basedOn must be builtin:<name> or the id of a rule set.");
        }
    }

    private JsonNode cleanSettings(JsonNode settings) {
        if (settings == null || settings.isNull()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!settings.isObject()) {
            throw ApiException.badRequest("INVALID_SETTING", "settings must be an object of setting keys and values.");
        }
        Map<String, JsonNode> raw = new LinkedHashMap<>();
        settings.fields().forEachRemaining(e -> raw.put(e.getKey(), e.getValue()));
        List<Map<String, String>> errors = new ArrayList<>();
        Map<String, JsonNode> kept = registry.checkAll(raw, errors);
        if (!errors.isEmpty()) {
            String first = errors.get(0).get("message");
            throw new ApiException(400, "INVALID_SETTING", errors.size() == 1 ? first : first + " (and " + (errors.size() - 1) + " more)", Map.of("errors", errors));
        }
        return toObject(kept);
    }

    private static JsonNode cleanData(JsonNode data) {
        if (data == null || data.isNull()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!data.isObject()) {
            throw ApiException.badRequest("INVALID_DATA", "data must be an object (classes, skills, powerTable, typeChart, loot, presets).");
        }
        try {
            if (JSON.writeValueAsBytes(data).length > SettingsRegistry.DATA_MAX_BYTES) {
                throw ApiException.badRequest("INVALID_DATA", "data is too large (the limit is " + SettingsRegistry.DATA_MAX_BYTES / 1024 + " KB).");
            }
        } catch (IOException e) {
            throw ApiException.badRequest("INVALID_DATA", "data cannot be read.");
        }
        return data;
    }

    private static ObjectNode toObject(Map<String, JsonNode> map) {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        map.forEach(o::set);
        return o;
    }

    private View view(BuiltinRuleSets.Builtin b) {
        return new View(b.id(), "builtin", null, null, b.name(), b.description(), FORMAT_VERSION, BuiltinRuleSets.DEFAULTS, toObject(b.settings()),
                JsonNodeFactory.instance.objectNode(), null, null, false);
    }

    private View view(RuleSet r) {
        return new View(r.id().toString(), r.scope(), r.ownerId(), r.serverId(), r.name(), null, r.formatVersion(), r.basedOn(), r.settings(), r.data(),
                r.createdAt(), r.updatedAt(), canWrite(r));
    }
}
