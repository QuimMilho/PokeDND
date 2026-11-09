/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.LinkCodeRow;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.McLinkRow;

/** The MongoDB adapter of the linked Minecraft accounts and their linking codes (chapter 16.3). */
@ApplicationScoped
public class MongoMinecraftLinkRepository implements MinecraftLinkRepository {

    private static MongoCollection<McLinkRow> links() {
        return McLinkRow.mongoCollection();
    }

    private static MongoCollection<LinkCodeRow> codes() {
        return LinkCodeRow.mongoCollection();
    }

    @Override
    public Optional<McLink> findByMcUuid(UUID mcUuid) {
        return Optional.ofNullable(links().find(eq("mcUuid", mcUuid)).first()).map(MongoMinecraftLinkRepository::toLink);
    }

    @Override
    public List<McLink> linksOfUser(UUID userId) {
        return MongoSupport.list(links().find(eq("userId", userId)).sort(com.mongodb.client.model.Sorts.ascending("linkedAt", "_id"))).stream()
                .map(MongoMinecraftLinkRepository::toLink).toList();
    }

    @Override
    public McLink addLink(McLink link) {
        McLinkRow row = new McLinkRow();
        row.id = link.id();
        row.userId = link.userId();
        row.mcUuid = link.mcUuid();
        row.mcName = link.mcName();
        row.source = link.source();
        row.linkedAt = link.linkedAt();
        row.createdAt = link.linkedAt();
        row.updatedAt = link.linkedAt();
        try {
            links().insertOne(row);
        } catch (RuntimeException e) {
            if (MongoSupport.isDuplicate(e)) {
                // the Minecraft account is linked already, or was linked a moment ago by someone else: the unique index decides
                throw new McAlreadyLinkedException(link.mcUuid());
            }
            throw e;
        }
        return link;
    }

    @Override
    public boolean removeLink(UUID userId, UUID mcUuid) {
        return links().deleteMany(and(eq("userId", userId), eq("mcUuid", mcUuid))).getDeletedCount() > 0;
    }

    @Override
    public void saveCode(LinkCode c) {
        LinkCodeRow row = new LinkCodeRow();
        row.id = c.id();
        row.code = c.code();
        row.direction = c.direction();
        row.userId = c.userId();
        row.mcUuid = c.mcUuid();
        row.mcName = c.mcName();
        row.expiresAt = c.expiresAt();
        row.usedAt = c.usedAt();
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        codes().insertOne(row);
    }

    @Override
    public Optional<LinkCode> findCode(String code) {
        return Optional.ofNullable(codes().find(eq("code", code)).first())
                .map(c -> new LinkCode(c.id, c.code, c.direction, c.userId, c.mcUuid, c.mcName, c.expiresAt, c.usedAt));
    }

    @Override
    public boolean useCode(UUID codeId, Instant at) {
        return codes().updateOne(and(eq("_id", codeId), eq("usedAt", null)), Updates.combine(Updates.set("usedAt", at), Updates.set("updatedAt", at)))
                .getModifiedCount() == 1;
    }

    @Override
    public void discardCodes(String direction, UUID userId, UUID mcUuid, Instant at) {
        boolean forUser = FOR_APP_USER.equals(direction);
        codes().updateMany(and(eq("direction", direction), forUser ? eq("userId", userId) : eq("mcUuid", mcUuid), eq("usedAt", null)),
                Updates.combine(Updates.set("usedAt", at), Updates.set("updatedAt", at)));
    }

    @Override
    public int deleteCodesExpiredBefore(Instant before) {
        return (int) codes().deleteMany(lt("expiresAt", before)).getDeletedCount();
    }

    private static McLink toLink(McLinkRow l) {
        return new McLink(l.id, l.userId, l.mcUuid, l.mcName, l.source, l.linkedAt);
    }
}
