/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.ne;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.ServerAdminRow;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.ServerRow;
import org.bson.conversions.Bson;

/** The MongoDB adapter of the servers and their admins (chapter 16.3). */
@ApplicationScoped
public class MongoServerRepository implements ServerRepository {

    private static MongoCollection<ServerRow> servers() {
        return ServerRow.mongoCollection();
    }

    private static MongoCollection<ServerAdminRow> admins() {
        return ServerAdminRow.mongoCollection();
    }

    @Override
    public Server create(Server s) {
        ServerRow row = new ServerRow();
        row.id = s.id();
        row.name = s.name();
        row.nameKey = key(s.name());
        row.tokenHash = s.tokenHash();
        row.tokenPrefix = s.tokenPrefix();
        row.bluemapUrl = s.bluemapUrl();
        row.status = s.status();
        row.lastSeenAt = s.lastSeenAt();
        row.modVersion = s.modVersion();
        row.mcVersion = s.mcVersion();
        row.createdAt = s.createdAt();
        row.updatedAt = s.createdAt();
        try {
            servers().insertOne(row);
        } catch (RuntimeException e) {
            if (MongoSupport.isDuplicate(e)) {
                throw new ServerNameTakenException(s.name());
            }
            throw e;
        }
        return s;
    }

    @Override
    public Optional<Server> find(UUID id) {
        return Optional.ofNullable(servers().find(eq("_id", id)).first()).map(MongoServerRepository::toServer);
    }

    @Override
    public Optional<Server> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(servers().find(eq("tokenHash", tokenHash)).first()).map(MongoServerRepository::toServer);
    }

    @Override
    public List<Server> all() {
        return MongoSupport.list(servers().find().sort(Sorts.ascending("nameKey", "_id"))).stream().map(MongoServerRepository::toServer).toList();
    }

    @Override
    public List<Server> administeredBy(UUID userId) {
        List<UUID> ids = new ArrayList<>();
        admins().find(eq("userId", userId)).forEach(a -> ids.add(a.serverId));
        if (ids.isEmpty()) {
            return List.of();
        }
        return MongoSupport.list(servers().find(in("_id", ids)).sort(Sorts.ascending("nameKey", "_id"))).stream().map(MongoServerRepository::toServer).toList();
    }

    @Override
    public boolean update(UUID id, String name, String bluemapUrl) {
        try {
            Bson url = bluemapUrl == null ? Updates.unset("bluemapUrl") : Updates.set("bluemapUrl", bluemapUrl);
            return servers().updateOne(eq("_id", id), Updates.combine(Updates.set("name", name), Updates.set("nameKey", key(name)), url,
                    Updates.set("updatedAt", Instant.now()))).getMatchedCount() == 1;
        } catch (RuntimeException e) {
            if (MongoSupport.isDuplicate(e)) {
                throw new ServerNameTakenException(name);
            }
            throw e;
        }
    }

    @Override
    public boolean replaceToken(UUID id, String tokenHash, String tokenPrefix) {
        return servers().updateOne(eq("_id", id), Updates.combine(Updates.set("tokenHash", tokenHash), Updates.set("tokenPrefix", tokenPrefix),
                Updates.set("updatedAt", Instant.now()))).getMatchedCount() == 1;
    }

    @Override
    public boolean delete(UUID id) {
        boolean deleted = servers().deleteOne(eq("_id", id)).getDeletedCount() == 1;
        // the admin list goes with the server (the SQL tables do this with ON DELETE CASCADE)
        admins().deleteMany(eq("serverId", id));
        return deleted;
    }

    @Override
    public List<UUID> adminsOf(UUID serverId) {
        List<UUID> out = new ArrayList<>();
        admins().find(eq("serverId", serverId)).sort(MongoSupport.oldestFirst()).forEach(a -> out.add(a.userId));
        return out;
    }

    @Override
    public boolean addAdmin(UUID serverId, UUID userId) {
        if (isAdmin(serverId, userId)) {
            return false;
        }
        ServerAdminRow row = new ServerAdminRow();
        row.id = Ids.newId();
        row.serverId = serverId;
        row.userId = userId;
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        try {
            admins().insertOne(row);
        } catch (RuntimeException e) {
            if (MongoSupport.isDuplicate(e)) {
                // the same person added twice at the same moment
                return false;
            }
            throw e;
        }
        return true;
    }

    @Override
    public boolean removeAdmin(UUID serverId, UUID userId) {
        return admins().deleteMany(and(eq("serverId", serverId), eq("userId", userId))).getDeletedCount() > 0;
    }

    @Override
    public boolean isAdmin(UUID serverId, UUID userId) {
        return admins().countDocuments(and(eq("serverId", serverId), eq("userId", userId))) > 0;
    }

    @Override
    public void recordStatus(UUID id, String status, Instant seenAt, String modVersion, String mcVersion) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set("status", status));
        updates.add(Updates.set("lastSeenAt", seenAt));
        if (modVersion != null) {
            updates.add(Updates.set("modVersion", modVersion));
        }
        if (mcVersion != null) {
            updates.add(Updates.set("mcVersion", mcVersion));
        }
        updates.add(Updates.set("updatedAt", seenAt));
        servers().updateOne(eq("_id", id), Updates.combine(updates));
    }

    @Override
    public int markAllOffline() {
        return (int) servers().updateMany(ne("status", OFFLINE), Updates.combine(Updates.set("status", OFFLINE), Updates.set("updatedAt", Instant.now())))
                .getModifiedCount();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static Server toServer(ServerRow r) {
        return new Server(r.id, r.name, r.tokenHash, r.tokenPrefix, r.bluemapUrl, r.status, r.lastSeenAt, r.modVersion, r.mcVersion, r.createdAt);
    }
}
