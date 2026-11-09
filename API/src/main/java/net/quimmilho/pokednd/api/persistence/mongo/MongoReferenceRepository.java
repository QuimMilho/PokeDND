/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.Sorts;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.RefBundle;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.RefDocument;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.RefEntry;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.RefSpecies;
import org.bson.conversions.Bson;

/**
 * The MongoDB adapter of the reference data (chapter 19.2). The collections are a cache of the data bundle, replaced whenever its
 * manifest changes. MongoDB cannot swap them in one step, so {@link #replaceAll} empties the collections first and writes the bundle
 * record <em>last</em>: a load that stops half way leaves no bundle record, and the next start loads again.
 */
@ApplicationScoped
public class MongoReferenceRepository implements ReferenceRepository {

    private static final int BATCH = 500;

    private static MongoCollection<RefBundle> bundles() {
        return RefBundle.mongoCollection();
    }

    private static MongoCollection<RefSpecies> species() {
        return RefSpecies.mongoCollection();
    }

    private static MongoCollection<RefEntry> entries() {
        return RefEntry.mongoCollection();
    }

    private static MongoCollection<RefDocument> documents() {
        return RefDocument.mongoCollection();
    }

    @Override
    public Optional<LoadedBundle> loadedBundle() {
        return Optional.ofNullable(bundles().find(eq("_id", 1)).first()).map(b -> new LoadedBundle(b.manifestHash, b.bundleFormat, b.cobblemon, b.loadedAt));
    }

    @Override
    public void replaceAll(ReferenceBundle bundle) {
        bundles().deleteMany(new org.bson.Document());
        species().deleteMany(new org.bson.Document());
        entries().deleteMany(new org.bson.Document());
        documents().deleteMany(new org.bson.Document());

        List<RefSpecies> batch = new ArrayList<>();
        for (SpeciesRecord s : bundle.species()) {
            RefSpecies e = new RefSpecies();
            e.id = s.id();
            e.dex = s.dex();
            e.name = s.name();
            e.generation = s.generation();
            e.type1 = s.types().isEmpty() ? "normal" : s.types().get(0);
            e.type2 = s.types().size() > 1 ? s.types().get(1) : null;
            e.playable = s.playable();
            e.data = MongoJson.toDocument(s.data());
            e.derived = MongoJson.toDocument(s.derived());
            e.evolutions = MongoJson.toDocument(s.evolutions());
            e.learnsets = MongoJson.toDocument(s.learnsets());
            batch.add(e);
            if (batch.size() >= BATCH) {
                species().insertMany(batch, new InsertManyOptions().ordered(false));
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            species().insertMany(batch, new InsertManyOptions().ordered(false));
        }

        List<RefEntry> entryBatch = new ArrayList<>();
        for (NamedRecord n : bundle.entries()) {
            RefEntry e = new RefEntry();
            e.pk = n.kind().id() + ":" + n.id();
            e.kind = n.kind().id();
            e.entryId = n.id();
            e.name = n.name();
            e.grp = n.group();
            e.data = MongoJson.toDocument(n.data());
            entryBatch.add(e);
            if (entryBatch.size() >= BATCH) {
                entries().insertMany(entryBatch, new InsertManyOptions().ordered(false));
                entryBatch = new ArrayList<>();
            }
        }
        if (!entryBatch.isEmpty()) {
            entries().insertMany(entryBatch, new InsertManyOptions().ordered(false));
        }

        List<RefDocument> docs = new ArrayList<>();
        bundle.documents().forEach((name, data) -> {
            RefDocument d = new RefDocument();
            d.name = name;
            d.data = MongoJson.toDocument(data);
            docs.add(d);
        });
        if (!docs.isEmpty()) {
            documents().insertMany(docs);
        }

        RefBundle b = new RefBundle();
        b.id = 1;
        b.manifestHash = bundle.info().hash();
        b.bundleFormat = bundle.info().format();
        b.cobblemon = bundle.info().cobblemon();
        b.loadedAt = bundle.info().loadedAt();
        bundles().insertOne(b);
    }

    @Override
    public Page<SpeciesRecord> findSpecies(SpeciesFilter f, String cursor, int limit) {
        List<Bson> filters = new ArrayList<>();
        if (!f.generations().isEmpty()) {
            filters.add(in("generation", f.generations()));
        }
        if (!f.types().isEmpty()) {
            filters.add(f.primaryTypeOnly() ? in("type1", f.types()) : or(in("type1", f.types()), in("type2", f.types())));
        }
        if (f.query() != null && !f.query().isBlank()) {
            String q = Pattern.quote(clean(f.query()));
            filters.add(or(regex("name", q, "i"), regex("_id", q)));
        }
        if (f.playable() != null) {
            filters.add(eq("playable", f.playable()));
        }
        if (cursor != null && !cursor.isBlank()) {
            int colon = cursor.indexOf(':');
            if (colon < 1) {
                throw new IllegalArgumentException("bad cursor");
            }
            int dex = Integer.parseInt(cursor.substring(0, colon));
            String id = cursor.substring(colon + 1);
            filters.add(or(gt("dex", dex), and(eq("dex", dex), gt("_id", id))));
        }
        Bson filter = filters.isEmpty() ? new org.bson.Document() : and(filters);
        List<RefSpecies> rows = MongoSupport.list(species().find(filter).sort(Sorts.ascending("dex", "_id")).limit(limit + 1));
        boolean more = rows.size() > limit;
        List<SpeciesRecord> items = rows.stream().limit(limit).map(MongoReferenceRepository::toRecord).toList();
        String next = more ? items.get(items.size() - 1).dex() + ":" + items.get(items.size() - 1).id() : null;
        return new Page<>(items, next);
    }

    @Override
    public Optional<SpeciesRecord> species(String id) {
        return Optional.ofNullable(species().find(eq("_id", id)).first()).map(MongoReferenceRepository::toRecord);
    }

    @Override
    public List<SpeciesRecord> speciesByDex(Collection<Integer> dexNumbers) {
        if (dexNumbers.isEmpty()) {
            return List.of();
        }
        return MongoSupport.list(species().find(in("dex", dexNumbers)).sort(Sorts.ascending("dex", "_id"))).stream().map(MongoReferenceRepository::toRecord).toList();
    }

    @Override
    public List<SpeciesRecord> speciesByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return MongoSupport.list(species().find(in("_id", ids)).sort(Sorts.ascending("dex", "_id"))).stream().map(MongoReferenceRepository::toRecord).toList();
    }

