/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** {@link TestDb} for MongoDB. */
@ApplicationScoped
public class MongoTestDb implements TestDb {

    @Inject
    MongoClient client;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    private MongoDatabase db() {
        return client.getDatabase(databaseName);
    }

    @Override
    public String passwordHashOf(String email) {
        Document d = db().getCollection("app_user").find(eq("liveEmail", email.toLowerCase(Locale.ROOT))).first();
        return d == null ? null : d.getString("passwordHash");
    }

    @Override
    public long accountsHolding(String text) {
        return db().getCollection("app_user").countDocuments(or(eq("passwordHash", text), eq("displayName", text), eq("email", text)));
    }

    @Override
    public boolean hasRefreshTokenWithHash(String tokenHash) {
        return db().getCollection("refresh_token").countDocuments(eq("tokenHash", tokenHash)) > 0;
    }

    @Override
    public Map<String, List<String>> everyRowAsText() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String name : db().listCollectionNames()) {
            List<String> rows = new ArrayList<>();
            db().getCollection(name).find().forEach(d -> rows.add(d.toJson()));
            out.put(name, rows);
        }
        return out;
    }

    @Override
    public List<String> serverTokenFields(String serverId) {
        // ids are standard UUIDs; the driver turns the text into one when the collection is read with the UUID type
        Document d = db().getCollection("server").find(eq("_id", UUID.fromString(serverId))).first();
        if (d == null) {
            throw new IllegalStateException("no such server: " + serverId);
        }
        return List.of(d.getString("tokenHash"), d.getString("tokenPrefix"));
    }
}
