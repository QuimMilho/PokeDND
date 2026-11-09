/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.ne;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.GameAdminRow;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.GameRow;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.MembershipRow;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.ServerAdminRow;
import org.bson.conversions.Bson;

/**
 * The MongoDB adapter of the games, their admins and their members (chapter 16.3).
 *
 * <p>Two rules that SQL keeps with partial unique indexes are kept here with sparse unique indexes on a field that exists only while the
 * rule applies: {@code activeServerId} (one active game per server) and {@code joinCode}.
 */
@ApplicationScoped
public class MongoGameRepository implements GameRepository {

    private static final List<String> ACTIVE = List.of("lobby", "running", "paused");

    private static MongoCollection<GameRow> games() {
        return GameRow.mongoCollection();
    }

    private static MongoCollection<GameAdminRow> admins() {
        return GameAdminRow.mongoCollection();
    }

    private static MongoCollection<MembershipRow> members() {
        return MembershipRow.mongoCollection();
    }

    // ------------------------------------------------------------------ games

    @Override
    public Game create(Game g) {
        GameRow row = new GameRow();
        row.id = g.id();
        row.serverId = g.serverId();
        row.ownerId = g.ownerId();
        row.name = g.name();
        row.state = g.state().id();
        setRuleSet(row, g.ruleSet());
        row.settings = MongoJson.toDocument(g.settings() == null ? JsonNodeFactory.instance.objectNode() : g.settings());
        row.settingsVersion = g.settingsVersion();
        row.joinCode = g.joinCode();
        row.activeServerId = g.state().active() ? g.serverId() : null;
        row.createdAt = g.createdAt();
        row.updatedAt = g.createdAt();
        try {
            games().insertOne(row);
        } catch (RuntimeException e) {
            throw translate(e, g.joinCode());
        }
        return g;
    }

    @Override
    public Optional<Game> find(UUID id) {
        return Optional.ofNullable(games().find(eq("_id", id)).first()).map(MongoGameRepository::toGame);
    }

    @Override
    public Optional<Game> findByJoinCode(String joinCode) {
        return Optional.ofNullable(games().find(and(eq("joinCode", joinCode), eq("state", "lobby"))).first()).map(MongoGameRepository::toGame);
    }

    @Override
    public Optional<Game> activeOnServer(UUID serverId) {
        return Optional.ofNullable(games().find(and(eq("serverId", serverId), in("state", ACTIVE))).first()).map(MongoGameRepository::toGame);
    }

    @Override
    public long countOnServer(UUID serverId) {
        return games().countDocuments(eq("serverId", serverId));
    }

    @Override
    public boolean transition(UUID id, GameState from, GameState to, String joinCode, Instant at) {
        GameRow current = games().find(eq("_id", id)).first();
        if (current == null) {
            return false;
        }
        Bson code = joinCode == null ? Updates.unset("joinCode") : Updates.set("joinCode", joinCode);
        Bson active = to.active() ? Updates.set("activeServerId", current.serverId) : Updates.unset("activeServerId");
        try {
            return games().updateOne(and(eq("_id", id), eq("state", from.id())),
                    Updates.combine(Updates.set("state", to.id()), code, active, Updates.set("updatedAt", at))).getMatchedCount() == 1;
        } catch (RuntimeException e) {
            throw translate(e, joinCode);
        }
    }

    @Override
    public boolean rename(UUID id, String name) {
        return games().updateOne(eq("_id", id), Updates.combine(Updates.set("name", name), Updates.set("updatedAt", Instant.now()))).getMatchedCount() == 1;
    }

