/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository;
import net.quimmilho.pokednd.api.persistence.sql.MinecraftTables.LinkCodeRow;
import net.quimmilho.pokednd.api.persistence.sql.MinecraftTables.McLinkRow;

/** The SQL adapter of the Minecraft linking (chapter 16.3). */
@ApplicationScoped
public class SqlMinecraftLinkRepository implements MinecraftLinkRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Optional<McLink> findByMcUuid(UUID mcUuid) {
        return em.createQuery("from McLinkRow l where l.mcUuid = :u", McLinkRow.class).setParameter("u", mcUuid).getResultStream().findFirst()
                .map(SqlMinecraftLinkRepository::toLink);
    }

    @Override
    @Transactional
    public List<McLink> linksOfUser(UUID userId) {
        return em.createQuery("from McLinkRow l where l.userId = :u order by l.linkedAt", McLinkRow.class).setParameter("u", userId)
                .getResultList().stream().map(SqlMinecraftLinkRepository::toLink).toList();
    }

    @Override
    @Transactional
    public McLink addLink(McLink link) {
        if (findByMcUuid(link.mcUuid()).isPresent()) {
            throw new McAlreadyLinkedException(link.mcUuid());
        }
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
            em.persist(row);
            em.flush();
        } catch (PersistenceException e) {
            // two links of the same Minecraft account at the same moment: the unique index stops the second
            throw new McAlreadyLinkedException(link.mcUuid());
        }
        return link;
    }

    @Override
    @Transactional
    public boolean removeLink(UUID userId, UUID mcUuid) {
        return em.createQuery("delete from McLinkRow l where l.userId = :u and l.mcUuid = :m").setParameter("u", userId)
                .setParameter("m", mcUuid).executeUpdate() > 0;
    }

    @Override
    @Transactional
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
        em.persist(row);
        em.flush();
    }

    @Override
    @Transactional
    public Optional<LinkCode> findCode(String code) {
        return em.createQuery("from LinkCodeRow c where c.code = :c", LinkCodeRow.class).setParameter("c", code).getResultStream().findFirst()
                .map(c -> new LinkCode(c.id, c.code, c.direction, c.userId, c.mcUuid, c.mcName, c.expiresAt, c.usedAt));
    }

    @Override
    @Transactional
    public boolean useCode(UUID codeId, Instant at) {
        return em.createQuery("update LinkCodeRow c set c.usedAt = :at, c.updatedAt = :at where c.id = :id and c.usedAt is null")
                .setParameter("at", at).setParameter("id", codeId).executeUpdate() == 1;
    }

    @Override
    @Transactional
    public void discardCodes(String direction, UUID userId, UUID mcUuid, Instant at) {
        boolean forUser = FOR_APP_USER.equals(direction);
        String who = forUser ? "c.userId = :s" : "c.mcUuid = :s";
        em.createQuery("update LinkCodeRow c set c.usedAt = :at, c.updatedAt = :at where c.direction = :d and " + who + " and c.usedAt is null")
                .setParameter("at", at).setParameter("d", direction).setParameter("s", forUser ? userId : mcUuid).executeUpdate();
    }

    @Override
    @Transactional
    public int deleteCodesExpiredBefore(Instant before) {
        return em.createQuery("delete from LinkCodeRow c where c.expiresAt < :b").setParameter("b", before).executeUpdate();
    }

    private static McLink toLink(McLinkRow l) {
        return new McLink(l.id, l.userId, l.mcUuid, l.mcName, l.source, l.linkedAt);
    }
}
