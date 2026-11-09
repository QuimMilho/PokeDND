/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.persistence.sql.RefTables.RefBundle;
import net.quimmilho.pokednd.api.persistence.sql.RefTables.RefDocument;
import net.quimmilho.pokednd.api.persistence.sql.RefTables.RefEntry;
import net.quimmilho.pokednd.api.persistence.sql.RefTables.RefSpecies;

/** The SQL adapter of the reference data: Hibernate ORM with Panache (chapter 16.3). */
@ApplicationScoped
public class SqlReferenceRepository implements ReferenceRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Optional<LoadedBundle> loadedBundle() {
        RefBundle b = em.find(RefBundle.class, 1);
        return b == null ? Optional.empty() : Optional.of(new LoadedBundle(b.manifestHash, b.bundleFormat, b.cobblemon, b.loadedAt));
    }

    @Override
    @Transactional
    public void replaceAll(ReferenceBundle bundle) {
        em.createQuery("delete from RefSpecies").executeUpdate();
        em.createQuery("delete from RefEntry").executeUpdate();
        em.createQuery("delete from RefDocument").executeUpdate();
        em.createQuery("delete from RefBundle").executeUpdate();

        for (SpeciesRecord s : bundle.species()) {
            RefSpecies e = new RefSpecies();
            e.id = s.id();
            e.dex = s.dex();
            e.name = s.name();
            e.generation = s.generation();
            e.type1 = s.types().isEmpty() ? "normal" : s.types().get(0);
            e.type2 = s.types().size() > 1 ? s.types().get(1) : null;
            e.playable = s.playable();
            e.data = s.data();
            e.derived = s.derived();
            e.evolutions = s.evolutions();
            e.learnsets = s.learnsets();
            em.persist(e);
        }
        for (NamedRecord n : bundle.entries()) {
            RefEntry e = new RefEntry();
            e.pk = n.kind().id() + ":" + n.id();
            e.kind = n.kind().id();
            e.entryId = n.id();
            e.name = n.name();
            e.grp = n.group();
            e.data = n.data();
            em.persist(e);
        }
        bundle.documents().forEach((name, data) -> {
            RefDocument d = new RefDocument();
            d.name = name;
            d.data = data;
            em.persist(d);
        });
        RefBundle b = new RefBundle();
        b.id = 1;
        b.manifestHash = bundle.info().hash();
        b.bundleFormat = bundle.info().format();
        b.cobblemon = bundle.info().cobblemon();
        b.loadedAt = bundle.info().loadedAt();
        em.persist(b);
        em.flush();
        em.clear();
    }

    @Override
    @Transactional
    public Page<SpeciesRecord> findSpecies(SpeciesFilter f, String cursor, int limit) {
        StringBuilder q = new StringBuilder("from RefSpecies s where 1 = 1");
        Map<String, Object> p = new HashMap<>();
        if (!f.generations().isEmpty()) {
            q.append(" and s.generation in :gens");
            p.put("gens", f.generations());
        }
        if (!f.types().isEmpty()) {
            q.append(f.primaryTypeOnly() ? " and s.type1 in :types" : " and (s.type1 in :types or s.type2 in :types)");
            p.put("types", f.types());
        }
        if (f.query() != null && !f.query().isBlank()) {
            q.append(" and (lower(s.name) like :q or s.id like :q)");
            p.put("q", like(f.query()));
        }
        if (f.playable() != null) {
            q.append(" and s.playable = :playable");
            p.put("playable", f.playable());
        }
        if (cursor != null && !cursor.isBlank()) {
            int colon = cursor.indexOf(':');
            if (colon < 1) {
                throw new IllegalArgumentException("bad cursor");
            }
            q.append(" and (s.dex > :cdex or (s.dex = :cdex and s.id > :cid))");
            p.put("cdex", Integer.parseInt(cursor.substring(0, colon)));
            p.put("cid", cursor.substring(colon + 1));
        }
        q.append(" order by s.dex, s.id");
        TypedQuery<RefSpecies> query = em.createQuery(q.toString(), RefSpecies.class);
        p.forEach(query::setParameter);
        query.setMaxResults(limit + 1);
        List<RefSpecies> rows = query.getResultList();
        boolean more = rows.size() > limit;
        List<SpeciesRecord> items = rows.stream().limit(limit).map(SqlReferenceRepository::toRecord).toList();
        String next = more ? items.get(items.size() - 1).dex() + ":" + items.get(items.size() - 1).id() : null;
        return new Page<>(items, next);
    }

    @Override
    @Transactional
    public Optional<SpeciesRecord> species(String id) {
        return Optional.ofNullable(em.find(RefSpecies.class, id)).map(SqlReferenceRepository::toRecord);
    }

    @Override
    @Transactional
    public List<SpeciesRecord> speciesByDex(Collection<Integer> dexNumbers) {
        if (dexNumbers.isEmpty()) {
            return List.of();
        }
        return em.createQuery("from RefSpecies s where s.dex in :dex order by s.dex, s.id", RefSpecies.class)
                .setParameter("dex", dexNumbers).getResultList().stream().map(SqlReferenceRepository::toRecord).toList();
    }

    @Override
    @Transactional
    public List<SpeciesRecord> speciesByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("from RefSpecies s where s.id in :ids order by s.dex, s.id", RefSpecies.class)
                .setParameter("ids", ids).getResultList().stream().map(SqlReferenceRepository::toRecord).toList();
    }

    @Override
    @Transactional
    public Page<NamedRecord> findEntries(EntryKind kind, String group, String query, String cursor, int limit) {
        StringBuilder q = new StringBuilder("from RefEntry e where e.kind = :kind");
        Map<String, Object> p = new HashMap<>();
        p.put("kind", kind.id());
        if (group != null && !group.isBlank()) {
            q.append(" and lower(e.grp) = :grp");
            p.put("grp", group.toLowerCase(Locale.ROOT));
        }
        if (query != null && !query.isBlank()) {
            q.append(" and (lower(e.name) like :q or e.entryId like :q)");
            p.put("q", like(query));
        }
        if (cursor != null && !cursor.isBlank()) {
            q.append(" and e.entryId > :cursor");
            p.put("cursor", cursor);
        }
        q.append(" order by e.entryId");
        TypedQuery<RefEntry> tq = em.createQuery(q.toString(), RefEntry.class);
        p.forEach(tq::setParameter);
        tq.setMaxResults(limit + 1);
        List<RefEntry> rows = tq.getResultList();
        boolean more = rows.size() > limit;
        List<NamedRecord> items = rows.stream().limit(limit).map(SqlReferenceRepository::toRecord).toList();
        return new Page<>(items, more ? items.get(items.size() - 1).id() : null);
    }

    @Override
    @Transactional
    public Optional<NamedRecord> entry(EntryKind kind, String id) {
        return Optional.ofNullable(em.find(RefEntry.class, kind.id() + ":" + id)).map(SqlReferenceRepository::toRecord);
    }

    @Override
    @Transactional
    public List<NamedRecord> entries(EntryKind kind, Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> pks = new ArrayList<>();
        ids.forEach(i -> pks.add(kind.id() + ":" + i));
        return em.createQuery("from RefEntry e where e.pk in :pks order by e.entryId", RefEntry.class)
                .setParameter("pks", pks).getResultList().stream().map(SqlReferenceRepository::toRecord).toList();
    }

    @Override
    @Transactional
    public Optional<JsonNode> document(String name) {
        return Optional.ofNullable(em.find(RefDocument.class, name)).map(d -> d.data);
    }

    @Override
    @Transactional
    public Counts counts() {
        return new Counts(count("select count(s) from RefSpecies s"), entryCount(EntryKind.MOVE), entryCount(EntryKind.ITEM),
                entryCount(EntryKind.ABILITY), count("select count(d) from RefDocument d"));
    }

    private long entryCount(EntryKind kind) {
        return em.createQuery("select count(e) from RefEntry e where e.kind = :k", Long.class).setParameter("k", kind.id()).getSingleResult();
    }

    private long count(String hql) {
        return em.createQuery(hql, Long.class).getSingleResult();
    }

    private static String like(String text) {
        return "%" + text.strip().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
    }

    private static SpeciesRecord toRecord(RefSpecies s) {
        List<String> types = s.type2 == null ? List.of(s.type1) : List.of(s.type1, s.type2);
        return new SpeciesRecord(s.id, s.dex, s.name, s.generation, types, s.playable, s.data, s.derived, s.evolutions, s.learnsets);
    }

    private static NamedRecord toRecord(RefEntry e) {
        return new NamedRecord(EntryKind.valueOf(e.kind.toUpperCase(Locale.ROOT)), e.entryId, e.name, e.grp, e.data);
    }
}