    @Override
    public boolean replaceSettings(UUID id, long expectedVersion, String ruleSet, JsonNode overrides, Instant at) {
        UUID saved = null;
        String builtin = null;
        if (ruleSet != null) {
            if (ruleSet.startsWith("builtin:")) {
                builtin = ruleSet;
            } else {
                saved = UUID.fromString(ruleSet);
            }
        }
        // the version in the filter makes this a compare-and-set: a second writer that read the same version finds no match
        return games().updateOne(and(eq("_id", id), eq("settingsVersion", expectedVersion)), Updates.combine(
                saved == null ? Updates.unset("ruleSetId") : Updates.set("ruleSetId", saved),
                builtin == null ? Updates.unset("builtinRuleSet") : Updates.set("builtinRuleSet", builtin),
                Updates.set("settings", MongoJson.toDocument(overrides)), Updates.set("settingsVersion", expectedVersion + 1), Updates.set("updatedAt", at)))
                .getMatchedCount() == 1;
    }

    @Override
    public long countUsingRuleSet(UUID ruleSetId) {
        return games().countDocuments(eq("ruleSetId", ruleSetId));
    }

    @Override
    public List<Game> allNotArchived() {
        return toGames(games().find(ne("state", "archived")));
    }

    @Override
    public List<Game> administeredBy(UUID userId) {
        Set<UUID> ids = new LinkedHashSet<>();
        admins().find(eq("userId", userId)).forEach(a -> ids.add(a.gameId));
        return ids.isEmpty() ? List.of() : toGames(games().find(and(in("_id", ids), ne("state", "archived"))));
    }

    @Override
    public List<Game> joinedBy(UUID userId) {
        Set<UUID> ids = new LinkedHashSet<>();
        members().find(and(eq("userId", userId), in("status", REQUESTED, ACCEPTED))).forEach(m -> ids.add(m.gameId));
        return ids.isEmpty() ? List.of() : toGames(games().find(and(in("_id", ids), ne("state", "archived"))));
    }

    @Override
    public List<Game> onServersAdministeredBy(UUID userId) {
        Set<UUID> serverIds = new LinkedHashSet<>();
        ServerAdminRow.<ServerAdminRow>mongoCollection().find(eq("userId", userId)).forEach(a -> serverIds.add(a.serverId));
        return serverIds.isEmpty() ? List.of() : toGames(games().find(and(in("serverId", serverIds), ne("state", "archived"))));
    }

    /** Newest first, then by id: the order the SQL adapter gives. */
    private static List<Game> toGames(Iterable<GameRow> rows) {
        return MongoSupport.list(rows).stream().map(MongoGameRepository::toGame)
                .sorted(java.util.Comparator.comparing(Game::createdAt).reversed().thenComparing(Game::id)).toList();
    }

    // ------------------------------------------------------------------ admins

    @Override
    public List<GameAdmin> adminsOf(UUID gameId) {
        // owner before co (the role names sort that way, backwards), then the oldest first
        return MongoSupport.list(admins().find(eq("gameId", gameId)).sort(Sorts.orderBy(Sorts.descending("role"), Sorts.ascending("createdAt", "_id")))).stream()
                .map(a -> new GameAdmin(a.gameId, a.userId, a.role)).toList();
    }

    @Override
    public Optional<GameAdmin> adminOf(UUID gameId, UUID userId) {
        return Optional.ofNullable(admins().find(and(eq("gameId", gameId), eq("userId", userId))).first()).map(a -> new GameAdmin(a.gameId, a.userId, a.role));
    }

    @Override
    public boolean addAdmin(UUID gameId, UUID userId, String role) {
        if (adminOf(gameId, userId).isPresent()) {
            return false;
        }
        GameAdminRow row = new GameAdminRow();
        row.id = Ids.newId();
        row.gameId = gameId;
        row.userId = userId;
        row.role = role;
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        try {
            admins().insertOne(row);
        } catch (RuntimeException e) {
            if (MongoSupport.isDuplicate(e)) {
                return false;
            }
            throw e;
        }
        return true;
    }

    @Override
    public boolean removeCoAdmin(UUID gameId, UUID userId) {
        return admins().deleteMany(and(eq("gameId", gameId), eq("userId", userId), eq("role", ADMIN_CO))).getDeletedCount() > 0;
    }