    @Override
    public Page<NamedRecord> findEntries(EntryKind kind, String group, String query, String cursor, int limit) {
        List<Bson> filters = new ArrayList<>();
        filters.add(eq("kind", kind.id()));
        if (group != null && !group.isBlank()) {
            filters.add(regex("grp", "^" + Pattern.quote(group.toLowerCase(Locale.ROOT)) + "$", "i"));
        }
        if (query != null && !query.isBlank()) {
            String q = Pattern.quote(clean(query));
            filters.add(or(regex("name", q, "i"), regex("entryId", q)));
        }
        if (cursor != null && !cursor.isBlank()) {
            filters.add(gt("entryId", cursor));
        }
        List<RefEntry> rows = MongoSupport.list(entries().find(and(filters)).sort(Sorts.ascending("entryId")).limit(limit + 1));
        boolean more = rows.size() > limit;
        List<NamedRecord> items = rows.stream().limit(limit).map(MongoReferenceRepository::toRecord).toList();
        return new Page<>(items, more ? items.get(items.size() - 1).id() : null);
    }

    @Override
    public Optional<NamedRecord> entry(EntryKind kind, String id) {
        return Optional.ofNullable(entries().find(eq("_id", kind.id() + ":" + id)).first()).map(MongoReferenceRepository::toRecord);
    }

    @Override
    public List<NamedRecord> entries(EntryKind kind, Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> pks = new ArrayList<>();
        ids.forEach(i -> pks.add(kind.id() + ":" + i));
        return MongoSupport.list(entries().find(in("_id", pks)).sort(Sorts.ascending("entryId"))).stream().map(MongoReferenceRepository::toRecord).toList();
    }

    @Override
    public Optional<JsonNode> document(String name) {
        return Optional.ofNullable(documents().find(eq("_id", name)).first()).map(d -> MongoJson.toJson(d.data));
    }

    @Override
    public Counts counts() {
        return new Counts(species().countDocuments(), entries().countDocuments(eq("kind", EntryKind.MOVE.id())),
                entries().countDocuments(eq("kind", EntryKind.ITEM.id())), entries().countDocuments(eq("kind", EntryKind.ABILITY.id())), documents().countDocuments());
    }

    /** The text to look for: stripped and lower case, without the characters that the SQL adapter's "like" takes out. */
    private static String clean(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
    }

    private static SpeciesRecord toRecord(RefSpecies s) {
        List<String> types = s.type2 == null ? List.of(s.type1) : List.of(s.type1, s.type2);
        return new SpeciesRecord(s.id, s.dex, s.name, s.generation, types, s.playable, MongoJson.toJson(s.data), MongoJson.toJson(s.derived),
                MongoJson.toJson(s.evolutions), MongoJson.toJson(s.learnsets));
    }

    private static NamedRecord toRecord(RefEntry e) {
        return new NamedRecord(EntryKind.valueOf(e.kind.toUpperCase(Locale.ROOT)), e.entryId, e.name, e.grp, MongoJson.toJson(e.data));
    }
}