    @Override
    public int removeCoAdminsOnServer(UUID serverId, UUID userId) {
        Set<UUID> gameIds = new LinkedHashSet<>();
        games().find(eq("serverId", serverId)).forEach(g -> gameIds.add(g.id));
        if (gameIds.isEmpty()) {
            return 0;
        }
        return (int) admins().deleteMany(and(eq("userId", userId), eq("role", ADMIN_CO), in("gameId", gameIds))).getDeletedCount();
    }

    // ------------------------------------------------------------------ members

    @Override
    public Optional<Membership> membership(UUID gameId, UUID userId) {
        return Optional.ofNullable(members().find(and(eq("gameId", gameId), eq("userId", userId))).first()).map(MongoGameRepository::toMembership);
    }

    @Override
    public Optional<Membership> membershipById(UUID gameId, UUID membershipId) {
        return Optional.ofNullable(members().find(and(eq("gameId", gameId), eq("_id", membershipId))).first()).map(MongoGameRepository::toMembership);
    }

    @Override
    public List<Membership> memberships(UUID gameId, String status) {
        return MongoSupport.list(members().find(and(eq("gameId", gameId), eq("status", status))).sort(MongoSupport.oldestFirst())).stream()
                .map(MongoGameRepository::toMembership).toList();
    }

    @Override
    public boolean addMembership(Membership m) {
        if (membership(m.gameId(), m.userId()).isPresent()) {
            return false;
        }
        MembershipRow row = new MembershipRow();
        row.id = m.id();
        row.gameId = m.gameId();
        row.userId = m.userId();
        row.role = m.role();
        row.status = m.status();
        row.createdAt = m.createdAt();
        row.updatedAt = m.createdAt();
        try {
            members().insertOne(row);
        } catch (RuntimeException e) {
            if (MongoSupport.isDuplicate(e)) {
                // the same person asked twice at the same moment: the unique index stops the second
                return false;
            }
            throw e;
        }
        return true;
    }

    @Override
    public boolean setMembershipStatus(UUID membershipId, String from, String to) {
        return members().updateOne(and(eq("_id", membershipId), eq("status", from)), Updates.combine(Updates.set("status", to), Updates.set("updatedAt", Instant.now())))
                .getMatchedCount() == 1;
    }

    @Override
    public void setMembershipRole(UUID membershipId, String role) {
        members().updateOne(eq("_id", membershipId), Updates.combine(Updates.set("role", role), Updates.set("updatedAt", Instant.now())));
    }

    // ------------------------------------------------------------------ helpers

    /** Names the failure the way the SQL adapter does, by the index that refused the write. */
    private static RuntimeException translate(RuntimeException e, String joinCode) {
        if (MongoSupport.isDuplicate(e)) {
            String text = MongoSupport.text(e);
            if (text.contains("game_one_active_per_server")) {
                return new ActiveGameExistsException(null);
            }
            if (text.contains("game_join_code_unique")) {
                return new JoinCodeTakenException(joinCode);
            }
        }
        return e;
    }

    private static void setRuleSet(GameRow row, String ruleSet) {
        row.ruleSetId = null;
        row.builtinRuleSet = null;
        if (ruleSet != null) {
            if (ruleSet.startsWith("builtin:")) {
                row.builtinRuleSet = ruleSet;
            } else {
                row.ruleSetId = UUID.fromString(ruleSet);
            }
        }
    }

    private static Game toGame(GameRow r) {
        JsonNode settings = r.settings == null ? JsonNodeFactory.instance.objectNode() : MongoJson.toJson(r.settings);
        return new Game(r.id, r.serverId, r.ownerId, r.name, GameState.ofId(r.state), r.joinCode, r.ruleSetId != null ? r.ruleSetId.toString() : r.builtinRuleSet,
                settings, r.settingsVersion, r.createdAt);
    }

    private static Membership toMembership(MembershipRow m) {
        return new Membership(m.id, m.gameId, m.userId, m.role, m.status, m.createdAt);
    }
}
